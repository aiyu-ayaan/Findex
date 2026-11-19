package com.aiyu.findex.ui.link

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.link.LinkPairException
import com.aiyu.findex.core.link.LinkPeer
import com.aiyu.findex.core.link.LinkRepository
import com.aiyu.findex.core.link.LinkServerState
import com.aiyu.findex.core.link.NearbyDevice
import com.aiyu.findex.core.link.PairRequest
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.network.SmbPath
import com.aiyu.findex.core.operation.OperationManager
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.components.groupedShape
import com.aiyu.findex.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where a pairing this phone started stands. */
sealed interface PairUi {
    data object Idle : PairUi
    data object Address : PairUi
    data object Connecting : PairUi
    data class Code(val name: String, val code: String) : PairUi
    data class Waiting(val name: String) : PairUi
    data class Failed(val reason: LinkPairException.Reason) : PairUi
}

@HiltViewModel
class LinkViewModel @Inject constructor(
    private val repo: LinkRepository,
    private val operations: OperationManager
) : ViewModel() {
    val server: StateFlow<LinkServerState> = repo.server
    val request: StateFlow<PairRequest?> = repo.request
    val peers: StateFlow<List<LinkPeer>> = repo.peers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** Devices on the network that are not paired yet. */
    val nearby: StateFlow<List<NearbyDevice>> = combine(repo.nearby, repo.peers) { found, paired ->
        found.filter { d -> paired.none { it.id.startsWith(d.id) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val deviceName: String get() = repo.deviceName

    private val _pair = MutableStateFlow<PairUi>(PairUi.Idle)
    val pair: StateFlow<PairUi> = _pair.asStateFlow()
    private var confirm: CompletableDeferred<Boolean>? = null

    /** Snackbar text as (string id, argument). */
    private val _messageRes = Channel<Pair<Int, String>>(Channel.BUFFERED)
    val messageRes = _messageRes.receiveAsFlow()

    fun setVisible(on: Boolean) = if (on) repo.startServer() else repo.stopServer()
    fun startDiscovery() = repo.startDiscovery()
    fun stopDiscovery() = repo.stopDiscovery()

    fun askAddress() { _pair.value = PairUi.Address }

    /** `ip` or `ip:port` (default port 47100). */
    fun pair(address: String) {
        val t = address.trim()
        val host = t.substringBeforeLast(':').takeIf { ':' in t && t.count { it == ':' } == 1 } ?: t
        val port = if (host != t) t.substringAfterLast(':').toIntOrNull() ?: return else DEFAULT_PORT
        if (host.isEmpty()) return
        pair(host, port)
    }

    /** Text scanned from a QR code; anything but a Findex pairing code shows a message. */
    fun pairQr(text: String) {
        val qr = com.aiyu.findex.core.link.LinkQr.parse(text)
        if (qr == null) {
            viewModelScope.launch { _messageRes.send(R.string.link_qr_invalid to "") }
            return
        }
        _pair.value = PairUi.Connecting
        viewModelScope.launch {
            try {
                val name = repo.pairQr(qr)
                _pair.value = PairUi.Idle
                _messageRes.send(R.string.link_paired_with to name)
            } catch (e: LinkPairException) {
                _pair.value = PairUi.Failed(e.reason)
            }
        }
    }

    fun pair(host: String, port: Int) {
        _pair.value = PairUi.Connecting
        viewModelScope.launch {
            try {
                val name = repo.pair(host, port) { name, code ->
                    val answer = CompletableDeferred<Boolean>()
                    confirm = answer
                    _pair.value = PairUi.Code(name, code)
                    answer.await().also { if (it) _pair.value = PairUi.Waiting(name) }
                }
                _pair.value = PairUi.Idle
                _messageRes.send(R.string.link_paired_with to name)
            } catch (e: LinkPairException) {
                _pair.value = if (e.reason == LinkPairException.Reason.CANCELLED) PairUi.Idle else PairUi.Failed(e.reason)
            }
        }
    }

    fun confirmCode(match: Boolean) { confirm?.complete(match) }

    fun dismissPair() {
        confirm?.complete(false)
        confirm = null
        if (_pair.value !is PairUi.Waiting) _pair.value = PairUi.Idle
    }

    fun answer(request: PairRequest, accept: Boolean) = repo.answer(request, accept)

    fun unpair(peerId: String) { viewModelScope.launch { repo.unpair(peerId) } }

    fun sendText(serverId: Long, name: String, text: String) {
        viewModelScope.launch {
            runCatching { repo.sendText(serverId, text) }
                .onSuccess { _messageRes.send(R.string.link_text_sent to name) }
                .onFailure { _messageRes.send(R.string.link_err_unreachable to "") }
        }
    }

    /**
     * Copies the files into the computer's `Received` folder (its Downloads/Findex). Runs through the
     * operation queue like any copy, so it shows progress and survives leaving the screen.
     */
    fun sendFiles(paths: List<String>, peer: LinkPeer) {
        val id = peer.serverId ?: return
        operations.enqueue(FileOperation.Copy(paths, SmbPath.of(id, "Received")))
    }

    companion object {
        const val DEFAULT_PORT = 47100
    }
}

/** "This phone" card: the switch that lets paired computers reach this phone. */
@Composable
fun ThisPhoneCard(state: LinkServerState, deviceName: String, onToggle: (Boolean) -> Unit) {
    val on = state is LinkServerState.Running || state is LinkServerState.Starting
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Dimens.lg)) {
            ShapeIcon(
                icon = Icons.Rounded.PhoneAndroid,
                shape = MaterialShapes.Cookie9Sided,
                container = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                content = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(Dimens.lg))
            Column(Modifier.weight(1f)) {
                Text(deviceName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.link_visible), style = MaterialTheme.typography.bodyMedium)
                Text(
                    when (state) {
                        is LinkServerState.Running -> stringResource(R.string.link_visible_on, state.address)
                        LinkServerState.Starting -> stringResource(R.string.link_visible_starting)
                        LinkServerState.NoNetwork -> stringResource(R.string.link_no_network)
                        LinkServerState.Stopped -> stringResource(R.string.link_visible_off)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state == LinkServerState.NoNetwork) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(Dimens.sm))
            Switch(checked = on, onCheckedChange = onToggle)
        }
    }
}

