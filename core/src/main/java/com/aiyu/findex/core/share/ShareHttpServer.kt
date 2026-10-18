package com.aiyu.findex.core.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.webkit.MimeTypeMap
import com.aiyu.findex.core.model.AppSettings
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.SharePermission
import com.aiyu.findex.core.model.StorageDrive
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.operation.FileOperationExecutor
import com.aiyu.findex.core.repository.StorageRepository
import com.aiyu.findex.core.vault.VaultKeyManager
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * LAN web server behind "Share this phone". Serves the web app from `assets/share/` and a small JSON API.
 * Every API call needs the session cookie set by a correct [pin]; each one also checks the live
 * [SharePermission]s from settings, so the phone can change access while browsers stay connected.
 * Paths are confined to the storage volume [roots]; the vault and recycle bin folders are never served.
 */
internal class ShareHttpServer(
    host: String,
    port: Int,
    private val context: Context,
    val pin: String,
    private val settings: StateFlow<AppSettings>,
    private val drives: List<StorageDrive>,
    private val storage: StorageRepository,
    private val executor: FileOperationExecutor
) : NanoHTTPD(host, port) {

    private val roots = drives.map { File(it.path).canonicalPath }
    private val sessions: MutableSet<String> = ConcurrentHashMap.newKeySet()
    /** Remote IP to consecutive wrong PINs and the time it may try again. */
    private val failures = ConcurrentHashMap<String, Pair<Int, Long>>()
    private val transfers = AtomicInteger()
    @Volatile private var lastActivity = System.currentTimeMillis()

    val clientCount: Int get() = sessions.size

    /** Zero while a download or upload is still streaming. */
    fun idleMillis(): Long = if (transfers.get() > 0) 0 else System.currentTimeMillis() - lastActivity

    private val indexHtml by lazy { context.assets.open("share/index.html").use { it.readBytes() } }

    override fun useGzipWhenAccepted(r: Response): Boolean = false

    override fun serve(session: IHTTPSession): Response {
        lastActivity = System.currentTimeMillis()
        return try {
            route(session)
        } catch (e: ApiException) {
            json(e.status, JSONObject().put("error", e.code)).apply { if (session.method == Method.PUT) closeConnection(true) }
        } catch (e: Exception) {
            json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", "internal")).apply { closeConnection(true) }
        } finally {
            lastActivity = System.currentTimeMillis()
        }
    }

    private fun route(session: IHTTPSession): Response {
        val uri = session.uri
        if (uri == "/" || uri == "/index.html") return page()
        if (!uri.startsWith("/api/")) throw ApiException(Response.Status.NOT_FOUND, "not_found")
        // Custom header forces a CORS preflight, so other sites can't post to this server from the user's browser.
        if (session.method != Method.GET && session.headers["x-findex"] != "1") throw ApiException(Response.Status.FORBIDDEN, "csrf")
        if (uri == "/api/login" && session.method == Method.POST) return login(session)
        val token = session.cookies.read(COOKIE)
        if (token == null || token !in sessions) throw ApiException(Response.Status.UNAUTHORIZED, "auth")
        return when (uri) {
            "/api/logout" -> { sessions.remove(token); ok() }
            "/api/info" -> info()
            "/api/list" -> list(session.param("path"))
            "/api/category" -> category(session.param("c"))
            "/api/file" -> file(session, session.param("path"), session.parameters["dl"] != null)
            "/api/thumb" -> thumb(session.param("path"))
            "/api/upload" -> upload(session)
            "/api/mkdir" -> body(session).let { mkdir(it.getString("dir"), it.getString("name")) }
            "/api/rename" -> body(session).let { rename(it.getString("path"), it.getString("name")) }
            "/api/delete" -> body(session).let { delete(it.getJSONArray("paths")) }
            else -> throw ApiException(Response.Status.NOT_FOUND, "not_found")
        }
    }

    private fun page(): Response =
        newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", indexHtml.inputStream(), indexHtml.size.toLong()).apply {
            addHeader("Cache-Control", "no-store")
            addHeader("X-Frame-Options", "DENY")
            addHeader("Referrer-Policy", "no-referrer")
            addHeader(
                "Content-Security-Policy",
                "default-src 'self'; img-src 'self' blob: data:; media-src 'self'; style-src 'self' 'unsafe-inline'; " +
                    "script-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'"
            )
        }

    private fun login(session: IHTTPSession): Response {
        val ip = session.remoteIpAddress ?: ""
        val (failed, retryAt) = failures[ip] ?: (0 to 0L)
        val now = System.currentTimeMillis()
        if (now < retryAt) {
            return json(Response.Status.TOO_MANY_REQUESTS, JSONObject().put("error", "locked").put("retryMs", retryAt - now))
        }
        val given = body(session).optString("pin")
        if (!MessageDigest.isEqual(given.toByteArray(), pin.toByteArray())) {
            val count = failed + 1
            failures[ip] = count to now + VaultKeyManager.lockoutMs(count)
            throw ApiException(Response.Status.UNAUTHORIZED, "wrong_pin")
        }
        failures.remove(ip)
        val token = ByteArray(32).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
        sessions += token
        return ok().apply { addHeader("Set-Cookie", "$COOKIE=$token; Path=/; HttpOnly; SameSite=Strict") }
    }

    private fun info(): Response {
        val s = settings.value
        val out = JSONObject()
            .put("device", Build.MODEL)
            .put("access", JSONArray(s.shareAccess.map { it.name }))
            .put("drives", JSONArray(drives.map { d ->
                val f = File(d.path)
                JSONObject().put("name", d.name).put("path", d.path).put("type", d.type.name)
                    .put("total", f.totalSpace).put("free", f.freeSpace)
            }))
        if (SharePermission.MEDIA in s.shareAccess) {
            val stats = runBlocking(Dispatchers.IO) { storage.getCategoryStats() }
            out.put("stats", JSONObject().apply {
                stats.forEach { (c, st) -> put(c.name, JSONObject().put("count", st.count).put("bytes", st.bytes)) }
            })
        }
        return json(Response.Status.OK, out)
    }

    private fun list(path: String): Response {
        require(SharePermission.FILES)
        val dir = resolve(path)
        if (!dir.isDirectory) throw ApiException(Response.Status.NOT_FOUND, "not_found")
        val items = runBlocking(Dispatchers.IO) { storage.getFilesInDirectory(dir.path, settings.value.showHidden) }
            .filterNot { it.name in BLOCKED_DIRS }
        return json(Response.Status.OK, JSONObject().put("path", dir.path).put("items", items.toJson()))
    }

    private fun category(name: String): Response {
        require(SharePermission.MEDIA)
        val c = FileCategory.entries.firstOrNull { it.name == name } ?: throw ApiException(Response.Status.BAD_REQUEST, "category")
        val items = runBlocking(Dispatchers.IO) { storage.getCategoryFiles(c) }.filter { isAllowed(File(it.path)) }
        return json(Response.Status.OK, JSONObject().put("items", items.toJson()))
    }

    private fun file(session: IHTTPSession, path: String, download: Boolean): Response {
        require(SharePermission.DOWNLOAD)
        val f = resolve(path)
        if (!f.isFile) throw ApiException(Response.Status.NOT_FOUND, "not_found")
        val length = f.length()
        // Opened inline, HTML/SVG from the phone would run script on this origin; show it as text instead.
        val mime = mimeOf(f.name).let { if (!download && SCRIPTABLE.any(it::contains)) "text/plain; charset=utf-8" else it }
        val range = session.headers["range"]?.let { parseRange(it, length) }
        val response = if (range == null) {
            newFixedLengthResponse(Response.Status.OK, mime, tracked(FileInputStream(f)), length)
        } else {
            val (start, end) = range
            if (start >= length || start > end) {
                return newFixedLengthResponse(Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", "").apply {
                    addHeader("Content-Range", "bytes */$length")
                }
            }
            val input = FileInputStream(f).apply { channel.position(start) }
            newFixedLengthResponse(Response.Status.PARTIAL_CONTENT, mime, tracked(input), end - start + 1).apply {
                addHeader("Content-Range", "bytes $start-$end/$length")
            }
        }
        return response.apply {
            addHeader("Accept-Ranges", "bytes")
            val encoded = URLEncoder.encode(f.name, "UTF-8").replace("+", "%20")
            addHeader("Content-Disposition", "${if (download) "attachment" else "inline"}; filename*=UTF-8''$encoded")
            addHeader("X-Content-Type-Options", "nosniff")
        }
    }

    private fun thumb(path: String): Response {
        require(SharePermission.DOWNLOAD)
        val f = resolve(path)
        val mime = mimeOf(f.name)
        val bitmap = runCatching {
            when {
                mime.startsWith("video/") -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ThumbnailUtils.createVideoThumbnail(f, Size(THUMB_PX, THUMB_PX), null)
                } else {
                    @Suppress("DEPRECATION")
                    ThumbnailUtils.createVideoThumbnail(f.path, MediaStore.Images.Thumbnails.MINI_KIND)
                }
                mime.startsWith("image/") -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ThumbnailUtils.createImageThumbnail(f, Size(THUMB_PX, THUMB_PX), null)
                } else {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(f.path, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= THUMB_PX && bounds.outHeight / (sample * 2) >= THUMB_PX) sample *= 2
                    BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
                }
                else -> null
            }
        }.getOrNull() ?: throw ApiException(Response.Status.NOT_FOUND, "no_thumb")
        val bytes = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }.toByteArray()
        bitmap.recycle()
        return newFixedLengthResponse(Response.Status.OK, "image/jpeg", bytes.inputStream(), bytes.size.toLong()).apply {
            addHeader("Cache-Control", "private, max-age=3600")
        }
    }

    /** `PUT /api/upload?dir=…&name=…` with the raw file as the body; a taken name gets a " (n)" suffix. */
    private fun upload(session: IHTTPSession): Response {
        if (session.method != Method.PUT) throw ApiException(Response.Status.METHOD_NOT_ALLOWED, "method")
        require(SharePermission.UPLOAD)
        val dir = resolve(session.param("dir"))
        val name = validName(session.param("name"))
        val length = session.headers["content-length"]?.toLongOrNull() ?: throw ApiException(Response.Status.LENGTH_REQUIRED, "length")
        if (!dir.isDirectory) throw ApiException(Response.Status.NOT_FOUND, "not_found")
        if (length > dir.usableSpace) throw ApiException(Response.Status.PAYLOAD_TOO_LARGE, "no_space")
        val target = uniqueFile(dir, name)
        val part = File(dir, ".${target.name}.part")
        transfers.incrementAndGet()
        try {
            part.outputStream().use { out ->
                val input = session.inputStream
                val buffer = ByteArray(64 * 1024)
                var left = length
                while (left > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (read < 0) throw IOException("upload cut short")
                    out.write(buffer, 0, read)
                    left -= read
                    lastActivity = System.currentTimeMillis()
                }
            }
            if (!part.renameTo(target)) throw IOException("rename failed")
        } catch (e: IOException) {
            part.delete()
            throw ApiException(Response.Status.INTERNAL_ERROR, "write")
        } finally {
            transfers.decrementAndGet()
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.path), null, null)
        return json(Response.Status.OK, JSONObject().put("item", target.toItem()))
    }

    private fun mkdir(dirPath: String, name: String): Response {
        require(SharePermission.UPLOAD)
        val dir = resolve(dirPath)
        val created = runBlocking(Dispatchers.IO) { storage.createFolder(dir.path, validName(name)) }
            ?: throw ApiException(Response.Status.CONFLICT, "exists")
        return json(Response.Status.OK, JSONObject().put("item", created.toJson()))
    }

    private fun rename(path: String, name: String): Response {
        require(SharePermission.MODIFY)
        val f = resolve(path)
        if (f.path in roots) throw ApiException(Response.Status.FORBIDDEN, "root")
        val renamed = runBlocking(Dispatchers.IO) { storage.rename(f.path, validName(name)) }
            ?: throw ApiException(Response.Status.CONFLICT, "exists")
        return json(Response.Status.OK, JSONObject().put("item", renamed.toJson()))
    }

    /** Moves into the recycle bin, restorable from the phone. Runs here: this server already lives in a foreground service. */
    private fun delete(paths: JSONArray): Response {
        require(SharePermission.MODIFY)
        val targets = (0 until paths.length()).map { resolve(paths.getString(it)) }
        if (targets.any { it.path in roots }) throw ApiException(Response.Status.FORBIDDEN, "root")
        val touched = runBlocking(Dispatchers.IO) { executor.execute(FileOperation.Trash(targets.map { it.path })) {} }
        if (touched.isNotEmpty()) MediaScannerConnection.scanFile(context, touched.toTypedArray(), null, null)
        return ok()
    }

    private fun require(permission: SharePermission) {
        if (permission !in settings.value.shareAccess) throw ApiException(Response.Status.FORBIDDEN, "denied")
    }

    /** Canonical file for [path], or 403 when it leaves the storage volumes or enters a private Findex folder. */
    private fun resolve(path: String): File {
        val f = File(path).canonicalFile
        if (!isAllowed(f)) throw ApiException(Response.Status.FORBIDDEN, "path")
        return f
    }

    private fun isAllowed(f: File): Boolean {
        val p = f.canonicalPath
        val root = roots.firstOrNull { p == it || p.startsWith("$it/") } ?: return false
        return p.removePrefix(root).split('/').none { it in BLOCKED_DIRS }
    }

    private fun validName(name: String): String {
        val n = name.trim()
        if (n.isEmpty() || n == "." || n == ".." || n.length > 255 || n.any { it == '/' || it == '\u0000' }) {
            throw ApiException(Response.Status.BAD_REQUEST, "name")
        }
        return n
    }

    private fun uniqueFile(dir: File, name: String): File {
        var f = File(dir, name)
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        var i = 1
        while (f.exists() || File(dir, ".${f.name}.part").exists()) f = File(dir, "$base (${i++})$ext")
        return f
    }

    /** Counts as a running transfer until the client has read it all or dropped the connection. */
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

    private fun IHTTPSession.param(name: String): String =
        parameters[name]?.firstOrNull() ?: throw ApiException(Response.Status.BAD_REQUEST, name)

    private fun body(session: IHTTPSession): JSONObject {
        val length = session.headers["content-length"]?.toIntOrNull() ?: 0
        if (length > MAX_JSON) throw ApiException(Response.Status.PAYLOAD_TOO_LARGE, "body")
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = session.inputStream.read(bytes, read, length - read)
            if (n < 0) break
            read += n
        }
        return runCatching { JSONObject(String(bytes, 0, read)) }.getOrElse { throw ApiException(Response.Status.BAD_REQUEST, "json") }
    }

    private fun ok() = json(Response.Status.OK, JSONObject().put("ok", true))

    private fun json(status: Response.Status, body: JSONObject): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body.toString()).apply {
            addHeader("Cache-Control", "no-store")
        }

    private fun List<StorageItem>.toJson() = JSONArray(map { it.toJson() })

    private fun StorageItem.toJson(): JSONObject = JSONObject()
        .put("name", name).put("path", path).put("dir", isDirectory).put("size", size)
        .put("mtime", lastModified).put("ext", extension).put("count", itemCount)

    private fun File.toItem() = StorageItem(path, name, length(), lastModified(), isDirectory, extension.lowercase()).toJson()

    private fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"

    /** Single `bytes=a-b`, `bytes=a-` or `bytes=-n` range; anything else is served whole. */
    private fun parseRange(header: String, length: Long): Pair<Long, Long>? {
        val spec = header.removePrefix("bytes=").takeIf { it != header && ',' !in it } ?: return null
        val (a, b) = spec.split('-', limit = 2).let { it[0].trim() to it.getOrElse(1) { "" }.trim() }
        return when {
            a.isEmpty() -> b.toLongOrNull()?.let { n -> (length - n).coerceAtLeast(0) to length - 1 }
            else -> a.toLongOrNull()?.let { start -> start to (b.toLongOrNull()?.coerceAtMost(length - 1) ?: (length - 1)) }
        }
    }

    private class ApiException(val status: Response.Status, val code: String) : Exception(code)

    companion object {
        private const val COOKIE = "findex_share"
        private const val THUMB_PX = 320
        private const val MAX_JSON = 1 shl 20
        private val SCRIPTABLE = listOf("html", "xml", "svg", "javascript")
        private val BLOCKED_DIRS = setOf(VaultKeyManager.DIR_NAME, FileOperationExecutor.TRASH_DIR)
    }
}
