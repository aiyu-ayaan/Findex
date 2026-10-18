package com.aiyu.findex.ui.dualpane

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aiyu.findex.core.compare.CompareStatus
import com.aiyu.findex.core.compare.FolderCompare
import com.aiyu.findex.core.model.ActiveOperation
import com.aiyu.findex.core.model.AppSettings
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.OperationResult
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.model.sortedBy
import com.aiyu.findex.core.network.NetworkException
import com.aiyu.findex.core.network.SmbPath
import com.aiyu.findex.core.network.SmbRepository
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.core.repository.StorageRepository
import com.aiyu.findex.core.settings.SettingsRepository
import com.aiyu.findex.ui.navigation.DualPaneRoute
import com.aiyu.findex.ui.network.messageRes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** One side of the dual-pane browser. [root] is the volume or share; going up stops there. */
data class Pane(
    val root: String = "",
    val rootName: String = "",
    val path: String = "",
    val items: List<StorageItem> = emptyList(),
    val loading: Boolean = true,
    val selection: Set<String> = emptySet(),
    /** Compare result by item path; empty while not comparing. */
    val status: Map<String, CompareStatus> = emptyMap()
) {
    val canGoUp: Boolean get() = path != root
    val title: String get() = if (path == root) rootName else path.substringAfterLast('/')
}

data class DualPaneUiState(
    val panes: List<Pane> = listOf(Pane(), Pane()),
    val active: Int = 0,
    val comparing: Boolean = false,
    val activeOperation: ActiveOperation? = null
) {
    val activePane: Pane get() = panes[active]
    val otherPane: Pane get() = panes[1 - active]
    val selection: Set<String> get() = activePane.selection
}

