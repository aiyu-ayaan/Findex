package com.aiyu.findex.ui.category

import com.aiyu.findex.ui.explorer.SelectionActions
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.components.icon
import com.aiyu.findex.ui.components.CategoryShape
import com.aiyu.findex.ui.components.isMediaExtension
import com.aiyu.findex.ui.components.titleRes
import com.aiyu.findex.ui.explorer.DeleteDialog
import com.aiyu.findex.ui.explorer.FileGrid
import com.aiyu.findex.ui.explorer.FileList
import com.aiyu.findex.ui.explorer.ItemAction
import com.aiyu.findex.ui.explorer.ItemActionSheet
import com.aiyu.findex.ui.explorer.MediaViewerDialog
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.theme.tone
import com.aiyu.findex.ui.util.formatSize
import com.aiyu.findex.ui.util.openFile
import com.aiyu.findex.ui.util.openFileWithChooser
import com.aiyu.findex.ui.util.shareFiles
import com.aiyu.findex.ui.viewer.InAppViewers
import java.io.File

@Composable
fun CategoryScreen(
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenArchive: (String) -> Unit,
    onOpenFile: (Any) -> Unit = {},
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var actionsFor by remember { mutableStateOf<StorageItem?>(null) }
    var deletePaths by remember { mutableStateOf<List<String>?>(null) }
    var viewerInitialPath by remember { mutableStateOf<String?>(null) }
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    BackHandler(enabled = viewerInitialPath == null && state.selection.isNotEmpty()) { viewModel.clearSelection() }

    fun open(item: StorageItem) {
        when {
            state.selection.isNotEmpty() -> viewModel.toggle(item.path)
            item.isArchive -> onOpenArchive(item.path)
            InAppViewers.opensMedia(item.extension) -> {
                viewModel.onOpened(item.path)
                viewerInitialPath = item.path
            }
            else -> {
                viewModel.onOpened(item.path)
                InAppViewers.routeFor(item)?.let(onOpenFile) ?: context.openFile(item.path, item.size)
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AnimatedContent(state.selection.isNotEmpty(), transitionSpec = { fadeIn(fastEffects) togetherWith fadeOut(fastEffects) }, label = "bar") { selecting ->
                // Same bar type both ways: a shorter selection bar would shift the list under the finger mid drag-select.
                if (selecting) {
                    MediumFlexibleTopAppBar(
                        title = { Text(stringResource(R.string.selected_count, state.selection.size), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        subtitle = { Text(stringResource(state.category.titleRes), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = { IconButton(onClick = viewModel::clearSelection) { Icon(Icons.Rounded.Close, stringResource(R.string.cd_clear_selection)) } },
                        actions = {
                            IconButton(onClick = viewModel::selectAll) { Icon(Icons.Rounded.SelectAll, stringResource(R.string.action_select_all)) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        scrollBehavior = scrollBehavior
                    )
                } else {
                    MediumFlexibleTopAppBar(
                        title = { Text(stringResource(state.category.titleRes)) },
                        subtitle = {
                            if (!state.isLoading) {
                                Text(pluralStringResource(R.plurals.item_count, state.items.size, state.items.size) + " · " + formatSize(state.totalBytes))
                            }
                        },
                        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
                        actions = {
                            IconButton(onClick = viewModel::toggleGrid) {
                                Icon(
                                    if (state.gridView) Icons.Rounded.ViewAgenda else Icons.Rounded.GridView,
                                    stringResource(if (state.gridView) R.string.cd_list_view else R.string.cd_grid_view)
                                )
                            }
                        },
                        scrollBehavior = scrollBehavior
                    )
                }
            }
        }
    ) { padding ->
        val contentPadding = PaddingValues(
            start = readableGutter(), end = readableGutter(),
            top = padding.calculateTopPadding() + Dimens.sm,
            // Room for the floating selection toolbar over the last rows.
            bottom = padding.calculateBottomPadding() + 120.dp
        )
        Box(Modifier.fillMaxSize()) {
        when {
            state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            state.items.isEmpty() -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(Dimens.xxl)
            ) {
                ShapeIcon(
                    icon = state.category.icon,
                    shape = CategoryShape,
                    container = state.category.tone.container,
                    content = state.category.tone.onContainer,
                    size = 120.dp,
                    iconSize = 48.dp
                )
                Spacer(Modifier.height(Dimens.xl))
                Text(stringResource(R.string.category_empty), style = MaterialTheme.typography.titleLargeEmphasized)
            }
            state.gridView -> FileGrid(
                items = state.items,
                selection = state.selection,
                state = rememberLazyGridState(),
                contentPadding = contentPadding,
                onClick = ::open,
                onSelectionChange = viewModel::setSelection,
                header = {}
            )
            else -> FileList(
                items = state.items,
                selection = state.selection,
                state = rememberLazyListState(),
                contentPadding = contentPadding,
                onClick = ::open,
                onMore = { actionsFor = it },
                onSelectionChange = viewModel::setSelection,
                header = {}
            )
        }
        SelectionActions(
            selected = state.items.filter { it.path in state.selection },
            onClear = viewModel::clearSelection,
            onRoute = onOpenFile,
            onOpenArchive = onOpenArchive,
            onOpenFolder = onOpenFolder,
            onChanged = viewModel::load,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .offset(y = -FloatingToolbarDefaults.ScreenOffset)
        )
        }
    }

    actionsFor?.let { item ->
        ItemActionSheet(
            item = item,
            allowed = setOf(ItemAction.OPEN, ItemAction.OPEN_WITH, ItemAction.SHARE, ItemAction.SHOW_IN_FOLDER, ItemAction.DELETE),
            onDismiss = { actionsFor = null },
            onAction = { action ->
                actionsFor = null
                when (action) {
                    ItemAction.OPEN -> open(item)
                    ItemAction.OPEN_WITH -> {
                        viewModel.onOpened(item.path)
                        context.openFileWithChooser(item.path, item.size)
                    }
                    ItemAction.SHARE -> context.shareFiles(listOf(item.path))
                    ItemAction.SHOW_IN_FOLDER -> File(item.path).parent?.let(onOpenFolder)
                    ItemAction.DELETE -> deletePaths = listOf(item.path)
                    else -> Unit
                }
            }
        )
    }
    viewerInitialPath?.let { initialPath ->
        MediaViewerDialog(
            items = state.items,
            initialPath = initialPath,
            onDismiss = { viewerInitialPath = null },
            onDelete = { target -> deletePaths = listOf(target.path) }
        )
    }
    deletePaths?.let { paths ->
        DeleteDialog(paths.size, onConfirm = { permanent -> viewModel.delete(paths, permanent); deletePaths = null }, onDismiss = { deletePaths = null })
    }
}
