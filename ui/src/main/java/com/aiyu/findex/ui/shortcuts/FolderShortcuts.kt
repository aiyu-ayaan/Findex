package com.aiyu.findex.ui.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.navigation.LaunchRequests
import java.security.MessageDigest

/** Pinned home-screen shortcuts to folders. */
fun Context.canPinShortcuts(): Boolean = ShortcutManagerCompat.isRequestPinShortcutSupported(this)

/** @return true when the launcher accepted the pin request. Also keeps it as a dynamic shortcut (long-press app icon). */
fun Context.pinFolderShortcut(path: String, label: String): Boolean {
    val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return false
    val intent = Intent(launch)
        .setAction(LaunchRequests.ACTION_OPEN_FOLDER)
        .putExtra(LaunchRequests.EXTRA_FOLDER_PATH, path)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val id = "folder:" + MessageDigest.getInstance("SHA-1").digest(path.toByteArray()).joinToString("") { "%02x".format(it) }
    val info = ShortcutInfoCompat.Builder(this, id)
        .setShortLabel(label.take(16).ifEmpty { path })
        .setLongLabel(label)
        .setIcon(IconCompat.createWithResource(this, R.drawable.ic_shortcut_folder))
        .setIntent(intent)
        .build()
    // Dynamic shortcuts are capped (usually 4 to 5); the pinned copy is what matters, so ignore a failure here.
    runCatching { ShortcutManagerCompat.pushDynamicShortcut(this, info) }
    return ShortcutManagerCompat.requestPinShortcut(this, info, null)
}