/** Two folders side by side: copy or move between them, and compare their contents. */
@HiltViewModel
class DualPaneViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val storageRepository: StorageRepository,
    private val smbRepository: SmbRepository,
    private val settingsRepository: SettingsRepository,
    private val operationManager: OperationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DualPaneUiState())
    val uiState: StateFlow<DualPaneUiState> = _uiState.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var settings = AppSettings()
    private val loadJobs = arrayOfNulls<Job>(2)
    private var compareJob: Job? = null

    init {
        val start = savedStateHandle.toRoute<DualPaneRoute>().path
        viewModelScope.launch {
            settings = settingsRepository.settings.first()
            val primary = storageRepository.getStorageDrives().first().first().path
            val first = paneAt(start ?: primary)
            _uiState.update { it.copy(panes = listOf(first, paneAt(primary))) }
            load(0)
            load(1)
            settingsRepository.settings.collect { s ->
                val reload = s.showHidden != settings.showHidden || s.sort != settings.sort
                settings = s
                if (reload) { load(0); load(1) }
            }
        }
        viewModelScope.launch { operationManager.active.collect { a -> _uiState.update { it.copy(activeOperation = a) } } }
        viewModelScope.launch {
            operationManager.results.collect { if (it !is OperationResult.Cancelled) { load(0); load(1) } }
        }
    }

    /** Same root rule as explorer tabs: the deepest storage volume holding [path], or the share. */
    private suspend fun paneAt(path: String): Pane {
        SmbPath.parse(path)?.let { (id, _) ->
            return Pane(root = SmbPath.of(id, ""), rootName = smbRepository.server(id)?.label ?: path, path = path)
        }
        val drive = storageRepository.getStorageDrives().first().filter { path.startsWith(it.path) }.maxByOrNull { it.path.length }
        return Pane(root = drive?.path ?: path, rootName = drive?.name ?: File(path).name, path = path)
    }

    private fun updatePane(index: Int, block: (Pane) -> Pane) = _uiState.update { s ->
        s.copy(panes = s.panes.toMutableList().also { it[index] = block(it[index]) })
    }

    private fun load(index: Int) {
        val path = _uiState.value.panes[index].path
        if (path.isEmpty()) return
        loadJobs[index]?.cancel()
        loadJobs[index] = viewModelScope.launch {
            updatePane(index) { it.copy(loading = true) }
            val items = try {
                storageRepository.getFilesInDirectory(path, settings.showHidden).sortedBy(settings.sort)
            } catch (e: NetworkException) {
                _messages.send(e.error.messageRes())
                emptyList()
            }
            updatePane(index) { p ->
                val paths = items.mapTo(HashSet()) { it.path }
                p.copy(items = items, loading = false, selection = p.selection.filterTo(HashSet()) { it in paths })
            }
            if (_uiState.value.comparing) compare()
        }
    }

    // region Navigation

    fun activate(index: Int) = _uiState.update { s ->
        if (s.active == index) s
        else s.copy(active = index, panes = s.panes.mapIndexed { i, p -> if (i == index) p else p.copy(selection = emptySet()) })
    }

    fun openFolder(index: Int, path: String) {
        activate(index)
        updatePane(index) { it.copy(path = path, selection = emptySet(), status = emptyMap()) }
        load(index)
    }

    /** @return false when the active pane is already at its root. */
    fun goUp(index: Int = _uiState.value.active): Boolean {
        val pane = _uiState.value.panes[index]
        if (!pane.canGoUp) return false
        // String parent works for both "/storage/…" and "smb://id/…" paths.
        openFolder(index, pane.path.substringBeforeLast('/').takeIf { it.length >= pane.root.length } ?: pane.root)
        return true
    }

    fun swap() = _uiState.update { s -> s.copy(panes = s.panes.reversed(), active = 1 - s.active) }

    // endregion

    // region Selection

    fun setSelection(index: Int, paths: Set<String>) {
        activate(index)
        updatePane(index) { it.copy(selection = paths) }
    }

    fun toggleSelection(index: Int, path: String) {
        val current = _uiState.value.panes[index].selection
        setSelection(index, if (path in current) current - path else current + path)
    }

    fun clearSelection() = updatePane(_uiState.value.active) { it.copy(selection = emptySet()) }

    /** Selects what the other pane lacks, ready to copy across. */
    fun selectMissing() {
        val pane = _uiState.value.activePane
        updatePane(_uiState.value.active) { p -> p.copy(selection = pane.status.filterValues { it == CompareStatus.ONLY_HERE }.keys) }
    }

    // endregion

    // region Compare

    fun setComparing(on: Boolean) {
        _uiState.update { s -> s.copy(comparing = on, panes = s.panes.map { it.copy(status = emptyMap()) }) }
        if (on) compare() else compareJob?.cancel()
    }

    private fun compare() {
        compareJob?.cancel()
        compareJob = viewModelScope.launch {
            val (left, right) = _uiState.value.panes
            val (l, r) = withContext(Dispatchers.IO) { FolderCompare.compare(left.items, right.items, ::signature) }
            // Skip a stale result if a pane moved on while the folders were summed.
            _uiState.update { s ->
                if (s.panes[0].items !== left.items || s.panes[1].items !== right.items) s
                else s.copy(panes = listOf(s.panes[0].copy(status = l), s.panes[1].copy(status = r)))
            }
        }
    }

    /** File count and bytes of a local folder; shares would need a full remote walk, so they stay unknown. */
    private fun signature(item: StorageItem): Any? {
        if (SmbPath.parse(item.path) != null) return null
        var count = 0
        var bytes = 0L
        File(item.path).walkTopDown().filter { it.isFile }.forEach { count++; bytes += it.length() }
        return count to bytes
    }

    // endregion

    // region Operations

    fun transferToOther(move: Boolean) {
        val s = _uiState.value
        val sources = s.selection.toList()
        if (sources.isEmpty()) return
        val dest = s.otherPane.path
        operationManager.enqueue(if (move) FileOperation.Move(sources, dest) else FileOperation.Copy(sources, dest))
        clearSelection()
    }

    fun delete(permanent: Boolean) {
        val paths = _uiState.value.selection.toList()
        if (paths.isEmpty()) return
        operationManager.enqueue(if (permanent) FileOperation.Delete(paths) else FileOperation.Trash(paths))
        clearSelection()
    }

    fun cancelOperation() = operationManager.cancelCurrent()

    // endregion
}
