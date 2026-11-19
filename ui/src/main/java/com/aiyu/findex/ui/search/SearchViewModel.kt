package com.aiyu.findex.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.core.repository.LibraryRepository
import com.aiyu.findex.core.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<StorageItem> = emptyList(),
    val isSearching: Boolean = false,
    val selection: Set<String> = emptySet()
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    private val libraryRepository: LibraryRepository,
    private val operationManager: OperationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            // Re-run the current query after a delete or move so stale rows disappear.
            val refresh = operationManager.results.map { query.value }
            merge(query.debounce(250).distinctUntilChanged(), refresh)
                .mapLatest { q -> q to storageRepository.search(q) }
                .collect { (q, items) ->
                    if (q != query.value) return@collect
                    // Keep only selected paths that are still in the results (deleted/moved ones drop out).
                    val paths = items.mapTo(HashSet()) { it.path }
                    _uiState.update { s -> s.copy(results = items, isSearching = false, selection = s.selection.filterTo(HashSet()) { it in paths }) }
                }
        }
    }

    fun onQueryChange(text: String) {
        query.value = text
        _uiState.update { it.copy(query = text, isSearching = text.isNotBlank(), results = if (text.isBlank()) emptyList() else it.results, selection = emptySet()) }
    }

    /** Re-runs the current query; a rename is not an operation, so [OperationManager.results] never reports it. */
    fun refresh() {
        val q = query.value
        if (q.isBlank()) return
        viewModelScope.launch {
            val items = storageRepository.search(q)
            val paths = items.mapTo(HashSet()) { it.path }
            if (q == query.value) _uiState.update { s -> s.copy(results = items, selection = s.selection.filterTo(HashSet()) { it in paths }) }
        }
    }

    fun setSelection(paths: Set<String>) = _uiState.update { it.copy(selection = paths) }
    fun toggle(path: String) = _uiState.update { s -> s.copy(selection = if (path in s.selection) s.selection - path else s.selection + path) }
    fun clearSelection() = _uiState.update { it.copy(selection = emptySet()) }

    fun onOpened(path: String) {
        viewModelScope.launch { libraryRepository.recordOpened(File(path)) }
    }

    fun delete(paths: List<String>, permanent: Boolean) {
        operationManager.enqueue(if (permanent) FileOperation.Delete(paths) else FileOperation.Trash(paths))
    }
}
