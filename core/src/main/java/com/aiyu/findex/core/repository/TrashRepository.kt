package com.aiyu.findex.core.repository

import com.aiyu.findex.core.db.TrashDao
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.TrashItem
import com.aiyu.findex.core.operation.OperationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Recycle bin entries in SQLite; the file moves themselves run through [OperationManager]. */
@Singleton
class TrashRepository @Inject constructor(
    private val trashDao: TrashDao,
    private val operationManager: OperationManager
) {
    companion object {
        val RETENTION_MS = TimeUnit.DAYS.toMillis(30)
    }

    val items: Flow<List<TrashItem>> = trashDao.observeAll().map { list ->
        list.map { TrashItem(it.id, it.name, it.originalPath, it.trashPath, it.size, it.isDirectory, it.deletedAt) }
    }

    fun restore(ids: List<Long>) {
        if (ids.isNotEmpty()) operationManager.enqueue(FileOperation.Restore(ids))
    }

    fun deleteForever(trashPaths: List<String>) {
        if (trashPaths.isNotEmpty()) operationManager.enqueue(FileOperation.Delete(trashPaths))
    }

    /** Restores everything one [FileOperation.Trash] moved. */
    suspend fun undo(trashOperationId: String) = restore(trashDao.idsInBatch(trashOperationId))

    /** Permanently deletes entries older than [RETENTION_MS]. */
    suspend fun purgeExpired(now: Long = System.currentTimeMillis()) =
        deleteForever(trashDao.olderThan(now - RETENTION_MS).map { it.trashPath })

    /** Drops rows whose file was removed outside the app. */
    suspend fun dropMissing() = withContext(Dispatchers.IO) {
        val missing = trashDao.all().filterNot { File(it.trashPath).exists() }.map { it.trashPath }
        if (missing.isNotEmpty()) trashDao.deleteByTrashPaths(missing)
    }
}
