package com.aiyu.findex

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aiyu.findex.core.vault.VaultSession
import com.aiyu.findex.ui.FindexApp
import com.aiyu.findex.ui.navigation.LaunchRequests
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var vaultSession: VaultSession

    // Per activity: another app's "Open with" starts a fresh instance in its own task, and a shared state
    // would let an older Findex instance consume the request instead.
    private val externalIntents = MutableStateFlow<Intent?>(null)

    // Read on the main thread only, by the splash screen's pre-draw check.
    private var contentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hold the system splash until real content is ready, instead of flashing an empty surface.
        installSplashScreen().apply {
            setKeepOnScreenCondition { !contentReady }
            setOnExitAnimationListener { provider ->
                // Leave as soon as content is ready; waiting out the 950ms logo animation held every start.
                val exit = AnimationUtils.loadInterpolator(this@MainActivity, R.interpolator.emphasized_accelerate)
                // The icon is its own surface on API 31+, so it does not inherit the parent's alpha.
                provider.iconView.animate()
                    .alpha(0f).scaleX(0.9f).scaleY(0.9f)
                    .setDuration(SPLASH_EXIT_MS)
                    .setInterpolator(exit)
                provider.view.animate()
                    .alpha(0f)
                    .setDuration(SPLASH_EXIT_MS)
                    .setInterpolator(exit)
                    .withEndAction { provider.remove() }
            }
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FindexApp(onReady = { contentReady = true }, externalIntents = externalIntents) }
        if (savedInstanceState == null) forwardFolder(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        vaultSession.lockIfNotInExternalFlow()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        super.onTopResumedActivityChanged(isTopResumedActivity)
        if (!isTopResumedActivity) vaultSession.lockIfNotInExternalFlow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        vaultSession.lockIfNotInExternalFlow()
        forwardFolder(intent)
    }

    /** Shortcuts, "Open with" and "Save to Findex" go to this activity's NavHost as is. */
    private fun forwardFolder(intent: Intent?) {
        val fromShortcut = intent?.hasExtra(LaunchRequests.EXTRA_FOLDER_PATH) == true || intent?.hasExtra(LaunchRequests.EXTRA_SCREEN) == true
        if (fromShortcut || intent?.action in EXTERNAL_ACTIONS) externalIntents.value = intent
    }

    private companion object {
        const val SPLASH_EXIT_MS = 200L // M3 emphasized accelerate exit duration
        val EXTERNAL_ACTIONS = setOf(Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)
    }
}
