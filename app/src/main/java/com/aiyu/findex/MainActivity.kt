package com.aiyu.findex

import android.os.Bundle
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
        installSplashScreen().setKeepOnScreenCondition { !contentReady }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { FindexApp(onReady = { contentReady = true }) }
    }
}
