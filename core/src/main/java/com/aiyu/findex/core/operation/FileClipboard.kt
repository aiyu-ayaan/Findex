package com.aiyu.findex.core.operation

import com.aiyu.findex.core.model.ClipboardContent
import com.aiyu.findex.core.model.FileOperation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** App-wide cut/copy buffer, shared by every explorer tab. */
@Singleton
class FileClipboard @Inject constructor(
    private val operationManager: OperationManager
) {
    private val _content = MutableStateFlow<ClipboardContent?>(null)
    val content: StateFlow<ClipboardContent?> = _content.asStateFlow()

    fun copy(paths: List<String>) { _content.value = ClipboardContent(paths, isCut = false) }

    fun cut(paths: List<String>) { _content.value = ClipboardContent(paths, isCut = true) }

    fun clear() { _content.value = null }

    fun paste(destinationDir: String) {
        val clip = _content.value ?: return
        operationManager.enqueue(
            if (clip.isCut) FileOperation.Move(clip.paths, destinationDir)
            else FileOperation.Copy(clip.paths, destinationDir)
        )
        // A cut can only be pasted once; a copy stays available for more pastes.
        if (clip.isCut) clear()
    }
}
