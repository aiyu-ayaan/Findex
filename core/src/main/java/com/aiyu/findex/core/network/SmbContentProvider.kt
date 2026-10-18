package com.aiyu.findex.core.network

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.OsConstants
import android.webkit.MimeTypeMap
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SmbProviderEntryPoint {
    fun smbRepository(): SmbRepository
}

/**
 * Exposes SMB share files as seekable `content://` URIs backed by [StorageManager.openProxyFileDescriptor].
 * External media players (VLC, MX Player, ExoPlayer, system video player) can seek and stream
 * directly over SMB without downloading the file into local storage first.
 */
class SmbContentProvider : ContentProvider() {

    private val smb: SmbRepository by lazy {
        EntryPointAccessors.fromApplication(
            checkNotNull(context).applicationContext,
            SmbProviderEntryPoint::class.java
        ).smbRepository()
    }

    private val handlers: Array<Handler> by lazy {
        Array(4) { i ->
            val thread = HandlerThread("smb-stream-$i").apply { start() }
            Handler(thread.looper)
        }
    }
    private val nextHandlerIndex = AtomicInteger(0)

    private fun nextHandler(): Handler = handlers[nextHandlerIndex.getAndIncrement() and 3]

    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? {
        val target = parse(uri) ?: return null
        return mimeTypeForName(target.name)
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val target = parse(uri) ?: return null
        val columns = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val needSize = OpenableColumns.SIZE in columns
        val resolvedSize = when {
            !needSize -> -1L
            target.size >= 0L -> target.size
            else -> runCatching {
                runBlocking(Dispatchers.IO) { smb.stat(target.serverId, target.inner)?.size }
            }.getOrNull() ?: -1L
        }
        val row = Array<Any?>(columns.size) { index ->
            when (columns[index]) {
                OpenableColumns.DISPLAY_NAME -> target.name
                OpenableColumns.SIZE -> if (resolvedSize >= 0L) resolvedSize else null
                else -> null
            }
        }
        return MatrixCursor(columns, 1).apply { addRow(row) }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("Read-only provider: $uri")
        val target = parse(uri) ?: throw FileNotFoundException("Invalid SMB URI: $uri")
        val ctx = context ?: throw FileNotFoundException("Context unavailable")
        val storageManager = ctx.getSystemService(StorageManager::class.java)
            ?: throw FileNotFoundException("StorageManager unavailable")
        return try {
            storageManager.openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY,
                SmbProxyCallback(smb, target.serverId, target.inner, target.size),
                nextHandler()
            )
        } catch (e: IOException) {
            throw FileNotFoundException(e.message ?: "Failed to open SMB stream")
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    private data class Target(val serverId: Long, val inner: String, val name: String, val size: Long)

    private fun parse(uri: Uri): Target? {
        val segments = uri.pathSegments
        if (segments.size < 2) return null
        val serverId = segments.first().toLongOrNull() ?: return null
        val inner = segments.drop(1).joinToString("/")
        val name = segments.last()
        val size = uri.getQueryParameter(PARAM_SIZE)?.toLongOrNull() ?: -1L
        return Target(serverId, inner, name, size)
    }

    private class SmbProxyCallback(
        private val smb: SmbRepository,
        private val serverId: Long,
        private val inner: String,
        private val knownSize: Long
    ) : ProxyFileDescriptorCallback() {

        private var raf: SmbRandomAccessFile? = null

        @Throws(ErrnoException::class)
        private fun reader(): SmbRandomAccessFile {
            raf?.let { return it }
            return try {
                runBlocking(Dispatchers.IO) {
                    smb.openRandomAccess(serverId, inner, knownSize)
                }.also { raf = it }
            } catch (e: Exception) {
                throw ErrnoException("open", OsConstants.EIO, e)
            }
        }

        @Throws(ErrnoException::class)
        override fun onGetSize(): Long {
            if (knownSize >= 0L) return knownSize
            return reader().size
        }

        @Throws(ErrnoException::class)
        override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
            if (size <= 0 || data.isEmpty()) return 0
            return try {
                val n = reader().readAt(offset, data, 0, minOf(size, data.size))
                if (n < 0) 0 else n
            } catch (e: ErrnoException) {
                throw e
            } catch (e: Exception) {
                throw ErrnoException("read", OsConstants.EIO, e)
            }
        }

        override fun onRelease() {
            runCatching { raf?.close() }
            raf = null
        }
    }

    companion object {
        private const val AUTHORITY_SUFFIX = ".smbprovider"
        private const val PARAM_SIZE = "size"

        fun uriFor(context: Context, serverId: Long, inner: String, size: Long = -1L): Uri =
            Uri.Builder()
                .scheme(ContentResolver.SCHEME_CONTENT)
                .authority("${context.packageName}$AUTHORITY_SUFFIX")
                .appendPath(serverId.toString())
                .apply {
                    inner.trim('/').split('/').filter { it.isNotEmpty() }.forEach(::appendPath)
                    if (size >= 0L) appendQueryParameter(PARAM_SIZE, size.toString())
                }
                .build()

        fun mimeTypeForName(nameOrPath: String): String {
            val ext = nameOrPath.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty()) return "*/*"
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
            return when (ext) {
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "3gp" -> "video/3gpp"
                "ts" -> "video/mp2ts"
                "mp3" -> "audio/mpeg"
                "m4a", "aac" -> "audio/mp4"
                "flac" -> "audio/flac"
                "wav" -> "audio/wav"
                "ogg", "opus" -> "audio/ogg"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "webp" -> "image/webp"
                "heic", "heif" -> "image/heic"
                "bmp" -> "image/bmp"
                "svg" -> "image/svg+xml"
                "avif" -> "image/avif"
                "pdf" -> "application/pdf"
                else -> "*/*"
            }
        }
    }
}
