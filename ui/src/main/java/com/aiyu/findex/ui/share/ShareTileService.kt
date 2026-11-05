package com.aiyu.findex.ui.share

import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.aiyu.findex.core.share.ShareServerManager
import com.aiyu.findex.core.share.ShareState
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.shortcuts.AppShortcut
import com.aiyu.findex.ui.shortcuts.screenIntent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Quick Settings tile for "Share this phone": tap starts or stops the LAN server; while it runs the
 * tile shows the address and PIN. With no Wi-Fi it opens the Share screen, which explains why.
 */
@AndroidEntryPoint
class ShareTileService : TileService() {
    @Inject lateinit var manager: ShareServerManager

    private val scope = CoroutineScope(Dispatchers.Main.immediate)
    private var watch: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        watch = scope.launch { manager.state.collect(::render) }
    }

    override fun onStopListening() {
        watch?.cancel()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        when (manager.state.value) {
            is ShareState.Running, ShareState.Starting -> manager.stop()
            ShareState.NoNetwork -> openShareScreen()
            ShareState.Stopped -> unlockAndRun { manager.start() } // files are exposed: never start from the lock screen
        }
    }

    private fun openShareScreen() {
        val intent = screenIntent(AppShortcut.SHARE) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(state: ShareState) {
        val tile = qsTile ?: return
        tile.label = getString(R.string.nav_share_phone)
        tile.state = when (state) {
            is ShareState.Running, ShareState.Starting -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (state) {
                is ShareState.Running -> getString(R.string.share_tile_running, state.url.removePrefix("http://"), state.pin)
                ShareState.Starting -> getString(R.string.share_tile_starting)
                ShareState.NoNetwork -> getString(R.string.share_tile_no_network)
                ShareState.Stopped -> getString(R.string.share_tile_off)
            }
        }
        tile.updateTile()
    }
}
