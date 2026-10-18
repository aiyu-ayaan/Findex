package com.aiyu.findex.core.network

import android.content.Context
import com.aiyu.findex.core.db.NetworkServerDao
import com.aiyu.findex.core.db.NetworkServerEntity
import com.aiyu.findex.core.model.StorageItem
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mserref.NtStatus
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.EnumSet
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A saved SMB share as the UI sees it; the password never leaves [SmbRepository]. */
data class NetworkServer(
    val id: Long,
    val name: String,
    val host: String,
    val share: String,
    val username: String,
    val domain: String
) {
    val label: String get() = name.ifBlank { "$host/$share" }
    val rootPath: String get() = SmbPath.of(id, "")
}

enum class NetworkError { HOST_UNREACHABLE, AUTH_FAILED, SHARE_NOT_FOUND, ACCESS_DENIED, UNKNOWN }

class NetworkException(val error: NetworkError, cause: Throwable? = null) : Exception(error.name, cause)

/** Virtual paths for SMB entries: `smb://<server id>/<path inside the share>`. */
object SmbPath {
    private const val SCHEME = "smb://"

    fun of(serverId: Long, inner: String) = "$SCHEME$serverId/${inner.trim('/')}".trimEnd('/')

    /** (server id, inner path), or null for a non-SMB path. */
    fun parse(path: String): Pair<Long, String>? {
        if (!path.startsWith(SCHEME)) return null
        val rest = path.removePrefix(SCHEME)
        val id = rest.substringBefore('/').toLongOrNull() ?: return null
        return id to rest.substringAfter('/', "")
    }
}

/**
 * Windows network shares (SMB 2/3) through smbj. Read-only for now: list folders and pull a file
 * into the cache to open or share it. Shares stay connected and are re-opened when they drop.
 */
