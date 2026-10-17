package com.aiyu.findex.ui.home

import com.aiyu.findex.core.model.StorageDrive

data class HomeUiState(
    val drives: List<StorageDrive> = emptyList(),
    val networkDrives: List<StorageDrive> = emptyList(),
    val selectedTabIndex: Int = 0,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
) {
    val hasNetworkDrives: Boolean
        get() = networkDrives.isNotEmpty()
}
