package com.aiyu.findex.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.aiyu.findex.ui.components.MenuIcon
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.FileCopy
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Usb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.core.model.CategoryStat
import com.aiyu.findex.core.model.DriveType
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.StorageDrive
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.components.FileThumbnail
import com.aiyu.findex.ui.components.MorphingShapeIcon
import com.aiyu.findex.ui.components.OperationBanner
import com.aiyu.findex.ui.components.SectionHeader
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.components.groupedShape
import com.aiyu.findex.ui.components.icon
import com.aiyu.findex.ui.components.CategoryShape
import com.aiyu.findex.ui.components.rememberStaggerState
import com.aiyu.findex.ui.components.staggeredEnter
import com.aiyu.findex.ui.components.titleRes
import com.aiyu.findex.ui.navigation.AnalyzerRoute
import com.aiyu.findex.ui.navigation.CategoryRoute
import com.aiyu.findex.ui.navigation.CleanerRoute
import com.aiyu.findex.ui.navigation.ExplorerRoute
import com.aiyu.findex.ui.navigation.DuplicatesRoute
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.draw.alpha
import com.aiyu.findex.ui.network.NetworkViewModel
import com.aiyu.findex.ui.network.AddServerSheet
import com.aiyu.findex.ui.network.ServerActionsSheet
import com.aiyu.findex.ui.network.RemoveServerDialog
import com.aiyu.findex.ui.network.labelRes
import com.aiyu.findex.ui.network.messageRes
import com.aiyu.findex.core.network.NetworkServer
import com.aiyu.findex.core.network.NetworkStatus
import com.aiyu.findex.core.network.NetworkError
import com.aiyu.findex.ui.network.UnlockServerDialog
import androidx.compose.material.icons.rounded.Lock
import com.aiyu.findex.ui.navigation.SearchRoute
import com.aiyu.findex.ui.navigation.SettingsRoute
import com.aiyu.findex.ui.navigation.WhatsAppRoute
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.theme.tone
import com.aiyu.findex.ui.util.formatSize
import com.aiyu.findex.ui.util.openFile
import java.io.File

