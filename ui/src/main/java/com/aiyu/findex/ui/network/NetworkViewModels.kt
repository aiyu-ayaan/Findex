package com.aiyu.findex.ui.network

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.network.NetworkError
import com.aiyu.findex.core.network.NetworkException
import com.aiyu.findex.core.network.NetworkProtocol
import com.aiyu.findex.core.network.NetworkServer
import com.aiyu.findex.core.network.SmbRepository
import com.aiyu.findex.ui.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

fun NetworkError.messageRes(): Int = when (this) {
    NetworkError.HOST_UNREACHABLE -> R.string.network_error_host
    NetworkError.AUTH_FAILED -> R.string.network_error_auth
    NetworkError.SHARE_NOT_FOUND -> R.string.network_error_share
    NetworkError.ACCESS_DENIED -> R.string.network_error_denied
    NetworkError.PASSWORD_REQUIRED -> R.string.network_error_password_required
    NetworkError.UNKNOWN -> R.string.network_error_unknown
}

fun NetworkProtocol.labelRes(): Int = when (this) {
    NetworkProtocol.SMB -> R.string.network_protocol_smb
    NetworkProtocol.SFTP -> R.string.network_protocol_sftp
    NetworkProtocol.FTP -> R.string.network_protocol_ftp
    NetworkProtocol.WEBDAV -> R.string.network_protocol_webdav
    NetworkProtocol.WEBDAVS -> R.string.network_protocol_webdavs
}

private fun Throwable.networkError() = (this as? NetworkException)?.error ?: NetworkError.UNKNOWN

/** [SmbRepository.unlock] as a result: null once connected, otherwise why it failed. */
suspend fun SmbRepository.unlockOrError(id: Long, password: String): NetworkError? = try {
    unlock(id, password)
    null
} catch (e: NetworkException) {
    e.error
}

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

    fun addServer(
        name: String,
        host: String,
        share: String,
        username: String,
        domain: String,
        password: String,
        protocol: NetworkProtocol = NetworkProtocol.SMB,
        port: Int = 0,
        rememberPassword: Boolean = true
    ) {
        if (_add.value.isConnecting) return
        _add.value = AddServerState(isConnecting = true)
        viewModelScope.launch {
            try {
                _added.send(smb.add(name, host, share, username, domain, password, protocol, port, rememberPassword))
                _add.value = AddServerState()
            } catch (e: Exception) {
                _add.value = AddServerState(error = e.networkError())
            }
        }
    }

    fun resetAdd() { _add.value = AddServerState() }

    fun isLocked(server: NetworkServer) = smb.isLocked(server)

    /** @return null once connected, otherwise why it failed. */
    suspend fun unlock(id: Long, password: String): NetworkError? = smb.unlockOrError(id, password)

    fun remove(id: Long) {
        viewModelScope.launch { smb.delete(id) }
    }
}
