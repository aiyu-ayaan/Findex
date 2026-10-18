package com.aiyu.findex.ui.explorer

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.NavigationDrawerItem
import com.aiyu.findex.ui.components.LocalContentWidth
import com.aiyu.findex.ui.components.windowWidth
import com.aiyu.findex.ui.components.ProvideContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.BorderStroke
import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.border
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.ui.LocalSnackbarHostState
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.components.OperationBanner
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.util.openFile
import com.aiyu.findex.ui.util.shareFiles
import java.io.File
import kotlinx.coroutines.launch

/** Which modal is showing; at most one at a time. */
private sealed interface ExplorerModal {
    data class Actions(val item: StorageItem) : ExplorerModal
    data class Rename(val item: StorageItem) : ExplorerModal
    data class Compress(val paths: List<String>, val defaultName: String) : ExplorerModal
    data class Extract(val item: StorageItem) : ExplorerModal
    data class Delete(val paths: List<String>) : ExplorerModal
    data class Details(val item: StorageItem) : ExplorerModal
    data object NewFolder : ExplorerModal
    data object Sort : ExplorerModal
}

@Composable
fun ExplorerScreen(
    onBack: () -> Unit,
    onOpenTabs: () -> Unit,
    onOpenArchive: (String) -> Unit,
    viewModel: ExplorerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var modal by remember { mutableStateOf<ExplorerModal?>(null) }
    var fabMenuOpen by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbar.showSnackbar(context.getString(it)) }
    }

    // Back: close FAB menu → clear selection → parent folder (predictive) → leave screen (NavHost predictive pop).
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backFromLeft by remember { mutableStateOf(true) }
    val goingUp = !fabMenuOpen && !state.inSelection && state.canGoUp
    PredictiveBackHandler(enabled = fabMenuOpen || state.inSelection || state.canGoUp) { events ->
        try {
            events.collect { event ->
                if (goingUp) {
                    backProgress = event.progress
                    backFromLeft = event.swipeEdge == BackEventCompat.EDGE_LEFT
                }
            }
            when {
                fabMenuOpen -> fabMenuOpen = false
                state.inSelection -> viewModel.clearSelection()
                else -> viewModel.goUp()
            }
        } finally {
            backProgress = 0f
        }
    }
    val backScale by animateFloatAsState(1f - 0.08f * backProgress, MaterialTheme.motionScheme.fastSpatialSpec(), label = "backScale")
    val listStates = remember { mutableMapOf<String, androidx.compose.foundation.lazy.LazyListState>() }
    val gridStates = remember { mutableMapOf<String, androidx.compose.foundation.lazy.grid.LazyGridState>() }

    val tab = state.tab
    // MD3 list-detail: with room for both panes, tapping a file shows it beside the list.
    val listDetail = (LocalContentWidth.current ?: windowWidth()) >= 720.dp

    fun open(item: StorageItem) {
        when {
            state.inSelection -> viewModel.toggleSelection(item.path)
            item.isDirectory -> viewModel.openFolder(item.path)
            listDetail -> viewModel.focus(item.path)
            item.isArchive -> onOpenArchive(item.path)
            else -> {
                viewModel.onFileOpened(item.path)
                context.openFile(item.path)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                AnimatedContent(
                    targetState = state.inSelection,
                    transitionSpec = { fadeIn(fastEffects) togetherWith fadeOut(fastEffects) },
                    label = "topBar"
                ) { selecting ->
                    if (selecting) {
                        SelectionTopBar(
                            count = state.selection.size,
                            onClose = viewModel::clearSelection,
                            onSelectAll = viewModel::selectAll
                        )
                    } else {
                        BrowseTopBar(
                            title = tab?.let { if (it.path == it.root) it.rootName else File(it.path).name } ?: "",
                            isBookmarked = state.isBookmarked,
                            gridView = state.settings.gridView,
                            onBack = { if (!viewModel.goUp()) onBack() },
                            onBookmark = { viewModel.toggleBookmark() },
                            onToggleView = { viewModel.setGridView(!state.settings.gridView) },
                            onSort = { modal = ExplorerModal.Sort },
                            onNewTab = viewModel::addTab,
                            onNewFolder = { modal = ExplorerModal.NewFolder },
                            tabCount = state.tabs.size,
                            onOpenTabs = onOpenTabs,
                            scrollBehavior = scrollBehavior
                        )
                    }
                }
                if (state.tabs.size > 1) {
                    TabStrip(
                        tabs = state.tabs,
                        active = state.activeTab,
                        onSelect = viewModel::selectTab,
                        onClose = viewModel::closeTab
                    )
                }
                if (tab != null) Breadcrumbs(tab = tab, onJump = viewModel::jumpTo)
            }
        },
        floatingActionButton = {
            if (!listDetail) {
                ExplorerFab(
                    visible = !state.inSelection,
                    clipboardCount = state.clipboard?.paths?.size ?: 0,
                    isCut = state.clipboard?.isCut == true,
                    menuOpen = fabMenuOpen,
                    onMenuOpenChange = { fabMenuOpen = it },
                    onPaste = viewModel::paste,
                    onClearClipboard = viewModel::clearClipboard,
                    onNewFolder = { fabMenuOpen = false; modal = ExplorerModal.NewFolder },
                    onNewTab = { fabMenuOpen = false; viewModel.addTab() }
                )
            }
        }
    ) { innerPadding ->
      Row(Modifier.fillMaxSize().padding(top = innerPadding.calculateTopPadding())) {
        ProvideContentWidth(Modifier.weight(1f).fillMaxHeight()) {
        Box(Modifier.fillMaxSize()) {
            val listPadding = PaddingValues(
                start = readableGutter(), end = readableGutter(), top = Dimens.sm,
                bottom = innerPadding.calculateBottomPadding() + 120.dp
            )
            val header: @Composable () -> Unit = {
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.activeOperation != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    state.activeOperation?.let {
                        OperationBanner(it, onCancel = viewModel::cancelOperation, modifier = Modifier.padding(bottom = Dimens.sm))
                    }
                }
            }

            // Shared-axis X between folders: deeper slides in from the right, up from the left.
            AnimatedContent(
                targetState = (tab?.id to state.itemsPath),
                transitionSpec = {
                    val dir = if (state.forward) 1 else -1
                    (slideInHorizontally(spatial) { it / 8 * dir } + fadeIn(effects)) togetherWith
                        (slideOutHorizontally(spatial) { -it / 8 * dir } + fadeOut(effects))
                },
                contentKey = { it },
                label = "folder",
                modifier = Modifier.graphicsLayer {
                    // Predictive back preview: the folder shrinks and slides toward the swipe direction.
                    scaleX = backScale
                    scaleY = backScale
                    translationX = (if (backFromLeft) 1 else -1) * backProgress * 32.dp.toPx()
                    alpha = 1f - 0.3f * backProgress
                }
            ) { (_, path) ->
                when {
                    path == null -> Unit
                    state.items.isEmpty() -> Column(Modifier.fillMaxSize()) {
                        Box(Modifier.padding(horizontal = Dimens.gutter)) { header() }
                        EmptyFolder()
                    }
                    state.settings.gridView -> FileGrid(
                        items = state.items,
                        selection = state.selection,
                        state = gridStates.getOrPut(path) { LazyGridState() },
                        contentPadding = listPadding,
                        onClick = ::open,
                        onSelectionChange = viewModel::setSelection,
                        header = header,
                        focused = state.focused
                    )
                    else -> FileList(
                        items = state.items,
                        selection = state.selection,
                        state = listStates.getOrPut(path) { LazyListState() },
                        contentPadding = listPadding,
                        onClick = ::open,
                        onMore = { modal = ExplorerModal.Actions(it) },
                        onSelectionChange = viewModel::setSelection,
                        header = header,
                        focused = state.focused
                    )
                }
            }

            if (state.isLoading) {
                LoadingIndicator(Modifier.align(Alignment.Center))
            }

            if (listDetail) {
                ExplorerFab(
                    visible = !state.inSelection,
                    clipboardCount = state.clipboard?.paths?.size ?: 0,
                    isCut = state.clipboard?.isCut == true,
                    menuOpen = fabMenuOpen,
                    onMenuOpenChange = { fabMenuOpen = it },
                    onPaste = viewModel::paste,
                    onClearClipboard = viewModel::clearClipboard,
                    onNewFolder = { fabMenuOpen = false; modal = ExplorerModal.NewFolder },
                    onNewTab = { fabMenuOpen = false; viewModel.addTab() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Dimens.xl)
                )
            }

            SelectionToolbar(
                visible = state.inSelection,
                singleSelected = state.selectedItems.singleOrNull(),
                onCopy = { viewModel.copySelection() },
                onCut = { viewModel.cutSelection() },
                onDelete = { modal = ExplorerModal.Delete(state.selection.toList()) },
                onCompress = {
                    val name = state.selectedItems.singleOrNull()?.name?.substringBeforeLast('.') ?: File(tab?.path ?: "").name
                    modal = ExplorerModal.Compress(state.selection.toList(), name.ifEmpty { "archive" })
                },
                onShare = { context.shareFiles(state.selection.toList()) },
                onRename = { state.selectedItems.singleOrNull()?.let { modal = ExplorerModal.Rename(it) } },
                onDetails = { state.selectedItems.singleOrNull()?.let { modal = ExplorerModal.Details(it) } },
                onOpenInNewTab = { state.selectedItems.singleOrNull()?.let { viewModel.openInNewTab(it.path) } },
                onExtract = { state.selectedItems.singleOrNull()?.let { viewModel.clearSelection(); modal = ExplorerModal.Extract(it) } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .offset(y = -FloatingToolbarDefaults.ScreenOffset)
            )
        }
      }
        if (listDetail) {
            VerticalDivider(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = Dimens.sm),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
            val focusedItem = state.focusedItem
            ExplorerDetailPane(
                item = focusedItem,
                folderName = tab?.let { if (it.path == it.root) it.rootName else File(it.path).name } ?: "",
                folderItemCount = state.items.size,
                load = viewModel::details,
                actions = DetailActions(
                    onOpen = { focusedItem?.let { viewModel.onFileOpened(it.path); context.openFile(it.path) } },
                    onShare = { focusedItem?.let { context.shareFiles(listOf(it.path)) } },
                    onCopy = { focusedItem?.let { viewModel.copySelection(listOf(it.path)) } },
                    onCut = { focusedItem?.let { viewModel.cutSelection(listOf(it.path)) } },
                    onRename = { focusedItem?.let { modal = ExplorerModal.Rename(it) } },
                    onCompress = { focusedItem?.let { modal = ExplorerModal.Compress(listOf(it.path), it.name.substringBeforeLast('.')) } },
                    onExtract = { focusedItem?.let { modal = ExplorerModal.Extract(it) } },
                    onBrowse = { focusedItem?.let { onOpenArchive(it.path) } },
                    onDelete = { focusedItem?.let { modal = ExplorerModal.Delete(listOf(it.path)) } },
                    onClose = { viewModel.focus(null) },
                    onNewFolder = { modal = ExplorerModal.NewFolder },
                    onOpenFolder = { viewModel.openFolder(it) }
                ),
                modifier = Modifier
                    .width(ExplorerDetailPaneWidth)
                    .fillMaxHeight()
                    .padding(start = Dimens.md, end = Dimens.lg, top = Dimens.sm, bottom = Dimens.lg)
            )
        }
      }
    }

    when (val m = modal) {
        null -> Unit
        is ExplorerModal.Actions -> ItemActionSheet(
            item = m.item,
            onDismiss = { modal = null },
            onAction = { action ->
                modal = null
                val item = m.item
                when (action) {
                    ItemAction.OPEN -> open(item)
                    ItemAction.SHOW_IN_FOLDER, ItemAction.RESTORE -> Unit
                    ItemAction.OPEN_IN_NEW_TAB -> viewModel.openInNewTab(item.path)
                    ItemAction.SHARE -> context.shareFiles(listOf(item.path))
                    ItemAction.COPY -> viewModel.copySelection(listOf(item.path))
                    ItemAction.CUT -> viewModel.cutSelection(listOf(item.path))
                    ItemAction.RENAME -> modal = ExplorerModal.Rename(item)
                    ItemAction.COMPRESS -> modal = ExplorerModal.Compress(listOf(item.path), if (item.isDirectory) item.name else item.name.substringBeforeLast('.'))
                    ItemAction.EXTRACT -> modal = ExplorerModal.Extract(item)
                    ItemAction.BOOKMARK -> viewModel.toggleBookmark(item.path, bookmarked = false)
                    ItemAction.DETAILS -> modal = ExplorerModal.Details(item)
                    ItemAction.DELETE -> modal = ExplorerModal.Delete(listOf(item.path))
                }
            }
        )
        is ExplorerModal.Rename -> NameDialog(
            title = stringResource(R.string.action_rename),
            initial = m.item.name,
            confirmLabel = stringResource(R.string.action_rename),
            onConfirm = { viewModel.rename(m.item.path, it); modal = null },
            onDismiss = { modal = null }
        )
        ExplorerModal.NewFolder -> NameDialog(
            title = stringResource(R.string.action_new_folder),
            initial = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { viewModel.createFolder(it); modal = null },
            onDismiss = { modal = null }
        )
        is ExplorerModal.Compress -> CompressDialog(
            defaultName = m.defaultName,
            onConfirm = { name, password, format -> viewModel.compress(m.paths, name, password, format); modal = null },
            onDismiss = { modal = null }
        )
        is ExplorerModal.Extract -> ExtractDialog(
            archiveName = m.item.name,
            onConfirm = { password -> viewModel.extract(m.item.path, password); modal = null },
            onDismiss = { modal = null }
        )
        is ExplorerModal.Delete -> DeleteDialog(
            count = m.paths.size,
            onConfirm = { permanent -> viewModel.delete(m.paths, permanent); modal = null },
            onDismiss = { modal = null }
        )
        is ExplorerModal.Details -> DetailsSheet(m.item, load = viewModel::details, onDismiss = { modal = null })
        ExplorerModal.Sort -> SortSheet(
            sort = state.settings.sort,
            showHidden = state.settings.showHidden,
            onSort = viewModel::setSort,
            onShowHidden = viewModel::setShowHidden,
            onDismiss = { modal = null }
        )
    }
}

