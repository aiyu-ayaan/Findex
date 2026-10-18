package com.aiyu.findex.core.vault

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.aiyu.findex.core.model.FailureReason
import com.aiyu.findex.core.model.FileOperationException
import com.aiyu.findex.core.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the vault DEK in memory while unlocked. Locks when the app has been in the background for
 * `vaultAutoLockMinutes`, unless a running operation holds the session open.
 */
@Singleton
class VaultSession @Inject constructor(
    @ApplicationContext context: Context,
    settings: SettingsRepository
) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var dek: ByteArray? = null
    private var holds = 0
    private var started = 0
    private var lockPending = false
    @Volatile private var autoLockMinutes = 1
    private val main = Handler(Looper.getMainLooper())
    private val autoLock = Runnable { synchronized(this) { if (holds > 0) lockPending = true else lockLocked() } }

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.settings.collect { autoLockMinutes = it.vaultAutoLockMinutes }
        }
        (context.applicationContext as Application).registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                started++
                main.removeCallbacks(autoLock)
            }

            override fun onActivityStopped(activity: Activity) {
                // The session may be created while an activity is already started, so never go below 0.
                started = (started - 1).coerceAtLeast(0)
                if (started == 0 && _unlocked.value && !activity.isChangingConfigurations) main.postDelayed(autoLock, autoLockMinutes * 60_000L)
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    @Synchronized
    fun open(key: ByteArray) {
        dek?.fill(0)
        dek = key
        lockPending = false
        _unlocked.value = true
    }

    @Synchronized
    fun lock() {
        main.removeCallbacks(autoLock)
        lockLocked()
    }

    private fun lockLocked() {
        dek?.fill(0)
        dek = null
        lockPending = false
        _unlocked.value = false
    }

    /** Runs [block] with the live DEK; never keep a reference to it. */
    @Synchronized
    fun <T> withKey(block: (ByteArray) -> T): T = block(dek ?: throw FileOperationException(FailureReason.VAULT_LOCKED))

    /** A copy of the DEK for one long operation; keeps auto-lock away until closed, then zero-fills the copy. */
    inner class Hold internal constructor(val key: ByteArray) : AutoCloseable {
        override fun close() {
            key.fill(0)
            synchronized(this@VaultSession) {
                holds--
                if (holds == 0 && lockPending) lockLocked()
            }
        }
    }

    /** Throws [FileOperationException] with [FailureReason.VAULT_LOCKED] when locked. */
    @Synchronized
    fun hold(): Hold {
        val key = dek ?: throw FileOperationException(FailureReason.VAULT_LOCKED)
        holds++
        return Hold(key.copyOf())
    }
}
