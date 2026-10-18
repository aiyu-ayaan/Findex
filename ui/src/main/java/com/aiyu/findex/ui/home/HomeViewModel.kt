package com.aiyu.findex.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.model.DriveType
import com.aiyu.findex.core.network.NetworkError
import com.aiyu.findex.core.network.NetworkStatus
import com.aiyu.findex.ui.network.unlockOrError
import com.aiyu.findex.core.network.SmbRepository
import com.aiyu.findex.core.model.OperationResult
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.core.repository.LibraryRepository
import com.aiyu.findex.core.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    private val libraryRepository: LibraryRepository,
    private val operationManager: OperationManager,
    private val smbRepository: SmbRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val downloadsPath: String get() = storageRepository.downloadsPath

    init {
        refresh()
        viewModelScope.launch { libraryRepository.bookmarks.collect { b -> _uiState.update { it.copy(bookmarks = b) } } }
        viewModelScope.launch { libraryRepository.recentFiles(6).collect { r -> _uiState.update { it.copy(recents = r) } } }
        viewModelScope.launch {
            smbRepository.servers.collect { s ->
                _uiState.update { it.copy(networkDrives = s, selectedTabIndex = if (s.isEmpty()) 0 else it.selectedTabIndex) }
                s.filter { it.id !in _uiState.value.networkStatus }.forEach { probe(it.id) }
            }
        }
        viewModelScope.launch { operationManager.active.collect { a -> _uiState.update { it.copy(activeOperation = a) } } }
        // Space and counts change after any finished operation.
        viewModelScope.launch {
            operationManager.results.collect { if (it is OperationResult.Success) refresh() }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            storageRepository.getStorageDrives().collect { all ->
                _uiState.update { it.copy(drives = all.filter { d -> d.type != DriveType.NETWORK }) }
            }
        }
        viewModelScope.launch {
            val stats = storageRepository.getCategoryStats()
            _uiState.update { it.copy(categoryStats = stats) }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index) }
        // Re-check on every visit so a share that dropped (Wi-Fi change, server off) shows as offline.
        if (index == 1) _uiState.value.networkDrives.forEach { probe(it.id) }
    }

    suspend fun unlock(id: Long, password: String): NetworkError? =
        smbRepository.unlockOrError(id, password).also { if (it == null) probe(id) }

    /** [force] re-probes even while a check is running, e.g. after the server was edited. */
    fun probe(id: Long, force: Boolean = false) {
        if (!force && _uiState.value.networkStatus[id] == NetworkStatus.Checking) return
        _uiState.update { it.copy(networkStatus = it.networkStatus + (id to NetworkStatus.Checking)) }
        viewModelScope.launch {
            val status = smbRepository.probe(id)
            _uiState.update { it.copy(networkStatus = it.networkStatus + (id to status)) }
        }
    }

    fun cancelOperation() = operationManager.cancelCurrent()

    fun onRecentOpened(path: String) {
        viewModelScope.launch { libraryRepository.recordOpened(File(path)) }
    }

    fun removeBookmark(path: String) {
        viewModelScope.launch { libraryRepository.removeBookmark(path) }
    }
}