@Composable
private fun BrowseTopBar(
    title: String,
    isBookmarked: Boolean,
    gridView: Boolean,
    onBack: () -> Unit,
    onBookmark: () -> Unit,
    onToggleView: () -> Unit,
    onSort: () -> Unit,
    onNewTab: () -> Unit,
    onNewFolder: () -> Unit,
    tabCount: Int,
    onOpenTabs: () -> Unit,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior
) {
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLargeEmphasized) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.cd_back))
            }
        },
        actions = {
            TabCountButton(tabCount, onOpenTabs)
            IconButton(onClick = onToggleView) {
                Icon(
                    if (gridView) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                    contentDescription = stringResource(if (gridView) R.string.cd_list_view else R.string.cd_grid_view)
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.large) {
                    DropdownMenuItem(
                        text = { Text(stringResource(if (isBookmarked) R.string.action_remove_bookmark else R.string.action_bookmark)) },
                        leadingIcon = { Icon(if (isBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null) },
                        onClick = { menu = false; onBookmark() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.sort_title)) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null) },
                        onClick = { menu = false; onSort() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_new_folder)) },
                        leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, null) },
                        onClick = { menu = false; onNewFolder() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_new_tab)) },
                        leadingIcon = { Icon(Icons.Rounded.Tab, null) },
                        onClick = { menu = false; onNewTab() }
                    )
                }
            }
        },
        scrollBehavior = scrollBehavior
    )
}

