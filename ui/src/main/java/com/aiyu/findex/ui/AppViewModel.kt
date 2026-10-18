package com.aiyu.findex.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.model.AppSettings
import com.aiyu.findex.core.model.OperationResult
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.core.repository.TrashRepository
import com.aiyu.findex.ui.explorer.TabSession
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.aiyu.findex.core.update.UpdateChannel
import com.aiyu.findex.core.update.UpdateRepository
import com.aiyu.findex.core.update.UpdateState
import com.aiyu.findex.core.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    operationManager: OperationManager,
    private val trashRepository: TrashRepository,
    tabSession: TabSession,
    private val updates: UpdateRepository
) : ViewModel() {
    val tabCount: StateFlow<Int> = tabSession.state.map { it.tabs.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Null until DataStore has been read, so the first frame never flashes the wrong theme. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val operationResults: SharedFlow<OperationResult> = operationManager.results

    val updateState: StateFlow<UpdateState> = updates.state
    val installedVersion: String get() = updates.installedName

    init {
        viewModelScope.launch { trashRepository.purgeExpired() }
        // Once per process: the global update dialog appears when this finds a newer release.
        viewModelScope.launch {
            val s = settingsRepository.settings.first()
            if (s.autoUpdate) updates.check(s.updateChannel ?: UpdateChannel.forVersion(updates.installedName))
        }
    }

    fun canInstall() = updates.canInstall()

    fun installUpdate() {
        viewModelScope.launch {
            var state = updates.state.value
            if (state is UpdateState.Available) {
                updates.download(state.release, state.apk)
                state = updates.state.value
            }
            if (state is UpdateState.Ready) {
                if (updates.canInstall()) updates.install(state.file) else updates.requestInstallPermission()
            }
        }
    }

    fun dismissUpdate() = updates.dismiss()

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingDone() }
    }

    fun undoTrash(trashOperationId: String) {
        viewModelScope.launch { trashRepository.undo(trashOperationId) }
    }
}
