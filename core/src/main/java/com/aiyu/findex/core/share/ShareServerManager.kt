package com.aiyu.findex.core.share

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ShareState {
    data object Stopped : ShareState
    data object Starting : ShareState
    data class Running(val url: String, val pin: String, val clients: Int = 0) : ShareState
    /** No Wi-Fi or hotspot address to serve on. */
    data object NoNetwork : ShareState
}

/**
 * "Share this phone": the UI starts and observes; [ShareServerService] owns the web server
 * and reports back here. Access rules and the idle timeout live in settings and apply live.
 */
@Singleton
class ShareServerManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _state = MutableStateFlow<ShareState>(ShareState.Stopped)
    val state: StateFlow<ShareState> = _state.asStateFlow()

    fun start() {
        _state.value = ShareState.Starting
        ContextCompat.startForegroundService(context, Intent(context, ShareServerService::class.java))
    }

    fun stop() {
        context.startService(Intent(context, ShareServerService::class.java).setAction(ShareServerService.ACTION_STOP))
    }

    internal fun publish(state: ShareState) { _state.value = state }
}
