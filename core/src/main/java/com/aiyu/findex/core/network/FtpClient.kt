package com.aiyu.findex.core.network

import com.aiyu.findex.core.model.StorageItem
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Duration
import java.util.Locale

/**
 * FTP session backed by Apache Commons Net [FTPClient] in passive binary UTF-8 mode.
 */
internal class FtpSession private constructor(
    private val host: String,
    private val port: Int,
    private val username: String,
    private val password: String,
    private var client: FTPClient,
    private val baseDir: String
) : NetworkSession {

    private val lock = Any()

    override val isConnected: Boolean
        get() = synchronized(lock) {
            client.isConnected && runCatching { client.sendNoOp() }.getOrDefault(false)
        }

    override fun list(serverId: Long, inner: String): List<StorageItem> = synchronized(lock) {
        try {
            val ftp = activeClient()
            val targetDir = remotePath(inner)
            val cleanInner = inner.trim('/')
            if (!ftp.changeWorkingDirectory(targetDir)) {
                throw mapFtpReply(ftp.replyCode)
            }
            val files = ftp.listFiles() ?: emptyArray()
            files.mapNotNull { file ->
                val name = file?.name ?: return@mapNotNull null
                if (name.isEmpty() || name == "." || name == "..") return@mapNotNull null
                val isDir = file.isDirectory || (file.isSymbolicLink && isSymlinkDirectory(ftp, targetDir, file))
                val childInner = if (cleanInner.isEmpty()) name else "$cleanInner/$name"
                StorageItem(
                    path = SmbPath.of(serverId, childInner),
                    name = name,
                    size = if (isDir) 0L else file.size.coerceAtLeast(0L),
                    lastModified = file.timestamp?.timeInMillis ?: 0L,
                    isDirectory = isDir,
                    extension = if (isDir) "" else name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                )
            }.sortedWith(compareByDescending<StorageItem> { it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
        } catch (e: Exception) {
            throw mapFtpException(e)
        }
    }

    override fun stat(serverId: Long, inner: String): StorageItem? = synchronized(lock) {
        val cleanInner = inner.trim('/')
        if (cleanInner.isEmpty()) {
            return StorageItem(
                path = SmbPath.of(serverId, ""),
                name = "",
                isDirectory = true
            )
        }
        try {
            val ftp = activeClient()
            val target = remotePath(cleanInner)
            val name = cleanInner.substringAfterLast('/')
            if (ftp.changeWorkingDirectory(target)) {
                return StorageItem(
                    path = SmbPath.of(serverId, cleanInner),
                    name = name,
                    isDirectory = true
                )
            }
            val mlist = runCatching { ftp.mlistFile(target) }.getOrNull()
            if (mlist != null) {
                val isDir = mlist.isDirectory
                return StorageItem(
                    path = SmbPath.of(serverId, cleanInner),
                    name = name,
                    size = if (isDir) 0L else mlist.size.coerceAtLeast(0L),
                    lastModified = mlist.timestamp?.timeInMillis ?: 0L,
                    isDirectory = isDir,
                    extension = if (isDir) "" else name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                )
            }
            val parentInner = cleanInner.substringBeforeLast('/', "")
            val parentDir = remotePath(parentInner)
            if (!ftp.changeWorkingDirectory(parentDir)) return null
            val match = ftp.listFiles()?.firstOrNull { it?.name == name } ?: return null
            val isDir = match.isDirectory
            StorageItem(
                path = SmbPath.of(serverId, cleanInner),
                name = name,
                size = if (isDir) 0L else match.size.coerceAtLeast(0L),
                lastModified = match.timestamp?.timeInMillis ?: 0L,
                isDirectory = isDir,
                extension = if (isDir) "" else name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            )
        } catch (e: Exception) {
            throw mapFtpException(e)
        }
    }

    override fun mkdir(inner: String) = synchronized(lock) {
        try {
            val ftp = activeClient()
            if (!ftp.makeDirectory(remotePath(inner))) {
                throw mapFtpReply(ftp.replyCode)
            }
        } catch (e: Exception) {
            throw mapFtpException(e)
        }
    }

    override fun delete(inner: String, isDirectory: Boolean) = synchronized(lock) {
        try {
            val ftp = activeClient()
            val target = remotePath(inner)
            if (isDirectory) {
                deleteDirectoryRecursive(ftp, target)
            } else {
                if (!ftp.deleteFile(target)) throw mapFtpReply(ftp.replyCode)
            }
        } catch (e: Exception) {
            throw mapFtpException(e)
        }
    }

    private fun deleteDirectoryRecursive(ftp: FTPClient, dirPath: String) {
        if (ftp.changeWorkingDirectory(dirPath)) {
            val children = ftp.listFiles() ?: emptyArray()
            for (child in children) {
                val name = child?.name ?: continue
                if (name == "." || name == "..") continue
                val childPath = "${dirPath.trimEnd('/')}/$name"
                if (child.isDirectory) {
                    deleteDirectoryRecursive(ftp, childPath)
                } else {
                    ftp.deleteFile(childPath)
                }
            }
        }
        if (!ftp.removeDirectory(dirPath)) {
            throw mapFtpReply(ftp.replyCode)
        }
    }

    override fun rename(inner: String, newInner: String) = synchronized(lock) {
        try {
            val ftp = activeClient()
            if (!ftp.rename(remotePath(inner), remotePath(newInner))) {
                throw mapFtpReply(ftp.replyCode)
            }
        } catch (e: Exception) {
            throw mapFtpException(e)
        }
    }

    override suspend fun <T> read(inner: String, block: suspend (InputStream) -> T): T {
        val target = remotePath(inner)
        val transferClient = openNewClient(host, port, username, password)
        try {
            val stream = transferClient.retrieveFileStream(target)
                ?: throw mapFtpReply(transferClient.replyCode)
            val result = stream.use { block(it) }
            runCatching { transferClient.completePendingCommand() }
            return result
        } catch (e: Exception) {
            throw mapFtpException(e)
        } finally {
            disconnectQuietly(transferClient)
        }
    }

    override suspend fun <T> write(inner: String, block: suspend (OutputStream) -> T): T {
        val target = remotePath(inner)
        val transferClient = openNewClient(host, port, username, password)
        try {
            val stream = transferClient.storeFileStream(target)
                ?: throw mapFtpReply(transferClient.replyCode)
            val result = stream.use { block(it) }
            if (!transferClient.completePendingCommand()) {
                throw mapFtpReply(transferClient.replyCode)
            }
            return result
        } catch (e: Exception) {
            throw mapFtpException(e)
        } finally {
            disconnectQuietly(transferClient)
        }
    }

    override fun openRandomAccess(
        serverId: Long,
        inner: String,
        knownSize: Long,
        reopenSession: () -> NetworkSession
    ): SmbRandomAccessFile {
        val size = if (knownSize >= 0L) {
            knownSize
        } else {
            stat(serverId, inner)?.size ?: throw NetworkException(NetworkError.SHARE_NOT_FOUND)
        }
        return SmbRandomAccessFile(
            size = size,
            initial = createRandomAccessHandle(inner, size),
            reopen = { createRandomAccessHandle(inner, size) }
        )
    }

    private fun createRandomAccessHandle(inner: String, size: Long): RandomAccessHandle {
        val target = remotePath(inner)
        return object : RandomAccessHandle {
            override val size: Long = size
            private var ftp: FTPClient? = null
            private var activeStream: InputStream? = null
            private var streamPos: Long = -1L

            @Synchronized
            override fun readChunk(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
                if (length <= 0 || position >= size) return -1
                try {
                    val currentStream = activeStream
                    if (currentStream != null && position == streamPos) {
                        val read = readFullyUpTo(currentStream, buffer, offset, length)
                        if (read > 0) streamPos += read
                        if (read < 0 || streamPos >= size) closeActiveStream(cleanComplete = true)
                        return read
                    }
                    if (currentStream != null && position > streamPos && position - streamPos <= 128 * 1024) {
                        skipFully(currentStream, position - streamPos)
                        streamPos = position
                        val read = readFullyUpTo(currentStream, buffer, offset, length)
                        if (read > 0) streamPos += read
                        if (read < 0 || streamPos >= size) closeActiveStream(cleanComplete = true)
                        return read
                    }
                    // Need a seek to a new offset: close any previous stream and open at position.
                    closeActiveStream(cleanComplete = false)
                    val client = ensureFtp()
                    client.restartOffset = position
                    val stream = client.retrieveFileStream(target)
                        ?: throw IOException("FTP retrieveFileStream failed (${client.replyCode}) at $position")
                    activeStream = stream
                    streamPos = position
                    val read = readFullyUpTo(stream, buffer, offset, length)
                    if (read > 0) streamPos += read
                    if (read < 0 || streamPos >= size) closeActiveStream(cleanComplete = true)
                    return read
                } catch (e: Exception) {
                    close()
                    throw if (e is IOException) e else IOException("FTP read failed at $position", e)
                }
            }

            private fun ensureFtp(): FTPClient {
                ftp?.takeIf { it.isConnected }?.let { return it }
                return openNewClient(host, port, username, password).also { ftp = it }
            }

            private fun closeActiveStream(cleanComplete: Boolean) {
                val stream = activeStream ?: return
                activeStream = null
                streamPos = -1L
                runCatching { stream.close() }
                val c = ftp ?: return
                if (cleanComplete) {
                    if (!runCatching { c.completePendingCommand() }.getOrDefault(false)) {
                        disconnectQuietly(c)
                        ftp = null
                    }
                } else {
                    // Aborting mid-stream on FTP is safest by resetting the transfer connection.
                    disconnectQuietly(c)
                    ftp = null
                }
            }

            @Synchronized
            override fun close() {
                runCatching { activeStream?.close() }
                activeStream = null
                streamPos = -1L
                ftp?.let { disconnectQuietly(it) }
                ftp = null
            }
        }
    }

    override fun close() = synchronized(lock) {
        disconnectQuietly(client)
    }

    private fun activeClient(): FTPClient {
        if (!client.isConnected || !runCatching { client.sendNoOp() }.getOrDefault(false)) {
            disconnectQuietly(client)
            client = openNewClient(host, port, username, password)
        }
        return client
    }

    private fun isSymlinkDirectory(ftp: FTPClient, parentDir: String, file: FTPFile): Boolean {
        val childPath = "${parentDir.trimEnd('/')}/${file.name}"
        val entered = ftp.changeWorkingDirectory(childPath)
        if (entered) ftp.changeWorkingDirectory(parentDir)
        return entered
    }

    private fun remotePath(inner: String): String {
        val clean = inner.trim('/')
        if (clean.isEmpty()) return baseDir
        return if (baseDir == "/") "/$clean" else "${baseDir.trimEnd('/')}/$clean"
    }

    companion object {
        fun connect(
            host: String,
            port: Int,
            rawShare: String,
            username: String,
            password: String
        ): FtpSession {
            val ftp = openNewClient(host, port, username, password)
            try {
                val home = runCatching { ftp.printWorkingDirectory() }.getOrNull().orEmpty().ifEmpty { "/" }
                val baseDir = resolveBaseDir(ftp, home, rawShare)
                return FtpSession(host, port, username, password, ftp, baseDir)
            } catch (e: Exception) {
                disconnectQuietly(ftp)
                throw mapFtpException(e)
            }
        }

        private fun openNewClient(host: String, port: Int, username: String, password: String): FTPClient {
            val ftp = FTPClient()
            try {
                ftp.connectTimeout = 15_000
                ftp.defaultTimeout = 30_000
                ftp.controlEncoding = "UTF-8"
                ftp.autodetectUTF8 = true
                ftp.connect(host, port)
                if (!FTPReply.isPositiveCompletion(ftp.replyCode)) {
                    throw NetworkException(NetworkError.HOST_UNREACHABLE)
                }
                val user = username.ifEmpty { "anonymous" }
                val pass = if (username.isEmpty() && password.isEmpty()) "guest@example.com" else password
                if (!ftp.login(user, pass)) {
                    throw NetworkException(NetworkError.AUTH_FAILED)
                }
                ftp.soTimeout = 30_000
                ftp.setDataTimeout(Duration.ofSeconds(30))
                ftp.enterLocalPassiveMode()
                ftp.setFileType(FTP.BINARY_FILE_TYPE)
                return ftp
            } catch (e: Exception) {
                disconnectQuietly(ftp)
                throw mapFtpException(e)
            }
        }

        private fun resolveBaseDir(ftp: FTPClient, home: String, rawShare: String): String {
            val trimmed = rawShare.trim()
            if (trimmed.isEmpty()) return home
            if (trimmed == "/") {
                if (ftp.changeWorkingDirectory("/")) return "/"
                return home
            }
            val clean = trimmed.trim('/')
            val candidates = buildList {
                add("/$clean")
                if (home != "/") add("${home.trimEnd('/')}/$clean")
            }
            for (candidate in candidates) {
                if (ftp.changeWorkingDirectory(candidate)) {
                    return ftp.printWorkingDirectory() ?: candidate
                }
            }
            throw NetworkException(NetworkError.SHARE_NOT_FOUND)
        }

        private fun disconnectQuietly(ftp: FTPClient) {
            runCatching { if (ftp.isConnected) ftp.logout() }
            runCatching { if (ftp.isConnected) ftp.disconnect() }
        }

        internal fun mapFtpReply(code: Int): NetworkException = when (code) {
            530, 331, 332 -> NetworkException(NetworkError.AUTH_FAILED)
            550 -> NetworkException(NetworkError.SHARE_NOT_FOUND)
            553, 532 -> NetworkException(NetworkError.ACCESS_DENIED)
            421, 425, 426 -> NetworkException(NetworkError.HOST_UNREACHABLE)
            else -> NetworkException(NetworkError.UNKNOWN)
        }

        internal fun mapFtpException(e: Exception): NetworkException {
            if (e is NetworkException) return e
            val causes = generateSequence<Throwable>(e) { it.cause }.toList()
            if (causes.any {
                    it is UnknownHostException ||
                        it is ConnectException ||
                        it is NoRouteToHostException ||
                        it is SocketTimeoutException
                }
            ) {
                return NetworkException(NetworkError.HOST_UNREACHABLE, e)
            }
            return NetworkException(NetworkError.UNKNOWN, e)
        }
    }
}
