package com.aiyu.findex.core.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: Release, val apk: ReleaseAsset) : UpdateState
    data class Downloading(val release: Release, val progress: Float) : UpdateState
    data class Ready(val release: Release, val file: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * The app updating itself from its own GitHub releases. Never installs silently: the last
 * screen is always Android's package installer.
 */
@Singleton
class UpdateRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private val downloads = File(context.cacheDir, "updates")
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    val installedName: String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    private val installed = Version.parse(installedName)

    /** [manual] reports "up to date"; the launch check stays silent about it. */
    suspend fun check(channel: UpdateChannel, manual: Boolean = false) = withContext(Dispatchers.IO) {
        if (_state.value is UpdateState.Downloading) return@withContext
        if (manual) _state.value = UpdateState.Checking
        _state.value = runCatching {
            val connection = (URL(Releases.API).openConnection() as HttpURLConnection).apply {
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            try {
                if (connection.responseCode !in 200..299) error("GitHub answered ${connection.responseCode}")
                val pick = Releases.pick(parse(connection.inputStream.bufferedReader().readText()), installed, channel)
                // A release whose Android job failed has no APK and is not an offer.
                val apk = pick?.let(Releases::apkFor)
                if (pick != null && apk != null) UpdateState.Available(pick, apk) else UpdateState.UpToDate
            } finally {
                connection.disconnect()
            }
        }.getOrElse { if (manual) UpdateState.Failed(it.message ?: "The update check failed") else UpdateState.Idle }
    }

    internal fun parse(body: String): List<Release> {
        val array = JSONArray(body)
        return (0 until array.length()).mapNotNull { i ->
            val json = array.getJSONObject(i)
            if (json.optBoolean("draft")) return@mapNotNull null
            val version = Version.parse(json.optString("tag_name")) ?: return@mapNotNull null
            val assets = json.optJSONArray("assets") ?: JSONArray()
            Release(
                version, json.optString("name").ifBlank { json.optString("tag_name") }, json.optString("body"),
                (0 until assets.length()).map {
                    val a = assets.getJSONObject(it)
                    ReleaseAsset(a.optString("name"), a.optString("browser_download_url"), a.optLong("size"))
                }
            )
        }
    }

    /** Streams the APK into the cache and reports progress through [state]. */
    suspend fun download(release: Release, asset: ReleaseAsset) = withContext(Dispatchers.IO) {
        runCatching {
            downloads.mkdirs()
            downloads.listFiles()?.forEach { if (it.name != asset.name) it.delete() }
            val target = File(downloads, asset.name)
            if (asset.size > 0 && target.length() == asset.size) return@runCatching target
            _state.value = UpdateState.Downloading(release, 0f)
            val connection = URL(asset.url).openConnection() as HttpURLConnection
            try {
                if (connection.responseCode !in 200..299) error("The download failed (${connection.responseCode})")
                val total = if (asset.size > 0) asset.size else connection.contentLengthLong
                var written = 0L
                connection.inputStream.use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            written += read
                            if (total > 0) _state.value = UpdateState.Downloading(release, (written.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                connection.disconnect()
            }
            target
        }.fold(
            onSuccess = { _state.value = UpdateState.Ready(release, it) },
            onFailure = { _state.value = UpdateState.Failed(it.message ?: "The download failed") }
        )
    }

    /** From API 26 "install unknown apps" is per-app and can only be granted in settings. */
    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun requestInstallPermission() {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** A session rather than ACTION_VIEW, so a failure such as a signing-key mismatch comes back as a reason. */
    suspend fun install(apk: File) = withContext(Dispatchers.IO) {
        runCatching {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("findex.apk", 0, apk.length()).use { sink ->
                    apk.inputStream().use { it.copyTo(sink) }
                    session.fsync(sink)
                }
                val status = PendingIntent.getBroadcast(
                    context, id,
                    Intent(context, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION).setPackage(context.packageName),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                session.commit(status.intentSender)
            }
        }.onFailure { fail(it.message ?: "The install failed") }
    }

    fun fail(message: String) { _state.value = UpdateState.Failed(message) }
    fun dismiss() { _state.value = UpdateState.Idle }
}
