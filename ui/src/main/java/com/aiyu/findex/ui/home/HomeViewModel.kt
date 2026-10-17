package com.aiyu.findex.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.model.DriveType
import com.aiyu.findex.core.repository.StorageRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val storageRepository: StorageRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState(isLoading = true))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadStorageDrives()
    }

    fun loadStorageDrives() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            storageRepository.getStorageDrives()
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage
                        )
                    }
                }
                .collect { allDrives ->
                    val localDrives = allDrives.filter { it.type != DriveType.NETWORK }
                    val networkDrives = allDrives.filter { it.type == DriveType.NETWORK }
                    _uiState.update {
                        it.copy(
                            drives = localDrives,
                            networkDrives = networkDrives,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTabIndex = index) }
    }
}
