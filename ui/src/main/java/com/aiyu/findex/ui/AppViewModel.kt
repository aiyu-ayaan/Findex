package com.aiyu.findex.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.model.AppSettings
import com.aiyu.findex.core.model.OperationResult
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.core.repository.TrashRepository
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
    private val trashRepository: TrashRepository
) : ViewModel() {
    /** Null until DataStore has been read, so the first frame never flashes the wrong theme. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val operationResults: SharedFlow<OperationResult> = operationManager.results

    init {
        viewModelScope.launch { trashRepository.purgeExpired() }
    }

    fun completeOnboarding() {
        viewModelScope.launch { settingsRepository.setOnboardingDone() }
    }

    fun undoTrash(trashOperationId: String) {
        viewModelScope.launch { trashRepository.undo(trashOperationId) }
    }
}
