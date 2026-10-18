package com.aiyu.findex.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaScannerConnection
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.aiyu.findex.core.model.ActiveOperation
import com.aiyu.findex.core.model.FailureReason
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.FileOperationException
import com.aiyu.findex.core.model.OperationProgress
import com.aiyu.findex.core.model.OperationResult
import com.aiyu.findex.core.operation.FileOperationExecutor
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.core.operation.OperationStrings
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Drains [OperationManager]'s queue one operation at a time, showing a progress notification.
 * All control flow runs on the main thread; only the I/O itself runs on [Dispatchers.IO].
 */
@AndroidEntryPoint
class FileOperationForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "findex_file_ops"
        const val NOTIFICATION_ID = 1001
        const val RESULT_NOTIFICATION_ID = 1002
        const val ACTION_START = "com.aiyu.findex.action.START_OPERATION"
        const val ACTION_CANCEL = "com.aiyu.findex.action.CANCEL_OPERATION"
    }

    @Inject lateinit var manager: OperationManager
    @Inject lateinit var executor: FileOperationExecutor
    @Inject lateinit var strings: OperationStrings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var worker: Job? = null
    private var currentJob: Job? = null
    private var lastStartId = 0
    private var lastNotifyAt = 0L

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        if (intent?.action == ACTION_CANCEL) {
            currentJob?.cancel()
            if (worker == null) stopSelf(startId)
            return START_NOT_STICKY
        }

        // Must call startForeground promptly after startForegroundService.
        startInForeground(buildProgressNotification(null))
        if (worker == null) worker = scope.launch { drainQueue() }
        return START_NOT_STICKY
    }

    private suspend fun drainQueue() {
        while (true) {
            val operation = manager.poll() ?: break
            runOperation(operation)
        }
        worker = null
        manager.publish(null as ActiveOperation?)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    private suspend fun runOperation(operation: FileOperation) {
        val initial = ActiveOperation(operation, OperationProgress(operation.id, isIndeterminate = true))
        manager.publish(initial)
        notificationManager.notify(NOTIFICATION_ID, buildProgressNotification(initial))

        var failure: FailureReason? = null
        var lastProgress = initial.progress
        val job = scope.launch {
            try {
                val touched = withContext(Dispatchers.IO) {
                    executor.execute(operation) { progress ->
                        lastProgress = progress
                        val active = ActiveOperation(operation, progress)
                        manager.publish(active)
                        throttledNotify(active)
                    }
                }
                // Scanning a path that no longer exists removes it from MediaStore.
                if (touched.isNotEmpty()) {
                    MediaScannerConnection.scanFile(applicationContext, touched.toTypedArray(), null, null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: FileOperationException) {
                failure = e.reason
            } catch (e: Exception) {
                failure = FailureReason.UNKNOWN
            }
        }
        currentJob = job
        job.join()
        currentJob = null
        val skipped = if (operation is FileOperation.ImageTransform && lastProgress.totalItems > lastProgress.processedItems) {
            lastProgress.totalItems - lastProgress.processedItems
        } else 0
        val result = when {
            failure != null -> OperationResult.Failed(operation, failure!!)
            job.isCancelled -> OperationResult.Cancelled(operation)
            else -> OperationResult.Success(operation, skipped = skipped)
        }
        manager.publish(result)
        showResultNotification(result)
    }

    private fun throttledNotify(active: ActiveOperation) {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < 500 && !active.progress.isComplete) return
        lastNotifyAt = now
        notificationManager.notify(NOTIFICATION_ID, buildProgressNotification(active))
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun buildProgressNotification(active: ActiveOperation?): Notification {
        val cancelIntent = PendingIntent.getService(
            this, 0,
            Intent(this, FileOperationForegroundService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val percent = active?.progress?.let { (it.progressFraction * 100).toInt() } ?: 0
        val indeterminate = active == null || active.progress.isIndeterminate
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(active?.let { strings.title(it.operation) } ?: strings.channelName)
            .setContentText(active?.let { strings.progress(it.progress.currentItemName, percent, manager.pendingCount()) })
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(launchIntent())
            .addAction(0, strings.cancel, cancelIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun showResultNotification(result: OperationResult) {
        if (result is OperationResult.Cancelled) return
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (result is OperationResult.Success) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(strings.title(result.operation))
            .setContentText(strings.result(result))
            .setAutoCancel(true)
            .setContentIntent(launchIntent())
            .build()
        notificationManager.notify(RESULT_NOTIFICATION_ID, notification)
    }

    private fun launchIntent(): PendingIntent? =
        packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 1, it, PendingIntent.FLAG_IMMUTABLE)
        }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, strings.channelName, NotificationManager.IMPORTANCE_LOW).apply {
                description = strings.channelDescription
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
