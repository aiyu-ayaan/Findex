package com.aiyu.findex.core.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.aiyu.findex.core.operation.OperationStrings
import com.aiyu.findex.core.settings.SettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface UpdateWorkerEntryPoint {
    fun settingsRepository(): SettingsRepository
    fun updateRepository(): UpdateRepository
    fun operationStrings(): OperationStrings
}

/**
 * Daily background check for new GitHub releases on the user's configured [UpdateChannel].
 * Posts a notification when a newer version is available and hasn't been notified yet.
 */
class UpdateCheckWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            UpdateWorkerEntryPoint::class.java
        )
        val settingsRepo = entryPoint.settingsRepository()
        val updateRepo = entryPoint.updateRepository()
        val strings = entryPoint.operationStrings()

        val settings = settingsRepo.settings.first()
        if (!settings.autoUpdate) return Result.success()

        val channel = settings.updateChannel ?: UpdateChannel.forVersion(updateRepo.installedName)
        updateRepo.check(channel, manual = false)

        val state = updateRepo.state.value
        if (state is UpdateState.Available) {
            val versionTag = state.release.name
            val lastNotified = settingsRepo.lastNotifiedUpdateVersion.first()
            if (lastNotified != versionTag && canPostNotification(applicationContext)) {
                postUpdateNotification(applicationContext, strings, versionTag)
                settingsRepo.setLastNotifiedUpdateVersion(versionTag)
            }
        }
        return Result.success()
    }

    private fun canPostNotification(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

    private fun postUpdateNotification(context: Context, strings: OperationStrings, versionName: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                strings.updateChannelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(strings.updateNotificationTitle(versionName))
            .setContentText(strings.updateNotificationBody)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(launchIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "findex_updates"
        const val NOTIFICATION_ID = 1003
    }
}
