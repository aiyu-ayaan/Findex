package com.aiyu.findex.core.repository

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import com.aiyu.findex.core.model.DriveType
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.StorageDrive
import com.aiyu.findex.core.model.StorageItem
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
    @ApplicationContext private val context: Context
) : StorageRepository {

    override fun getStorageDrives(): Flow<List<StorageDrive>> = flow {
        val drives = mutableListOf<StorageDrive>()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager

        if (storageManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val volumes: List<StorageVolume> = storageManager.storageVolumes
            for (volume in volumes) {
                val directory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    volume.directory
                } else {
                    null
                }
                val path = directory?.absolutePath ?: Environment.getExternalStorageDirectory().absolutePath
                val file = File(path)
                val totalSpace = if (file.exists()) file.totalSpace else 0L
                val freeSpace = if (file.exists()) file.freeSpace else 0L

                val isPrimary = volume.isPrimary
                val isRemovable = volume.isRemovable
                val driveType = when {
                    isPrimary -> DriveType.INTERNAL
                    isRemovable -> DriveType.SD_CARD
                    else -> DriveType.USB
                }

                val driveName = volume.getDescription(context).ifBlank {
                    if (isPrimary) "Internal Storage" else "External Storage"
                }

                drives.add(
                    StorageDrive(
                        id = volume.uuid ?: "primary",
                        name = driveName,
                        path = path,
                        totalBytes = totalSpace,
                        freeBytes = freeSpace,
                        type = driveType,
                        isPrimary = isPrimary,
                        isRemovable = isRemovable
                    )
                )
            }
        }

        // Fallback if no volumes obtained
        if (drives.isEmpty()) {
            val primaryFile = Environment.getExternalStorageDirectory()
            val total = primaryFile.totalSpace
            val free = primaryFile.freeSpace
            drives.add(
                StorageDrive(
                    id = "internal_primary",
                    name = "Internal Storage",
                    path = primaryFile.absolutePath,
                    totalBytes = total,
                    freeBytes = free,
                    type = DriveType.INTERNAL,
                    isPrimary = true,
                    isRemovable = false
                )
            )
        }

        emit(drives)
    }.flowOn(Dispatchers.IO)

    override suspend fun getFilesInDirectory(directoryPath: String): List<StorageItem> = withContext(Dispatchers.IO) {
        val dir = File(directoryPath)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()

        val files = dir.listFiles() ?: return@withContext emptyList()
        files.map { file ->
            val isDir = file.isDirectory
            StorageItem(
                path = file.absolutePath,
                name = file.name,
                size = if (isDir) 0L else file.length(),
                lastModified = file.lastModified(),
                isDirectory = isDir,
                extension = file.extension.lowercase(),
                itemCount = if (isDir) (file.list()?.size ?: 0) else 0
            )
        }.sortedWith(compareByDescending<StorageItem> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    override suspend fun getCategoryCount(category: FileCategory): Int = withContext(Dispatchers.IO) {
        // Placeholder for category file counting logic
        0
    }
}
