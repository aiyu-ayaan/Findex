package com.aiyu.findex.ui.explorer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.model.ArchiveFormat
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.operation.FileClipboard
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.core.operation.uniqueTarget
import com.aiyu.findex.core.repository.StorageRepository
import com.aiyu.findex.core.repository.TagRepository
import com.aiyu.findex.ui.LocalSnackbarHostState
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.isImageExtension
import com.aiyu.findex.ui.navigation.BatchRenameRoute
import com.aiyu.findex.ui.navigation.ImageCompressRoute
import com.aiyu.findex.ui.navigation.SelectionHandoff
import com.aiyu.findex.ui.navigation.VaultRoute
import com.aiyu.findex.ui.tags.TagPickerSheet
import com.aiyu.findex.ui.util.shareFiles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Selection actions for screens that list files from many folders (categories, search). Explorer keeps its own. */
@HiltViewModel
class SelectionActionsViewModel @Inject constructor(
    private val clipboard: FileClipboard,
    private val operationManager: OperationManager,
    private val storageRepository: StorageRepository,
    private val tagRepository: TagRepository
) : ViewModel() {
    val allTags = tagRepository.tags().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _messages = Channel<Int>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun copy(paths: List<String>) { clipboard.copy(paths); _messages.trySend(R.string.clipboard_copied_hint) }
    fun cut(paths: List<String>) { clipboard.cut(paths); _messages.trySend(R.string.clipboard_cut_hint) }

    fun delete(paths: List<String>, permanent: Boolean) =
        operationManager.enqueue(if (permanent) FileOperation.Delete(paths) else FileOperation.Trash(paths))

    /** The items can sit in many folders; the archive goes next to the first one. */
    fun compress(paths: List<String>, archiveName: String, password: String?, format: ArchiveFormat) {
        val dir = File(paths.firstOrNull() ?: return).parentFile ?: return
        val name = archiveName.trim().removeSuffix(".${format.extension}").ifEmpty { return }
        operationManager.enqueue(FileOperation.Compress(paths, uniqueTarget(dir, "$name.${format.extension}").absolutePath, password, format))
    }

    fun extract(archivePath: String, password: String?) {
        val archive = File(archivePath)
        val dest = uniqueTarget(archive.parentFile ?: return, ArchiveFormat.baseName(archive.name))
        operationManager.enqueue(FileOperation.Extract(archivePath, dest.absolutePath, password))
    }

    fun rename(path: String, newName: String, onDone: () -> Unit) {
        viewModelScope.launch {
            if (storageRepository.rename(path, newName) == null) _messages.send(R.string.error_rename)
            onDone()
        }
    }

    suspend fun commonTagIds(paths: List<String>): Set<Long> =
        paths.map { tagRepository.tagIdsFor(it) }.reduceOrNull { a, b -> a intersect b } ?: emptySet()

    fun applyTags(paths: List<String>, initial: Set<Long>, checked: Set<Long>) {
        viewModelScope.launch { tagRepository.apply(paths, add = checked - initial, remove = initial - checked) }
    }

    fun createTag(name: String, colorIndex: Int) {
        viewModelScope.launch { tagRepository.create(name, colorIndex) }
    }
}

private sealed interface SelectionModal {
    data class Compress(val paths: List<String>, val defaultName: String) : SelectionModal
    data class Extract(val item: StorageItem) : SelectionModal
    data class Rename(val item: StorageItem) : SelectionModal
    data class Delete(val paths: List<String>) : SelectionModal
    data class Details(val item: StorageItem) : SelectionModal
    data class Tags(val paths: List<String>) : SelectionModal
    data class SendToComputer(val paths: List<String>) : SelectionModal
}

/**
 * Explorer's floating selection toolbar plus the dialogs behind it, for local files gathered from many folders.
 * [onRoute] navigates to a feature route (batch rename, vault, image compress); [onChanged] reloads after a rename.
 */