/** A computer found on the network, not paired yet. */
@Composable
fun NearbyRow(device: NearbyDevice, index: Int, count: Int, onPair: () -> Unit) {
    ListItem(
        headlineContent = { Text(device.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("${device.platform.replaceFirstChar { it.uppercase() }} · ${device.host}") },
        leadingContent = { Icon(if (device.platform == LinkRepository.PLATFORM) Icons.Rounded.PhoneAndroid else Icons.Rounded.Computer, null) },
        trailingContent = { FilledTonalButton(onClick = onPair) { Text(stringResource(R.string.link_pair)) } },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clip(groupedShape(index, count))
    )
}

/** Pairing this phone started: address, code check, waiting, failure. */
@Composable
fun PairDialogs(state: PairUi, onAddress: (String) -> Unit, onConfirm: (Boolean) -> Unit, onDismiss: () -> Unit) {
    when (state) {
        PairUi.Idle -> Unit
        PairUi.Address -> {
            var text by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = { Icon(Icons.Rounded.Link, null) },
                title = { Text(stringResource(R.string.link_pair_by_address)) },
                text = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text(stringResource(R.string.link_address_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = { TextButton(onClick = { onAddress(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.link_pair)) } },
                dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
            )
        }
        PairUi.Connecting, is PairUi.Waiting -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(if (state is PairUi.Waiting) R.string.link_waiting else R.string.link_pairing)) },
            text = { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { LoadingIndicator() } },
            confirmButton = {}
        )
        is PairUi.Code -> CodeDialog(
            title = stringResource(R.string.link_check_code_title),
            message = stringResource(R.string.link_check_code, state.name),
            code = state.code,
            confirm = stringResource(R.string.link_pair),
            dismiss = stringResource(R.string.cancel),
            onAnswer = onConfirm
        )
        is PairUi.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Rounded.LinkOff, null) },
            title = { Text(stringResource(R.string.link_pair)) },
            text = {
                Text(
                    stringResource(
                        when (state.reason) {
                            LinkPairException.Reason.DECLINED -> R.string.link_err_declined
                            LinkPairException.Reason.IDENTITY_MISMATCH -> R.string.link_err_identity
                            else -> R.string.link_err_unreachable
                        }
                    )
                )
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } }
        )
    }
}

