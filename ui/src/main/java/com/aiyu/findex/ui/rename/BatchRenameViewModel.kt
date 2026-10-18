package com.aiyu.findex.ui.rename

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.RenameEntry
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.core.operation.RenamePlanner
import com.aiyu.findex.core.operation.RenamePreview
import com.aiyu.findex.core.operation.RenameRule
import com.aiyu.findex.core.operation.RenameStatus
import com.aiyu.findex.core.repository.StorageRepository
import com.aiyu.findex.ui.navigation.BatchRenameRoute
import com.aiyu.findex.ui.navigation.SelectionHandoff
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class BatchRenameViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val storage: StorageRepository,
    private val operationManager: OperationManager
) : ViewModel() {
    private val token = savedStateHandle.toRoute<BatchRenameRoute>().token

    /** Null while loading. */
    private val items = MutableStateFlow<List<StorageItem>?>(null)
    private val siblings = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    private val _rule = MutableStateFlow(RenameRule())
    val rule: StateFlow<RenameRule> = _rule.asStateFlow()

    /** True when the hand-off token is gone (process death or reopened screen). */
    private val _expired = MutableStateFlow(false)
    val expired: StateFlow<Boolean> = _expired.asStateFlow()

    val previews: StateFlow<List<RenamePreview>> = combine(items, siblings, _rule) { i, s, r -> Triple(i, s, r) }
        .debounce(150)
        .mapLatest { (list, sib, rule) -> if (list == null) emptyList() else RenamePlanner.plan(list, rule, sib) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Keep the paths across recomposition and rotation: ViewModel survives, token is one-shot.
        val paths = SelectionHandoff.take(token)
        if (paths.isNullOrEmpty()) _expired.value = true
        else viewModelScope.launch(Dispatchers.IO) {
            val loaded = paths.map { p -> storage.getItem(p) ?: StorageItem(path = p, name = p.substringAfterLast('/')) }
            siblings.value = loaded.map { RenamePlanner.parentOf(it.path) }.distinct()
                .mapNotNull { parent ->
                    val names = if (parent.startsWith("smb://")) {
                        runCatching { storage.getFilesInDirectory(parent, showHidden = true).mapTo(HashSet()) { it.name } }.getOrNull()
                    } else {
                        File(parent).list()?.toSet()
                    }
                    names?.let { parent to it }
                }.toMap()
            items.value = loaded
        }
    }

    fun update(change: (RenameRule) -> RenameRule) = _rule.update(change)

    /** All rows OK or unchanged, and at least one real change. */
    fun canApply(list: List<RenamePreview>): Boolean =
        list.isNotEmpty() && list.all { it.status == RenameStatus.OK || it.status == RenameStatus.UNCHANGED } &&
            list.any { it.status == RenameStatus.OK }

    fun apply() {
        val renames = previews.value.filter { it.status == RenameStatus.OK }.map { RenameEntry(it.item.path, it.newName) }
        if (renames.isNotEmpty()) operationManager.enqueue(FileOperation.BatchRename(renames))
    }
}
