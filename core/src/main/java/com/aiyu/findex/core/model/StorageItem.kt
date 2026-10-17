package com.aiyu.findex.core.model

enum class FileCategory {
    PHOTOS,
    VIDEOS,
    AUDIO,
    DOCUMENTS,
    DOWNLOADS,
    ARCHIVES,
    APKS
}

data class StorageItem(
    val path: String,
    val name: String,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val isDirectory: Boolean = false,
    val extension: String = "",
    val mimeType: String? = null,
    val itemCount: Int = 0
)
