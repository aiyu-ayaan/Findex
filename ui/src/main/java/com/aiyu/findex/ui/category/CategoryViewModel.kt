package com.aiyu.findex.ui.category

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.OperationResult
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.operation.FileClipboard
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.core.repository.LibraryRepository
import com.aiyu.findex.core.repository.StorageRepository
import com.aiyu.findex.ui.navigation.CategoryRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class CategoryUiState(
    val category: FileCategory,
    val items: List<StorageItem> = emptyList(),
    val isLoading: Boolean = true,
    val selection: Set<String> = emptySet(),
    val gridView: Boolean = category == FileCategory.PHOTOS || category == FileCategory.VIDEOS
) {
    val totalBytes: Long get() = items.sumOf { it.size }
}

@HiltViewModel
class CategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val storageRepository: StorageRepository,
    private val libraryRepository: LibraryRepository,
    private val clipboard: FileClipboard,
    private val operationManager: OperationManager
) : ViewModel() {

    private val category = FileCategory.valueOf(savedStateHandle.toRoute<CategoryRoute>().category)
    private val _uiState = MutableStateFlow(CategoryUiState(category))
    val uiState: StateFlow<CategoryUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch { operationManager.results.collect { if (it is OperationResult.Success) load() } }
    }

    fun load() {
        viewModelScope.launch {
            // MediaStore can lag behind deletes; drop entries whose file is gone.
            val items = storageRepository.getCategoryFiles(category).filter { File(it.path).exists() }
            _uiState.update { s -> s.copy(items = items, isLoading = false, selection = s.selection.filterTo(HashSet()) { p -> items.any { it.path == p } }) }
        }
    }

    fun toggleGrid() = _uiState.update { it.copy(gridView = !it.gridView) }
    fun setSelection(paths: Set<String>) = _uiState.update { it.copy(selection = paths) }
    fun toggle(path: String) = _uiState.update { s -> s.copy(selection = if (path in s.selection) s.selection - path else s.selection + path) }
    fun selectAll() = _uiState.update { s -> s.copy(selection = s.items.mapTo(HashSet()) { it.path }) }
    fun clearSelection() = _uiState.update { it.copy(selection = emptySet()) }

    fun copySelection() { clipboard.copy(_uiState.value.selection.toList()); clearSelection() }
    fun cutSelection() { clipboard.cut(_uiState.value.selection.toList()); clearSelection() }

    fun delete(paths: List<String>, permanent: Boolean) {
        operationManager.enqueue(if (permanent) FileOperation.Delete(paths) else FileOperation.Trash(paths))
        clearSelection()
    }

    fun onOpened(path: String) {
        viewModelScope.launch { libraryRepository.recordOpened(File(path)) }
    }
}
