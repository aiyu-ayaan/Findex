package com.aiyu.findex.core.link

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.aiyu.findex.core.R
import com.aiyu.findex.core.operation.FileOperationExecutor
import com.aiyu.findex.core.operation.OperationStrings
import com.aiyu.findex.core.repository.StorageRepository
import com.aiyu.findex.core.settings.AppLocale
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject

/**
 * Keeps this phone reachable for paired computers: runs [LinkHttpServer] in the foreground, advertises it
 * over NSD, and stops after [IDLE_MS] without requests or when the Wi-Fi/hotspot address goes away.
 */
@AndroidEntryPoint
class LinkServerService : Service() {

    companion object {
        const val CHANNEL_ID = "findex_link"
        const val NOTIFICATION_ID = 1004
        private const val EVENT_NOTIFICATION_ID = 1005
        const val ACTION_STOP = "com.aiyu.findex.action.STOP_LINK"
        val PORTS = 47100..47110
        private const val SOCKET_TIMEOUT_MS = 60_000
        private const val WATCHDOG_MS = 10_000L
        // shortcut: fixed idle limit; move to settings if people want it longer.
        private const val IDLE_MS = 30 * 60_000L
    }

    @Inject lateinit var repo: LinkRepository
    @Inject lateinit var storage: StorageRepository
    @Inject lateinit var executor: FileOperationExecutor
    @Inject lateinit var strings: OperationStrings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var server: LinkHttpServer? = null
    private var startJob: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLocale.wrap(base))

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(NotificationChannel(CHANNEL_ID, strings.linkChannelName, NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        startInForeground(buildNotification(null))
        if (server == null && startJob == null) startJob = scope.launch { launchServer() }
        return START_NOT_STICKY
    }

    private suspend fun launchServer() {
        val host = lanAddress() ?: run {
            repo.publish(LinkServerState.NoNetwork)
            stopForegroundAndSelf()
            return
        }
        val drives = storage.getStorageDrives().first()
        val started = withContext(Dispatchers.IO) {
            val sockets = repo.identity.serverSocketFactory()
            PORTS.firstNotNullOfOrNull { port ->
                val s = LinkHttpServer(host, port, applicationContext, repo, drives, executor, ::onReceived, ::onText)
                s.makeSecure(sockets, null)
                try { s.start(SOCKET_TIMEOUT_MS, true); s } catch (e: IOException) { null }
            }
        }
        if (started == null) {
            repo.publish(LinkServerState.NoNetwork)
            stopForegroundAndSelf()
            return
        }
        server = started
        acquireLocks()
        val address = "$host:${started.listeningPort}"
        repo.advertise(started.listeningPort)
        repo.publish(LinkServerState.Running(address))
        notificationManager.notify(NOTIFICATION_ID, buildNotification(address))
        while (scope.isActive) {
            delay(WATCHDOG_MS)
            val s = server ?: return
            if (s.idleMillis() > IDLE_MS || withContext(Dispatchers.IO) { lanAddress() } != host) return shutdown()
        }
    }

    private fun onReceived(from: String, file: java.io.File) {
        repo.emit(LinkEvent.Received(from, file.name, file.path))
        notifyEvent(strings.linkReceived(from, file.name))
    }

    private fun onText(from: String, text: String) {
        // Writing the clipboard is allowed from a foreground service; reading it is not needed.
        scope.launch {
            runCatching { getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(from, text)) }
        }
        repo.emit(LinkEvent.Text(from, text))
        notifyEvent(strings.linkTextReceived(from), text)
    }

    private fun notifyEvent(title: String, text: String? = null) {
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.let { PendingIntent.getActivity(this, 3, it, PendingIntent.FLAG_IMMUTABLE) }
        notificationManager.notify(
            EVENT_NOTIFICATION_ID,
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_file_operation)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(launch)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun shutdown() {
        startJob?.cancel()
        startJob = null
        server?.stop()
        server = null
        repo.stopAdvertising()
        releaseLocks()
        repo.publish(LinkServerState.Stopped)
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Site-local IPv4 of Wi-Fi, hotspot or Ethernet (never mobile data). */
    private fun lanAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.name.startsWith("rmnet") && !it.name.startsWith("ccmni") }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("swlan")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "findex:link").apply { acquire() }
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "findex:link").apply { acquire(IDLE_MS + WATCHDOG_MS) }
    }

    private fun releaseLocks() {
        wifiLock?.takeIf { it.isHeld }?.release()
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock = null
        wakeLock = null
    }

    private fun startInForeground(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(address: String?): Notification {
        val stop = PendingIntent.getService(this, 0, Intent(this, LinkServerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.let { PendingIntent.getActivity(this, 2, it, PendingIntent.FLAG_IMMUTABLE) }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_file_operation)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentTitle(strings.linkTitle)
            .setContentText(address?.let(strings::linkText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(launch)
            .addAction(0, strings.linkStop, stop)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Android 15 caps dataSync services at 6 h a day. */
    override fun onTimeout(startId: Int, fgsType: Int) = shutdown()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        server?.stop()
        repo.stopAdvertising()
        releaseLocks()
        if (repo.server.value !is LinkServerState.NoNetwork) repo.publish(LinkServerState.Stopped)
        scope.cancel()
        super.onDestroy()
    }
}