@Composable
fun HomeScreen(
    onOpenDrawer: (() -> Unit)?,
    onNavigate: (Any) -> Unit,
    modifier: Modifier = Modifier,
    drawerProgress: () -> Float = { 0f },
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val stagger = rememberStaggerState()
    var recentViewerPath by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var unlocking by remember { androidx.compose.runtime.mutableStateOf<NetworkServer?>(null) }
    var serverMenu by remember { androidx.compose.runtime.mutableStateOf<NetworkServer?>(null) }
    var editingServer by remember { androidx.compose.runtime.mutableStateOf<NetworkServer?>(null) }
    var addingServer by remember { androidx.compose.runtime.mutableStateOf(false) }
    var removingServer by remember { androidx.compose.runtime.mutableStateOf<NetworkServer?>(null) }
    val networkViewModel: NetworkViewModel = hiltViewModel()
    LaunchedEffect(Unit) {
        networkViewModel.added.collect {
            addingServer = false
            editingServer = null
            viewModel.probe(it.id, force = true)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                subtitle = { Text(stringResource(R.string.home_subtitle)) },
                navigationIcon = {
                    if (onOpenDrawer != null) IconButton(onClick = onOpenDrawer) {
                        MenuIcon(drawerProgress, Modifier.semantics { contentDescription = context.getString(R.string.cd_open_drawer) })
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate(SearchRoute) }) {
                        Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.action_search))
                    }
                    IconButton(onClick = { onNavigate(SettingsRoute) }) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.nav_settings))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { innerPadding ->
        val listState = rememberLazyListState()
        // Saved servers load after the drives; keyed items would keep the hero on top and hide the new tab row.
        LaunchedEffect(uiState.hasNetworkDrives) {
            if (uiState.hasNetworkDrives && listState.firstVisibleItemIndex <= 1) listState.scrollToItem(0)
        }
        // No full-screen spinner: drives load in a few ms, and a spinner would only flash between splash and content.
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = readableGutter(), end = readableGutter(),
                top = innerPadding.calculateTopPadding() + Dimens.xs,
                bottom = innerPadding.calculateBottomPadding() + Dimens.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
            modifier = Modifier.fillMaxSize()
        ) {
            if (uiState.hasNetworkDrives) {
                item(key = "tabs") {
                    // M3 Expressive connected button group: the selected side morphs to a full pill.
                    val options = listOf(
                        R.string.tab_device to Icons.Rounded.PhoneAndroid,
                        R.string.tab_network to Icons.Rounded.Dns
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                        modifier = Modifier.fillMaxWidth().selectableGroup()
                    ) {
                        options.forEachIndexed { index, (label, icon) ->
                            ToggleButton(
                                checked = uiState.selectedTabIndex == index,
                                onCheckedChange = { viewModel.selectTab(index) },
                                shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                                else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = Dimens.minTouch)
                                    .semantics { role = Role.Tab }
                            ) {
                                Icon(icon, contentDescription = null, modifier = Modifier.padding(end = Dimens.sm).size(ToggleButtonDefaults.IconSize))
                                Text(stringResource(label), maxLines = 1)
                            }
                        }
                    }
                }
            }

            item(key = "operation") {
                AnimatedVisibility(
                    visible = uiState.activeOperation != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    uiState.activeOperation?.let { OperationBanner(it, onCancel = viewModel::cancelOperation) }
                }
            }

            if (uiState.selectedTabIndex == 1 && uiState.hasNetworkDrives) {
                items(uiState.networkDrives, key = { "smb${it.id}" }) { server ->
                    NetworkDriveCard(
                        server = server,
                        status = uiState.networkStatus[server.id] ?: NetworkStatus.Checking,
                        onClick = { onNavigate(ExplorerRoute(server.rootPath)) },
                        onRetry = { viewModel.probe(server.id) },
                        onUnlock = { unlocking = server },
                        onLongClick = { serverMenu = server }
                    )
                }
                item(key = "addServer") {
                    OutlinedButton(onClick = { networkViewModel.resetAdd(); addingServer = true }, modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouch)) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.padding(end = Dimens.sm))
                        Text(stringResource(R.string.network_add))
                    }
                }
                return@LazyColumn
            }

            val primary = uiState.drives.firstOrNull()
            if (primary != null) {
                item(key = "hero") {
                    StorageHeroCard(
                        drive = primary,
                        onOpen = { onNavigate(ExplorerRoute(primary.path)) },
                        modifier = Modifier.staggeredEnter(stagger, "hero", 0)
                    )
                }
            }
            items(uiState.drives.drop(1), key = { it.id }) { drive ->
                DriveCard(drive, onClick = { onNavigate(ExplorerRoute(drive.path)) })
            }

            item(key = "categoriesHeader") { SectionHeader(stringResource(R.string.section_categories)) }
            item(key = "categories") {
                CategoryGrid(
                    stats = uiState.categoryStats,
                    onClick = { category ->
                        if (category == FileCategory.DOWNLOADS) onNavigate(ExplorerRoute(viewModel.downloadsPath))
                        else onNavigate(CategoryRoute(category.name))
                    },
                    modifier = Modifier.staggeredEnter(stagger, "categories", 1)
                )
            }

            if (uiState.bookmarks.isNotEmpty()) {
                item(key = "bookmarksHeader") { SectionHeader(stringResource(R.string.section_bookmarks)) }
                item(key = "bookmarks") {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        items(uiState.bookmarks, key = { it.path }) { bookmark ->
                            BookmarkChip(bookmark.name, onClick = { onNavigate(ExplorerRoute(bookmark.path)) })
                        }
                    }
                }
            }

            item(key = "toolsHeader") { SectionHeader(stringResource(R.string.section_suggestions)) }
            item(key = "tools") {
                ToolsBento(
                    onClean = { onNavigate(CleanerRoute) },
                    onWhatsApp = { onNavigate(WhatsAppRoute) },
                    onAnalyze = { onNavigate(AnalyzerRoute) },
                    onDuplicates = { onNavigate(DuplicatesRoute) },
                    modifier = Modifier.staggeredEnter(stagger, "tools", 2)
                )
            }

            if (uiState.recents.isNotEmpty()) {
                item(key = "recentsHeader") { SectionHeader(stringResource(R.string.section_recents)) }
                item(key = "recents") {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.xxs)) {
                        uiState.recents.forEachIndexed { index, recent ->
                            val ext = recent.name.substringAfterLast('.', "")
                            ListItem(
                                headlineContent = { Text(recent.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(formatSize(recent.size)) },
                                leadingContent = {
                                    FileThumbnail(recent.path, isDirectory = false, extension = ext)
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier
                                    .clip(groupedShape(index, uiState.recents.size))
                                    .clickable {
                                        viewModel.onRecentOpened(recent.path)
                                        if (com.aiyu.findex.ui.components.isMediaExtension(ext)) {
                                            recentViewerPath = recent.path
                                        } else {
                                            context.openFile(recent.path, recent.size)
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }

    serverMenu?.let { server ->
        ServerActionsSheet(
            server = server,
            onEdit = { serverMenu = null; networkViewModel.resetAdd(); editingServer = server },
            onRemove = { serverMenu = null; removingServer = server },
            onDismiss = { serverMenu = null }
        )
    }
    if (addingServer || editingServer != null) {
        AddServerSheet(viewModel = networkViewModel, editing = editingServer, onDismiss = { addingServer = false; editingServer = null })
    }
    removingServer?.let { server ->
        RemoveServerDialog(server, onConfirm = { networkViewModel.remove(server.id); removingServer = null }, onDismiss = { removingServer = null })
    }
    unlocking?.let { server ->
        UnlockServerDialog(
            server = server,
            onUnlock = { viewModel.unlock(server.id, it) },
            onUnlocked = { unlocking = null },
            onDismiss = { unlocking = null }
        )
    }
    recentViewerPath?.let { initialPath ->
        val recentStorageItems = remember(uiState.recents) {
            uiState.recents.map { r ->
                com.aiyu.findex.core.model.StorageItem(
                    name = r.name,
                    path = r.path,
                    isDirectory = false,
                    size = r.size,
                    lastModified = r.openedAt
                )
            }
        }
        com.aiyu.findex.ui.explorer.MediaViewerDialog(
            items = recentStorageItems,
            initialPath = initialPath,
            onDismiss = { recentViewerPath = null }
        )
    }
}

@Composable
private fun StorageHeroCard(drive: StorageDrive, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(
        targetValue = drive.usedPercentage,
        animationSpec = MaterialTheme.motionScheme.slowSpatialSpec(),
        label = "heroProgress"
    )
    val stroke = with(LocalDensity.current) { Stroke(width = 10.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round) }
    Card(
        onClick = onOpen,
        shape = MaterialTheme.shapes.extraLargeIncreased,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Box {
            // Decorative expressive shape bleeding off the top-right corner.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 56.dp, y = (-48).dp)
                    .size(200.dp)
                    .graphicsLayer { rotationZ = 18f }
                    .clip(MaterialShapes.Cookie12Sided.toShape())
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            )
            Column(Modifier.padding(Dimens.xl)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(driveIcon(drive.type), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(Dimens.sm))
                    Text(
                        text = drive.name.ifBlank { stringResource(R.string.storage_internal) },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(Dimens.lg))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "${(drive.usedPercentage * 100).toInt()}%",
                        style = MaterialTheme.typography.displayLargeEmphasized
                    )
                    Spacer(Modifier.width(Dimens.sm))
                    Text(
                        text = stringResource(R.string.storage_used_label),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = Dimens.md)
                    )
                }
                Spacer(Modifier.height(Dimens.md))
                LinearWavyProgressIndicator(
                    progress = { progress },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f),
                    stroke = stroke,
                    trackStroke = stroke,
                    wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                )
                Spacer(Modifier.height(Dimens.lg))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.storage_used_of, formatSize(drive.usedBytes), formatSize(drive.totalBytes)),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.storage_free_space, formatSize(drive.freeBytes)),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    FilledIconButton(
                        onClick = onOpen,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.size(IconButtonDefaults.mediumContainerSize())
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = stringResource(R.string.cd_browse_drive))
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveCard(drive: StorageDrive, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(Dimens.lg)) {
            ShapeIcon(
                icon = driveIcon(drive.type),
                shape = MaterialShapes.Pill,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(Dimens.lg))
            Column(Modifier.weight(1f)) {
                Text(drive.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(Dimens.sm))
                LinearWavyProgressIndicator(progress = { drive.usedPercentage }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Dimens.xs))
                Text(
                    stringResource(R.string.storage_used_of, formatSize(drive.usedBytes), formatSize(drive.totalBytes)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Compact sibling of [DriveCard] for a saved server; disabled while the server can't be reached. */
@Composable
private fun NetworkDriveCard(
    server: NetworkServer,
    status: NetworkStatus,
    onClick: () -> Unit,
    onRetry: () -> Unit,
    onUnlock: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val online = status is NetworkStatus.Online
    val locked = (status as? NetworkStatus.Offline)?.error == NetworkError.PASSWORD_REQUIRED
    val offline = status is NetworkStatus.Offline && !locked
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Long press works offline too, so a broken server can still be edited or removed.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .combinedClickable(
                    onClick = { if (online) onClick() },
                    onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() }
                )
                .padding(Dimens.lg)
        ) {
            ShapeIcon(
                modifier = Modifier.alpha(if (online) 1f else DisabledAlpha),
                icon = when {
                    locked -> Icons.Rounded.Lock
                    offline -> Icons.Rounded.CloudOff
                    else -> Icons.Rounded.Dns
                },
                shape = MaterialShapes.Pill,
                container = if (offline) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.secondaryContainer,
                content = if (offline) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(Modifier.width(Dimens.lg))
            Column(Modifier.weight(1f).alpha(if (online) 1f else DisabledAlpha)) {
                Text(server.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(server.protocol.labelRes()) + " · " + server.username.ifEmpty { stringResource(R.string.network_guest) } + " · " + server.displayAddress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
                Spacer(Modifier.height(Dimens.sm))
                when (status) {
                    NetworkStatus.Checking -> {
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(Dimens.xs))
                        Text(stringResource(R.string.network_connecting), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is NetworkStatus.Online -> status.space?.let { space ->
                        LinearWavyProgressIndicator(progress = { space.usedFraction }, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(Dimens.xs))
                        Text(
                            stringResource(R.string.storage_used_of, formatSize(space.usedBytes), formatSize(space.totalBytes)) +
                                " · " + stringResource(R.string.storage_free_space, formatSize(space.freeBytes)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } ?: Text(stringResource(R.string.network_status_connected), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    is NetworkStatus.Offline -> if (locked) Text(
                        stringResource(R.string.network_error_password_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ) else Text(
                        stringResource(R.string.network_status_offline, stringResource(status.error.messageRes())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (offline) {
                IconButton(onClick = onRetry) { Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.network_retry)) }
            }
            if (locked) {
                FilledTonalButton(onClick = onUnlock) { Text(stringResource(R.string.network_unlock)) }
            }
        }
    }
}

private const val DisabledAlpha = 0.6f

private fun driveIcon(type: DriveType): ImageVector = when (type) {
    DriveType.INTERNAL -> Icons.Rounded.Storage
    DriveType.SD_CARD -> Icons.Rounded.SdCard
    DriveType.USB -> Icons.Rounded.Usb
    DriveType.NETWORK -> Icons.Rounded.Storage
}

@Composable
private fun CategoryGrid(
    stats: Map<FileCategory, CategoryStat>,
    onClick: (FileCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        FileCategory.entries.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                row.forEach { category ->
                    CategoryTile(category, stats[category], onClick = { onClick(category) }, modifier = Modifier.weight(1f))
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun CategoryTile(category: FileCategory, stat: CategoryStat?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tone = category.tone
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = Dimens.md, horizontal = Dimens.xs)
        ) {
            // Press morphs the category's signature shape into a circle.
            MorphingShapeIcon(
                icon = category.icon,
                from = CategoryShape,
                to = MaterialShapes.Circle,
                morphed = pressed,
                container = tone.container,
                content = tone.onContainer,
                size = 52.dp,
                iconSize = 26.dp
            )
            Spacer(Modifier.height(Dimens.sm))
            Text(
                stringResource(category.titleRes),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Always present (empty for Downloads) so every tile has the same height.
            Text(
                stat?.let { pluralStringResource(R.plurals.item_count, it.count, it.count) }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ToolsBento(
    onClean: () -> Unit,
    onWhatsApp: () -> Unit,
    onAnalyze: () -> Unit,
    onDuplicates: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm), modifier = modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm), modifier = Modifier.height(232.dp)) {
            ToolTile(
                title = stringResource(R.string.cleaner_title),
                body = stringResource(R.string.cleaner_desc),
                icon = Icons.Rounded.CleaningServices,
                shape = MaterialShapes.Burst,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = onClean,
                large = true,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm), modifier = Modifier.weight(1f)) {
                ToolTile(
                    title = stringResource(R.string.whatsapp_cleaner_title),
                    body = null,
                    icon = Icons.Rounded.Forum,
                    shape = MaterialShapes.Clover4Leaf,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onWhatsApp,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                ToolTile(
                    title = stringResource(R.string.storage_graph_title),
                    body = null,
                    icon = Icons.Rounded.PieChart,
                    shape = MaterialShapes.Pentagon,
                    container = MaterialTheme.colorScheme.surfaceContainerHighest,
                    content = MaterialTheme.colorScheme.onSurface,
                    onClick = onAnalyze,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
        ToolTile(
            title = stringResource(R.string.dup_title),
            body = null,
            icon = Icons.Rounded.FileCopy,
            shape = MaterialShapes.Cookie6Sided,
            container = MaterialTheme.colorScheme.primaryContainer,
            content = MaterialTheme.colorScheme.onPrimaryContainer,
            onClick = onDuplicates,
            modifier = Modifier.fillMaxWidth().height(112.dp)
        )
    }
}

@Composable
private fun ToolTile(
    title: String,
    body: String?,
    icon: ImageVector,
    shape: androidx.graphics.shapes.RoundedPolygon,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    large: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = if (large) MaterialTheme.shapes.extraLargeIncreased else MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
        modifier = modifier
    ) {
        Column(Modifier.padding(Dimens.lg), verticalArrangement = Arrangement.SpaceBetween) {
            MorphingShapeIcon(
                icon = icon,
                from = shape,
                to = MaterialShapes.Circle,
                morphed = pressed,
                container = content,
                content = container,
                size = if (large) 56.dp else 40.dp,
                iconSize = if (large) 28.dp else 20.dp
            )
            Spacer(Modifier.weight(1f))
            Text(
                title,
                style = if (large) MaterialTheme.typography.titleLargeEmphasized else MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (body != null) {
                Spacer(Modifier.height(Dimens.xs))
                Text(body, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BookmarkChip(name: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.widthIn(max = 200.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = Dimens.md, vertical = Dimens.md)) {
            Icon(Icons.Rounded.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Dimens.sm))
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
