package com.aiyu.findex.core.vault

import android.content.ContentProvider
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
import com.aiyu.findex.core.db.VaultDao
import com.aiyu.findex.core.db.VaultItemEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException
import java.io.RandomAccessFile

/**
 * Read-only `content://<pkg>.vaultprovider/<itemId>`: streams decrypted vault items through a proxy
 * file descriptor, so viewers can seek without any plaintext touching the disk. Fails while locked.
 */
class VaultContentProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface VaultProviderEntryPoint {
        fun vaultDao(): VaultDao
        fun vaultSession(): VaultSession
        fun vaultRepository(): VaultRepository
    }

    companion object {
        fun uri(context: Context, id: Long): Uri = Uri.parse("content://${context.packageName}.vaultprovider/$id")
        private val thread by lazy { HandlerThread("vault-provider").apply { start() } }
    }

    private val deps by lazy { EntryPointAccessors.fromApplication(context!!.applicationContext, VaultProviderEntryPoint::class.java) }

    override fun onCreate(): Boolean = true

    private fun row(uri: Uri): VaultItemEntity? {
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        if (!deps.vaultSession().unlocked.value) return null
        return runBlocking { deps.vaultDao().byId(id) }
    }

    override fun getType(uri: Uri): String? = row(uri)?.mimeType

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val row = row(uri) ?: return null
        val name = runCatching { deps.vaultRepository().decrypt(row).name }.getOrNull() ?: return null
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(cols, 1).apply {
            addRow(cols.map { if (it == OpenableColumns.SIZE) row.size else if (it == OpenableColumns.DISPLAY_NAME) name else null })
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw SecurityException("read-only")
        val row = row(uri) ?: throw FileNotFoundException("vault locked or item missing")
        val raf = RandomAccessFile(VaultRepository.blobFile(row.blobName), "r")
        val ra = try {
            deps.vaultSession().withKey { VaultFormat.openRandomAccess(raf, it) }
        } catch (e: Exception) {
            raf.close()
            throw FileNotFoundException("cannot open vault item").apply { initCause(e) }
        }
        val session = deps.vaultSession()
        val storage = context!!.getSystemService(StorageManager::class.java)
        return storage.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, object : ProxyFileDescriptorCallback() {
            override fun onGetSize(): Long = ra.size
            override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
                // An FD opened while unlocked must stop yielding plaintext once the vault locks.
                if (!session.unlocked.value) throw ErrnoException("read", OsConstants.EACCES)
                return try {
                    ra.readAt(offset, data, 0, size).coerceAtLeast(0)
                } catch (e: Exception) {
                    throw ErrnoException("read", OsConstants.EIO)
                }
            }
            override fun onRelease() = ra.close()
        }, Handler(thread.looper))
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
