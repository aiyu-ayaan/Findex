package com.aiyu.findex.ui.vault

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.core.settings.SettingsRepository
import com.aiyu.findex.core.vault.BiometricInvalidatedException
import com.aiyu.findex.core.vault.LockedOutException
import com.aiyu.findex.core.vault.VaultEntry
import com.aiyu.findex.core.vault.VaultKeyManager
import com.aiyu.findex.core.vault.VaultRepository
import com.aiyu.findex.core.vault.VaultSession
import com.aiyu.findex.core.vault.VaultUnreadableException
import com.aiyu.findex.core.vault.WrongPasswordException
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.navigation.SelectionHandoff
import com.aiyu.findex.ui.navigation.VaultRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.crypto.Cipher
import javax.inject.Inject

sealed interface VaultPhase {
    data object Loading : VaultPhase
    data object Setup : VaultPhase
    /** Vault data exists but `vault.key` is missing or unreadable; setup would destroy it. */
    data object Unreadable : VaultPhase
    data class Locked(val lockoutUntil: Long, val biometric: Boolean) : VaultPhase
    data object Unlocked : VaultPhase
}

data class VaultUiState(
    val phase: VaultPhase = VaultPhase.Loading,
    val busy: Boolean = false,
    /** String resource to show once (error or confirmation). */
    val message: Int? = null,
    val biometricEnabled: Boolean = false,
    /** Blob count on disk when the index is empty (reinstall), else 0. */
    val restorable: Int = 0,
    /** Paths handed over by the Explorer, waiting for confirmation. */
    val pendingImport: List<String>? = null,
    /** Setup asked for biometrics; the unlocked screen runs the enrol prompt once. */
    val enrollBiometric: Boolean = false
)

