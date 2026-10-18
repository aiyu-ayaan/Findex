package com.aiyu.findex.core.repository

import com.aiyu.findex.core.model.CategoryStat
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.StorageDrive
import com.aiyu.findex.core.model.StorageItem
import kotlinx.coroutines.flow.Flow

interface StorageRepository {
    fun getStorageDrives(): Flow<List<StorageDrive>>
    suspend fun getFilesInDirectory(directoryPath: String, showHidden: Boolean = false): List<StorageItem>
    suspend fun getItem(path: String): StorageItem?

    /** Files of a category from MediaStore, newest first. */
    suspend fun getCategoryFiles(category: FileCategory): List<StorageItem>
    suspend fun getCategoryStats(): Map<FileCategory, CategoryStat>

    /** Files and folders whose name contains [query], from the MediaStore file index; folders first. */
    suspend fun search(query: String, limit: Int = 300): List<StorageItem>

    /** @return the renamed item, or null when the name is taken or invalid. */
    suspend fun rename(path: String, newName: String): StorageItem?
    suspend fun createFolder(parentPath: String, name: String): StorageItem?

    /** Absolute path of the public Downloads folder. */
    val downloadsPath: String
}
