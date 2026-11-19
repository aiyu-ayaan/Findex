package com.aiyu.findex.core.operation

import com.aiyu.findex.core.db.ClipboardDao
import com.aiyu.findex.core.db.ClipboardEntity
import com.aiyu.findex.core.model.ClipboardContent
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.network.SmbPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** App-wide cut/copy buffer, shared by every explorer tab. Saved in Room so it outlives the process. */
@Singleton
class FileClipboard @Inject constructor(
    private val operationManager: OperationManager,
    private val dao: ClipboardDao
) {
    private val _content = MutableStateFlow<ClipboardContent?>(null)
    val content: StateFlow<ClipboardContent?> = _content.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            // Paths deleted while the app was gone are dropped; network paths can't be checked offline, so they stay.
            val saved = runCatching { dao.all() }.getOrDefault(emptyList())
                .filter { SmbPath.parse(it.path) != null || File(it.path).exists() }
            // Something copied before the restore finished wins over the saved clip.
            if (saved.isNotEmpty()) _content.compareAndSet(null, ClipboardContent(saved.map { it.path }, saved.first().isCut))
            _content.collect { clip ->
                // Shared imports are content:// URIs whose read grant dies with the activity; never persist them.
                val rows = if (clip == null || clip.isImport) emptyList()
                else clip.paths.mapIndexed { i, p -> ClipboardEntity(i, p, clip.isCut) }
                runCatching { dao.replaceAll(rows) }
            }
        }
    }

    fun copy(paths: List<String>) { _content.value = ClipboardContent(paths, isCut = false) }

    fun cut(paths: List<String>) { _content.value = ClipboardContent(paths, isCut = true) }

    /** Files shared into Findex: the user browses to a folder and pastes ("Save here"). */
    fun import(uris: List<String>) { _content.value = ClipboardContent(uris, isCut = false, isImport = true) }

    fun clear() { _content.value = null }

    fun paste(destinationDir: String) {
        val clip = _content.value ?: return
        operationManager.enqueue(
            if (clip.isCut) FileOperation.Move(clip.paths, destinationDir)
            else FileOperation.Copy(clip.paths, destinationDir)
        )
        // A cut or a shared import can only be pasted once; a copy stays available for more pastes.
        if (clip.isCut || clip.isImport) clear()
    }
}