@Composable
fun SelectionActions(
    selected: List<StorageItem>,
    onClear: () -> Unit,
    onRoute: (Any) -> Unit,
    onOpenArchive: (String) -> Unit,
    /** "Open in new tab" on a single folder: the Explorer opens (or focuses) a tab there. */
    onOpenFolder: (String) -> Unit,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SelectionActionsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var modal by remember { mutableStateOf<SelectionModal?>(null) }
    val paths = selected.map { it.path }
    val single = selected.singleOrNull()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { scope.launch { snackbar.showSnackbar(context.getString(it)) } }
    }

    SelectionToolbar(
        visible = selected.isNotEmpty(),
        singleSelected = single,
        onCopy = { viewModel.copy(paths); onClear() },
        onCut = { viewModel.cut(paths); onClear() },
        onDelete = { modal = SelectionModal.Delete(paths) },
        onCompress = {
            val name = single?.let { if (it.isDirectory) it.name else it.name.substringBeforeLast('.') } ?: "archive"
            modal = SelectionModal.Compress(paths, name.ifEmpty { "archive" })
        },
        onShare = { context.shareFiles(selected.filter { !it.isDirectory }.map { it.path }) },
        onRename = { single?.let { modal = SelectionModal.Rename(it) } },
        onDetails = { single?.let { modal = SelectionModal.Details(it) } },
        onOpenInNewTab = { single?.let { onClear(); onOpenFolder(it.path) } },
        onExtract = { single?.let { onClear(); modal = SelectionModal.Extract(it) } },
        onBrowse = { single?.let { onClear(); onOpenArchive(it.path) } },
        selectionCount = selected.size,
        canShare = selected.any { !it.isDirectory },
        allImages = selected.isNotEmpty() && selected.all { !it.isDirectory && isImageExtension(it.extension) },
        onFeature = { action ->
            if (action == ItemAction.TAGS) { modal = SelectionModal.Tags(paths); return@SelectionToolbar }
            if (action == ItemAction.SEND_TO_COMPUTER) { modal = SelectionModal.SendToComputer(paths); onClear(); return@SelectionToolbar }
            val token = SelectionHandoff.put(paths)
            onClear()
            when (action) {
                ItemAction.BATCH_RENAME -> onRoute(BatchRenameRoute(token))
                ItemAction.MOVE_TO_VAULT -> onRoute(VaultRoute(token))
                ItemAction.COMPRESS_IMAGE -> onRoute(ImageCompressRoute(token))
                else -> Unit
            }
        },
        modifier = modifier
    )

    when (val m = modal) {
        null -> Unit
        is SelectionModal.Compress -> CompressDialog(
            defaultName = m.defaultName,
            onConfirm = { name, password, format -> viewModel.compress(m.paths, name, password, format); onClear(); modal = null },
            onDismiss = { modal = null }
        )
        is SelectionModal.Extract -> ExtractDialog(
            archiveName = m.item.name,
            onConfirm = { password -> viewModel.extract(m.item.path, password); modal = null },
            onDismiss = { modal = null }
        )
        is SelectionModal.Rename -> NameDialog(
            title = stringResource(R.string.action_rename),
            initial = m.item.name,
            confirmLabel = stringResource(R.string.action_rename),
            onConfirm = { viewModel.rename(m.item.path, it) { onClear(); onChanged() }; modal = null },
            onDismiss = { modal = null }
        )
        is SelectionModal.Delete -> DeleteDialog(
            count = m.paths.size,
            onConfirm = { permanent -> viewModel.delete(m.paths, permanent); onClear(); modal = null },
            onDismiss = { modal = null }
        )
        is SelectionModal.Details -> DetailsSheet(m.item, load = ::loadFileDetails, onDismiss = { modal = null })
        is SelectionModal.SendToComputer -> com.aiyu.findex.ui.link.SendToComputerSheet(m.paths, onDone = { modal = null })
        is SelectionModal.Tags -> {
            val allTags by viewModel.allTags.collectAsStateWithLifecycle()
            val initial by produceState<Set<Long>?>(null, m.paths) { value = viewModel.commonTagIds(m.paths) }
            initial?.let { start ->
                TagPickerSheet(
                    tags = allTags,
                    initial = start,
                    onApply = { viewModel.applyTags(m.paths, start, it); onClear(); modal = null },
                    onCreate = viewModel::createTag,
                    onDismiss = { modal = null }
                )
            }
        }
    }
}
