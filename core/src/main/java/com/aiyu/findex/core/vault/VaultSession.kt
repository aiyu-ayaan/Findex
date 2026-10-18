package com.aiyu.findex.core.vault

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import com.aiyu.findex.core.model.FailureReason
import com.aiyu.findex.core.model.FileOperationException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the vault DEK in memory while unlocked. Locks immediately on activity pause/stop, screen off,
 * or leaving the vault screen, except while an in-vault system picker or biometric enrollment is open.
 */
@Singleton
class VaultSession @Inject constructor(
    @ApplicationContext context: Context
) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var dek: ByteArray? = null
    @Volatile private var externalFlowActive = false

    init {
        val app = context.applicationContext as Application
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(activity: Activity) {
                lockIfNotInExternalFlow()
            }

            override fun onActivityStopped(activity: Activity) {
                lockIfNotInExternalFlow()
            }

            override fun onActivityResumed(activity: Activity) {
                externalFlowActive = false
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        runCatching {
            app.registerReceiver(
                object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) {
                        externalFlowActive = false
                        lock()
                    }
                },
                IntentFilter(Intent.ACTION_SCREEN_OFF)
            )
        }
    }

    /** Suppresses pause-triggered locking while a system picker or biometric enrollment launched from Vault is open. */
    fun beginExternalFlow() {
        externalFlowActive = true
    }

    fun endExternalFlow() {
        externalFlowActive = false
    }

    fun lockIfNotInExternalFlow() {
        if (!externalFlowActive) lock()
    }

    @Synchronized
    fun open(key: ByteArray) {
        dek?.fill(0)
        dek = key
        _unlocked.value = true
    }

    @Synchronized
    fun lock() {
        dek?.fill(0)
        dek = null
        _unlocked.value = false
    }

    /** Runs [block] with the live DEK; never keep a reference to it. */
    @Synchronized
    fun <T> withKey(block: (ByteArray) -> T): T = block(dek ?: throw FileOperationException(FailureReason.VAULT_LOCKED))

    /** A copy of the DEK for one long operation; zero-fills the copy when closed. */
    class Hold internal constructor(val key: ByteArray) : AutoCloseable {
        override fun close() {
            key.fill(0)
        }
    }

    /** Throws [FileOperationException] with [FailureReason.VAULT_LOCKED] when locked. */
    @Synchronized
    fun hold(): Hold {
        val key = dek ?: throw FileOperationException(FailureReason.VAULT_LOCKED)
        return Hold(key.copyOf())
    }
}
