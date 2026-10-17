package com.aiyu.findex.core.model

enum class DriveType {
    INTERNAL,
    SD_CARD,
    NETWORK,
    USB
}

data class StorageDrive(
    val id: String,
    val name: String,
    val path: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val type: DriveType,
    val isPrimary: Boolean = false,
    val isRemovable: Boolean = false
) {
    val usedBytes: Long
        get() = (totalBytes - freeBytes).coerceAtLeast(0L)

    val usedPercentage: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}
