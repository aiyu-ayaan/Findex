package com.aiyu.findex.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Answers a PackageInstaller session. PENDING_USER_ACTION hands back the system confirm screen,
 * which something has to start; failures (e.g. a different signing key) are reported to [UpdateRepository].
 */
@AndroidEntryPoint
class UpdateInstallReceiver : BroadcastReceiver() {
    @Inject lateinit var updates: UpdateRepository

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = (if (android.os.Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else intent.getParcelableExtra(Intent.EXTRA_INTENT)) ?: return
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { updates.fail(it.message ?: "Android would not show the install screen") }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> updates.dismiss()
            PackageInstaller.STATUS_FAILURE_CONFLICT -> updates.fail("This APK is signed with a different key from the installed build. Uninstall the installed copy first; that removes its data.")
            else -> updates.fail(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "The install failed")
        }
    }

    companion object {
        const val ACTION = "com.aiyu.findex.UPDATE_INSTALL_STATUS"
    }
}
