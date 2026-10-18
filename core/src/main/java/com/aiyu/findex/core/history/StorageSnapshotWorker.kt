package com.aiyu.findex.core.history

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SnapshotWorkerEntryPoint {
    fun history(): StorageHistoryRepository
}

class StorageSnapshotWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        EntryPointAccessors.fromApplication(applicationContext, SnapshotWorkerEntryPoint::class.java).history().takeSnapshot()
        return Result.success()
    }
}
