package com.aiyu.findex.core.history

import android.os.StatFs
import com.aiyu.findex.core.db.StorageSnapshotDao
import com.aiyu.findex.core.db.StorageSnapshotEntity
import com.aiyu.findex.core.model.DriveType
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.repository.StorageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageHistoryRepository @Inject constructor(
    private val dao: StorageSnapshotDao,
    private val storage: StorageRepository
) {
    /** One row per mounted volume for today; a second run on the same day replaces it. */
    suspend fun takeSnapshot() = withContext(Dispatchers.IO) {
        val today = LocalDate.now().toEpochDay()
        val stats = runCatching { storage.getCategoryStats() }.getOrDefault(emptyMap())
        for (drive in storage.getStorageDrives().first().filter { it.type != DriveType.NETWORK }) {
            val fs = runCatching { StatFs(drive.path) }.getOrNull() ?: continue
            val total = fs.totalBytes
            val bytes = { c: FileCategory -> if (drive.isPrimary) stats[c]?.bytes ?: 0L else 0L }
            dao.put(
                StorageSnapshotEntity(
                    volumePath = drive.path, day = today, takenAt = System.currentTimeMillis(),
                    totalBytes = total, usedBytes = (total - fs.availableBytes).coerceAtLeast(0),
                    imageBytes = bytes(FileCategory.PHOTOS), videoBytes = bytes(FileCategory.VIDEOS),
                    audioBytes = bytes(FileCategory.AUDIO), documentBytes = bytes(FileCategory.DOCUMENTS),
                    archiveBytes = bytes(FileCategory.ARCHIVES), apkBytes = bytes(FileCategory.APKS)
                )
            )
        }
        dao.trim(today - 730)
    }

    suspend fun hasToday(): Boolean = dao.hasDay(LocalDate.now().toEpochDay())

    fun history(volume: String, days: Int): Flow<List<StorageSnapshotEntity>> =
        dao.observe(volume, LocalDate.now().toEpochDay() - days)
}
