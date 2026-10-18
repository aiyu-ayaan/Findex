package com.aiyu.findex.ui.share

import android.content.ClipData
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aiyu.findex.core.model.AppSettings
import com.aiyu.findex.core.model.SharePermission
import com.aiyu.findex.core.settings.SettingsRepository
import com.aiyu.findex.core.share.ShareServerManager
import com.aiyu.findex.core.share.ShareState
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.SectionHeader
import com.aiyu.findex.ui.components.SettingItem
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.components.groupedShape
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShareViewModel @Inject constructor(
    private val manager: ShareServerManager,
    private val repo: SettingsRepository
) : ViewModel() {
    val state = manager.state
    val settings = repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun start() = manager.start()
    fun stop() = manager.stop()
    fun setAccess(access: Set<SharePermission>) = viewModelScope.launch { repo.setShareAccess(access) }
    fun toggle(permission: SharePermission, on: Boolean) =
        setAccess(if (on) settings.value.shareAccess + permission else settings.value.shareAccess - permission)
    fun setIdleMinutes(minutes: Int) = viewModelScope.launch { repo.setShareIdleMinutes(minutes) }
}

private data class AccessLevel(val labelRes: Int, val access: Set<SharePermission>)

private val levels = listOf(
    AccessLevel(R.string.share_level_view, setOf(SharePermission.MEDIA, SharePermission.FILES)),
    AccessLevel(R.string.share_level_download, SharePermission.DEFAULT),
    AccessLevel(R.string.share_level_full, SharePermission.entries.toSet()),
)

private data class PermissionRow(val permission: SharePermission, val icon: ImageVector, val titleRes: Int, val descRes: Int)

private val permissionRows = listOf(
    PermissionRow(SharePermission.MEDIA, Icons.Rounded.PhotoLibrary, R.string.share_perm_media, R.string.share_perm_media_desc),
    PermissionRow(SharePermission.FILES, Icons.Rounded.FolderOpen, R.string.share_perm_files, R.string.share_perm_files_desc),
    PermissionRow(SharePermission.DOWNLOAD, Icons.Rounded.Download, R.string.share_perm_download, R.string.share_perm_download_desc),
    PermissionRow(SharePermission.UPLOAD, Icons.Rounded.Upload, R.string.share_perm_upload, R.string.share_perm_upload_desc),
    PermissionRow(SharePermission.MODIFY, Icons.Rounded.DriveFileRenameOutline, R.string.share_perm_modify, R.string.share_perm_modify_desc),
)

private val idleChoices = listOf(1, 5, 15, 30, 0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShareScreen(onBack: () -> Unit, viewModel: ShareViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.nav_share_phone)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = readableGutter(), end = readableGutter(),
                top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Dimens.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.xxs)
        ) {
            item {
                AnimatedContent(targetState = state is ShareState.Running, label = "share-status") { running ->
                    if (running) RunningCard(state as? ShareState.Running ?: return@AnimatedContent, viewModel::stop)
                    else IdleCard(state, viewModel::start)
                }
            }

            item { SectionHeader(stringResource(R.string.share_access)) }
            item {
                Surface(shape = groupedShape(0, permissionRows.size + 1), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(Dimens.lg)) {
                        Text(stringResource(R.string.share_access_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Dimens.md))
                        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                            levels.forEachIndexed { index, level ->
                                ToggleButton(
                                    checked = settings.shareAccess == level.access,
                                    onCheckedChange = { viewModel.setAccess(level.access) },
                                    shapes = when (index) {
                                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                        levels.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(stringResource(level.labelRes), maxLines = 1) }
                            }
                        }
                    }
                }
            }
            permissionRows.forEachIndexed { index, row ->
                item(key = row.permission) {
                    val checked = row.permission in settings.shareAccess
                    SettingItem(
                        headlineContent = { Text(stringResource(row.titleRes)) },
                        supportingContent = { Text(stringResource(row.descRes)) },
                        leadingContent = { Icon(row.icon, null) },
                        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
                        modifier = Modifier
                            .clip(groupedShape(index + 1, permissionRows.size + 1))
                            .toggleable(value = checked, role = Role.Switch, onValueChange = { viewModel.toggle(row.permission, it) })
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.share_auto_stop)) }
            item {
                Surface(shape = groupedShape(0, 1), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column(Modifier.fillMaxWidth().padding(Dimens.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Timer, null)
                            Spacer(Modifier.width(Dimens.lg))
                            Text(stringResource(R.string.share_auto_stop_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(Dimens.sm))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                            idleChoices.forEach { minutes ->
                                FilterChip(
                                    selected = settings.shareIdleMinutes == minutes,
                                    onClick = { viewModel.setIdleMinutes(minutes) },
                                    label = { Text(if (minutes == 0) stringResource(R.string.share_never) else stringResource(R.string.share_minutes, minutes)) }
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.share_security_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.xs, vertical = Dimens.md)
                )
            }
        }
    }
}

@Composable
private fun IdleCard(state: ShareState, onStart: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(top = Dimens.sm)) {
        Column(Modifier.padding(Dimens.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            ShapeIcon(
                icon = Icons.Rounded.Wifi,
                shape = MaterialShapes.Cookie9Sided,
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
                size = Dimens.xxxl + Dimens.lg,
                iconSize = Dimens.xl + Dimens.sm
            )
            Spacer(Modifier.height(Dimens.lg))
            Text(stringResource(R.string.share_intro_title), style = MaterialTheme.typography.titleLargeEmphasized)
            Spacer(Modifier.height(Dimens.sm))
            Text(stringResource(R.string.share_intro_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state is ShareState.NoNetwork) {
                Spacer(Modifier.height(Dimens.md))
                Text(stringResource(R.string.share_no_network), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(Dimens.xl))
            Button(onClick = onStart, enabled = state !is ShareState.Starting, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state is ShareState.Starting) R.string.share_starting else R.string.share_start))
            }
        }
    }
}

@Composable
private fun RunningCard(state: ShareState.Running, onStop: () -> Unit) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copied = stringResource(R.string.share_copied)
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().padding(top = Dimens.sm)) {
        Column(Modifier.padding(Dimens.xl)) {
            Text(stringResource(R.string.share_open_url), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    state.url,
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(state.url, state.url))) }
                    Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.share_copy_url), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
            Spacer(Modifier.height(Dimens.lg))
            Text(stringResource(R.string.share_pin), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                state.pin.chunked(3).joinToString(" "),
                style = MaterialTheme.typography.displayMediumEmphasized.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(Dimens.sm))
            Text(
                if (state.clients == 0) stringResource(R.string.share_clients_none)
                else pluralStringResource(R.plurals.share_clients, state.clients, state.clients),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(Dimens.lg))
            FilledTonalButton(onClick = onStop, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_stop)) }
        }
    }
}
