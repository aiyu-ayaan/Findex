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
    val isSearching: Boolean = false
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
                    if (q == query.value) _uiState.update { it.copy(results = items, isSearching = false) }
                }
        }
    }

    fun onQueryChange(text: String) {
        query.value = text
        _uiState.update { it.copy(query = text, isSearching = text.isNotBlank(), results = if (text.isBlank()) emptyList() else it.results) }
    }

    fun onOpened(path: String) {
        viewModelScope.launch { libraryRepository.recordOpened(File(path)) }
    }

    fun delete(paths: List<String>, permanent: Boolean) {
        operationManager.enqueue(if (permanent) FileOperation.Delete(paths) else FileOperation.Trash(paths))
    }
}
