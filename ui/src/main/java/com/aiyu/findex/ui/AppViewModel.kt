package com.aiyu.findex.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.history.StorageHistoryScheduler
import com.aiyu.findex.core.model.AppSettings
import com.aiyu.findex.core.model.OperationResult
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.core.operation.FileClipboard
import com.aiyu.findex.ui.reminder.CleanupReminderScheduler
import com.aiyu.findex.core.repository.TrashRepository
import com.aiyu.findex.ui.explorer.TabSession
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.aiyu.findex.core.update.UpdateChannel
import com.aiyu.findex.core.update.UpdateCheckScheduler
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
import android.content.Context
import com.aiyu.findex.core.repository.LibraryRepository
import com.aiyu.findex.ui.widget.StorageWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.merge

@HiltViewModel
class AppViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    operationManager: OperationManager,
    private val trashRepository: TrashRepository,
    tabSession: TabSession,
    private val updates: UpdateRepository,
    private val updateCheckScheduler: UpdateCheckScheduler,
    private val storageHistoryScheduler: StorageHistoryScheduler,
    private val clipboard: FileClipboard,
    private val cleanupReminderScheduler: CleanupReminderScheduler,
    libraryRepository: LibraryRepository,
    @ApplicationContext context: Context
) : ViewModel() {
    val tabCount: StateFlow<Int> = tabSession.state.map { it.tabs.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Null until DataStore has been read, so the first frame never flashes the wrong theme. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val operationResults: SharedFlow<OperationResult> = operationManager.results

    val updateState: StateFlow<UpdateState> = updates.state
    val installedVersion: String get() = updates.installedName

    init {
        // The home screen widget shows bookmarks and free space: redraw it when either can have changed.
        viewModelScope.launch {
            merge(libraryRepository.bookmarks.distinctUntilChanged().map { }, operationManager.results.map { })
                .collect { StorageWidget.refresh(context) }
        }
        viewModelScope.launch { trashRepository.purgeExpired() }
        // Once per process: the global update dialog appears when this finds a newer release.
        viewModelScope.launch {
            val s = settingsRepository.settings.first()
            if (s.autoUpdate) updates.check(s.updateChannel ?: UpdateChannel.forVersion(updates.installedName))
        }
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.autoUpdate }
                .distinctUntilChanged()
                .collect { enabled -> updateCheckScheduler.sync(enabled) }
        }
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.storageHistory }
                .distinctUntilChanged()
                .collect { enabled -> storageHistoryScheduler.sync(enabled) }
        }
        viewModelScope.launch {
            settingsRepository.settings
                .map { it.cleanupReminder }
                .distinctUntilChanged()
                .collect { enabled -> cleanupReminderScheduler.sync(enabled) }
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

    /** "Save to Findex": shared URIs wait on the clipboard until the user pastes them into a folder. */
    fun importShared(uris: List<String>) = clipboard.import(uris)
}
