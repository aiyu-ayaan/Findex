package com.aiyu.findex

import android.app.Application
import com.aiyu.findex.ui.shortcuts.publishAppShortcuts
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FindexApp : Application() {
    override fun onCreate() {
        super.onCreate()
        publishAppShortcuts()
    }
}
