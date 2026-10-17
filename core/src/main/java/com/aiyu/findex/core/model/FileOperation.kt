package com.aiyu.findex.core.model

sealed interface FileOperation {
    val id: String

    data class Copy(
        override val id: String,
        val sources: List<String>,
        val destinationDir: String
    ) : FileOperation

    data class Move(
        override val id: String,
        val sources: List<String>,
        val destinationDir: String
    ) : FileOperation

    data class Delete(
        override val id: String,
        val targets: List<String>
    ) : FileOperation

    data class Compress(
        override val id: String,
        val targets: List<String>,
        val destinationArchive: String,
        val format: String = "zip",
        val password: String? = null
    ) : FileOperation

    data class Extract(
        override val id: String,
        val archivePath: String,
        val destinationDir: String,
        val password: String? = null
    ) : FileOperation

    data class Clean(
        override val id: String,
        val targets: List<String>,
        val cleanerType: String
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
