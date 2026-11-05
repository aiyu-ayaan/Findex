package com.aiyu.findex.ui.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.aiyu.findex.core.scanner.StorageScanner
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.shortcuts.AppShortcut
import com.aiyu.findex.ui.shortcuts.screenIntent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Settings "Cleanup reminder" (opt-in): a weekly junk scan that only speaks up when there is enough to free. */
@Singleton
class CleanupReminderScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun sync(enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK_NAME)
            return
        }
        // A full storage walk: only when the battery is fine and the phone is idle.
        val constraints = Constraints.Builder().setRequiresBatteryNotLow(true).setRequiresDeviceIdle(true).build()
        wm.enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<CleanupReminderWorker>(7, TimeUnit.DAYS).setConstraints(constraints).build()
        )
    }

    companion object { const val WORK_NAME = "findex_cleanup_reminder" }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface CleanupReminderEntryPoint {
    fun scanner(): StorageScanner
}

class CleanupReminderWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val scanner = EntryPointAccessors.fromApplication(applicationContext, CleanupReminderEntryPoint::class.java).scanner()
        val bytes = runCatching { scanner.scanJunk().sumOf { it.bytes } }.getOrElse { return Result.success() }
        if (bytes >= MIN_BYTES) notify(applicationContext, bytes)
        return Result.success()
    }

    companion object {
        /** Below this a reminder is noise. */
        const val MIN_BYTES = 200L * 1024 * 1024
        private const val CHANNEL_ID = "cleanup_reminder"
        private const val NOTIFICATION_ID = 4107

        fun notify(context: Context, bytes: Long) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, context.getString(R.string.cleanup_reminder_channel), NotificationManager.IMPORTANCE_LOW)
                )
            }
            val open = context.screenIntent(AppShortcut.CLEAN)?.let {
                PendingIntent.getActivity(context, NOTIFICATION_ID, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            val size = Formatter.formatShortFileSize(context, bytes)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(com.aiyu.findex.core.R.drawable.ic_stat_file_operation)
                .setContentTitle(context.getString(R.string.cleanup_reminder_title, size))
                .setContentText(context.getString(R.string.cleanup_reminder_text))
                .setContentIntent(open)
                .addAction(0, context.getString(R.string.cleanup_reminder_action), open)
                .setAutoCancel(true)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }
    }
}
