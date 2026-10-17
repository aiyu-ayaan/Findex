package com.aiyu.findex.ui.home

import com.aiyu.findex.core.model.ActiveOperation
import com.aiyu.findex.core.model.Bookmark
import com.aiyu.findex.core.model.CategoryStat
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.RecentFile
import com.aiyu.findex.core.model.StorageDrive

data class HomeUiState(
    val drives: List<StorageDrive> = emptyList(),
    val networkDrives: List<StorageDrive> = emptyList(),
    val categoryStats: Map<FileCategory, CategoryStat> = emptyMap(),
    val bookmarks: List<Bookmark> = emptyList(),
    val recents: List<RecentFile> = emptyList(),
    val activeOperation: ActiveOperation? = null,
    val selectedTabIndex: Int = 0,
) {
    val hasNetworkDrives: Boolean get() = networkDrives.isNotEmpty()
}
