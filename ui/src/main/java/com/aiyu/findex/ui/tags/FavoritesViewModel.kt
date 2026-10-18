package com.aiyu.findex.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.model.Bookmark
import com.aiyu.findex.core.repository.LibraryRepository
import com.aiyu.findex.core.repository.Tag
import com.aiyu.findex.core.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class FavoritesUiState(val folders: List<Bookmark> = emptyList(), val files: List<Bookmark> = emptyList(), val tags: List<Tag> = emptyList(), val loaded: Boolean = false)

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val library: LibraryRepository,
    private val tagRepository: TagRepository
) : ViewModel() {
    val state: StateFlow<FavoritesUiState> = combine(library.bookmarks, tagRepository.tags()) { marks, tags ->
        // Network bookmarks are folders; a file is only known by checking the disk.
        val (files, folders) = marks.partition { "://" !in it.path && File(it.path).isFile }
        FavoritesUiState(folders, files, tags, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState())

    fun removeBookmark(path: String) { viewModelScope.launch { library.removeBookmark(path) } }
    fun createTag(name: String, color: Int) { viewModelScope.launch { tagRepository.create(name, color) } }
    fun updateTag(tag: Tag, name: String, color: Int) { viewModelScope.launch { tagRepository.update(tag, name, color) } }
    fun deleteTag(tag: Tag) { viewModelScope.launch { tagRepository.delete(tag.id) } }
}
