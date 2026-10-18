package com.aiyu.findex.core.share

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
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
import com.aiyu.findex.core.model.AppSettings
import com.aiyu.findex.core.operation.FileOperationExecutor
import com.aiyu.findex.core.operation.OperationStrings
import com.aiyu.findex.core.repository.StorageRepository
import com.aiyu.findex.core.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.SecureRandom
import javax.inject.Inject

/**
 * Runs [ShareHttpServer] in the foreground with a "Sharing at … · PIN …" notification.
 * Stops itself after the configured idle minutes, or when the Wi-Fi/hotspot address it serves on goes away.
 */
@AndroidEntryPoint
class ShareServerService : Service() {

    companion object {
        const val CHANNEL_ID = "findex_share"
        const val NOTIFICATION_ID = 1003
        const val ACTION_STOP = "com.aiyu.findex.action.STOP_SHARE"
        private const val SOCKET_TIMEOUT_MS = 30_000
        private const val WATCHDOG_MS = 10_000L
        private val PORTS = 8080..8090
    }

    @Inject lateinit var manager: ShareServerManager
    @Inject lateinit var settingsRepo: SettingsRepository
    @Inject lateinit var storage: StorageRepository
    @Inject lateinit var executor: FileOperationExecutor
    @Inject lateinit var strings: OperationStrings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var server: ShareHttpServer? = null
    private var startJob: Job? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, strings.shareChannelName, NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        // Must call startForeground promptly after startForegroundService.
        startInForeground(buildNotification(null))
        if (server == null && startJob == null) startJob = scope.launch { launchServer() }
        return START_NOT_STICKY
    }

    private suspend fun launchServer() {
        val host = lanAddress() ?: run {
            manager.publish(ShareState.NoNetwork)
            stopForegroundAndSelf()
            return
        }
        // Wait for the saved access rules, so no request is served with the defaults.
        val settings = settingsRepo.settings.stateIn(scope, SharingStarted.Eagerly, settingsRepo.settings.first())
        val drives = storage.getStorageDrives().first()
        val pin = "%06d".format(SecureRandom().nextInt(1_000_000))
        val started = withContext(Dispatchers.IO) {
            PORTS.firstNotNullOfOrNull { port ->
                val s = ShareHttpServer(host, port, applicationContext, pin, settings, drives, storage, executor)
                try { s.start(SOCKET_TIMEOUT_MS, true); s } catch (e: IOException) { null }
            }
        }
        if (started == null) {
            manager.publish(ShareState.NoNetwork)
            stopForegroundAndSelf()
            return
        }
        server = started
        acquireLocks()
        val running = ShareState.Running("http://$host:${started.listeningPort}", pin)
        manager.publish(running)
        notificationManager.notify(NOTIFICATION_ID, buildNotification(running))
        watchdog(host, settings, running)
    }

    private suspend fun watchdog(host: String, settings: kotlinx.coroutines.flow.StateFlow<AppSettings>, running: ShareState.Running) {
        var shown = running
        while (scope.isActive) {
            delay(WATCHDOG_MS)
            val s = server ?: return
            val idleMinutes = settings.value.shareIdleMinutes
            val idle = idleMinutes > 0 && s.idleMillis() > idleMinutes * 60_000L
            if (idle || withContext(Dispatchers.IO) { lanAddress() } != host) return shutdown()
            if (s.clientCount != shown.clients) {
                shown = shown.copy(clients = s.clientCount)
                manager.publish(shown)
            }
        }
    }

    private fun shutdown() {
        startJob?.cancel()
        startJob = null
        server?.stop()
        server = null
        releaseLocks()
        manager.publish(ShareState.Stopped)
        stopForegroundAndSelf()
    }

    private fun stopForegroundAndSelf() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Site-local IPv4 of Wi-Fi, hotspot or Ethernet. Binding to it keeps the server off mobile data and IPv6. */
    private fun lanAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && !it.name.startsWith("rmnet") && !it.name.startsWith("ccmni") }
            .sortedBy { if (it.name.startsWith("wlan") || it.name.startsWith("ap") || it.name.startsWith("swlan")) 0 else 1 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
            ?.hostAddress
    }.getOrNull()

    /** Transfers keep going with the screen off. Held only while serving; the idle timeout bounds it. */
    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "findex:share").apply { acquire() }
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "findex:share").apply { acquire() }
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

    private fun buildNotification(running: ShareState.Running?): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, ShareServerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 2, it, PendingIntent.FLAG_IMMUTABLE)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_file_operation)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentTitle(strings.shareTitle)
            .setContentText(running?.let { strings.shareText(it.url, it.pin) })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(launch)
            .addAction(0, strings.shareStop, stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    /** Android 15 caps dataSync services at 6 h a day. */
    override fun onTimeout(startId: Int, fgsType: Int) = shutdown()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        server?.stop()
        releaseLocks()
        if (manager.state.value !is ShareState.NoNetwork) manager.publish(ShareState.Stopped)
        scope.cancel()
        super.onDestroy()
    }
}
