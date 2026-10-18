package com.aiyu.findex.ui.tags

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.repository.StorageRepository
import com.aiyu.findex.core.repository.Tag
import com.aiyu.findex.core.repository.TagRepository
import com.aiyu.findex.ui.navigation.TaggedFilesRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TaggedUiState(val tag: Tag? = null, val items: List<StorageItem> = emptyList(), val loaded: Boolean = false)

@HiltViewModel
class TaggedFilesViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tagRepository: TagRepository,
    private val storage: StorageRepository
) : ViewModel() {
    val route: TaggedFilesRoute = savedStateHandle.toRoute<TaggedFilesRoute>()

    private val _state = MutableStateFlow(TaggedUiState())
    val state: StateFlow<TaggedUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            var pruned = false
            tagRepository.filesWithTag(route.tagId).collect { paths ->
                // Once per visit: drop rows for files deleted outside Findex.
                if (!pruned) { pruned = true; tagRepository.prune(paths) }
                val items = paths.mapNotNull { runCatching { storage.getItem(it) }.getOrNull() }
                _state.value = TaggedUiState(tagRepository.tags().first().firstOrNull { it.id == route.tagId }, items, loaded = true)
            }
        }
    }
}
