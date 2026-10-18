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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var vaultSession: VaultSession

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
        setContent { FindexApp(onReady = { contentReady = true }) }
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

    /** Pinned folder shortcuts carry the folder path; the NavHost opens it. */
    private fun forwardFolder(intent: Intent?) {
        intent?.getStringExtra(LaunchRequests.EXTRA_FOLDER_PATH)?.let { LaunchRequests.folder.value = it }
    }

    private companion object {
        const val SPLASH_EXIT_MS = 200L // M3 emphasized accelerate exit duration
    }
}
