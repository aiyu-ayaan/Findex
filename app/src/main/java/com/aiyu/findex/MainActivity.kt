package com.aiyu.findex

import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.aiyu.findex.ui.FindexApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    // Read on the main thread only, by the splash screen's pre-draw check.
    private var contentReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Hold the system splash until real content is ready, instead of flashing an empty surface.
        installSplashScreen().apply {
            setKeepOnScreenCondition { !contentReady }
            setOnExitAnimationListener { provider ->
                // Let the logo animation finish before leaving, so a fast start never cuts it mid-draw.
                val remaining = provider.iconAnimationStartMillis + provider.iconAnimationDurationMillis -
                    System.currentTimeMillis()
                val exit = AnimationUtils.loadInterpolator(this@MainActivity, R.interpolator.emphasized_accelerate)
                // The icon is its own surface on API 31+, so it does not inherit the parent's alpha.
                provider.iconView.animate()
                    .alpha(0f).scaleX(0.9f).scaleY(0.9f)
                    .setStartDelay(remaining.coerceAtLeast(0))
                    .setDuration(SPLASH_EXIT_MS)
                    .setInterpolator(exit)
                provider.view.animate()
                    .alpha(0f)
                    .setStartDelay(remaining.coerceAtLeast(0))
                    .setDuration(SPLASH_EXIT_MS)
                    .setInterpolator(exit)
                    .withEndAction { provider.remove() }
            }
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FindexApp(onReady = { contentReady = true }) }
    }

    private companion object {
        const val SPLASH_EXIT_MS = 200L // M3 emphasized accelerate exit duration
    }
}
