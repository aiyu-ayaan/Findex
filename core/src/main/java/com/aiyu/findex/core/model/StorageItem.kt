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
) {
    val isHidden: Boolean get() = name.startsWith(".")
    val isArchive: Boolean get() = extension == "zip"
}

enum class SortBy { NAME, DATE, SIZE, TYPE }

data class SortSpec(val by: SortBy = SortBy.NAME, val ascending: Boolean = true)

/** Folders first, then by [SortSpec]. */
fun List<StorageItem>.sortedBy(spec: SortSpec): List<StorageItem> {
    val byField: Comparator<StorageItem> = when (spec.by) {
        SortBy.NAME -> compareBy { it.name.lowercase() }
        SortBy.DATE -> compareBy { it.lastModified }
        SortBy.SIZE -> compareBy<StorageItem> { it.size }.thenBy { it.itemCount }
        SortBy.TYPE -> compareBy<StorageItem> { it.extension }.thenBy { it.name.lowercase() }
    }
    val ordered = if (spec.ascending) byField else byField.reversed()
    return sortedWith(compareByDescending<StorageItem> { it.isDirectory }.then(ordered))
}

data class CategoryStat(val count: Int = 0, val bytes: Long = 0L)
