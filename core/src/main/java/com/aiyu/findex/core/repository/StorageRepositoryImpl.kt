package com.aiyu.findex.core.repository

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.aiyu.findex.core.model.CategoryStat
import com.aiyu.findex.core.model.DriveType
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.StorageDrive
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.network.SmbPath
import com.aiyu.findex.core.network.SmbRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smb: SmbRepository
) : StorageRepository {

    override val downloadsPath: String =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath

    override fun getStorageDrives(): Flow<List<StorageDrive>> = flow {
        val drives = mutableListOf<StorageDrive>()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager

        storageManager?.storageVolumes?.forEach { volume ->
            val directory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) volume.directory else null
            val path = directory?.absolutePath
                ?: if (volume.isPrimary) Environment.getExternalStorageDirectory().absolutePath else return@forEach
            val file = File(path)
            if (!file.exists()) return@forEach

            val driveType = when {
                volume.isPrimary -> DriveType.INTERNAL
                volume.isRemovable -> DriveType.SD_CARD
                else -> DriveType.USB
            }
            drives.add(
                StorageDrive(
                    id = volume.uuid ?: "primary",
                    name = volume.getDescription(context),
                    path = path,
                    totalBytes = file.totalSpace,
                    freeBytes = file.freeSpace,
                    type = driveType,
                    isPrimary = volume.isPrimary,
                    isRemovable = volume.isRemovable
                )
            )
        }

        if (drives.isEmpty()) {
            val primary = Environment.getExternalStorageDirectory()
            drives.add(
                StorageDrive(
                    id = "primary",
                    name = "",
                    path = primary.absolutePath,
                    totalBytes = primary.totalSpace,
                    freeBytes = primary.freeSpace,
                    type = DriveType.INTERNAL,
                    isPrimary = true
                )
            )
        }
        emit(drives.sortedByDescending { it.isPrimary })
    }.flowOn(Dispatchers.IO)

    override suspend fun getFilesInDirectory(directoryPath: String, showHidden: Boolean): List<StorageItem> =
        withContext(Dispatchers.IO) {
            // Network shares open in normal tabs; their listing comes from SMB. Throws NetworkException.
            SmbPath.parse(directoryPath)?.let { (id, inner) ->
                return@withContext smb.list(id, inner).filter { showHidden || !it.isHidden }
            }
            val files = File(directoryPath).listFiles() ?: return@withContext emptyList()
            files.asSequence()
                .filter { showHidden || !it.isHidden }
                .map { it.toStorageItem() }
                .toList()
        }

    override suspend fun getItem(path: String): StorageItem? = withContext(Dispatchers.IO) {
        SmbPath.parse(path)?.let { (id, inner) -> return@withContext runCatching { smb.stat(id, inner) }.getOrNull() }
        File(path).takeIf { it.exists() }?.toStorageItem()
    }

    override suspend fun getCategoryFiles(category: FileCategory): List<StorageItem> = withContext(Dispatchers.IO) {
        if (category == FileCategory.DOWNLOADS) {
            return@withContext File(downloadsPath).walkTopDown().filter { it.isFile }.map { it.toStorageItem() }
                .sortedByDescending { it.lastModified }.toList()
        }
        val query = category.mediaQuery()
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.MIME_TYPE
        )
        val items = mutableListOf<StorageItem>()
        context.contentResolver.query(
            query.uri, projection, query.selection, query.args,
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { c ->
            val data = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val name = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val size = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val modified = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val mime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            while (c.moveToNext()) {
                val path = c.getString(data) ?: continue
                val displayName = c.getString(name) ?: path.substringAfterLast('/')
                items += StorageItem(
                    path = path,
                    name = displayName,
                    size = c.getLong(size),
                    lastModified = c.getLong(modified) * 1000,
                    extension = displayName.substringAfterLast('.', "").lowercase(),
                    mimeType = c.getString(mime)
                )
            }
        }
        items
    }

    override suspend fun getCategoryStats(): Map<FileCategory, CategoryStat> = withContext(Dispatchers.IO) {
        FileCategory.entries.filter { it != FileCategory.DOWNLOADS }.associateWith { category ->
            val query = category.mediaQuery()
            var count = 0
            var bytes = 0L
            context.contentResolver.query(
                query.uri, arrayOf(MediaStore.MediaColumns.SIZE), query.selection, query.args, null
            )?.use { c ->
                while (c.moveToNext()) {
                    count++
                    bytes += c.getLong(0)
                }
            }
            CategoryStat(count, bytes)
        }
    }

    override suspend fun search(query: String, limit: Int): List<StorageItem> = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.isEmpty()) return@withContext emptyList()
        // shortcut: MediaStore skips hidden and .nomedia folders; build a filesystem index if users miss those.
        val escaped = term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val paths = mutableListOf<String>()
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.MediaColumns.DATA),
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'",
            arrayOf("%$escaped%"),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { c ->
            while (c.moveToNext() && paths.size < limit) c.getString(0)?.let(paths::add)
        }
        // The index lags behind deletes; only report what is still on disk.
        paths.map(::File).filter { it.exists() }.map { it.toStorageItem() }
            .sortedWith(compareByDescending<StorageItem> { it.isDirectory }.thenByDescending { it.name.startsWith(term, ignoreCase = true) })
    }

    override suspend fun rename(path: String, newName: String): StorageItem? = withContext(Dispatchers.IO) {
        val name = newName.trim()
        if (!isValidName(name)) return@withContext null
        SmbPath.parse(path)?.let { (id, inner) ->
            val newInner = (inner.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }) + name
            return@withContext runCatching {
                if (smb.stat(id, newInner) != null) return@runCatching null
                smb.rename(id, inner, newInner)
                smb.stat(id, newInner)
            }.getOrNull()
        }
        val src = File(path)
        val target = File(src.parentFile, name)
        if (target.exists() || !src.renameTo(target)) return@withContext null
        scan(src, target)
        target.toStorageItem()
    }

    override suspend fun createFolder(parentPath: String, name: String): StorageItem? = withContext(Dispatchers.IO) {
        val trimmed = name.trim()
        if (!isValidName(trimmed)) return@withContext null
        SmbPath.parse(parentPath)?.let { (id, inner) ->
            val newInner = if (inner.isEmpty()) trimmed else "$inner/$trimmed"
            return@withContext runCatching {
                if (smb.stat(id, newInner) != null) return@runCatching null
                smb.mkdir(id, newInner)
                smb.stat(id, newInner)
            }.getOrNull()
        }
        val dir = File(parentPath, trimmed)
        if (dir.exists() || !dir.mkdir()) return@withContext null
        dir.toStorageItem()
    }

    private fun isValidName(name: String) =
        name.isNotEmpty() && name != "." && name != ".." && name.none { it == '/' || it == '\u0000' }

    /** Keeps MediaStore (gallery, category screens) in sync after a rename. */
    private fun scan(vararg files: File) {
        MediaScannerConnection.scanFile(context, files.map { it.absolutePath }.toTypedArray(), null, null)
    }

    private class MediaQuery(val uri: Uri, val selection: String?, val args: Array<String>?)

    private fun FileCategory.mediaQuery(): MediaQuery {
        val files = MediaStore.Files.getContentUri("external")
        return when (this) {
            FileCategory.PHOTOS -> MediaQuery(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null, null)
            FileCategory.VIDEOS -> MediaQuery(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, null, null)
            FileCategory.AUDIO -> MediaQuery(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, null, null)
            FileCategory.DOCUMENTS -> extensionQuery(files, DOCUMENT_EXTENSIONS)
            FileCategory.ARCHIVES -> extensionQuery(files, ARCHIVE_EXTENSIONS)
            FileCategory.APKS -> extensionQuery(files, listOf("apk"))
            FileCategory.DOWNLOADS -> MediaQuery(files, null, null)
        }
    }

    private fun extensionQuery(uri: Uri, extensions: List<String>) = MediaQuery(
        uri,
        extensions.joinToString(" OR ") { "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?" },
        extensions.map { "%.$it" }.toTypedArray()
    )

    private companion object {
        val DOCUMENT_EXTENSIONS = listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "csv", "odt", "ods", "odp", "md", "epub")
        val ARCHIVE_EXTENSIONS = listOf("zip", "7z", "rar", "tar", "gz", "tgz", "bz2", "xz")
    }
}

fun File.toStorageItem(): StorageItem {
    val isDir = isDirectory
    val ext = if (isDir) "" else extension.lowercase()
    return StorageItem(
        path = absolutePath,
        name = name,
        size = if (isDir) 0L else length(),
        lastModified = lastModified(),
        isDirectory = isDir,
        extension = ext,
        mimeType = if (isDir) null else MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext),
        itemCount = if (isDir) (list()?.size ?: 0) else 0
    )
}
