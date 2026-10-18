package com.aiyu.findex.ui.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.model.CategoryStat
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.OperationResult
import com.aiyu.findex.core.model.StorageDrive
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.core.repository.StorageRepository
import com.aiyu.findex.core.scanner.DuplicateSet
import com.aiyu.findex.core.scanner.JunkType
import com.aiyu.findex.core.scanner.ScanGroup
import com.aiyu.findex.core.scanner.StorageScanner
import com.aiyu.findex.core.scanner.WhatsAppMediaType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CleanUiState<T>(
    val isScanning: Boolean = true,
    val groups: List<ScanGroup<T>> = emptyList(),
    val selected: Set<T> = emptySet()
) {
    val selectedBytes: Long get() = groups.filter { it.type in selected }.sumOf { it.bytes }
    val selectedPaths: List<String> get() = groups.filter { it.type in selected }.flatMap { g -> g.files.map { it.path } }
}

/** Scan → pick groups → clean through the foreground service → rescan. */
abstract class CleanViewModel<T>(
    private val operationManager: OperationManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(CleanUiState<T>())
    val uiState: StateFlow<CleanUiState<T>> = _uiState.asStateFlow()

    protected abstract suspend fun scan(): List<ScanGroup<T>>
    protected open fun defaultSelected(groups: List<ScanGroup<T>>): Set<T> = groups.map { it.type }.toSet()

    init {
        viewModelScope.launch { operationManager.results.collect { if (it is OperationResult.Success && it.operation is FileOperation.Clean) rescan() } }
    }

    fun rescan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val groups = scan()
            _uiState.update { CleanUiState(isScanning = false, groups = groups, selected = defaultSelected(groups)) }
        }
    }

    fun toggle(type: T) = _uiState.update { s ->
        s.copy(selected = if (type in s.selected) s.selected - type else s.selected + type)
    }

    fun clean() {
        val paths = _uiState.value.selectedPaths
        if (paths.isNotEmpty()) operationManager.enqueue(FileOperation.Clean(paths))
    }
}

@HiltViewModel
class CleanerViewModel @Inject constructor(
    private val scanner: StorageScanner,
    operationManager: OperationManager
) : CleanViewModel<JunkType>(operationManager) {
    init { rescan() }
    override suspend fun scan() = scanner.scanJunk()
    // APKs might still be wanted; let the user opt in.
    override fun defaultSelected(groups: List<ScanGroup<JunkType>>) = groups.map { it.type }.toSet() - JunkType.APK_FILES
}

@HiltViewModel
class WhatsAppViewModel @Inject constructor(
    private val scanner: StorageScanner,
    operationManager: OperationManager
) : CleanViewModel<WhatsAppMediaType>(operationManager) {
    init { rescan() }
    override suspend fun scan() = scanner.scanWhatsApp()
    // Personal media is opt-in; nothing is pre-selected.
    override fun defaultSelected(groups: List<ScanGroup<WhatsAppMediaType>>) = emptySet<WhatsAppMediaType>()
}

data class AnalyzerUiState(
    val drive: StorageDrive? = null,
    val stats: Map<FileCategory, CategoryStat> = emptyMap(),
    val largest: List<StorageItem> = emptyList(),
    val isScanning: Boolean = true
) {
    val categorizedBytes: Long get() = stats.values.sumOf { it.bytes }
    val otherBytes: Long get() = ((drive?.usedBytes ?: 0L) - categorizedBytes).coerceAtLeast(0L)
}

@HiltViewModel
class AnalyzerViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    private val scanner: StorageScanner
) : ViewModel() {
    private val _uiState = MutableStateFlow(AnalyzerUiState())
    val uiState: StateFlow<AnalyzerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val drive = storageRepository.getStorageDrives().first().firstOrNull()
            val stats = storageRepository.getCategoryStats()
            _uiState.update { it.copy(drive = drive, stats = stats) }
            val largest = scanner.largestFiles()
            _uiState.update { it.copy(largest = largest, isScanning = false) }
        }
    }
}

data class DuplicatesUiState(
    val isScanning: Boolean = true,
    val sets: List<DuplicateSet> = emptyList(),
    /** Paths marked for deletion. */
    val selected: Set<String> = emptySet()
) {
    val selectedBytes: Long get() = sets.sumOf { set -> set.files.filter { it.path in selected }.sumOf { it.size } }
    val wastedBytes: Long get() = sets.sumOf { it.wastedBytes }
}

@HiltViewModel
class DuplicatesViewModel @Inject constructor(
    private val scanner: StorageScanner,
    private val operationManager: OperationManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(DuplicatesUiState())
    val uiState: StateFlow<DuplicatesUiState> = _uiState.asStateFlow()
    private var scanJob: Job? = null

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
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val sets = scanner.findDuplicates()
            // Keep the oldest copy (first, sets are sorted by date); mark the rest.
            val selected = sets.flatMapTo(HashSet()) { set -> set.files.drop(1).map { it.path } }
            _uiState.update { DuplicatesUiState(isScanning = false, sets = sets, selected = selected) }
        }
    }

    /** Never lets every copy of a set be marked: one always survives. */
    fun toggle(set: DuplicateSet, path: String) = _uiState.update { s ->
        when {
            path in s.selected -> s.copy(selected = s.selected - path)
            set.files.count { it.path !in s.selected } <= 1 -> s
            else -> s.copy(selected = s.selected + path)
        }
    }

    fun delete(permanent: Boolean) {
        val paths = _uiState.value.selected.toList()
        if (paths.isNotEmpty()) operationManager.enqueue(if (permanent) FileOperation.Delete(paths) else FileOperation.Trash(paths))
    }
}
