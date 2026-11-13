package com.aiyu.findex

import android.app.Application
import android.content.Context
import com.aiyu.findex.core.settings.AppLocale
import com.aiyu.findex.ui.shortcuts.publishAppShortcuts
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FindexApp : Application() {
    override fun attachBaseContext(base: Context) = super.attachBaseContext(AppLocale.wrap(base))

    override fun onCreate() {
        super.onCreate()
        publishAppShortcuts()
    }
}
