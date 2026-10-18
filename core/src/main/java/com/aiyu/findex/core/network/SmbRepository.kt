package com.aiyu.findex.core.network

import android.content.Context
import com.aiyu.findex.core.db.NetworkServerDao
import com.aiyu.findex.core.db.NetworkServerEntity
import com.aiyu.findex.core.model.StorageItem
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes
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
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Supported network file-sharing protocols and their standard TCP ports. */
enum class NetworkProtocol(val defaultPort: Int, val displayName: String) {
    SMB(445, "SMB"),
    SFTP(22, "SFTP"),
    FTP(21, "FTP"),
    WEBDAV(80, "WebDAV"),
    WEBDAVS(443, "WebDAVS");

    val isWebDav: Boolean get() = this == WEBDAV || this == WEBDAVS

    companion object {
        fun fromName(name: String): NetworkProtocol =
            entries.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) } ?: SMB
    }
}

/** A saved network server as the UI sees it; the password never leaves [SmbRepository]. */
data class NetworkServer(
    val id: Long,
    val name: String,
    val host: String,
    val share: String,
    val username: String,
    val domain: String,
    val protocol: NetworkProtocol = NetworkProtocol.SMB,
    val port: Int = 0,
    val rememberPassword: Boolean = true
) {
    val effectivePort: Int get() = if (port > 0) port else protocol.defaultPort

    val label: String
        get() = name.ifBlank {
            val cleanShare = share.trim('/')
            if (cleanShare.isEmpty()) host else "$host/$cleanShare"
        }

    val displayAddress: String
        get() {
            val customPort = if (port > 0 && port != protocol.defaultPort) ":$port" else ""
            val cleanShare = share.trim('/')
            return when (protocol) {
                NetworkProtocol.SMB -> "\\\\$host$customPort\\$cleanShare"
                NetworkProtocol.SFTP -> if (cleanShare.isEmpty()) "sftp://$host$customPort" else "sftp://$host$customPort/$cleanShare"
                NetworkProtocol.FTP -> if (cleanShare.isEmpty()) "ftp://$host$customPort" else "ftp://$host$customPort/$cleanShare"
                NetworkProtocol.WEBDAV -> if (cleanShare.isEmpty()) "http://$host$customPort" else "http://$host$customPort/$cleanShare"
                NetworkProtocol.WEBDAVS -> if (cleanShare.isEmpty()) "https://$host$customPort" else "https://$host$customPort/$cleanShare"
            }
        }

    val rootPath: String get() = SmbPath.of(id, "")
}

