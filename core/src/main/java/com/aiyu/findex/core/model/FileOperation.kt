package com.aiyu.findex.core.model

import java.util.UUID

sealed interface FileOperation {
    val id: String

    data class Copy(
        val sources: List<String>,
        val destinationDir: String,
        override val id: String = UUID.randomUUID().toString()
    ) : FileOperation

    data class Move(
        val sources: List<String>,
        val destinationDir: String,
        override val id: String = UUID.randomUUID().toString()
    ) : FileOperation

    data class Delete(
        val targets: List<String>,
        override val id: String = UUID.randomUUID().toString()
    ) : FileOperation

    data class Compress(
        val targets: List<String>,
        val destinationArchive: String,
        val password: String? = null,
        override val id: String = UUID.randomUUID().toString()
    ) : FileOperation

    data class Extract(
        val archivePath: String,
        val destinationDir: String,
        val password: String? = null,
        override val id: String = UUID.randomUUID().toString()
    ) : FileOperation

    data class Clean(
        val targets: List<String>,
        override val id: String = UUID.randomUUID().toString()
    ) : FileOperation
}

data class OperationProgress(
    val operationId: String,
    val currentItemName: String = "",
    val processedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val processedItems: Int = 0,
    val totalItems: Int = 0,
    val isIndeterminate: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes > 0) (processedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        else if (totalItems > 0) (processedItems.toFloat() / totalItems.toFloat()).coerceIn(0f, 1f)
        else 0f
}

/** The operation currently running in the foreground service plus its live progress. */
data class ActiveOperation(val operation: FileOperation, val progress: OperationProgress)

sealed interface OperationResult {
    val operation: FileOperation

    data class Success(override val operation: FileOperation) : OperationResult
    data class Cancelled(override val operation: FileOperation) : OperationResult
    data class Failed(override val operation: FileOperation, val reason: FailureReason) : OperationResult
}

enum class FailureReason { WRONG_PASSWORD, NO_SPACE, INTO_ITSELF, ACCESS_DENIED, UNKNOWN }

class FileOperationException(val reason: FailureReason, cause: Throwable? = null) :
    Exception(reason.name, cause)
