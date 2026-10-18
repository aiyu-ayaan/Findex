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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

    LaunchedEffect(Unit) { viewModel.added.collect { adding = false; onOpenServer(it.id) } }

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
                        onClick = { onOpenServer(server.id) },
                        onRemove = { removing = server }
                    )
                }
            }
        }
    }

    if (adding) {
        AddServerSheet(viewModel = viewModel, onDismiss = { adding = false })
    }
    removing?.let { server ->
        AlertDialog(
            onDismissRequest = { removing = null },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text(stringResource(R.string.network_remove_title, server.label)) },
            text = { Text(stringResource(R.string.network_remove_body)) },
            confirmButton = { TextButton(onClick = { viewModel.remove(server.id); removing = null }) { Text(stringResource(R.string.network_remove)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
}

/** One saved share; also used on the Home "Network" tab. [onRemove] null hides the remove button. */
@Composable
fun NetworkServerCard(server: NetworkServer, shape: Shape, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Dimens.lg, end = Dimens.xs, top = Dimens.md, bottom = Dimens.md)) {
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
                    "\\\\${server.host}\\${server.share}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
                Text(
                    server.username.ifEmpty { stringResource(R.string.network_guest) },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.network_remove))
                }
            }
        }
    }
}

@Composable
private fun AddServerSheet(viewModel: NetworkViewModel, onDismiss: () -> Unit) {
    val state by viewModel.add.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by rememberSaveable { mutableStateOf("") }
    var host by rememberSaveable { mutableStateOf("") }
    var share by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    // Not saveable: a password must not end up in the saved-instance-state bundle.
    var password by remember { mutableStateOf("") }
    var domain by rememberSaveable { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    // Editing any field clears the last connection error.
    LaunchedEffect(host, share, user, password, domain) { if (viewModel.add.value.error != null) viewModel.resetAdd() }
    val canSubmit = host.isNotBlank() && share.isNotBlank() && !state.isConnecting
    val submit = { if (canSubmit) viewModel.addServer(name, host, share, user, domain, password) }

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
            Text(stringResource(R.string.network_add_title), style = MaterialTheme.typography.headlineSmallEmphasized)
            Field(host, { host = it }, R.string.network_field_host, R.string.network_field_host_help, KeyboardType.Uri, enabled = !state.isConnecting)
            Field(share, { share = it }, R.string.network_field_share, R.string.network_field_share_help, enabled = !state.isConnecting)
            Field(user, { user = it }, R.string.network_field_user, R.string.network_field_user_help, enabled = !state.isConnecting)
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
                supportingText = { Text(stringResource(R.string.network_password_note)) },
                modifier = Modifier.fillMaxWidth()
            )
            Field(domain, { domain = it }, R.string.network_field_domain, null, enabled = !state.isConnecting && user.isNotEmpty())
            Field(name, { name = it }, R.string.network_field_name, null, imeAction = ImeAction.Done, onDone = submit, enabled = !state.isConnecting)

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
                    Text(stringResource(R.string.network_connect))
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
