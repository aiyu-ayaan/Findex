package com.aiyu.findex.ui.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.navigation.LaunchRequests

/** Screens opened from outside the app (launcher shortcuts, widget, notifications, tile); the name travels in [LaunchRequests.EXTRA_SCREEN]. */
enum class AppShortcut { SEARCH, DOWNLOADS, RECENT, CLEAN, SHARE }

/** Intent that opens [screen] in the app, for shortcuts, the widget and notifications. */
fun Context.screenIntent(screen: AppShortcut): Intent? = packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
    Intent(launch)
        .setAction(LaunchRequests.ACTION_OPEN_SCREEN)
        .putExtra(LaunchRequests.EXTRA_SCREEN, screen.name)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
}

private val launcherShortcuts = listOf(
    Triple(AppShortcut.SEARCH, R.string.nav_search, R.drawable.ic_shortcut_search),
    Triple(AppShortcut.DOWNLOADS, R.string.category_downloads, R.drawable.ic_shortcut_download),
    Triple(AppShortcut.RECENT, R.string.nav_recent_changes, R.drawable.ic_shortcut_recent),
    Triple(AppShortcut.CLEAN, R.string.cleaner_title, R.drawable.ic_shortcut_clean)
)

/**
 * Publishes the launcher shortcuts (long-press the app icon). Dynamic, not `shortcuts.xml`: a static intent
 * would hard-code the package name, which differs between debug and release.
 */
fun Context.publishAppShortcuts() {
    launcherShortcuts.forEachIndexed { rank, (screen, label, icon) ->
        val intent = screenIntent(screen) ?: return
        val info = ShortcutInfoCompat.Builder(this, "screen:${screen.name}")
            .setShortLabel(getString(label))
            .setIcon(IconCompat.createWithResource(this, icon))
            .setRank(rank)
            .setIntent(intent)
            .build()
        // Push (not set) so pinned-folder shortcuts kept as dynamic ones survive.
        runCatching { ShortcutManagerCompat.pushDynamicShortcut(this, info) }
    }
}
