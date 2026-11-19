package com.aiyu.findex.core.link

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.StorageDrive
import com.aiyu.findex.core.operation.FileOperationExecutor
import com.aiyu.findex.core.vault.VaultKeyManager
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * The phone side of Findex Link: HTTPS (this phone's Keystore identity) bound to the Wi-Fi / hotspot IPv4
 * only (never mobile data or IPv6), serving the storage
 * volumes to paired computers. Same API as the desktop server (`development/docs/ECOSYSTEM.md`).
 * Only the two pairing routes work without a bearer token; deletes go to the Findex recycle bin.
 */
internal class LinkHttpServer(
    host: String,
    port: Int,
    private val context: Context,
    private val repo: LinkRepository,
    drives: List<StorageDrive>,
    private val executor: FileOperationExecutor,
    private val onReceived: (from: String, file: File) -> Unit,
    private val onText: (from: String, text: String) -> Unit
) : NanoHTTPD(host, port) {

    /** Root name on the wire to folder; names are made unique. */
    private val roots: Map<String, File> = buildMap {
        drives.forEach { d ->
            var name = d.name.replace('/', '-')
            var n = 2
            while (containsKey(name)) name = "${d.name} (${n++})"
            put(name, File(d.path))
        }
    }
    private val inbox = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Findex")
    private val pending = ConcurrentHashMap<String, Pending>()
    private val transfers = AtomicInteger()
    @Volatile private var lastActivity = System.currentTimeMillis()

    private class Pending(val clientId: String, val name: String, val platform: String, val port: Int, val commit: ByteArray, val nonce: ByteArray, val at: Long)

    private class ApiException(val status: Response.Status, val code: String) : Exception(code)

    /** Zero while a transfer is streaming. */
    fun idleMillis(): Long = if (transfers.get() > 0) 0 else System.currentTimeMillis() - lastActivity

    override fun useGzipWhenAccepted(r: Response): Boolean = false

    /** Whether this request's body was (partly) read; NanoHTTPD serves each connection on its own thread. */
    private val bodyRead = ThreadLocal.withInitial { false }

    override fun serve(session: IHTTPSession): Response {
        lastActivity = System.currentTimeMillis()
        bodyRead.set(false)
        return try {
            route(session)
        } catch (e: ApiException) {
            drain(session)
            json(e.status, JSONObject().put("error", e.code)).apply { closeConnection(true) }
        } catch (e: Exception) {
            json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", "io")).apply { closeConnection(true) }
        } finally {
            lastActivity = System.currentTimeMillis()
        }
    }

    /**
     * Reads an unread request body (up to [MAX_DRAIN]) before an error answer, so the client gets the
     * error instead of a reset connection. Bigger bodies are cut off by closing.
     */
    private fun drain(s: IHTTPSession) {
        if (bodyRead.get()) return
        val length = s.headers["content-length"]?.toLongOrNull() ?: return
        if (length > MAX_DRAIN) return
        runCatching {
            val buffer = ByteArray(16 * 1024)
            var left = length
            while (left > 0) {
                val n = s.inputStream.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                if (n < 0) break
                left -= n
            }
        }
    }

    private fun route(s: IHTTPSession): Response {
        val m = s.method
        when {
            m == Method.POST && s.uri == "/v1/pair/start" -> return pairStart(s)
            m == Method.POST && s.uri == "/v1/pair/finish" -> return pairFinish(s)
        }
        val token = s.headers["authorization"]?.removePrefix("Bearer ")?.trim() ?: throw ApiException(Response.Status.UNAUTHORIZED, "unauthorized")
        val peer = runBlocking(Dispatchers.IO) { repo.peerForToken(token) } ?: throw ApiException(Response.Status.UNAUTHORIZED, "unauthorized")
        return when {
            m == Method.GET && s.uri == "/v1/info" -> info()
            m == Method.GET && s.uri == "/v1/list" -> list(s.param("path"))
            m == Method.GET && s.uri == "/v1/stat" -> resolve(s.param("path")).let { f ->
                if (!f.exists()) throw ApiException(Response.Status.NOT_FOUND, "not_found")
                json(Response.Status.OK, f.item())
            }
            m == Method.GET && s.uri == "/v1/file" -> file(s)
            m == Method.PUT && s.uri == "/v1/file" -> {
                val target = resolveNew(s.param("path"))
                receive(s, target, overwrite = true)
                if (target.parentFile?.canonicalPath == inbox.canonicalPath) onReceived(peer.name, target)
                ok()
            }
            m == Method.POST && s.uri == "/v1/mkdir" -> {
                if (!resolveNew(s.param("path")).mkdir()) throw ApiException(Response.Status.CONFLICT, "exists")
                ok()
            }
            m == Method.POST && s.uri == "/v1/delete" -> {
                val f = resolve(s.param("path"))
                if (roots.values.any { it.canonicalPath == f.canonicalPath }) throw ApiException(Response.Status.FORBIDDEN, "access_denied")
                val touched = runBlocking(Dispatchers.IO) { executor.execute(FileOperation.Trash(listOf(f.path))) {} }
                if (touched.isNotEmpty()) MediaScannerConnection.scanFile(context, touched.toTypedArray(), null, null)
                ok()
            }
            m == Method.POST && s.uri == "/v1/rename" -> {
                val from = resolve(s.param("path"))
                val to = resolveNew(s.param("to"))
                if (to.exists()) throw ApiException(Response.Status.CONFLICT, "exists")
                if (!from.renameTo(to)) throw ApiException(Response.Status.INTERNAL_ERROR, "io")
                MediaScannerConnection.scanFile(context, arrayOf(from.path, to.path), null, null)
                ok()
            }
            m == Method.POST && s.uri == "/v1/send" -> {
                val name = s.param("name").takeIf(LinkPaths::validName) ?: throw ApiException(Response.Status.BAD_REQUEST, "name")
                inbox.mkdirs()
                val target = unique(inbox, name)
                receive(s, target, overwrite = false)
                onReceived(peer.name, target)
                json(Response.Status.OK, JSONObject().put("saved", target.name))
            }
            m == Method.POST && s.uri == "/v1/text" -> {
                onText(peer.name, String(body(s, MAX_TEXT), Charsets.UTF_8))
                ok()
            }
            m == Method.POST && s.uri == "/v1/unpair" -> {
                runBlocking(Dispatchers.IO) { repo.forget(peer.id) }
                ok()
            }
            else -> throw ApiException(Response.Status.NOT_FOUND, "no_route")
        }
    }

    // ---------------- pairing ----------------

    private fun pairStart(s: IHTTPSession): Response {
        val v = jsonBody(s)
        val clientId = v.optString("id").takeIf { LinkCrypto.unhex(it)?.size == 32 } ?: throw ApiException(Response.Status.BAD_REQUEST, "id")
        val commit = LinkCrypto.unhex(v.optString("commit"))?.takeIf { it.size == 32 } ?: throw ApiException(Response.Status.BAD_REQUEST, "commit")
        val now = System.currentTimeMillis()
        pending.entries.removeIf { now - it.value.at > PENDING_MS }
        if (pending.size >= 16) throw ApiException(Response.Status.TOO_MANY_REQUESTS, "busy")
        val nonce = LinkCrypto.random32()
        val session = LinkCrypto.token()
        pending[session] = Pending(clientId, v.optString("name").ifBlank { "Computer" }, v.optString("platform"), v.optInt("port"), commit, nonce, now)
        return json(
            Response.Status.OK,
            JSONObject().put("session", session).put("nonce", LinkCrypto.hex(nonce)).put("id", repo.identity.id)
                .put("name", repo.deviceName).put("platform", LinkRepository.PLATFORM)
        )
    }

    private fun pairFinish(s: IHTTPSession): Response {
        val v = jsonBody(s)
        val p = pending.remove(v.optString("session")) ?: throw ApiException(Response.Status.NOT_FOUND, "session")
        val nonce = LinkCrypto.unhex(v.optString("nonce")) ?: throw ApiException(Response.Status.BAD_REQUEST, "nonce")
        val tokenOut = v.optString("token").takeIf { it.length >= 32 } ?: throw ApiException(Response.Status.BAD_REQUEST, "token")
        if (!java.security.MessageDigest.isEqual(LinkCrypto.sha256(nonce), p.commit)) throw ApiException(Response.Status.FORBIDDEN, "commit")
        val code = LinkCrypto.code(repo.identity.fingerprint, LinkCrypto.unhex(p.clientId)!!, nonce, p.nonce)
        if (!repo.approve(p.name, p.platform, code)) throw ApiException(Response.Status.FORBIDDEN, "declined")
        val tokenIn = LinkCrypto.token()
        val host = s.remoteIpAddress ?: throw ApiException(Response.Status.BAD_REQUEST, "host")
        runBlocking(Dispatchers.IO) { repo.savePeer(p.clientId, p.name, p.platform, host, p.port.takeIf { it > 0 } ?: LinkServerService.PORTS.first, tokenOut, tokenIn) }
        return json(Response.Status.OK, JSONObject().put("token", tokenIn))
    }

    // ---------------- files ----------------

    private fun info(): Response {
        val list = JSONArray(roots.map { (name, dir) -> JSONObject().put("name", name).put("total", dir.totalSpace).put("free", dir.freeSpace) })
        return json(
            Response.Status.OK,
            JSONObject().put("id", repo.identity.id).put("name", repo.deviceName).put("platform", LinkRepository.PLATFORM)
                .put("version", runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty())
                .put("roots", list)
        )
    }

    private fun list(path: String): Response {
        val items = if (LinkPaths.split(path)?.isEmpty() == true) {
            roots.map { (name, dir) -> JSONObject().put("name", name).put("dir", true).put("size", 0).put("mtime", dir.lastModified()) }
        } else {
            val dir = resolve(path)
            if (!dir.isDirectory) throw ApiException(Response.Status.NOT_FOUND, "not_found")
            (dir.listFiles() ?: emptyArray()).filter { it.name !in BLOCKED_DIRS }.map { it.item() }
        }
        return json(Response.Status.OK, JSONObject().put("items", JSONArray(items)))
    }

    private fun file(s: IHTTPSession): Response {
        val f = resolve(s.param("path"))
        if (!f.isFile) throw ApiException(Response.Status.NOT_FOUND, "not_found")
        val length = f.length()
        val range = s.headers["range"]?.let { parseRange(it, length) ?: throw ApiException(Response.Status.RANGE_NOT_SATISFIABLE, "range") }
        val response = if (range == null) {
            newFixedLengthResponse(Response.Status.OK, MIME, tracked(FileInputStream(f)), length)
        } else {
            val (start, end) = range
            val input = FileInputStream(f).apply { channel.position(start) }
            newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, MIME, tracked(input), end - start + 1).apply {
                addHeader("Content-Range", "bytes $start-$end/$length")
            }
        }
        return response.apply { addHeader("Accept-Ranges", "bytes") }
    }

    private fun receive(s: IHTTPSession, target: File, overwrite: Boolean) {
        val length = s.headers["content-length"]?.toLongOrNull() ?: throw ApiException(Response.Status.LENGTH_REQUIRED, "length")
        if (!overwrite && target.exists()) throw ApiException(Response.Status.CONFLICT, "exists")
        if (target.isDirectory) throw ApiException(Response.Status.CONFLICT, "is_dir")
        val dir = target.parentFile ?: throw ApiException(Response.Status.BAD_REQUEST, "path")
        if (length > dir.usableSpace) throw ApiException(Response.Status.lookup(507) ?: Response.Status.INTERNAL_ERROR, "no_space")
        val part = File(dir, ".${target.name}.findex-part")
        bodyRead.set(true)
        transfers.incrementAndGet()
        try {
            part.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var left = length
                while (left > 0) {
                    val n = s.inputStream.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n < 0) throw IOException("cut short")
                    out.write(buffer, 0, n)
                    left -= n
                    lastActivity = System.currentTimeMillis()
                }
            }
            if (target.exists() && overwrite) target.delete()
            if (!part.renameTo(target)) throw IOException("rename")
        } catch (e: IOException) {
            part.delete()
            throw ApiException(Response.Status.INTERNAL_ERROR, "io")
        } finally {
            transfers.decrementAndGet()
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.path), null, null)
    }

    /** An existing file or folder inside a root; never a Findex private folder. */
    private fun resolve(path: String): File {
        val parts = LinkPaths.split(path) ?: throw ApiException(Response.Status.BAD_REQUEST, "path")
        val root = parts.firstOrNull()?.let(roots::get) ?: throw ApiException(Response.Status.NOT_FOUND, "not_found")
        val f = parts.drop(1).fold(root) { acc, part -> File(acc, part) }
        check(root, f.canonicalFile)
        return f
    }

    /** A path to create: its parent must exist inside a root. */
    private fun resolveNew(path: String): File {
        val parts = LinkPaths.split(path)?.takeIf { it.size >= 2 } ?: throw ApiException(Response.Status.BAD_REQUEST, "path")
        val parent = resolve("/" + parts.dropLast(1).joinToString("/"))
        if (!parent.isDirectory) throw ApiException(Response.Status.NOT_FOUND, "not_found")
        if (parts.last() in BLOCKED_DIRS) throw ApiException(Response.Status.FORBIDDEN, "access_denied")
        return File(parent, parts.last())
    }

    private fun check(root: File, f: File) {
        val r = root.canonicalPath
        val p = f.canonicalPath
        if (p != r && !p.startsWith("$r/")) throw ApiException(Response.Status.FORBIDDEN, "access_denied")
        if (p.removePrefix(r).split('/').any { it in BLOCKED_DIRS }) throw ApiException(Response.Status.FORBIDDEN, "access_denied")
    }

    private fun unique(dir: File, name: String): File {
        var f = File(dir, name)
        val base = name.substringBeforeLast('.', name).ifEmpty { name }
        val ext = if (base == name) "" else "." + name.substringAfterLast('.')
        var i = 2
        while (f.exists()) f = File(dir, "$base (${i++})$ext")
        return f
    }

    private fun tracked(input: InputStream): InputStream {
        transfers.incrementAndGet()
        return object : FilterInputStream(input) {
            private var closed = false
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                lastActivity = System.currentTimeMillis()
                return super.read(b, off, len)
            }
            override fun close() {
                if (!closed) { closed = true; transfers.decrementAndGet() }
                super.close()
            }
        }
    }

    private fun File.item(): JSONObject = JSONObject().put("name", name).put("dir", isDirectory)
        .put("size", if (isDirectory) 0 else length()).put("mtime", lastModified())

    private fun IHTTPSession.param(name: String): String = parameters[name]?.firstOrNull() ?: throw ApiException(Response.Status.BAD_REQUEST, name)

    private fun body(s: IHTTPSession, max: Int): ByteArray {
        val length = s.headers["content-length"]?.toIntOrNull() ?: throw ApiException(Response.Status.LENGTH_REQUIRED, "length")
        if (length > max) throw ApiException(Response.Status.PAYLOAD_TOO_LARGE, "too_large")
        bodyRead.set(true)
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = s.inputStream.read(bytes, read, length - read)
            if (n < 0) break
            read += n
        }
        return bytes.copyOf(read)
    }

    private fun jsonBody(s: IHTTPSession): JSONObject =
        runCatching { JSONObject(String(body(s, MAX_JSON), Charsets.UTF_8)) }.getOrElse { throw ApiException(Response.Status.BAD_REQUEST, "json") }

    private fun ok() = json(Response.Status.OK, JSONObject().put("ok", true))

    private fun json(status: Response.Status, body: JSONObject): Response =
        newFixedLengthResponse(status, "application/json", body.toString()).apply { addHeader("Cache-Control", "no-store") }

    /** `bytes=a-b`, `bytes=a-` or `bytes=-n`; null when unsatisfiable. */
    private fun parseRange(header: String, length: Long): Pair<Long, Long>? {
        val spec = header.trim().removePrefix("bytes=").takeIf { it != header.trim() }?.substringBefore(',') ?: return null
        val a = spec.substringBefore('-').trim()
        val b = spec.substringAfter('-', "").trim()
        if (length == 0L) return null
        val (start, end) = if (a.isEmpty()) {
            val n = b.toLongOrNull() ?: return null
            (length - n).coerceAtLeast(0) to length - 1
        } else {
            val start = a.toLongOrNull() ?: return null
            start to (b.toLongOrNull()?.coerceAtMost(length - 1) ?: (length - 1))
        }
        return (start to end).takeIf { start <= end && end < length }
    }

    companion object {
        private const val MIME = "application/octet-stream"
        private const val MAX_JSON = 64 * 1024
        private const val MAX_TEXT = 1 shl 20
        private const val MAX_DRAIN = 8L shl 20
        private const val PENDING_MS = 120_000L
        private val BLOCKED_DIRS = setOf(VaultKeyManager.DIR_NAME, FileOperationExecutor.TRASH_DIR)
    }
}
