package com.aiyu.findex.ui.network

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.network.NetworkError
import com.aiyu.findex.core.network.NetworkException
import com.aiyu.findex.core.network.NetworkServer
import com.aiyu.findex.core.network.SmbPath
import com.aiyu.findex.core.network.SmbRepository
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.navigation.NetworkBrowseRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

fun NetworkError.messageRes(): Int = when (this) {
    NetworkError.HOST_UNREACHABLE -> R.string.network_error_host
    NetworkError.AUTH_FAILED -> R.string.network_error_auth
    NetworkError.SHARE_NOT_FOUND -> R.string.network_error_share
    NetworkError.ACCESS_DENIED -> R.string.network_error_denied
    NetworkError.UNKNOWN -> R.string.network_error_unknown
}

private fun Throwable.networkError() = (this as? NetworkException)?.error ?: NetworkError.UNKNOWN

data class AddServerState(val isConnecting: Boolean = false, val error: NetworkError? = null)

@HiltViewModel
class NetworkViewModel @Inject constructor(
    private val smb: SmbRepository
) : ViewModel() {

    val servers: StateFlow<List<NetworkServer>?> = smb.servers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _add = MutableStateFlow(AddServerState())
    val add: StateFlow<AddServerState> = _add.asStateFlow()

    private val _added = Channel<NetworkServer>(Channel.BUFFERED)
    /** Emits once a server connected and was saved, so the sheet can close. */
    val added = _added.receiveAsFlow()

    fun addServer(name: String, host: String, share: String, username: String, domain: String, password: String) {
        if (_add.value.isConnecting) return
        _add.value = AddServerState(isConnecting = true)
        viewModelScope.launch {
            try {
                _added.send(smb.add(name, host, share, username, domain, password))
                _add.value = AddServerState()
            } catch (e: Exception) {
                _add.value = AddServerState(error = e.networkError())
            }
        }
    }

    fun resetAdd() { _add.value = AddServerState() }

    fun remove(id: Long) {
        viewModelScope.launch { smb.delete(id) }
    }
}

/** What to do with a remote file once it is in the cache. */
enum class RemoteIntent { OPEN, SHARE }

data class NetworkBrowseState(
    val server: NetworkServer? = null,
    /** Folder inside the share; "" is the share root. */
    val dir: String = "",
    val items: List<StorageItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: NetworkError? = null,
    val downloading: String? = null
)

@HiltViewModel
class NetworkBrowseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val smb: SmbRepository
) : ViewModel() {

    private val serverId = savedStateHandle.toRoute<NetworkBrowseRoute>().serverId
    private val _uiState = MutableStateFlow(NetworkBrowseState())
    val uiState: StateFlow<NetworkBrowseState> = _uiState.asStateFlow()

    private val _ready = Channel<Pair<File, RemoteIntent>>(Channel.BUFFERED)
    val ready = _ready.receiveAsFlow()

    private val _errors = Channel<NetworkError>(Channel.BUFFERED)
    val errors = _errors.receiveAsFlow()

    private var loadJob: Job? = null
    private var downloadJob: Job? = null

    init {
        viewModelScope.launch { _uiState.update { it.copy(server = smb.server(serverId)) } }
        load("")
    }

    fun load(dir: String = _uiState.value.dir) {
        loadJob?.cancel()
        _uiState.update { it.copy(dir = dir, isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            try {
                val items = smb.list(serverId, dir)
                _uiState.update { it.copy(items = items, isLoading = false) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(items = emptyList(), isLoading = false, error = e.networkError()) }
            }
        }
    }

    /** @return false at the share root (the screen should pop). */
    fun up(): Boolean {
        val dir = _uiState.value.dir
        if (dir.isEmpty()) return false
        load(dir.substringBeforeLast('/', ""))
        return true
    }

    fun open(item: StorageItem, intent: RemoteIntent = RemoteIntent.OPEN) {
        val inner = SmbPath.parse(item.path)?.second ?: return
        if (item.isDirectory) {
            load(inner)
            return
        }
        if (downloadJob?.isActive == true) return
        _uiState.update { it.copy(downloading = item.name) }
        downloadJob = viewModelScope.launch {
            try {
                _ready.send(smb.download(serverId, inner, item.size) to intent)
            } catch (e: Exception) {
                if (e !is CancellationException) _errors.send(e.networkError())
            } finally {
                _uiState.update { it.copy(downloading = null) }
            }
        }
    }

    fun cancelDownload() = downloadJob?.cancel()
}
