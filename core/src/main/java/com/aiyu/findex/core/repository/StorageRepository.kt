package com.aiyu.findex.core.repository

import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.StorageDrive
import com.aiyu.findex.core.model.StorageItem
import kotlinx.coroutines.flow.Flow

interface StorageRepository {
    fun getStorageDrives(): Flow<List<StorageDrive>>
    suspend fun getFilesInDirectory(directoryPath: String): List<StorageItem>
    suspend fun getCategoryCount(category: FileCategory): Int
}