@Composable
private fun CodeDialog(title: String, message: String, code: String, confirm: String, dismiss: String, onAnswer: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onAnswer(false) },
        icon = { Icon(Icons.Rounded.Link, null) },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.lg)) {
                Text(message)
                Text(
                    code.chunked(3).joinToString(" "),
                    style = MaterialTheme.typography.displayMedium,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onAnswer(true) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text(dismiss) } }
    )
}

/** A computer asks to pair with this phone; shown over any screen while the request waits. */
@Composable
fun LinkRequestHost(viewModel: LinkViewModel = hiltViewModel()) {
    val request by viewModel.request.collectAsStateWithLifecycle()
    request?.let { r ->
        val device = if (r.platform.isBlank()) r.name else "${r.name} (${r.platform})"
        CodeDialog(
            title = stringResource(R.string.link_request_title),
            message = stringResource(R.string.link_request_text, device),
            code = r.code,
            confirm = stringResource(R.string.link_pair),
            dismiss = stringResource(R.string.link_decline),
            onAnswer = { viewModel.answer(r, it) }
        )
    }
}

/** Long press on a paired computer: send text or unpair. */
@Composable
fun ComputerActionsSheet(name: String, onSendText: () -> Unit, onUnpair: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Dimens.lg)) {
            Text(name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Dimens.xl, vertical = Dimens.sm))
            ListItem(
                headlineContent = { Text(stringResource(R.string.link_send_text)) },
                leadingContent = { Icon(Icons.Rounded.TextFields, null) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.heightIn(min = Dimens.minTouch).clip(MaterialTheme.shapes.large).clickable(onClick = onSendText)
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.link_unpair), color = MaterialTheme.colorScheme.error) },
                leadingContent = { Icon(Icons.Rounded.LinkOff, null, tint = MaterialTheme.colorScheme.error) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.heightIn(min = Dimens.minTouch).clip(MaterialTheme.shapes.large).clickable(onClick = onUnpair)
            )
        }
    }
}

@Composable
fun SendTextDialog(name: String, onSend: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.TextFields, null) },
        title = { Text(stringResource(R.string.link_send_text) + " · " + name) },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text(stringResource(R.string.link_send_text_hint)) }, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = { TextButton(onClick = { onSend(text) }, enabled = text.isNotBlank()) { Text(stringResource(R.string.link_send)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun UnpairDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.LinkOff, null) },
        title = { Text(stringResource(R.string.link_unpair)) },
        text = { Text(stringResource(R.string.link_unpair_confirm, name)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.link_unpair), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** "Send to computer" from a selection: pick one of the paired computers. */
@Composable
fun SendToComputerSheet(paths: List<String>, onDone: () -> Unit, viewModel: LinkViewModel = hiltViewModel()) {
    val peers by viewModel.peers.collectAsStateWithLifecycle()
    // Progress and the result come from the operation queue (notification and snackbar), like any copy.
    ModalBottomSheet(onDismissRequest = onDone) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Dimens.lg)) {
            Text(stringResource(R.string.link_choose_computer), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Dimens.xl, vertical = Dimens.sm))
            val computers = peers.filter { it.serverId != null }
            if (computers.isEmpty()) {
                Text(stringResource(R.string.link_none_paired), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Dimens.xl, vertical = Dimens.sm))
            }
            computers.forEach { peer ->
                ListItem(
                    headlineContent = { Text(peer.name) },
                    supportingContent = { Text(peer.platform.replaceFirstChar { it.uppercase() }) },
                    leadingContent = { Icon(if (peer.platform == LinkRepository.PLATFORM) Icons.Rounded.PhoneAndroid else Icons.Rounded.Computer, null) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.heightIn(min = Dimens.minTouch).clickable { viewModel.sendFiles(paths, peer); onDone() }
                )
            }
        }
    }
}