@Composable
private fun SelectionTopBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit) {
    TopAppBar(
        title = {
            AnimatedContent(targetState = count, label = "count") { c ->
                Text(stringResource(R.string.selected_count, c), style = MaterialTheme.typography.titleLargeEmphasized)
            }
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear_selection))
            }
        },
        actions = {
            IconButton(onClick = onSelectAll) {
                Icon(Icons.Rounded.SelectAll, contentDescription = stringResource(R.string.action_select_all))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    )
}

/** Pill tabs: the active one is filled and outlined, a new tab slides in and is scrolled into view. */
@Composable
private fun TabStrip(tabs: List<ExplorerTab>, active: Int, onSelect: (Int) -> Unit, onClose: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(active, tabs.size) { if (active in tabs.indices) listState.animateScrollToItem(active) }
    val colors = MaterialTheme.colorScheme
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = Dimens.gutter, vertical = Dimens.xs),
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
    ) {
        itemsIndexed(tabs, key = { _, tab -> tab.id }) { index, tab ->
            val selected = index == active
            val name = if (tab.path == tab.root) tab.rootName else File(tab.path).name
            Surface(
                onClick = { onSelect(index) },
                shape = MaterialTheme.shapes.extraLarge,
                color = if (selected) colors.secondaryContainer else colors.surfaceContainerHigh,
                contentColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                border = if (selected) BorderStroke(1.5.dp, colors.primary) else null,
                modifier = Modifier
                    .animateItem()
                    .heightIn(min = 40.dp)
                    .semantics { this.selected = selected }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Dimens.md)) {
                    Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Dimens.sm))
                    Text(
                        name,
                        style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                    // Close sits inside the pill; the Tabs screen offers a full-size alternative.
                    IconButton(onClick = { onClose(index) }, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_close_tab, name), modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Breadcrumbs(tab: ExplorerTab, onJump: (String) -> Unit) {
    val crumbs = remember(tab.path, tab.root) {
        val relative = tab.path.removePrefix(tab.root).trim('/')
        val parts = if (relative.isEmpty()) emptyList() else relative.split('/')
        listOf(tab.rootName to tab.root) + parts.mapIndexed { i, name -> name to tab.root + "/" + parts.take(i + 1).joinToString("/") }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(crumbs.size) { listState.animateScrollToItem(crumbs.lastIndex) }
    LazyRow(
        state = listState,
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = Dimens.sm),
        modifier = Modifier.fillMaxWidth().height(Dimens.minTouch)
    ) {
        itemsIndexed(crumbs, key = { _, c -> c.second }) { index, (name, path) ->
            val last = index == crumbs.lastIndex
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.animateItem()) {
                TextButton(onClick = { onJump(path) }, enabled = !last) {
                    Text(
                        name,
                        maxLines = 1,
                        style = if (last) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
                        color = if (last) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!last) Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EmptyFolder() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(Dimens.xxl)
    ) {
        ShapeIcon(
            icon = Icons.Rounded.FolderOff,
            shape = MaterialShapes.Cookie12Sided,
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            content = MaterialTheme.colorScheme.onSurfaceVariant,
            size = 120.dp,
            iconSize = 48.dp
        )
        Spacer(Modifier.height(Dimens.xl))
        Text(stringResource(R.string.empty_folder_title), style = MaterialTheme.typography.titleLargeEmphasized)
        Text(stringResource(R.string.empty_folder_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ExplorerFab(
    visible: Boolean,
    clipboardCount: Int,
    isCut: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onPaste: () -> Unit,
    onClearClipboard: () -> Unit,
    onNewFolder: () -> Unit,
    onNewTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (clipboardCount > 0) {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm)
        ) {
            SmallFloatingActionButton(onClick = onClearClipboard, modifier = Modifier.animateFloatingActionButton(visible, Alignment.BottomEnd)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.cd_clear_clipboard))
            }
            ExtendedFloatingActionButton(
                onClick = onPaste,
                icon = { Icon(Icons.Rounded.ContentPaste, null) },
                text = { Text(pluralStringResource(if (isCut) R.plurals.move_here else R.plurals.paste_here, clipboardCount, clipboardCount)) },
                modifier = Modifier.animateFloatingActionButton(visible, Alignment.BottomEnd)
            )
        }
        return
    }
    // Expressive FAB menu: the FAB morphs into a close button and items fan out.
    FloatingActionButtonMenu(
        expanded = menuOpen,
        button = {
            ToggleFloatingActionButton(
                checked = menuOpen,
                onCheckedChange = onMenuOpenChange,
                modifier = Modifier
                    .animateFloatingActionButton(visible || menuOpen, Alignment.BottomEnd)
                    .semantics {
                        traversalIndex = -1f
                        stateDescription = if (menuOpen) "expanded" else "collapsed"
                    }
            ) {
                val icon by remember { androidx.compose.runtime.derivedStateOf { if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add } }
                Icon(icon, contentDescription = stringResource(R.string.cd_create_menu), modifier = Modifier.animateIcon({ checkedProgress }))
            }
        },
        modifier = modifier.semantics { isTraversalGroup = true }
    ) {
        FloatingActionButtonMenuItem(
            onClick = onNewFolder,
            icon = { Icon(Icons.Rounded.CreateNewFolder, null) },
            text = { Text(stringResource(R.string.action_new_folder)) }
        )
        FloatingActionButtonMenuItem(
            onClick = onNewTab,
            icon = { Icon(Icons.Rounded.Tab, null) },
            text = { Text(stringResource(R.string.action_new_tab)) }
        )
    }
}

@Composable
private fun SelectionToolbar(
    visible: Boolean,
    singleSelected: StorageItem?,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onCompress: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDetails: () -> Unit,
    onOpenInNewTab: () -> Unit,
    onExtract: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = FloatingToolbarDefaults.horizontalEnterTransition(Alignment.CenterHorizontally),
        exit = FloatingToolbarDefaults.horizontalExitTransition(Alignment.CenterHorizontally),
        modifier = modifier
    ) {
        var more by remember { mutableStateOf(false) }
        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()
        ) {
            IconButton(onClick = onCopy) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.action_copy)) }
            IconButton(onClick = onCut) { Icon(Icons.Rounded.ContentCut, stringResource(R.string.action_cut)) }
            IconButton(onClick = onCompress) { Icon(Icons.Rounded.FolderZip, stringResource(R.string.action_compress)) }
            IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, stringResource(R.string.action_share)) }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
            Box {
                IconButton(onClick = { more = true }) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.cd_more_options)) }
                DropdownMenu(expanded = more, onDismissRequest = { more = false }, shape = MaterialTheme.shapes.large) {
                    if (singleSelected?.isDirectory == true) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_open_in_new_tab)) },
                            leadingIcon = { Icon(Icons.Rounded.Tab, null) },
                            onClick = { more = false; onOpenInNewTab() }
                        )
                    }
                    if (singleSelected?.isArchive == true) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_extract)) },
                            leadingIcon = { Icon(Icons.Rounded.Unarchive, null) },
                            onClick = { more = false; onExtract() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                        enabled = singleSelected != null,
                        onClick = { more = false; onRename() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_details)) },
                        leadingIcon = { Icon(Icons.Rounded.Info, null) },
                        enabled = singleSelected != null,
                        onClick = { more = false; onDetails() }
                    )
                }
            }
        }
    }
}

/** Browser-style tab counter: a rounded square with the number of open tabs; opens the Tabs overview. */
@Composable
private fun TabCountButton(count: Int, onClick: () -> Unit) {
    val label = pluralStringResource(R.plurals.tabs_open_count, count, count)
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .border(2.dp, LocalContentColor.current, MaterialTheme.shapes.extraSmall)
        ) {
            AnimatedContent(targetState = count.coerceAtMost(99), label = "tabCount") { c ->
                Text(c.toString(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
    }
}
