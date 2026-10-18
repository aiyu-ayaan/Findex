package com.aiyu.findex.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.rounded.Edit
import com.aiyu.findex.ui.explorer.ActionRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.aiyu.findex.core.network.NetworkError
import com.aiyu.findex.core.network.NetworkProtocol
import com.aiyu.findex.core.network.NetworkServer
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.components.groupedShape
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.theme.Dimens

/** Saved SMB shares, with a sheet to add one. Tapping a share opens [NetworkBrowseScreen]. */
@Composable
fun NetworkScreen(onBack: () -> Unit, onOpenServer: (Long) -> Unit, viewModel: NetworkViewModel = hiltViewModel()) {
    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var adding by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<NetworkServer?>(null) }
    var unlocking by remember { mutableStateOf<NetworkServer?>(null) }
    var editing by remember { mutableStateOf<NetworkServer?>(null) }
    var menuFor by remember { mutableStateOf<NetworkServer?>(null) }

    LaunchedEffect(Unit) {
        viewModel.added.collect {
            adding = false
            if (editing != null) editing = null else onOpenServer(it.id)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.network_title)) },
                subtitle = { Text(stringResource(R.string.network_subtitle)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.resetAdd(); adding = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text(stringResource(R.string.network_add)) }
            )
        }
    ) { padding ->
        val list = servers
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            list.isEmpty() -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(padding).padding(Dimens.xxl)
            ) {
                ShapeIcon(
                    icon = Icons.Rounded.Dns,
                    shape = MaterialShapes.Cookie9Sided,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    size = 120.dp,
                    iconSize = 52.dp
                )
                Spacer(Modifier.height(Dimens.xl))
                Text(stringResource(R.string.network_empty_title), style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(
                    stringResource(R.string.network_empty_body),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = readableGutter(), end = readableGutter(),
                    top = padding.calculateTopPadding() + Dimens.sm,
                    bottom = padding.calculateBottomPadding() + 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.xxs)
            ) {
                itemsIndexed(list, key = { _, s -> s.id }) { index, server ->
                    NetworkServerCard(
                        server = server,
                        shape = groupedShape(index, list.size),
                        onClick = { if (viewModel.isLocked(server)) unlocking = server else onOpenServer(server.id) },
                        onLongClick = { menuFor = server },
                        onRemove = { removing = server }
                    )
                }
            }
        }
    }

    if (adding || editing != null) {
        AddServerSheet(viewModel = viewModel, editing = editing, onDismiss = { adding = false; editing = null })
    }
    menuFor?.let { server ->
        ServerActionsSheet(
            server = server,
            onEdit = { menuFor = null; viewModel.resetAdd(); editing = server },
            onRemove = { menuFor = null; removing = server },
            onDismiss = { menuFor = null }
        )
    }
    unlocking?.let { server ->
        UnlockServerDialog(
            server = server,
            onUnlock = { viewModel.unlock(server.id, it) },
            onUnlocked = { unlocking = null; onOpenServer(server.id) },
            onDismiss = { unlocking = null }
        )
    }
    removing?.let { server ->
        RemoveServerDialog(server, onConfirm = { viewModel.remove(server.id); removing = null }, onDismiss = { removing = null })
    }
}

/** Long-press menu for a saved server. */
@Composable
fun ServerActionsSheet(server: NetworkServer, onEdit: () -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = Dimens.lg)) {
            ListItem(
                headlineContent = { Text(server.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
                supportingContent = { Text(server.displayAddress, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                leadingContent = {
                    ShapeIcon(
                        icon = Icons.Rounded.Dns,
                        shape = MaterialShapes.Pill,
                        container = MaterialTheme.colorScheme.secondaryContainer,
                        content = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
            )
            HorizontalDivider(Modifier.padding(horizontal = Dimens.xl, vertical = Dimens.sm))
            ActionRow(Icons.Rounded.Edit, R.string.network_edit, onClick = onEdit)
            ActionRow(Icons.Rounded.Delete, R.string.network_remove, MaterialTheme.colorScheme.error, onClick = onRemove)
        }
    }
}

@Composable
fun RemoveServerDialog(server: NetworkServer, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Delete, null) },
        title = { Text(stringResource(R.string.network_remove_title, server.label)) },
        text = { Text(stringResource(R.string.network_remove_body)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(R.string.network_remove)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

/** One saved server; also used on the Home "Network" tab. [onRemove] null hides the remove button. */
@Composable
fun NetworkServerCard(
    server: NetworkServer,
    shape: Shape,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    val haptics = LocalHapticFeedback.current
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick?.let { { haptics.performHapticFeedback(HapticFeedbackType.LongPress); it() } }
                )
                .padding(start = Dimens.lg, end = Dimens.xs, top = Dimens.md, bottom = Dimens.md)
        ) {
            ShapeIcon(
                icon = Icons.Rounded.Dns,
                shape = MaterialShapes.Pill,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(Dimens.lg))
            Column(Modifier.weight(1f)) {
                Text(server.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    server.displayAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
                Spacer(Modifier.height(Dimens.xxs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.xs)
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = stringResource(server.protocol.labelRes()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = Dimens.xs, vertical = 2.dp)
                        )
                    }
                    Text(
                        server.username.ifEmpty { stringResource(R.string.network_guest) },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.network_remove))
                }
            }
        }
    }
}

