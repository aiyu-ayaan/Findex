package com.aiyu.findex.ui.home

import com.aiyu.findex.core.model.ActiveOperation
import com.aiyu.findex.core.model.Bookmark
import com.aiyu.findex.core.model.CategoryStat
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.RecentFile
import com.aiyu.findex.core.model.StorageDrive
import com.aiyu.findex.core.network.NetworkServer
import com.aiyu.findex.core.network.NetworkStatus

data class HomeUiState(
    val drives: List<StorageDrive> = emptyList(),
    /** Saved SMB shares; the Network tab only shows when there is at least one. */
    val networkDrives: List<NetworkServer> = emptyList(),
    /** Last probe per server id; missing means not probed yet. */
    val networkStatus: Map<Long, NetworkStatus> = emptyMap(),
    val categoryStats: Map<FileCategory, CategoryStat> = emptyMap(),
    val bookmarks: List<Bookmark> = emptyList(),
    val recents: List<RecentFile> = emptyList(),
    val activeOperation: ActiveOperation? = null,
    val selectedTabIndex: Int = 0,
) {
    val hasNetworkDrives: Boolean get() = networkDrives.isNotEmpty()
}