/** Capacity of a share, in bytes. */
data class NetworkSpace(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

/** Reachability of a saved server, as probed by [SmbRepository.probe]. */
sealed interface NetworkStatus {
    data object Checking : NetworkStatus
    /** [space] is null when the protocol cannot report capacity (FTP, most WebDAV). */
    data class Online(val space: NetworkSpace?) : NetworkStatus
    data class Offline(val error: NetworkError) : NetworkStatus
}

enum class NetworkError {
    HOST_UNREACHABLE, AUTH_FAILED, SHARE_NOT_FOUND, ACCESS_DENIED,
    /** The server doesn't keep its password and none was entered this app run; see [SmbRepository.unlock]. */
    PASSWORD_REQUIRED,
    UNKNOWN
}

class NetworkException(val error: NetworkError, cause: Throwable? = null) : Exception(error.name, cause)

/** Virtual paths for network entries: `smb://<server id>/<path inside the server>`. */
object SmbPath {
    private const val SCHEME = "smb://"

    fun of(serverId: Long, inner: String) = "$SCHEME$serverId/${inner.trim('/')}".trimEnd('/')

    /** (server id, inner path), or null for a local path. */
    fun parse(path: String): Pair<Long, String>? {
        if (!path.startsWith(SCHEME)) return null
        val rest = path.removePrefix(SCHEME)
        val id = rest.substringBefore('/').toLongOrNull() ?: return null
        return id to rest.substringAfter('/', "")
    }
}

internal data class NormalizedEndpoint(
    val host: String,
    val share: String,
    val protocol: NetworkProtocol,
    val port: Int
)

/**
 * Normalizes user-entered host, port, and share/path strings (including full URLs like
 * `https://nas.local:5006/webdav/folder` or `\\server\share`) into canonical fields.
 */
internal object NetworkEndpoint {
    fun normalize(
        rawHost: String,
        rawShare: String,
        protocol: NetworkProtocol,
        rawPort: Int
    ): NormalizedEndpoint {
        var resolvedProtocol = protocol
        var hostPart = rawHost.trim().replace('\\', '/')
        when {
            hostPart.startsWith("https://", ignoreCase = true) -> {
                hostPart = hostPart.substring(8)
                if (resolvedProtocol.isWebDav) resolvedProtocol = NetworkProtocol.WEBDAVS
            }
            hostPart.startsWith("http://", ignoreCase = true) -> {
                hostPart = hostPart.substring(7)
                if (resolvedProtocol.isWebDav) resolvedProtocol = NetworkProtocol.WEBDAV
            }
            hostPart.startsWith("webdavs://", ignoreCase = true) -> {
                hostPart = hostPart.substring(10)
                resolvedProtocol = NetworkProtocol.WEBDAVS
            }
            hostPart.startsWith("webdav://", ignoreCase = true) -> {
                hostPart = hostPart.substring(9)
                resolvedProtocol = NetworkProtocol.WEBDAV
            }
            hostPart.startsWith("sftp://", ignoreCase = true) -> {
                hostPart = hostPart.substring(7)
                resolvedProtocol = NetworkProtocol.SFTP
            }
            hostPart.startsWith("ftp://", ignoreCase = true) -> {
                hostPart = hostPart.substring(6)
                resolvedProtocol = NetworkProtocol.FTP
            }
            hostPart.startsWith("smb://", ignoreCase = true) -> {
                hostPart = hostPart.substring(6)
                resolvedProtocol = NetworkProtocol.SMB
            }
            hostPart.startsWith("//") -> {
                hostPart = hostPart.removePrefix("//")
            }
        }

        val slashIndex = hostPart.indexOf('/')
        val hostAndPort = if (slashIndex >= 0) hostPart.substring(0, slashIndex) else hostPart
        val embeddedPath = if (slashIndex >= 0) hostPart.substring(slashIndex + 1).trim('/') else ""

        var finalHost = hostAndPort.trim()
        var finalPort = rawPort.coerceAtLeast(0)

        // Extract inline :port if host is not an unbracketed IPv6 literal.
        if (finalHost.startsWith("[") && finalHost.contains("]:")) {
            val closeIdx = finalHost.indexOf("]:")
            val parsedPort = finalHost.substring(closeIdx + 2).toIntOrNull()
            if (parsedPort != null && parsedPort in 1..65535) {
                if (finalPort == 0) finalPort = parsedPort
                finalHost = finalHost.substring(0, closeIdx + 1)
            }
        } else if (finalHost.count { it == ':' } == 1) {
            val parsedPort = finalHost.substringAfter(':').toIntOrNull()
            if (parsedPort != null && parsedPort in 1..65535) {
                if (finalPort == 0) finalPort = parsedPort
                finalHost = finalHost.substringBefore(':')
            }
        }

        val cleanRawShare = rawShare.trim().replace('\\', '/').trim('/')
        val combinedShare = when {
            embeddedPath.isNotEmpty() && cleanRawShare.isNotEmpty() -> "$embeddedPath/$cleanRawShare"
            embeddedPath.isNotEmpty() -> embeddedPath
            cleanRawShare.isNotEmpty() -> cleanRawShare
            rawShare.trim() == "/" && resolvedProtocol != NetworkProtocol.SMB -> "/"
            else -> ""
        }

        if (finalPort == resolvedProtocol.defaultPort) {
            finalPort = 0
        }

        return NormalizedEndpoint(
            host = finalHost,
            share = combinedShare,
            protocol = resolvedProtocol,
            port = finalPort
        )
    }
}

internal interface NetworkSession : Closeable {
    val isConnected: Boolean
    fun list(serverId: Long, inner: String): List<StorageItem>
    fun stat(serverId: Long, inner: String): StorageItem?
    /** Share capacity, or null when the server cannot report it. */
    fun space(): NetworkSpace? = null
    fun mkdir(inner: String)
    fun delete(inner: String, isDirectory: Boolean)
    fun rename(inner: String, newInner: String)
    suspend fun <T> read(inner: String, block: suspend (InputStream) -> T): T
    suspend fun <T> write(inner: String, block: suspend (OutputStream) -> T): T
    fun openRandomAccess(
        serverId: Long,
        inner: String,
        knownSize: Long,
        reopenSession: () -> NetworkSession
    ): SmbRandomAccessFile
}

internal interface RandomAccessHandle : Closeable {
    val size: Long
    @Throws(IOException::class)
    fun readChunk(position: Long, buffer: ByteArray, offset: Int, length: Int): Int
}

/**
 * Network shares repository supporting SMB 2/3 (smbj), SFTP (JSch), FTP (Apache Commons Net),
 * and WebDAV / WebDAVS. Sessions stay connected and are re-opened transparently when they drop.
 */
@Singleton
class SmbRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: NetworkServerDao,
    private val cipher: CredentialCipher
) {
    private val smbClient = SMBClient(
        SmbConfig.builder()
            .withTimeout(15, TimeUnit.SECONDS)
            .withSoTimeout(30, TimeUnit.SECONDS)
            .build()
    )
    private val sessions = HashMap<Long, NetworkSession>()
    // Passwords of servers saved without "Remember password"; process memory only, never written to disk.
    private val sessionPasswords = java.util.concurrent.ConcurrentHashMap<Long, String>()
    private val lock = Mutex()

    val servers: Flow<List<NetworkServer>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    /** Connects once to check host, credentials and share/remote path, then saves the server. */
    suspend fun add(
        name: String,
        host: String,
        share: String,
        username: String,
        domain: String,
        password: String,
        protocol: NetworkProtocol = NetworkProtocol.SMB,
        port: Int = 0,
        rememberPassword: Boolean = true
    ): NetworkServer = withContext(Dispatchers.IO) {
        val norm = NetworkEndpoint.normalize(host, share, protocol, port)
        if (norm.host.isEmpty()) throw NetworkException(NetworkError.HOST_UNREACHABLE)
        if (norm.protocol == NetworkProtocol.SMB && norm.share.isEmpty()) {
            throw NetworkException(NetworkError.SHARE_NOT_FOUND)
        }
        val entity = NetworkServerEntity(
            name = name.trim(),
            host = norm.host,
            share = norm.share,
            username = username.trim(),
            domain = if (norm.protocol == NetworkProtocol.SMB) domain.trim() else "",
            passwordCipher = password.takeIf { rememberPassword && it.isNotEmpty() }?.let(cipher::encrypt),
            protocol = norm.protocol.name,
            port = norm.port,
            rememberPassword = rememberPassword
        )
        open(entity, password).close()
        val id = dao.upsert(entity)
        if (!rememberPassword) sessionPasswords[id] = password
        entity.copy(id = id).toModel()
    }

    suspend fun delete(id: Long) {
        lock.withLock {
            sessions.remove(id)?.runCatching { close() }
            sessionPasswords.remove(id)
        }
        dao.delete(id)
        File(cacheRoot, id.toString()).deleteRecursively()
    }

    suspend fun server(id: Long): NetworkServer? = dao.byId(id)?.toModel()

    /** True when [server] needs its password typed before it can connect. */
    fun isLocked(server: NetworkServer): Boolean =
        !server.rememberPassword && server.username.isNotEmpty() && !sessionPasswords.containsKey(server.id)

    /** Connects [id] with [password] and keeps it in memory until the app process ends. Throws [NetworkException]. */
    suspend fun unlock(id: Long, password: String): Unit = withContext(Dispatchers.IO) {
        lock.withLock {
            val entity = dao.byId(id) ?: throw NetworkException(NetworkError.UNKNOWN)
            val session = open(entity, password)
            sessions.remove(id)?.runCatching { close() }
            sessions[id] = session
            sessionPasswords[id] = password
        }
    }

    /** Connects (or reuses the live session) to tell whether [id] is reachable, with its capacity when known. */
    suspend fun probe(id: Long): NetworkStatus = withContext(Dispatchers.IO) {
        try {
            withSession(id) { NetworkStatus.Online(runCatching { it.space() }.getOrNull()) }
        } catch (e: Exception) {
            NetworkStatus.Offline((e as? NetworkException)?.error ?: NetworkError.UNKNOWN)
        }
    }

    /** Children of [inner] (path inside the server), folders first. */
    suspend fun list(serverId: Long, inner: String): List<StorageItem> = withContext(Dispatchers.IO) {
        withSession(serverId) { session -> session.list(serverId, inner) }
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
            read(serverId, inner) { input ->
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
            if (!partial.renameTo(target)) throw NetworkException(NetworkError.UNKNOWN)
            target
        } finally {
            partial.delete()
        }
    }

    // region Primitives for the file operation executor (copy / move / delete across local and network shares)

    /** The entry at [inner], or null when nothing is there. */
    suspend fun stat(serverId: Long, inner: String): StorageItem? = withContext(Dispatchers.IO) {
        withSession(serverId) { session -> session.stat(serverId, inner) }
    }

    suspend fun mkdir(serverId: Long, inner: String) = withContext(Dispatchers.IO) {
        withSession(serverId) { session -> session.mkdir(inner) }
    }

    /** Removes a file, or a folder with everything in it. */
    suspend fun delete(serverId: Long, inner: String, isDirectory: Boolean) = withContext(Dispatchers.IO) {
        withSession(serverId) { session -> session.delete(inner, isDirectory) }
    }

    /** Server-side rename or move within the same server. */
    suspend fun rename(serverId: Long, inner: String, newInner: String) = withContext(Dispatchers.IO) {
        withSession(serverId) { session -> session.rename(inner, newInner) }
    }

    suspend fun <T> read(serverId: Long, inner: String, block: suspend (InputStream) -> T): T = withContext(Dispatchers.IO) {
        withSession(serverId) { session -> session.read(inner, block) }
    }

    /** Creates or writes a file on the remote server. */
    suspend fun <T> write(serverId: Long, inner: String, block: suspend (OutputStream) -> T): T = withContext(Dispatchers.IO) {
        withSession(serverId) { session -> session.write(inner, block) }
    }

    /**
     * Opens a seekable reader for [inner] on [serverId]. Uses a 256 KB read-ahead buffer so
     * sequential streaming and small header probes (thumbnails, video players) avoid per-call RPCs,
     * and reconnects transparently if the session drops mid-stream.
     */
    suspend fun openRandomAccess(serverId: Long, inner: String, knownSize: Long = -1L): SmbRandomAccessFile =
        withContext(Dispatchers.IO) {
            withSession(serverId) { session ->
                session.openRandomAccess(
                    serverId = serverId,
                    inner = inner,
                    knownSize = knownSize,
                    reopenSession = {
                        runBlocking(Dispatchers.IO) {
                            lock.withLock {
                                sessions.remove(serverId)?.runCatching { close() }
                                val entity = dao.byId(serverId) ?: throw NetworkException(NetworkError.UNKNOWN)
                                open(entity).also { sessions[serverId] = it }
                            }
                        }
                    }
                )
            }
        }

    // endregion

    private val cacheRoot get() = File(context.cacheDir, "network")

    /** Runs [block] on the server's session, reconnecting once if the cached session went stale. */
    private suspend fun <T> withSession(serverId: Long, block: suspend (NetworkSession) -> T): T {
        suspend fun session(fresh: Boolean): NetworkSession = lock.withLock {
            if (fresh) sessions.remove(serverId)?.runCatching { close() }
            sessions[serverId]?.takeIf { it.isConnected } ?: run {
                val entity = dao.byId(serverId) ?: throw NetworkException(NetworkError.UNKNOWN)
                open(entity).also { sessions[serverId] = it }
            }
        }
        return try {
            block(session(fresh = false))
        } catch (e: IOException) {
            // Transport failures (idle timeout, Wi-Fi change) leave a dead session: retry once on a new one.
            try { block(session(fresh = true)) } catch (e2: Exception) { throw e2.toNetwork() }
        } catch (e: Exception) {
            throw e.toNetwork()
        }
    }

    private fun storedPassword(entity: NetworkServerEntity): String = when {
        entity.rememberPassword -> entity.passwordCipher?.let { cipher.decrypt(it) ?: throw NetworkException(NetworkError.AUTH_FAILED) }.orEmpty()
        entity.username.isEmpty() -> ""
        else -> sessionPasswords[entity.id] ?: throw NetworkException(NetworkError.PASSWORD_REQUIRED)
    }

    private fun open(entity: NetworkServerEntity, password: String = storedPassword(entity)): NetworkSession = try {
        val protocol = NetworkProtocol.fromName(entity.protocol)
        val effectivePort = if (entity.port > 0) entity.port else protocol.defaultPort
        when (protocol) {
            NetworkProtocol.SMB -> openSmb(entity, password, effectivePort)
            NetworkProtocol.SFTP -> SftpSession.connect(entity.host, effectivePort, entity.share, entity.username, password)
            NetworkProtocol.FTP -> FtpSession.connect(entity.host, effectivePort, entity.share, entity.username, password)
            NetworkProtocol.WEBDAV, NetworkProtocol.WEBDAVS -> {
                WebDavSession(
                    host = entity.host,
                    port = effectivePort,
                    https = protocol == NetworkProtocol.WEBDAVS,
                    basePath = entity.share,
                    username = entity.username,
                    password = password
                ).also { it.verify() }
            }
        }
    } catch (e: Exception) {
        throw e.toNetwork()
    }

    private fun openSmb(entity: NetworkServerEntity, password: String, port: Int): NetworkSession {
        val auth = if (entity.username.isEmpty()) AuthenticationContext.guest()
        else AuthenticationContext(entity.username, password.toCharArray(), entity.domain.ifEmpty { null })
        val rawShare = entity.share.trim().replace('\\', '/').trim('/')
        val shareName = rawShare.substringBefore('/')
        val subPath = rawShare.substringAfter('/', "")
        if (shareName.isEmpty()) throw NetworkException(NetworkError.SHARE_NOT_FOUND)
        val connection = if (port == NetworkProtocol.SMB.defaultPort) {
            smbClient.connect(entity.host)
        } else {
            smbClient.connect(entity.host, port)
        }
        val session = connection.authenticate(auth)
        val diskShare = session.connectShare(shareName) as? DiskShare
            ?: throw NetworkException(NetworkError.SHARE_NOT_FOUND)
        if (subPath.isNotEmpty() && !diskShare.folderExists(subPath.replace('/', '\\'))) {
            runCatching { diskShare.close() }
            throw NetworkException(NetworkError.SHARE_NOT_FOUND)
        }
        return SmbSession(diskShare, subPath)
    }

    private fun NetworkServerEntity.toModel() = NetworkServer(
        id = id,
        name = name,
        host = host,
        share = share,
        username = username,
        domain = domain,
        protocol = NetworkProtocol.fromName(protocol),
        port = port,
        rememberPassword = rememberPassword
    )
}