@HiltViewModel
class VaultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val keys: VaultKeyManager,
    private val session: VaultSession,
    private val repository: VaultRepository,
    private val operations: OperationManager,
    private val settings: SettingsRepository
) : ViewModel() {
    val route: VaultRoute = savedStateHandle.toRoute<VaultRoute>()

    private val _state = MutableStateFlow(VaultUiState(pendingImport = route.importToken?.let(SelectionHandoff::take)))
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    val items: StateFlow<List<VaultEntry>> = repository.items().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val autoLockMinutes: StateFlow<Int> = settings.settings.map { it.vaultAutoLockMinutes }.stateIn(viewModelScope, SharingStarted.Eagerly, 1)
    val biometricSupported: Boolean get() = keys.biometricSupported

    init {
        viewModelScope.launch {
            session.unlocked.collect { unlocked ->
                if (!unlocked) lockStateImmediately()
                refresh()
            }
        }
    }

    private fun lockStateImmediately() {
        _state.update { cur ->
            if (cur.phase == VaultPhase.Unlocked) {
                cur.copy(phase = VaultPhase.Locked(0L, cur.biometricEnabled && keys.biometricSupported))
            } else cur
        }
    }

    private suspend fun refresh() {
        val meta = try {
            keys.meta()
        } catch (e: VaultUnreadableException) {
            _state.update { it.copy(phase = VaultPhase.Unreadable) }
            return
        }
        val phase = when {
            meta == null -> VaultPhase.Setup
            session.unlocked.value -> VaultPhase.Unlocked
            else -> VaultPhase.Locked(meta.lockoutUntil, meta.biometricWrappedKey != null && keys.biometricSupported)
        }
        val restorable = if (phase == VaultPhase.Unlocked && repository.items().first().isEmpty()) repository.blobCount() else 0
        _state.update { it.copy(phase = phase, biometricEnabled = meta?.biometricWrappedKey != null, restorable = restorable) }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    private fun say(res: Int) = _state.update { it.copy(message = res) }

    private fun work(block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        try { block() } finally { _state.update { it.copy(busy = false) } }
    }

    fun create(password: CharArray, biometric: Boolean) = work {
        try {
            session.open(keys.create(password))
        } catch (e: VaultUnreadableException) {
            refresh()
            return@work
        } finally {
            password.fill('\u0000')
        }
        _state.update { it.copy(enrollBiometric = biometric) }
        refresh()
    }

    fun enrollHandled() = _state.update { it.copy(enrollBiometric = false) }

    fun unlock(password: CharArray) = work {
        try {
            session.open(keys.unlockWithPassword(password))
        } catch (e: WrongPasswordException) {
            say(R.string.vault_unlock_wrong_password)
        } catch (e: LockedOutException) {
            say(R.string.vault_unlock_wrong_password)
        } finally {
            password.fill('\u0000')
        }
        refresh()
    }

    /** Cipher for the unlock prompt, or null (biometric off or invalidated; a message is shown for the latter). */
    suspend fun biometricUnlockCipher(): Cipher? = try {
        keys.biometricUnlockCipher()
    } catch (e: BiometricInvalidatedException) {
        say(R.string.vault_biometric_invalidated); refresh(); null
    } catch (e: Exception) {
        say(R.string.vault_biometric_error); null
    }

    fun onBiometricAuthenticated(cipher: Cipher) = work {
        runCatching { session.open(keys.unlockWithBiometric(cipher)) }.onFailure { say(R.string.vault_biometric_error) }
        refresh()
    }

    fun biometricEnrollCipher(): Cipher? = runCatching { keys.biometricEnrollCipher() }.getOrNull().also { if (it == null) say(R.string.vault_biometric_error) }

    fun onBiometricEnrolled(cipher: Cipher) = work {
        runCatching { session.hold().use { keys.enableBiometric(cipher, it.key) } }.onFailure { say(R.string.vault_biometric_error) }
        refresh()
    }

    fun disableBiometric() = work { keys.disableBiometric(); refresh() }

    fun changePassword(old: CharArray, new: CharArray) = work {
        try {
            keys.changePassword(old, new)
            say(R.string.vault_password_changed)
        } catch (e: WrongPasswordException) {
            say(R.string.vault_unlock_wrong_password)
        } catch (e: LockedOutException) {
            say(R.string.vault_unlock_wrong_password)
        } finally {
            old.fill('\u0000'); new.fill('\u0000')
        }
        refresh()
    }

    fun setAutoLock(minutes: Int) = viewModelScope.launch { settings.setVaultAutoLockMinutes(minutes) }

    fun beginExternalFlow() = session.beginExternalFlow()

    fun endExternalFlow() = session.endExternalFlow()

    fun onScreenPaused() {
        session.lockIfNotInExternalFlow()
        if (!session.unlocked.value) lockStateImmediately()
    }

    fun lock() {
        session.lock()
        lockStateImmediately()
    }

    override fun onCleared() {
        super.onCleared()
        session.endExternalFlow()
        session.lock()
    }

    fun rebuildIndex() = work { repository.rebuildIndex(); refresh() }

    fun requestImport(paths: List<String>) {
        if (paths.isNotEmpty()) _state.update { it.copy(pendingImport = paths) }
    }

    fun confirmImport() {
        val paths = _state.value.pendingImport ?: return
        operations.enqueue(FileOperation.VaultImport(paths))
        dismissImport()
    }

    fun dismissImport() = _state.update { it.copy(pendingImport = null) }

    /** Decrypts back to each item's original folder and removes it from the vault. */
    fun restoreToOriginal(ids: Set<Long>) {
        items.value.filter { it.id in ids }.groupBy { File(it.originalPath).parent ?: "/" }.forEach { (dir, group) ->
            operations.enqueue(FileOperation.VaultExport(group.map { it.id }, dir, removeFromVault = true))
        }
    }

    /** Saves decrypted copies into [dir]; the items stay in the vault. */
    fun exportTo(ids: Set<Long>, dir: String) = operations.enqueue(FileOperation.VaultExport(ids.toList(), dir, removeFromVault = false))

    fun delete(ids: Set<Long>) = operations.enqueue(FileOperation.VaultDelete(ids.toList()))
}