@Singleton
class SmbRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: NetworkServerDao,
    private val cipher: CredentialCipher
) {
    private val client = SMBClient(
        SmbConfig.builder()
            .withTimeout(15, TimeUnit.SECONDS)
            .withSoTimeout(30, TimeUnit.SECONDS)
            .build()
    )
    private val shares = HashMap<Long, DiskShare>()
    private val lock = Mutex()

    val servers: Flow<List<NetworkServer>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    /** Connects once to check host, credentials and share, then saves the server. */
    suspend fun add(name: String, host: String, share: String, username: String, domain: String, password: String): NetworkServer =
        withContext(Dispatchers.IO) {
            val entity = NetworkServerEntity(
                name = name.trim(),
                host = host.trim(),
                share = share.trim().trim('/', '\\'),
                username = username.trim(),
                domain = domain.trim(),
                passwordCipher = password.takeIf { it.isNotEmpty() }?.let(cipher::encrypt)
            )
            open(entity).close()
            val id = dao.upsert(entity)
            entity.copy(id = id).toModel()
        }

    suspend fun delete(id: Long) {
        lock.withLock { shares.remove(id)?.runCatching { close() } }
        dao.delete(id)
        File(cacheRoot, id.toString()).deleteRecursively()
    }

    suspend fun server(id: Long): NetworkServer? = dao.byId(id)?.toModel()

    /** Children of [inner] (path inside the share), folders first. */
    suspend fun list(serverId: Long, inner: String): List<StorageItem> = withContext(Dispatchers.IO) {
        withShare(serverId) { share ->
            share.list(inner.toSmb())
                .filter { it.fileName != "." && it.fileName != ".." }
                .map { info ->
                    val isDir = EnumWithValue.EnumUtils.isSet(info.fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY)
                    val childInner = if (inner.isEmpty()) info.fileName else "${inner.trim('/')}/${info.fileName}"
                    StorageItem(
                        path = SmbPath.of(serverId, childInner),
                        name = info.fileName,
                        size = if (isDir) 0 else info.endOfFile,
                        lastModified = info.lastWriteTime.toEpochMillis(),
                        isDirectory = isDir,
                        extension = if (isDir) "" else info.fileName.substringAfterLast('.', "").lowercase()
                    )
                }
                .sortedWith(compareByDescending<StorageItem> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }

    /** Copies one remote file into the cache; a cached copy with the same size is reused. */
    suspend fun download(serverId: Long, inner: String, size: Long): File = withContext(Dispatchers.IO) {
        val target = File(File(cacheRoot, serverId.toString()), inner.trim('/'))
        // Remote names come from the server; never let "..\\" escape the cache folder.
        if (!target.canonicalPath.startsWith(cacheRoot.canonicalPath + File.separator)) throw NetworkException(NetworkError.ACCESS_DENIED)
        if (target.isFile && target.length() == size) return@withContext target
        target.parentFile?.mkdirs()
        val partial = File(target.path + ".part")
        try {
            withShare(serverId) { share ->
                share.openFile(
                    inner.toSmb(),
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                ).use { remote ->
                    remote.inputStream.use { input ->
                        partial.outputStream().use { out ->
                            val buffer = ByteArray(256 * 1024)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                out.write(buffer, 0, read)
                            }
                        }
                    }
                }
            }
            if (!partial.renameTo(target)) throw NetworkException(NetworkError.UNKNOWN)
            target
        } finally {
            partial.delete()
        }
    }

    // region Primitives for the file operation executor (copy / move / delete across local and SMB)

    /** The entry at [inner], or null when nothing is there. */
    suspend fun stat(serverId: Long, inner: String): StorageItem? = withContext(Dispatchers.IO) {
        withShare(serverId) { share ->
            val path = inner.toSmb()
            when {
                path.isEmpty() || share.folderExists(path) -> StorageItem(
                    path = SmbPath.of(serverId, inner), name = inner.substringAfterLast('/'), isDirectory = true
                )
                share.fileExists(path) -> {
                    val info = share.getFileInformation(path)
                    val name = inner.substringAfterLast('/')
                    StorageItem(
                        path = SmbPath.of(serverId, inner),
                        name = name,
                        size = info.standardInformation.endOfFile,
                        lastModified = info.basicInformation.lastWriteTime.toEpochMillis(),
                        extension = name.substringAfterLast('.', "").lowercase()
                    )
                }
                else -> null
            }
        }
    }

    suspend fun mkdir(serverId: Long, inner: String) = withContext(Dispatchers.IO) {
        withShare(serverId) { it.mkdir(inner.toSmb()) }
    }

    /** Removes a file, or a folder with everything in it. */
    suspend fun delete(serverId: Long, inner: String, isDirectory: Boolean) = withContext(Dispatchers.IO) {
        withShare(serverId) { if (isDirectory) it.rmdir(inner.toSmb(), true) else it.rm(inner.toSmb()) }
    }

    /** Server-side rename or move within the same share. */
    suspend fun rename(serverId: Long, inner: String, newInner: String) = withContext(Dispatchers.IO) {
        withShare(serverId) { share ->
            share.open(
                inner.toSmb(),
                EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            ).use { it.rename(newInner.toSmb()) }
        }
    }

    suspend fun <T> read(serverId: Long, inner: String, block: suspend (InputStream) -> T): T = withContext(Dispatchers.IO) {
        withShare(serverId) { share ->
            share.openFile(inner.toSmb(), EnumSet.of(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
                .use { remote -> remote.inputStream.use { block(it) } }
        }
    }

    /** Creates a new file; fails if one already exists, so nothing is ever overwritten. */
    suspend fun <T> write(serverId: Long, inner: String, block: suspend (OutputStream) -> T): T = withContext(Dispatchers.IO) {
        withShare(serverId) { share ->
            share.openFile(inner.toSmb(), EnumSet.of(AccessMask.GENERIC_WRITE), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_CREATE, null)
                .use { remote -> remote.outputStream.use { block(it) } }
        }
    }

    /**
     * Opens a seekable reader for [inner] on [serverId]. Uses a 256 KB read-ahead buffer so
     * sequential streaming and small header probes (thumbnails, video players) avoid per-call RPCs,
     * and reconnects transparently if the share drops mid-stream.
     */
    suspend fun openRandomAccess(serverId: Long, inner: String, knownSize: Long = -1L): SmbRandomAccessFile =
        withContext(Dispatchers.IO) {
            val smbPath = inner.toSmb()
            val (remote, size) = withShare(serverId) { share ->
                val f = share.openFile(
                    smbPath,
                    EnumSet.of(AccessMask.GENERIC_READ),
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OPEN,
                    null
                )
                try {
                    val s = if (knownSize >= 0L) knownSize else f.fileInformation.standardInformation.endOfFile
                    f to s
                } catch (e: Exception) {
                    runCatching { f.close() }
                    throw e
                }
            }
            SmbRandomAccessFile(
                size = size,
                initial = remote,
                reopen = {
                    runBlocking(Dispatchers.IO) {
                        withShare(serverId) { share ->
                            share.openFile(
                                smbPath,
                                EnumSet.of(AccessMask.GENERIC_READ),
                                null,
                                SMB2ShareAccess.ALL,
                                SMB2CreateDisposition.FILE_OPEN,
                                null
                            )
                        }
                    }
                }
            )
        }

    // endregion

    private val cacheRoot get() = File(context.cacheDir, "network")

    /** Runs [block] on the server's share, reconnecting once if the cached session went stale. */
    private suspend fun <T> withShare(serverId: Long, block: suspend (DiskShare) -> T): T {
        suspend fun share(fresh: Boolean): DiskShare = lock.withLock {
            if (fresh) shares.remove(serverId)?.runCatching { close() }
            shares[serverId]?.takeIf { it.isConnected } ?: run {
                val entity = dao.byId(serverId) ?: throw NetworkException(NetworkError.UNKNOWN)
                open(entity).also { shares[serverId] = it }
            }
        }
        return try {
            block(share(fresh = false))
        } catch (e: IOException) {
            // Transport failures (idle timeout, Wi-Fi change) leave a dead session: retry once on a new one.
            try { block(share(fresh = true)) } catch (e2: Exception) { throw e2.toNetwork() }
        } catch (e: Exception) {
            throw e.toNetwork()
        }
    }

    private fun open(entity: NetworkServerEntity): DiskShare = try {
        val password = entity.passwordCipher?.let { cipher.decrypt(it) ?: throw NetworkException(NetworkError.AUTH_FAILED) }
        val auth = if (entity.username.isEmpty()) AuthenticationContext.guest()
        else AuthenticationContext(entity.username, password.orEmpty().toCharArray(), entity.domain.ifEmpty { null })
        val session = client.connect(entity.host).authenticate(auth)
        session.connectShare(entity.share) as? DiskShare ?: throw NetworkException(NetworkError.SHARE_NOT_FOUND)
    } catch (e: Exception) {
        throw e.toNetwork()
    }

    private fun Exception.toNetwork(): NetworkException = when (this) {
        is NetworkException -> this
        is UnknownHostException, is ConnectException, is NoRouteToHostException, is SocketTimeoutException ->
            NetworkException(NetworkError.HOST_UNREACHABLE, this)
        is SMBApiException -> when (status) {
            NtStatus.STATUS_LOGON_FAILURE, NtStatus.STATUS_ACCOUNT_DISABLED, NtStatus.STATUS_LOGON_TYPE_NOT_GRANTED, NtStatus.STATUS_PASSWORD_EXPIRED ->
                NetworkException(NetworkError.AUTH_FAILED, this)
            NtStatus.STATUS_BAD_NETWORK_NAME, NtStatus.STATUS_BAD_NETWORK_PATH -> NetworkException(NetworkError.SHARE_NOT_FOUND, this)
            NtStatus.STATUS_ACCESS_DENIED -> NetworkException(NetworkError.ACCESS_DENIED, this)
            else -> NetworkException(NetworkError.UNKNOWN, this)
        }
        // smbj wraps socket failures in its own transport exceptions.
        else -> generateSequence(cause) { it.cause }.firstOrNull { it is UnknownHostException || it is ConnectException || it is NoRouteToHostException || it is SocketTimeoutException }
            ?.let { NetworkException(NetworkError.HOST_UNREACHABLE, this) }
            ?: NetworkException(NetworkError.UNKNOWN, this)
    }

    private fun String.toSmb() = trim('/').replace('/', '\\')

    private fun NetworkServerEntity.toModel() = NetworkServer(id, name, host, share, username, domain)
}

/**
 * Thread-safe, seekable reader over an SMB file with a 256 KB read-ahead window.
 * Supports both `MediaDataSource` (video thumbnails/metadata) and `ProxyFileDescriptorCallback` (streaming).
 */
class SmbRandomAccessFile internal constructor(
    val size: Long,
    initial: com.hierynomus.smbj.share.File,
    private val reopen: () -> com.hierynomus.smbj.share.File
) : Closeable {
    private var remote: com.hierynomus.smbj.share.File = initial
    private val chunk = ByteArray(256 * 1024)
    private var chunkOffset = -1L
    private var chunkLength = 0
    private var closed = false

    @Synchronized
    @Throws(IOException::class)
    fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (closed) throw IOException("Closed")
        if (position < 0L || position >= size) return -1
        if (length <= 0) return 0

        val maxAvail = minOf(length.toLong(), size - position).toInt()
        var total = 0
        var pos = position

        while (total < maxAvail) {
            if (chunkOffset < 0L || pos < chunkOffset || pos >= chunkOffset + chunkLength) {
                val toFetch = minOf(chunk.size.toLong(), size - pos).toInt()
                val fetched = fetchChunk(pos, toFetch)
                if (fetched <= 0) break
                chunkOffset = pos
                chunkLength = fetched
            }
            val inChunk = (pos - chunkOffset).toInt()
            val copy = minOf(maxAvail - total, chunkLength - inChunk)
            System.arraycopy(chunk, inChunk, buffer, offset + total, copy)
            total += copy
            pos += copy
        }
        return if (total == 0) -1 else total
    }

    private fun fetchChunk(pos: Long, targetLen: Int): Int {
        return try {
            remote.read(chunk, pos, 0, targetLen)
        } catch (e: Exception) {
            runCatching { remote.close() }
            try {
                remote = reopen()
                remote.read(chunk, pos, 0, targetLen)
            } catch (e2: Exception) {
                throw IOException("SMB read failed at $pos", e2)
            }
        }
    }

    /** Returns an [InputStream] view starting at [startOffset] without closing this reader on stream close. */
    fun asInputStream(startOffset: Long = 0L): InputStream = object : InputStream() {
        private var pos = startOffset
        private val one = ByteArray(1)

        override fun read(): Int {
            val n = read(one, 0, 1)
            return if (n <= 0) -1 else one[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            val n = readAt(pos, b, off, len)
            if (n > 0) pos += n
            return n
        }

        override fun skip(n: Long): Long {
            if (n <= 0L) return 0L
            val skipped = minOf(n, (size - pos).coerceAtLeast(0L))
            pos += skipped
            return skipped
        }

        override fun available(): Int = (size - pos).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        runCatching { remote.close() }
    }
}

