package com.aiyu.findex.ui.archive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aiyu.findex.core.model.ArchiveEntry
import com.aiyu.findex.core.model.ArchiveFormat
import com.aiyu.findex.core.model.ArchivePath
import com.aiyu.findex.core.model.FailureReason
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.FileOperationException
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.operation.ArchiveBrowser
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.core.operation.uniqueTarget
import com.aiyu.findex.ui.navigation.ArchiveRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** What to do with an entry once it is in the preview cache. */
enum class EntryIntent { OPEN, SHARE }

data class ArchiveUiState(
    val archivePath: String,
    /** Inner folder being shown; "" is the archive root. */
    val dir: String = "",
    val entries: List<ArchiveEntry> = emptyList(),
    val isLoading: Boolean = true,
    val unreadable: Boolean = false,
    /** Entry currently being pulled out for viewing. */
    val opening: String? = null,
    /** Entry waiting for a password before it can be opened. */
    val passwordFor: Pair<String, EntryIntent>? = null
) {
    val name: String get() = File(archivePath).name

    /** Children of [dir], folders first, as [StorageItem]s with virtual `archive!/entry` paths. */
    val items: List<StorageItem>
        get() {
            val childCounts = entries.groupingBy { it.path.substringBeforeLast('/', "") }.eachCount()
            // Implied folders and some formats carry no timestamp; show the archive's date instead of 1970.
            val archiveModified = File(archivePath).lastModified()
            return entries.filter { it.path.substringBeforeLast('/', "") == dir }
            .sortedWith(compareByDescending<ArchiveEntry> { it.isDirectory }.thenBy { it.path.lowercase() })
            .map { e ->
                val name = e.path.substringAfterLast('/')
                StorageItem(
                    path = ArchivePath.root(archivePath) + "/" + e.path,
                    name = name,
                    size = e.size,
                    lastModified = e.modified.takeIf { it > 0 } ?: archiveModified,
                    isDirectory = e.isDirectory,
                    extension = if (e.isDirectory) "" else name.substringAfterLast('.', "").lowercase(),
                    itemCount = childCounts[e.path] ?: 0
                )
            }
        }
}

@HiltViewModel
class ArchiveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val browser: ArchiveBrowser,
    private val operationManager: OperationManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArchiveUiState(savedStateHandle.toRoute<ArchiveRoute>().path))
    val uiState: StateFlow<ArchiveUiState> = _uiState.asStateFlow()

    private val _ready = Channel<Pair<File, EntryIntent>>(Channel.BUFFERED)
    /** Entries extracted to the cache, ready to open or share. */
    val ready = _ready.receiveAsFlow()

    private val _errors = Channel<FailureReason>(Channel.BUFFERED)
    val errors = _errors.receiveAsFlow()

    /** Remembered after the first correct password so every entry doesn't ask again. */
    private var password: String? = null

    init {
        viewModelScope.launch {
            val entries = try {
                browser.list(_uiState.value.archivePath)
            } catch (_: Exception) {
                null
            }
            _uiState.update { it.copy(entries = entries.orEmpty(), isLoading = false, unreadable = entries == null) }
        }
    }

    fun openDir(innerPath: String) = _uiState.update { it.copy(dir = innerPath) }

    /** @return false at the archive root (the screen should pop). */
    fun up(): Boolean {
        val dir = _uiState.value.dir
        if (dir.isEmpty()) return false
        _uiState.update { it.copy(dir = dir.substringBeforeLast('/', "")) }
        return true
    }

    fun open(item: StorageItem, intent: EntryIntent = EntryIntent.OPEN) {
        val inner = ArchivePath.parse(item.path)?.second ?: return
        if (item.isDirectory) openDir(inner) else extract(inner, intent, password)
    }

    fun submitPassword(value: String) {
        val (entry, intent) = _uiState.value.passwordFor ?: return
        _uiState.update { it.copy(passwordFor = null) }
        extract(entry, intent, value)
    }

    fun dismissPassword() = _uiState.update { it.copy(passwordFor = null) }

    private fun extract(entry: String, intent: EntryIntent, pass: String?) {
        if (_uiState.value.opening != null) return
        _uiState.update { it.copy(opening = entry.substringAfterLast('/')) }
        viewModelScope.launch {
            try {
                val file = browser.extractToCache(_uiState.value.archivePath, entry, pass)
                if (pass != null) password = pass
                _ready.send(file to intent)
            } catch (e: FileOperationException) {
                if (e.reason == FailureReason.WRONG_PASSWORD && ArchiveFormat.of(_uiState.value.name)?.passwordOnRead == true) {
                    _uiState.update { it.copy(passwordFor = entry to intent) }
                } else {
                    _errors.send(e.reason)
                }
            } catch (_: Exception) {
                _errors.send(FailureReason.UNKNOWN)
            } finally {
                _uiState.update { it.copy(opening = null) }
            }
        }
    }

    /** Full extraction still runs through the foreground service, like the Explorer's Extract. */
    fun extractAll(password: String?) {
        val archive = File(_uiState.value.archivePath)
        val dest = uniqueTarget(archive.parentFile ?: return, ArchiveFormat.baseName(archive.name))
        operationManager.enqueue(FileOperation.Extract(archive.absolutePath, dest.absolutePath, password ?: this.password))
    }
}
