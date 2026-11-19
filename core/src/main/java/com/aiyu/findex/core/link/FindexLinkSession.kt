package com.aiyu.findex.core.link

import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.network.NetworkException
import com.aiyu.findex.core.network.NetworkSession
import com.aiyu.findex.core.network.NetworkSpace
import com.aiyu.findex.core.network.RandomAccessHandle
import com.aiyu.findex.core.network.SmbPath
import com.aiyu.findex.core.network.SmbRandomAccessFile
import com.aiyu.findex.core.network.readFullyUpTo
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

/**
 * A paired computer as a network "share": the Explorer, copy/paste through OperationManager,
 * thumbnails and the media player use it like SMB or WebDAV. Paths inside the server are
 * `<root>/<relative>` (for the desktop: `Home/…` and `Received/…`).
 */
internal class FindexLinkSession(private val client: LinkClient) : NetworkSession {

    override val isConnected: Boolean = true

    /** Contacts the computer once, so an offline or unpaired computer fails here (and shows offline). */
    fun verify() = net { client.info() }

    private inline fun <T> net(block: () -> T): T = try {
        block()
    } catch (e: LinkHttpException) {
        throw e.toNetwork()
    }

    override fun list(serverId: Long, inner: String): List<StorageItem> = net {
        client.list(LinkPaths.wire(inner)).map { it.toStorage(serverId, LinkPaths.join(inner, it.name)) }
            .sortedWith(compareByDescending<StorageItem> { it.isDirectory }.thenBy { it.name.lowercase(Locale.ROOT) })
    }

    override fun stat(serverId: Long, inner: String): StorageItem? = net {
        if (inner.trim('/').isEmpty()) return StorageItem(path = SmbPath.of(serverId, ""), name = "", isDirectory = true)
        client.stat(LinkPaths.wire(inner))?.toStorage(serverId, inner.trim('/'))
    }

    /** The first root (the computer's Home), as the card's capacity. */
    override fun space(): NetworkSpace? = net {
        client.info().roots.firstOrNull { it.total > 0 }?.let { NetworkSpace(it.total, it.free) }
    }

    override fun mkdir(inner: String) = net { client.mkdir(LinkPaths.wire(inner)) }

    override fun delete(inner: String, isDirectory: Boolean) = net { client.delete(LinkPaths.wire(inner)) }

    override fun rename(inner: String, newInner: String) = net { client.rename(LinkPaths.wire(inner), LinkPaths.wire(newInner)) }

    override suspend fun <T> read(inner: String, block: suspend (InputStream) -> T): T {
        val input = net { client.open(LinkPaths.wire(inner)) }
        return input.use { block(it) }
    }

    override suspend fun <T> write(inner: String, block: suspend (OutputStream) -> T): T {
        // The upload needs a blocking writer; collect the suspend block's result through it.
        var result: Result<T>? = null
        net {
            client.upload(LinkPaths.wire(inner)) { out ->
                result = runCatching { kotlinx.coroutines.runBlocking { block(out) } }
            }
        }
        return result!!.getOrThrow()
    }

    override fun openRandomAccess(serverId: Long, inner: String, knownSize: Long, reopenSession: () -> NetworkSession): SmbRandomAccessFile {
        val size = if (knownSize >= 0) knownSize else stat(serverId, inner)?.size ?: throw NetworkException(com.aiyu.findex.core.network.NetworkError.SHARE_NOT_FOUND)
        val path = LinkPaths.wire(inner)
        return SmbRandomAccessFile(size = size, initial = Handle(path, size), reopen = { Handle(path, size) })
    }

    /** Keeps one ranged stream open while reads are sequential (playback), and reopens at a new offset on seek. */
    private inner class Handle(private val path: String, override val size: Long) : RandomAccessHandle {
        private var stream: InputStream? = null
        private var position = -1L

        override fun readChunk(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
            if (length <= 0 || position >= size) return -1
            if (stream == null || position != this.position) {
                close()
                stream = try { client.open(path, position) } catch (e: LinkHttpException) { throw IOException(e) }
                this.position = position
            }
            val n = readFullyUpTo(stream!!, buffer, offset, minOf(length.toLong(), size - position).toInt())
            if (n > 0) this.position += n
            return n
        }

        override fun close() {
            runCatching { stream?.close() }
            stream = null
            position = -1
        }
    }

    override fun close() {}

    private fun LinkItem.toStorage(serverId: Long, inner: String) = StorageItem(
        path = SmbPath.of(serverId, inner),
        name = name,
        size = if (dir) 0 else size,
        lastModified = mtime,
        isDirectory = dir,
        extension = if (dir) "" else name.substringAfterLast('.', "").lowercase(Locale.ROOT)
    )
}
