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

    /** Moves targets into the recycle bin; restorable until purged. */
    data class Trash(
        val targets: List<String>,
        override val id: String = UUID.randomUUID().toString()
    ) : FileOperation

    /** Moves recycle-bin entries (by row id) back to where they came from. */
    data class Restore(
        val ids: List<Long>,
        override val id: String = UUID.randomUUID().toString()
    ) : FileOperation

    data class Compress(
        val targets: List<String>,
        val destinationArchive: String,
        val password: String? = null,
        val format: ArchiveFormat = ArchiveFormat.ZIP,
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

    /** Renames inside one or more folders. [renames] is the planner output; names only, never paths. */
    data class BatchRename(
        val renames: List<RenameEntry>,
        override val id: String = UUID.randomUUID().toString()
    ) : FileOperation

    data class ImageTransform(
        val sources: List<String>,
        val spec: ImageTransformSpec,
        override val id: String = UUID.randomUUID().toString()
    ) : FileOperation

    /** Encrypts [sources] into the vault and deletes the originals permanently (never to the bin). */
    data class VaultImport(
        val sources: List<String>,
        override val id: String = UUID.randomUUID().toString()
    ) : FileOperation

    /** Decrypts vault rows into [destinationDir]; [removeFromVault] deletes the blobs after success. */
    data class VaultExport(
        val itemIds: List<Long>,
        val destinationDir: String,
        val removeFromVault: Boolean,
        override val id: String = UUID.randomUUID().toString()
    ) : FileOperation

    data class VaultDelete(
        val itemIds: List<Long>,
        override val id: String = UUID.randomUUID().toString()
    ) : FileOperation
}

data class RenameEntry(val path: String, val newName: String)

enum class ImageOutputFormat { KEEP, JPEG, WEBP, PNG }

enum class ImageOutputMode { COPY_WITH_SUFFIX, REPLACE, INTO_FOLDER }

data class ImageTransformSpec(
    /** null = keep size; mutually exclusive with [scalePercent]. */
    val maxLongEdgePx: Int? = null,
    /** 10..100 */
    val scalePercent: Int? = null,
    /** 1..100, ignored for PNG. */
    val quality: Int = 82,
    val format: ImageOutputFormat = ImageOutputFormat.KEEP,
    val output: ImageOutputMode = ImageOutputMode.COPY_WITH_SUFFIX,
    /** Required for [ImageOutputMode.INTO_FOLDER]. */
    val outputDir: String? = null,
    /** false strips GPS and camera tags. */
    val keepMetadata: Boolean = false
)

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

    data class Success(override val operation: FileOperation, val skipped: Int = 0) : OperationResult
    data class Cancelled(override val operation: FileOperation) : OperationResult
    data class Failed(override val operation: FileOperation, val reason: FailureReason) : OperationResult
}

enum class FailureReason { WRONG_PASSWORD, NO_SPACE, INTO_ITSELF, ACCESS_DENIED, UNSUPPORTED_ARCHIVE, NETWORK, NOT_SUPPORTED, UNKNOWN, VAULT_LOCKED, NAME_CONFLICT, INVALID_NAME, UNSUPPORTED_IMAGE, NESTED_SELECTION }

class FileOperationException(val reason: FailureReason, cause: Throwable? = null) :
    Exception(reason.name, cause)
