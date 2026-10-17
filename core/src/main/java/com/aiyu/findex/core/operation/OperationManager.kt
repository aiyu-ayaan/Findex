package com.aiyu.findex.core.operation

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aiyu.findex.core.model.ActiveOperation
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.OperationResult
import com.aiyu.findex.core.service.FileOperationForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Queue of long-running file operations. The UI enqueues and observes;
 * [FileOperationForegroundService] drains the queue and reports back here.
 */
@Singleton
class OperationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val queue = ArrayDeque<FileOperation>()

    private val _active = MutableStateFlow<ActiveOperation?>(null)
    val active: StateFlow<ActiveOperation?> = _active.asStateFlow()

    private val _results = MutableSharedFlow<OperationResult>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val results: SharedFlow<OperationResult> = _results.asSharedFlow()

    fun enqueue(operation: FileOperation) {
        synchronized(queue) { queue.addLast(operation) }
        ContextCompat.startForegroundService(
            context,
            Intent(context, FileOperationForegroundService::class.java).setAction(FileOperationForegroundService.ACTION_START)
        )
    }

    fun cancelCurrent() {
        context.startService(
            Intent(context, FileOperationForegroundService::class.java).setAction(FileOperationForegroundService.ACTION_CANCEL)
        )
    }

    internal fun poll(): FileOperation? = synchronized(queue) { queue.removeFirstOrNull() }

    internal fun hasPending(): Boolean = synchronized(queue) { queue.isNotEmpty() }

    internal fun pendingCount(): Int = synchronized(queue) { queue.size }

    internal fun publish(active: ActiveOperation?) { _active.value = active }

    internal fun publish(result: OperationResult) { _results.tryEmit(result) }
}
