package com.aiyu.findex.ui.explorer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aiyu.findex.core.model.ActiveOperation
import com.aiyu.findex.core.model.AppSettings
import com.aiyu.findex.core.model.ClipboardContent
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.OperationResult
import com.aiyu.findex.core.model.SortSpec
import com.aiyu.findex.core.model.StorageDrive
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.model.sortedBy
import com.aiyu.findex.core.operation.FileClipboard
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.core.operation.uniqueTarget
import com.aiyu.findex.core.repository.LibraryRepository
import com.aiyu.findex.core.repository.StorageRepository
import com.aiyu.findex.core.settings.SettingsRepository
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.navigation.ExplorerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** One browsing tab. [root] is the storage volume the tab lives on; up-navigation stops there. */
data class ExplorerTab(val id: Long, val root: String, val rootName: String, val path: String)

data class ExplorerUiState(
    val tabs: List<ExplorerTab> = emptyList(),
    val activeTab: Int = 0,
    val items: List<StorageItem> = emptyList(),
    /** Folder that [items] belong to; the UI switches content only when this changes. */
    val itemsPath: String? = null,
    val isLoading: Boolean = false,
    val selection: Set<String> = emptySet(),
    val settings: AppSettings = AppSettings(),
    val clipboard: ClipboardContent? = null,
    val activeOperation: ActiveOperation? = null,
    val isBookmarked: Boolean = false,
    /** Direction of the last folder change, for the shared-axis animation. */
    val forward: Boolean = true
) {
    val tab: ExplorerTab? get() = tabs.getOrNull(activeTab)
    val inSelection: Boolean get() = selection.isNotEmpty()
    val canGoUp: Boolean get() = tab?.let { it.path != it.root } ?: false
    val selectedItems: List<StorageItem> get() = items.filter { it.path in selection }
}

