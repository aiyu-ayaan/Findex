package com.aiyu.findex.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.model.TrashItem
import com.aiyu.findex.core.repository.TrashRepository
import com.aiyu.findex.core.repository.toStorageItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Bin entries shown as [StorageItem]s keyed by their trash path, so the shared file list can render them. */
data class RecycleBinUiState(
    val entries: List<Pair<TrashItem, StorageItem>> = emptyList(),
    val selection: Set<String> = emptySet(),
    val isLoading: Boolean = true
) {
    val items: List<StorageItem> get() = entries.map { it.second }
    val totalBytes: Long get() = entries.sumOf { it.first.size }
}

@HiltViewModel
class RecycleBinViewModel @Inject constructor(
    private val trashRepository: TrashRepository
) : ViewModel() {

    private val selection = MutableStateFlow(emptySet<String>())

    private val entries = trashRepository.items.map { list ->
        list.map { trash ->
            // Display the original name and the deletion time instead of the bin file's own.
            trash to File(trash.trashPath).toStorageItem().copy(name = trash.name, size = trash.size, lastModified = trash.deletedAt)
        }
    }.flowOn(Dispatchers.IO)

    val uiState: StateFlow<RecycleBinUiState> = combine(entries, selection) { entries, selected ->
        RecycleBinUiState(entries, selected.filterTo(HashSet()) { p -> entries.any { it.first.trashPath == p } }, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecycleBinUiState())

    init {
        viewModelScope.launch {
            trashRepository.dropMissing()
            trashRepository.purgeExpired()
        }
    }

    fun setSelection(paths: Set<String>) = selection.update { paths }
    fun toggle(path: String) = selection.update { if (path in it) it - path else it + path }
    fun selectAll() = selection.update { uiState.value.entries.mapTo(HashSet()) { it.first.trashPath } }
    fun clearSelection() = selection.update { emptySet() }

    fun restore(trashPaths: Collection<String>) {
        trashRepository.restore(uiState.value.entries.filter { it.first.trashPath in trashPaths }.map { it.first.id })
        clearSelection()
    }

    fun deleteForever(trashPaths: Collection<String>) {
        trashRepository.deleteForever(trashPaths.toList())
        clearSelection()
    }
}
