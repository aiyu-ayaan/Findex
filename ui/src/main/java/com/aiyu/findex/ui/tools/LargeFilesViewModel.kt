package com.aiyu.findex.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.OperationResult
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.core.scanner.StorageScanner
import com.aiyu.findex.ui.components.categoryOf
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LargeFilesUiState(
    val isScanning: Boolean = true,
    val minBytes: Long = 100L * MB,
    val olderThanDays: Int? = null,
    val category: FileCategory? = null,
    val includeHidden: Boolean = false,
    val items: List<StorageItem> = emptyList(),
    val selected: Set<String> = emptySet()
) {
    val selectedBytes: Long get() = items.filter { it.path in selected }.sumOf { it.size }
}

private const val MB = 1024L * 1024L
val LARGE_FILE_SIZES = listOf(10 * MB, 50 * MB, 100 * MB, 500 * MB, 1024 * MB)
val LARGE_FILE_AGES: List<Int?> = listOf(null, 30, 90, 180, 365)

@HiltViewModel
class LargeFilesViewModel @Inject constructor(
    private val scanner: StorageScanner,
    private val operationManager: OperationManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(LargeFilesUiState())
    val uiState: StateFlow<LargeFilesUiState> = _uiState.asStateFlow()

    // One walk of storage; the filters below only narrow this list.
    private var all: List<StorageItem> = emptyList()

    init {
        rescan()
        viewModelScope.launch {
            operationManager.results.collect { result ->
                val op = (result as? OperationResult.Success)?.operation
                if (op is FileOperation.Trash || op is FileOperation.Delete) rescan()
            }
        }
    }

    fun rescan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            all = scanner.largestFiles(LIMIT)
            _uiState.update { refilter(it.copy(isScanning = false, selected = emptySet())) }
        }
    }

    fun setMinBytes(v: Long) = filter { it.copy(minBytes = v) }
    fun setAge(days: Int?) = filter { it.copy(olderThanDays = days) }
    fun setCategory(c: FileCategory?) = filter { it.copy(category = c) }
    fun setIncludeHidden(v: Boolean) = filter { it.copy(includeHidden = v) }

    fun toggle(path: String) = _uiState.update { s -> s.copy(selected = if (path in s.selected) s.selected - path else s.selected + path) }
    fun selectAll() = _uiState.update { s -> s.copy(selected = s.items.mapTo(HashSet()) { it.path }) }
    fun clearSelection() = _uiState.update { it.copy(selected = emptySet()) }

    /** Bin by default; [permanent] only when the user ticked it in the dialog. */
    fun delete(permanent: Boolean) {
        val paths = _uiState.value.selected.toList()
        if (paths.isNotEmpty()) operationManager.enqueue(if (permanent) FileOperation.Delete(paths) else FileOperation.Trash(paths))
    }

    private fun filter(change: (LargeFilesUiState) -> LargeFilesUiState) = _uiState.update { refilter(change(it)) }

    private fun refilter(s: LargeFilesUiState): LargeFilesUiState {
        val cutoff = s.olderThanDays?.let { System.currentTimeMillis() - it * 86_400_000L }
        val items = all.filter { f ->
            f.size >= s.minBytes &&
                (cutoff == null || f.lastModified <= cutoff) &&
                (s.category == null || categoryOf(f.extension) == s.category) &&
                (s.includeHidden || f.path.split('/').none { it.startsWith(".") })
        }
        val paths = items.mapTo(HashSet()) { it.path }
        return s.copy(items = items, selected = s.selected.filterTo(HashSet()) { it in paths })
    }

    private companion object { const val LIMIT = 2000 }
}