private val protocolChoices = listOf(
    NetworkProtocol.SMB,
    NetworkProtocol.SFTP,
    NetworkProtocol.FTP,
    NetworkProtocol.WEBDAV
)

/** Adds a server, or edits [editing] in place (an empty password then keeps the saved one). */
@Composable
fun AddServerSheet(viewModel: NetworkViewModel, editing: NetworkServer? = null, onDismiss: () -> Unit) {
    val state by viewModel.add.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialProtocol = editing?.protocol ?: NetworkProtocol.SMB
    var baseProtocolName by rememberSaveable {
        mutableStateOf((if (initialProtocol == NetworkProtocol.WEBDAVS) NetworkProtocol.WEBDAV else initialProtocol).name)
    }
    var webdavHttps by rememberSaveable { mutableStateOf(initialProtocol == NetworkProtocol.WEBDAVS) }
    val selectedBase = NetworkProtocol.fromName(baseProtocolName)
    val effectiveProtocol = if (selectedBase == NetworkProtocol.WEBDAV && webdavHttps) {
        NetworkProtocol.WEBDAVS
    } else {
        selectedBase
    }

    var name by rememberSaveable { mutableStateOf(editing?.name.orEmpty()) }
    var host by rememberSaveable { mutableStateOf(editing?.host.orEmpty()) }
    var portText by rememberSaveable { mutableStateOf(editing?.port?.takeIf { it > 0 }?.toString().orEmpty()) }
    var share by rememberSaveable { mutableStateOf(editing?.share.orEmpty()) }
    var user by rememberSaveable { mutableStateOf(editing?.username.orEmpty()) }
    // Not saveable: a password must not end up in the saved-instance-state bundle.
    var password by remember { mutableStateOf("") }
    var domain by rememberSaveable { mutableStateOf(editing?.domain.orEmpty()) }
    var showPassword by remember { mutableStateOf(false) }
    var rememberPassword by rememberSaveable { mutableStateOf(editing?.rememberPassword ?: true) }

    // Editing any field clears the last connection error.
    LaunchedEffect(effectiveProtocol, host, portText, share, user, password, domain) {
        if (viewModel.add.value.error != null) viewModel.resetAdd()
    }

    val requiresShare = effectiveProtocol == NetworkProtocol.SMB && !host.trim().replace('\\', '/').removePrefix("//").contains('/')
    val parsedPort = portText.trim().toIntOrNull() ?: 0
    val validPort = portText.isBlank() || (parsedPort in 1..65535)
    val canSubmit = host.isNotBlank() && (!requiresShare || share.isNotBlank()) && validPort && !state.isConnecting
    val submit = {
        if (canSubmit) {
            viewModel.addServer(
                name = name,
                host = host,
                share = share,
                username = user,
                domain = domain,
                password = password,
                protocol = effectiveProtocol,
                port = parsedPort,
                rememberPassword = rememberPassword,
                existingId = editing?.id
            )
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Dimens.xl)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = Dimens.lg)
        ) {
            Text(
                stringResource(if (editing != null) R.string.network_edit_title else R.string.network_add_title),
                style = MaterialTheme.typography.headlineSmallEmphasized
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                modifier = Modifier.fillMaxWidth()
            ) {
                protocolChoices.forEachIndexed { index, choice ->
                    ToggleButton(
                        checked = selectedBase == choice,
                        onCheckedChange = { baseProtocolName = choice.name },
                        enabled = !state.isConnecting,
                        shapes = when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            protocolChoices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                        contentPadding = PaddingValues(horizontal = Dimens.xs),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(choice.labelRes()), maxLines = 1)
                    }
                }
            }

            if (selectedBase == NetworkProtocol.WEBDAV) {
                FilterChip(
                    selected = webdavHttps,
                    onClick = { webdavHttps = !webdavHttps },
                    enabled = !state.isConnecting,
                    label = { Text(stringResource(R.string.network_webdav_https)) },
                    leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            Field(
                value = host,
                onValueChange = { host = it },
                labelRes = R.string.network_field_host,
                helpRes = R.string.network_field_host_help,
                keyboardType = KeyboardType.Uri,
                enabled = !state.isConnecting
            )

            OutlinedTextField(
                value = portText,
                onValueChange = { input -> portText = input.filter { it.isDigit() }.take(5) },
                singleLine = true,
                enabled = !state.isConnecting,
                label = { Text(stringResource(R.string.network_field_port)) },
                placeholder = { Text(stringResource(R.string.network_field_port_default, effectiveProtocol.defaultPort)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                    autoCorrectEnabled = false
                ),
                modifier = Modifier.fillMaxWidth()
            )

            val shareLabelRes = if (effectiveProtocol == NetworkProtocol.SMB) {
                R.string.network_field_share
            } else {
                R.string.network_field_path
            }
            val shareHelpRes = when (effectiveProtocol) {
                NetworkProtocol.SMB -> R.string.network_field_share_help
                NetworkProtocol.SFTP -> R.string.network_field_path_help_sftp
                NetworkProtocol.FTP -> R.string.network_field_path_help_ftp
                NetworkProtocol.WEBDAV, NetworkProtocol.WEBDAVS -> R.string.network_field_path_help_webdav
            }
            Field(
                value = share,
                onValueChange = { share = it },
                labelRes = shareLabelRes,
                helpRes = shareHelpRes,
                keyboardType = KeyboardType.Uri,
                enabled = !state.isConnecting
            )

            val userHelpRes = if (effectiveProtocol == NetworkProtocol.SFTP) {
                R.string.network_field_user_help_sftp
            } else {
                R.string.network_field_user_help
            }
            Field(
                value = user,
                onValueChange = { user = it },
                labelRes = R.string.network_field_user,
                helpRes = userHelpRes,
                enabled = !state.isConnecting
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                singleLine = true,
                enabled = !state.isConnecting && user.isNotEmpty(),
                label = { Text(stringResource(R.string.network_field_password)) },
                leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(if (showPassword) R.string.cd_hide_password else R.string.cd_show_password)
                        )
                    }
                },
                supportingText = {
                    Text(
                        stringResource(
                            when {
                                editing != null -> R.string.network_password_keep_note
                                rememberPassword -> R.string.network_password_note
                                else -> R.string.network_password_not_saved_note
                            }
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (user.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = Dimens.minTouch)
                        .toggleable(
                            value = rememberPassword,
                            enabled = !state.isConnecting,
                            role = Role.Switch,
                            onValueChange = { rememberPassword = it }
                        )
                ) {
                    Text(stringResource(R.string.network_remember_password), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = rememberPassword, onCheckedChange = null, enabled = !state.isConnecting)
                }
            }

            if (effectiveProtocol == NetworkProtocol.SMB) {
                Field(
                    value = domain,
                    onValueChange = { domain = it },
                    labelRes = R.string.network_field_domain,
                    helpRes = null,
                    enabled = !state.isConnecting && user.isNotEmpty()
                )
            }

            Field(
                value = name,
                onValueChange = { name = it },
                labelRes = R.string.network_field_name,
                helpRes = null,
                imeAction = ImeAction.Done,
                onDone = submit,
                enabled = !state.isConnecting
            )

            state.error?.let { error ->
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(Dimens.md)) {
                        Icon(Icons.Rounded.ErrorOutline, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(Dimens.sm))
                        Text(stringResource(error.messageRes()), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            Button(onClick = submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth().height(Dimens.minTouch + Dimens.sm)) {
                if (state.isConnecting) {
                    LoadingIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(Dimens.sm))
                    Text(stringResource(R.string.network_connecting))
                } else {
                    Text(stringResource(if (editing != null) R.string.network_save else R.string.network_connect))
                }
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    labelRes: Int,
    helpRes: Int?,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        enabled = enabled,
        label = { Text(stringResource(labelRes)) },
        supportingText = helpRes?.let { { Text(stringResource(it)) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Asks for the password of a server saved without "Remember password".
 * [onUnlock] connects and returns the error, or null once connected.
 */
@Composable
fun UnlockServerDialog(
    server: NetworkServer,
    onUnlock: suspend (String) -> NetworkError?,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit
) {
    // Not saveable: a password must not end up in the saved-instance-state bundle.
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<NetworkError?>(null) }
    val scope = rememberCoroutineScope()
    val submit = {
        if (!busy) {
            busy = true
            scope.launch {
                error = onUnlock(password)
                busy = false
                if (error == null) onUnlocked()
            }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text(stringResource(R.string.network_unlock_title, server.label)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                singleLine = true,
                enabled = !busy,
                label = { Text(stringResource(R.string.network_field_password)) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                trailingIcon = {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                            contentDescription = stringResource(if (showPassword) R.string.cd_hide_password else R.string.cd_show_password)
                        )
                    }
                },
                isError = error != null,
                supportingText = { Text(stringResource(error?.messageRes() ?: R.string.network_unlock_body, server.username)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { submit() }, enabled = !busy) {
                Text(stringResource(if (busy) R.string.network_connecting else R.string.network_unlock))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) } }
    )
}
