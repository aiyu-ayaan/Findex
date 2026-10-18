package com.aiyu.findex.core.repository

import android.content.Context
import android.provider.MediaStore
import com.aiyu.findex.core.model.StorageItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Files changed lately, from the MediaStore file index (so hidden and .nomedia folders are not covered). */
@Singleton
class RecentChangesRepository @Inject constructor(@ApplicationContext private val context: Context) {

    suspend fun changes(sinceDays: Int = 7, limit: Int = 500): List<StorageItem> = withContext(Dispatchers.IO) {
        val since = System.currentTimeMillis() / 1000 - sinceDays * 86_400L
        val out = ArrayList<StorageItem>()
        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.DATE_ADDED),
            // Folders have no MIME type in the Files table.
            "(${MediaStore.MediaColumns.DATE_MODIFIED} >= ? OR ${MediaStore.MediaColumns.DATE_ADDED} >= ?) AND ${MediaStore.MediaColumns.MIME_TYPE} IS NOT NULL",
            arrayOf(since.toString(), since.toString()),
            "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
        )?.use { c ->
            val data = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            while (c.moveToNext() && out.size < limit) {
                val path = c.getString(data) ?: continue
                if ("/${com.aiyu.findex.core.vault.VaultKeyManager.DIR_NAME}/" in "$path/" || "/.FindexTrash/" in "$path/") continue
                val file = File(path)
                if (!file.isFile) continue
                out += StorageItem(
                    path = file.absolutePath, name = file.name, size = file.length(),
                    lastModified = file.lastModified(), extension = file.extension.lowercase()
                )
            }
        }
        out.sortedByDescending { it.lastModified }
    }
}