data class FileDetails(val item: StorageItem, val totalBytes: Long, val fileCount: Int, val folderCount: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ExplorerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val storageRepository: StorageRepository,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val clipboard: FileClipboard,
    private val operationManager: OperationManager
) : ViewModel() {

    private val startPath = savedStateHandle.toRoute<ExplorerRoute>().path

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private val _messages = Channel<Int>(Channel.BUFFERED)
    /** One-shot snackbar messages (string resource ids). */
    val messages = _messages.receiveAsFlow()

    private var drives: List<StorageDrive> = emptyList()
    private var nextTabId = 0L
    private var loadJob: Job? = null

    // Recently visited listings: going back or switching tabs renders instantly, then refreshes.
    private val listingCache = object : LinkedHashMap<String, List<StorageItem>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<StorageItem>>?) = size > 48
    }

    init {
        viewModelScope.launch {
            drives = storageRepository.getStorageDrives().first()
            val initialSettings = settingsRepository.settings.first()
            _uiState.update { it.copy(tabs = listOf(newTab(startPath)), settings = initialSettings) }
            load()
            settingsRepository.settings.collect { settings ->
                val reload = settings.showHidden != _uiState.value.settings.showHidden || settings.sort != _uiState.value.settings.sort
                _uiState.update { it.copy(settings = settings) }
                if (reload) load()
            }
        }
        viewModelScope.launch { clipboard.content.collect { c -> _uiState.update { it.copy(clipboard = c) } } }
        viewModelScope.launch { operationManager.active.collect { a -> _uiState.update { it.copy(activeOperation = a) } } }
        viewModelScope.launch {
            operationManager.results.collect { if (it !is OperationResult.Cancelled) load(showLoading = false) }
        }
        viewModelScope.launch {
            _uiState.map { it.tab?.path }.distinctUntilChanged()
                .flatMapLatest { path -> libraryRepository.isBookmarked(path ?: "") }
                .collect { b -> _uiState.update { it.copy(isBookmarked = b) } }
        }
    }

    private fun newTab(path: String): ExplorerTab {
        val drive = drives.filter { path.startsWith(it.path) }.maxByOrNull { it.path.length }
        return ExplorerTab(
            id = nextTabId++,
            root = drive?.path ?: path,
            rootName = drive?.name ?: File(path).name,
            path = path
        )
    }

    fun load(showLoading: Boolean = true) {
        val tab = _uiState.value.tab ?: return
        val settings = _uiState.value.settings
        val key = "${tab.path}|${settings.showHidden}|${settings.sort}"
        loadJob?.cancel()
        listingCache[key]?.let { cached -> publish(tab.path, cached) }
        loadJob = viewModelScope.launch {
            // Only show a spinner when the folder is slow; fast folders just appear.
            val spinner = if (showLoading && key !in listingCache) launch {
                delay(200)
                _uiState.update { it.copy(isLoading = true) }
            } else null
            val items = storageRepository.getFilesInDirectory(tab.path, settings.showHidden).sortedBy(settings.sort)
            spinner?.cancel()
            listingCache[key] = items
            publish(tab.path, items)
        }
    }

    private fun publish(path: String, items: List<StorageItem>) {
        _uiState.update { state ->
            // Drop selections of items that vanished (deleted/moved elsewhere).
            val paths = items.mapTo(HashSet()) { it.path }
            state.copy(items = items, itemsPath = path, isLoading = false, selection = state.selection.filterTo(HashSet()) { it in paths })
        }
    }

    // region Navigation

    private fun setPath(path: String, forward: Boolean) {
        _uiState.update { s ->
            val tabs = s.tabs.toMutableList()
            tabs[s.activeTab] = tabs[s.activeTab].copy(path = path)
            s.copy(tabs = tabs, selection = emptySet(), forward = forward)
        }
        load()
    }

    fun openFolder(path: String) = setPath(path, forward = true)

    fun jumpTo(path: String) {
        val current = _uiState.value.tab?.path ?: return
        if (path != current) setPath(path, forward = path.length > current.length)
    }

    /** @return false when already at the volume root (the screen should pop). */
    fun goUp(): Boolean {
        val s = _uiState.value
        val tab = s.tab ?: return false
        if (!s.canGoUp) return false
        setPath(File(tab.path).parent ?: tab.root, forward = false)
        return true
    }

    fun onFileOpened(path: String) {
        viewModelScope.launch { libraryRepository.recordOpened(File(path)) }
    }

    // endregion

    // region Tabs

    fun addTab() {
        val tab = _uiState.value.tab ?: return
        _uiState.update { s -> s.copy(tabs = s.tabs + newTab(tab.path).copy(root = tab.root, rootName = tab.rootName), activeTab = s.tabs.size, selection = emptySet()) }
        load()
    }

    fun selectTab(index: Int) {
        if (index == _uiState.value.activeTab) return
        _uiState.update { it.copy(activeTab = index, selection = emptySet()) }
        load()
    }

    fun closeTab(index: Int) {
        val s = _uiState.value
        if (s.tabs.size <= 1) return
        val tabs = s.tabs.toMutableList().apply { removeAt(index) }
        val active = when {
            index < s.activeTab -> s.activeTab - 1
            index == s.activeTab -> (s.activeTab).coerceAtMost(tabs.lastIndex)
            else -> s.activeTab
        }
        _uiState.update { it.copy(tabs = tabs, activeTab = active, selection = emptySet()) }
        load()
    }

    // endregion

    // region Selection

    fun toggleSelection(path: String) = _uiState.update { s ->
        s.copy(selection = if (path in s.selection) s.selection - path else s.selection + path)
    }

    fun setSelection(paths: Set<String>) = _uiState.update { it.copy(selection = paths) }

    fun selectAll() = _uiState.update { s -> s.copy(selection = s.items.mapTo(HashSet()) { it.path }) }

    fun clearSelection() = _uiState.update { it.copy(selection = emptySet()) }

    // endregion

    // region Clipboard & operations

    fun copySelection(paths: Collection<String> = _uiState.value.selection) {
        clipboard.copy(paths.toList())
        clearSelection()
    }

    fun cutSelection(paths: Collection<String> = _uiState.value.selection) {
        clipboard.cut(paths.toList())
        clearSelection()
    }

    fun paste() {
        val tab = _uiState.value.tab ?: return
        clipboard.paste(tab.path)
    }

    fun clearClipboard() = clipboard.clear()

    fun delete(paths: Collection<String>) {
        operationManager.enqueue(FileOperation.Delete(paths.toList()))
        clearSelection()
    }

    fun compress(paths: Collection<String>, archiveName: String, password: String?) {
        val tab = _uiState.value.tab ?: return
        val name = archiveName.trim().removeSuffix(".zip").ifEmpty { return }
        val target = uniqueTarget(File(tab.path), "$name.zip")
        operationManager.enqueue(FileOperation.Compress(paths.toList(), target.absolutePath, password))
        clearSelection()
    }

    fun extract(archivePath: String, password: String?) {
        val archive = File(archivePath)
        val dest = uniqueTarget(archive.parentFile ?: return, archive.nameWithoutExtension)
        operationManager.enqueue(FileOperation.Extract(archivePath, dest.absolutePath, password))
    }

    fun cancelOperation() = operationManager.cancelCurrent()

    fun rename(path: String, newName: String) {
        viewModelScope.launch {
            if (storageRepository.rename(path, newName) == null) _messages.send(R.string.error_rename)
            clearSelection()
            load(showLoading = false)
        }
    }

    fun createFolder(name: String) {
        val tab = _uiState.value.tab ?: return
        viewModelScope.launch {
            val created = storageRepository.createFolder(tab.path, name)
            if (created == null) _messages.send(R.string.error_new_folder) else load(showLoading = false)
        }
    }

    /** Toggles the current folder's bookmark. */
    fun toggleBookmark() {
        val path = _uiState.value.tab?.path ?: return
        toggleBookmark(path, _uiState.value.isBookmarked)
    }

    fun toggleBookmark(path: String, bookmarked: Boolean) {
        viewModelScope.launch {
            libraryRepository.toggleBookmark(path, bookmarked)
            _messages.send(if (bookmarked) R.string.bookmark_removed else R.string.bookmark_added)
        }
    }

    suspend fun details(item: StorageItem): FileDetails = withContext(Dispatchers.IO) {
        val file = File(item.path)
        if (!file.isDirectory) return@withContext FileDetails(item, file.length(), 1, 0)
        var bytes = 0L
        var files = 0
        var folders = 0
        file.walkTopDown().forEach { if (it.isFile) { files++; bytes += it.length() } else if (it != file) folders++ }
        FileDetails(item, bytes, files, folders)
    }

    // endregion

    // region Settings

    fun setSort(sort: SortSpec) = viewModelScope.launch { settingsRepository.setSort(sort) }

    fun setGridView(grid: Boolean) = viewModelScope.launch { settingsRepository.setGridView(grid) }

    fun setShowHidden(show: Boolean) = viewModelScope.launch { settingsRepository.setShowHidden(show) }

    // endregion
}
