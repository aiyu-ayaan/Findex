package com.aiyu.findex.core.history

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules or cancels the daily storage snapshot worker. */
@Singleton
class StorageHistoryScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun sync(enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).build()
        wm.enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StorageSnapshotWorker>(1, TimeUnit.DAYS).setConstraints(constraints).build()
        )
        // So the chart has a point today instead of waiting up to a day for the first periodic run.
        wm.enqueueUniqueWork(WORK_NAME + "_now", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<StorageSnapshotWorker>().build())
    }

    companion object { const val WORK_NAME = "findex_storage_snapshot" }
}
