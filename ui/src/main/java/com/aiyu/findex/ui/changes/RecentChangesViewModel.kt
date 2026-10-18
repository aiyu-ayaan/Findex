package com.aiyu.findex.ui.changes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.repository.RecentChangesRepository
import com.aiyu.findex.ui.components.categoryOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RecentChangesState(
    val days: Int = 7,
    val category: FileCategory? = null,
    val all: List<StorageItem> = emptyList(),
    val loading: Boolean = true
) {
    val items: List<StorageItem> get() = if (category == null) all else all.filter { categoryOf(it.extension) == category }
    val limited: Boolean get() = all.size >= LIMIT
}

private const val LIMIT = 500

@HiltViewModel
class RecentChangesViewModel @Inject constructor(private val repository: RecentChangesRepository) : ViewModel() {
    private val _state = MutableStateFlow(RecentChangesState())
    val state: StateFlow<RecentChangesState> = _state.asStateFlow()
    private var job: Job? = null

    init { load() }

    fun setDays(days: Int) { _state.update { it.copy(days = days) }; load() }
    fun setCategory(c: FileCategory?) = _state.update { it.copy(category = c) }

    private fun load() {
        job?.cancel()
        job = viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val items = repository.changes(_state.value.days, LIMIT)
            _state.update { it.copy(all = items, loading = false) }
        }
    }
}
