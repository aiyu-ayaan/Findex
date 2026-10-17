package com.aiyu.findex.ui.category

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
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.components.icon
import com.aiyu.findex.ui.components.CategoryShape
import com.aiyu.findex.ui.components.titleRes
import com.aiyu.findex.ui.explorer.DeleteDialog
import com.aiyu.findex.ui.explorer.FileGrid
import com.aiyu.findex.ui.explorer.FileList
import com.aiyu.findex.ui.explorer.ItemAction
import com.aiyu.findex.ui.explorer.ItemActionSheet
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.theme.tone
import com.aiyu.findex.ui.util.formatSize
import com.aiyu.findex.ui.util.openFile
import com.aiyu.findex.ui.util.shareFiles
import java.io.File

@Composable
fun CategoryScreen(
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    viewModel: CategoryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var actionsFor by remember { mutableStateOf<StorageItem?>(null) }
    var deletePaths by remember { mutableStateOf<List<String>?>(null) }
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    BackHandler(enabled = state.selection.isNotEmpty()) { viewModel.clearSelection() }

    fun open(item: StorageItem) {
        if (state.selection.isNotEmpty()) viewModel.toggle(item.path)
        else {
            viewModel.onOpened(item.path)
            context.openFile(item.path)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            AnimatedContent(state.selection.isNotEmpty(), transitionSpec = { fadeIn(fastEffects) togetherWith fadeOut(fastEffects) }, label = "bar") { selecting ->
                if (selecting) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.selected_count, state.selection.size)) },
                        navigationIcon = { IconButton(onClick = viewModel::clearSelection) { Icon(Icons.Rounded.Close, stringResource(R.string.cd_clear_selection)) } },
                        actions = {
                            IconButton(onClick = viewModel::selectAll) { Icon(Icons.Rounded.SelectAll, stringResource(R.string.action_select_all)) }
                            IconButton(onClick = { context.shareFiles(state.selection.toList()) }) { Icon(Icons.Rounded.Share, stringResource(R.string.action_share)) }
                            IconButton(onClick = viewModel::copySelection) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.action_copy)) }
                            IconButton(onClick = viewModel::cutSelection) { Icon(Icons.Rounded.ContentCut, stringResource(R.string.action_cut)) }
                            IconButton(onClick = { deletePaths = state.selection.toList() }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
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
            start = Dimens.gutter, end = Dimens.gutter,
            top = padding.calculateTopPadding() + Dimens.sm,
            bottom = padding.calculateBottomPadding() + Dimens.xxl
        )
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
    }

    actionsFor?.let { item ->
        ItemActionSheet(
            item = item,
            allowed = setOf(ItemAction.OPEN, ItemAction.SHARE, ItemAction.SHOW_IN_FOLDER, ItemAction.DELETE),
            onDismiss = { actionsFor = null },
            onAction = { action ->
                actionsFor = null
                when (action) {
                    ItemAction.OPEN -> open(item)
                    ItemAction.SHARE -> context.shareFiles(listOf(item.path))
                    ItemAction.SHOW_IN_FOLDER -> File(item.path).parent?.let(onOpenFolder)
                    ItemAction.DELETE -> deletePaths = listOf(item.path)
                    else -> Unit
                }
            }
        )
    }
    deletePaths?.let { paths ->
        DeleteDialog(paths.size, onConfirm = { viewModel.delete(paths); deletePaths = null }, onDismiss = { deletePaths = null })
    }
}
