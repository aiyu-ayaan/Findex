package com.aiyu.findex.core.network

import com.aiyu.findex.core.model.StorageItem
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SftpATTRS
import com.jcraft.jsch.SftpException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.Properties

/**
 * SFTP (SSH File Transfer Protocol) session backed by [JSch].
 */
internal class SftpSession private constructor(
    private val session: Session,
    private val channel: ChannelSftp,
    private val baseDir: String
) : NetworkSession {

    private val channelLock = Any()

    override val isConnected: Boolean
        get() = session.isConnected && channel.isConnected

    override fun list(serverId: Long, inner: String): List<StorageItem> = synchronized(channelLock) {
        try {
            val targetDir = remotePath(inner)
            val cleanInner = inner.trim('/')
            val entries = channel.ls(targetDir)
            entries.mapNotNull { entry ->
                val name = entry.filename
                if (name.isEmpty() || name == "." || name == "..") return@mapNotNull null
                val attrs = entry.attrs
                val isDir = attrs.isDir || (attrs.isLink && isLinkDirectory( "$targetDir/$name"))
                val childInner = if (cleanInner.isEmpty()) name else "$cleanInner/$name"
                StorageItem(
                    path = SmbPath.of(serverId, childInner),
                    name = name,
                    size = if (isDir) 0L else attrs.size.coerceAtLeast(0L),
                    lastModified = attrs.mTime.toLong() * 1000L,
                    isDirectory = isDir,
                    extension = if (isDir) "" else name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                )
            }.sortedWith(compareByDescending<StorageItem> { it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
        } catch (e: Exception) {
            throw mapSftpException(e)
        }
    }

    override fun stat(serverId: Long, inner: String): StorageItem? = synchronized(channelLock) {
        val cleanInner = inner.trim('/')
        if (cleanInner.isEmpty()) {
            return StorageItem(
                path = SmbPath.of(serverId, ""),
                name = "",
                isDirectory = true
            )
        }
        try {
            val target = remotePath(cleanInner)
            val attrs = channel.stat(target) ?: return null
            val name = cleanInner.substringAfterLast('/')
            val isDir = attrs.isDir
            StorageItem(
                path = SmbPath.of(serverId, cleanInner),
                name = name,
                size = if (isDir) 0L else attrs.size.coerceAtLeast(0L),
                lastModified = attrs.mTime.toLong() * 1000L,
                isDirectory = isDir,
                extension = if (isDir) "" else name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            )
        } catch (e: SftpException) {
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) null else throw mapSftpException(e)
        } catch (e: Exception) {
            throw mapSftpException(e)
        }
    }

    override fun mkdir(inner: String) = synchronized(channelLock) {
        try {
            channel.mkdir(remotePath(inner))
        } catch (e: Exception) {
            throw mapSftpException(e)
        }
    }

    override fun delete(inner: String, isDirectory: Boolean) = synchronized(channelLock) {
        try {
            val target = remotePath(inner)
            if (isDirectory) {
                deleteDirectoryRecursive(target)
            } else {
                channel.rm(target)
            }
        } catch (e: Exception) {
            throw mapSftpException(e)
        }
    }

    private fun deleteDirectoryRecursive(dirPath: String) {
        val entries = channel.ls(dirPath)
        for (entry in entries) {
            val name = entry.filename
            if (name == "." || name == "..") continue
            val childPath = "${dirPath.trimEnd('/')}/$name"
            if (entry.attrs.isDir) {
                deleteDirectoryRecursive(childPath)
            } else {
                channel.rm(childPath)
            }
        }
        channel.rmdir(dirPath)
    }

    override fun rename(inner: String, newInner: String) = synchronized(channelLock) {
        try {
            channel.rename(remotePath(inner), remotePath(newInner))
        } catch (e: Exception) {
            throw mapSftpException(e)
        }
    }

    override suspend fun <T> read(inner: String, block: suspend (InputStream) -> T): T {
        val target = remotePath(inner)
        val dedicated = openDedicatedChannel()
        if (dedicated != null) {
            try {
                return dedicated.get(target).use { block(it) }
            } catch (e: Exception) {
                throw mapSftpException(e)
            } finally {
                runCatching { dedicated.disconnect() }
            }
        }
        // Fallback when server restricts MaxSessions to 1: open stream via primary channel.
        val stream = synchronized(channelLock) {
            try {
                channel.get(target)
            } catch (e: Exception) {
                throw mapSftpException(e)
            }
        }
        return try {
            stream.use { block(it) }
        } catch (e: Exception) {
            throw mapSftpException(e)
        }
    }

    override suspend fun <T> write(inner: String, block: suspend (OutputStream) -> T): T {
        val target = remotePath(inner)
        val dedicated = openDedicatedChannel()
        if (dedicated != null) {
            try {
                return dedicated.put(target, ChannelSftp.OVERWRITE).use { block(it) }
            } catch (e: Exception) {
                throw mapSftpException(e)
            } finally {
                runCatching { dedicated.disconnect() }
            }
        }
        val stream = synchronized(channelLock) {
            try {
                channel.put(target, ChannelSftp.OVERWRITE)
            } catch (e: Exception) {
                throw mapSftpException(e)
            }
        }
        return try {
            stream.use { block(it) }
        } catch (e: Exception) {
            throw mapSftpException(e)
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
            initial = createHandle(inner, size),
            reopen = {
                val fresh = reopenSession() as SftpSession
                fresh.createHandle(inner, size)
            }
        )
    }

    private fun createHandle(inner: String, size: Long): RandomAccessHandle {
        val target = remotePath(inner)
        return object : RandomAccessHandle {
            override val size: Long = size

            override fun readChunk(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
                if (length <= 0 || position >= size) return -1
                return synchronized(channelLock) {
                    try {
                        channel.get(target, null, position).use { input ->
                            readFullyUpTo(input, buffer, offset, length)
                        }
                    } catch (e: Exception) {
                        throw IOException("SFTP read failed at $position", e)
                    }
                }
            }

            override fun close() {}
        }
    }

    override fun close() {
        runCatching { channel.disconnect() }
        runCatching { session.disconnect() }
    }

    private fun openDedicatedChannel(): ChannelSftp? = try {
        (session.openChannel("sftp") as? ChannelSftp)?.apply { connect(15_000) }
    } catch (_: Exception) {
        null
    }

    private fun isLinkDirectory(path: String): Boolean = try {
        channel.stat(path)?.isDir == true
    } catch (_: Exception) {
        false
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
        ): SftpSession {
            var session: Session? = null
            var channel: ChannelSftp? = null
            try {
                val jsch = JSch()
                val user = username.ifEmpty { "guest" }
                session = jsch.getSession(user, host, port).apply {
                    if (password.isNotEmpty()) setPassword(password)
                    val config = Properties().apply {
                        put("StrictHostKeyChecking", "no")
                        put("PreferredAuthentications", "password,keyboard-interactive,publickey")
                    }
                    setConfig(config)
                    timeout = 30_000
                    connect(15_000)
                }
                channel = (session.openChannel("sftp") as ChannelSftp).apply {
                    connect(15_000)
                }
                val home = runCatching { channel.home }.getOrDefault("/").ifEmpty { "/" }
                val baseDir = resolveBaseDir(channel, home, rawShare)
                return SftpSession(session, channel, baseDir)
            } catch (e: Exception) {
                runCatching { channel?.disconnect() }
                runCatching { session?.disconnect() }
                throw mapSftpException(e)
            }
        }

        private fun resolveBaseDir(channel: ChannelSftp, home: String, rawShare: String): String {
            val trimmed = rawShare.trim()
            if (trimmed.isEmpty()) return home
            if (trimmed == "/") return "/"
            val clean = trimmed.trim('/')
            val candidates = buildList {
                add("/$clean")
                if (home != "/") add("${home.trimEnd('/')}/$clean")
            }
            for (candidate in candidates) {
                val attrs: SftpATTRS? = try {
                    channel.stat(candidate)
                } catch (_: SftpException) {
                    null
                }
                if (attrs != null && attrs.isDir) return candidate
            }
            throw NetworkException(NetworkError.SHARE_NOT_FOUND)
        }

        internal fun mapSftpException(e: Exception): NetworkException {
            if (e is NetworkException) return e
            if (e is SftpException) {
                return when (e.id) {
                    ChannelSftp.SSH_FX_PERMISSION_DENIED -> NetworkException(NetworkError.ACCESS_DENIED, e)
                    ChannelSftp.SSH_FX_NO_SUCH_FILE -> NetworkException(NetworkError.SHARE_NOT_FOUND, e)
                    else -> NetworkException(NetworkError.UNKNOWN, e)
                }
            }
            val msg = e.message.orEmpty().lowercase(Locale.ROOT)
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
            if (e is JSchException) {
                return when {
                    "auth fail" in msg || "auth cancel" in msg || "too many authentication failures" in msg ->
                        NetworkException(NetworkError.AUTH_FAILED, e)
                    "timeout" in msg || "connection refused" in msg || "unknownhost" in msg || "unreachable" in msg || "socket" in msg ->
                        NetworkException(NetworkError.HOST_UNREACHABLE, e)
                    else -> NetworkException(NetworkError.UNKNOWN, e)
                }
            }
            return NetworkException(NetworkError.UNKNOWN, e)
        }
    }
}