private class SmbSession(
    private val share: DiskShare,
    private val baseSubPath: String
) : NetworkSession {

    override val isConnected: Boolean get() = share.isConnected

    override fun space(): NetworkSpace = share.shareInformation.let { NetworkSpace(it.totalSpace, it.freeSpace) }

    private fun resolveSmbPath(inner: String): String {
        val cleanInner = inner.trim('/')
        val combined = when {
            baseSubPath.isNotEmpty() && cleanInner.isNotEmpty() -> "$baseSubPath/$cleanInner"
            baseSubPath.isNotEmpty() -> baseSubPath
            else -> cleanInner
        }
        return combined.trim('/').replace('/', '\\')
    }

    override fun list(serverId: Long, inner: String): List<StorageItem> {
        val cleanInner = inner.trim('/')
        return share.list(resolveSmbPath(cleanInner))
            .filter { it.fileName != "." && it.fileName != ".." }
            .map { info ->
                val isDir = EnumWithValue.EnumUtils.isSet(info.fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY)
                val childInner = if (cleanInner.isEmpty()) info.fileName else "$cleanInner/${info.fileName}"
                StorageItem(
                    path = SmbPath.of(serverId, childInner),
                    name = info.fileName,
                    size = if (isDir) 0 else info.endOfFile,
                    lastModified = info.lastWriteTime.toEpochMillis(),
                    isDirectory = isDir,
                    extension = if (isDir) "" else info.fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
                )
            }
            .sortedWith(compareByDescending<StorageItem> { it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
    }

    override fun stat(serverId: Long, inner: String): StorageItem? {
        val path = resolveSmbPath(inner)
        return when {
            path.isEmpty() || share.folderExists(path) -> StorageItem(
                path = SmbPath.of(serverId, inner),
                name = inner.substringAfterLast('/'),
                isDirectory = true
            )
            share.fileExists(path) -> {
                val info = share.getFileInformation(path)
                val name = inner.substringAfterLast('/')
                StorageItem(
                    path = SmbPath.of(serverId, inner),
                    name = name,
                    size = info.standardInformation.endOfFile,
                    lastModified = info.basicInformation.lastWriteTime.toEpochMillis(),
                    extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                )
            }
            else -> null
        }
    }

    override fun mkdir(inner: String) {
        share.mkdir(resolveSmbPath(inner))
    }

    override fun delete(inner: String, isDirectory: Boolean) {
        val path = resolveSmbPath(inner)
        if (isDirectory) share.rmdir(path, true) else share.rm(path)
    }

    override fun rename(inner: String, newInner: String) {
        share.open(
            resolveSmbPath(inner),
            EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        ).use { it.rename(resolveSmbPath(newInner)) }
    }

    override suspend fun <T> read(inner: String, block: suspend (InputStream) -> T): T {
        return share.openFile(
            resolveSmbPath(inner),
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        ).use { remote -> remote.inputStream.use { block(it) } }
    }

    override suspend fun <T> write(inner: String, block: suspend (OutputStream) -> T): T {
        return share.openFile(
            resolveSmbPath(inner),
            EnumSet.of(AccessMask.GENERIC_WRITE),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_CREATE,
            null
        ).use { remote -> remote.outputStream.use { block(it) } }
    }

    override fun openRandomAccess(
        serverId: Long,
        inner: String,
        knownSize: Long,
        reopenSession: () -> NetworkSession
    ): SmbRandomAccessFile {
        val smbPath = resolveSmbPath(inner)
        val f = openRemoteFile(smbPath)
        val size = try {
            if (knownSize >= 0L) knownSize else f.fileInformation.standardInformation.endOfFile
        } catch (e: Exception) {
            runCatching { f.close() }
            throw e
        }
        return SmbRandomAccessFile(
            size = size,
            initial = wrapFile(f, size),
            reopen = {
                val freshSession = reopenSession() as SmbSession
                freshSession.wrapFile(freshSession.openRemoteFile(smbPath), size)
            }
        )
    }

    private fun openRemoteFile(smbPath: String): com.hierynomus.smbj.share.File =
        share.openFile(
            smbPath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )

    private fun wrapFile(file: com.hierynomus.smbj.share.File, size: Long): RandomAccessHandle =
        object : RandomAccessHandle {
            override val size: Long = size
            override fun readChunk(position: Long, buffer: ByteArray, offset: Int, length: Int): Int =
                file.read(buffer, position, offset, length)
            override fun close() = file.close()
        }

    override fun close() = share.close()
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
    // smbj and other clients wrap socket failures in their own transport exceptions.
    else -> generateSequence<Throwable>(cause) { it.cause }.firstOrNull {
        it is UnknownHostException || it is ConnectException || it is NoRouteToHostException || it is SocketTimeoutException
    }?.let { NetworkException(NetworkError.HOST_UNREACHABLE, this) }
        ?: NetworkException(NetworkError.UNKNOWN, this)
}

/**
 * Thread-safe, seekable reader over a network file with a 256 KB read-ahead window.
 * Supports both `MediaDataSource` (video thumbnails/metadata) and `ProxyFileDescriptorCallback` (streaming).
 */
class SmbRandomAccessFile internal constructor(
    val size: Long,
    initial: RandomAccessHandle,
    private val reopen: () -> RandomAccessHandle
) : Closeable {
    private var remote: RandomAccessHandle = initial
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
            remote.readChunk(pos, chunk, 0, targetLen)
        } catch (e: Exception) {
            runCatching { remote.close() }
            try {
                remote = reopen()
                remote.readChunk(pos, chunk, 0, targetLen)
            } catch (e2: Exception) {
                throw IOException("Network read failed at $pos", e2)
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
