package com.aiyu.findex.ui.trash

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.SelectAll
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.CategoryShape
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.explorer.DeleteDialog
import com.aiyu.findex.ui.explorer.FileList
import com.aiyu.findex.ui.explorer.ItemAction
import com.aiyu.findex.ui.explorer.ItemActionSheet
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.util.formatSize

@Composable
fun RecycleBinScreen(
    onBack: () -> Unit,
    viewModel: RecycleBinViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var actionsFor by remember { mutableStateOf<StorageItem?>(null) }
    var deletePaths by remember { mutableStateOf<List<String>?>(null) }
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    BackHandler(enabled = state.selection.isNotEmpty()) { viewModel.clearSelection() }

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
                            IconButton(onClick = { viewModel.restore(state.selection) }) { Icon(Icons.Rounded.RestoreFromTrash, stringResource(R.string.action_restore)) }
                            IconButton(onClick = { deletePaths = state.selection.toList() }) { Icon(Icons.Rounded.DeleteForever, stringResource(R.string.delete_permanently)) }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    )
                } else {
                    MediumFlexibleTopAppBar(
                        title = { Text(stringResource(R.string.nav_recycle_bin)) },
                        subtitle = { Text(stringResource(R.string.recycle_bin_hint)) },
                        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
                        actions = {
                            if (state.entries.isNotEmpty()) {
                                IconButton(onClick = { deletePaths = state.entries.map { it.first.trashPath } }) {
                                    Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.action_empty_bin))
                                }
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
            state.entries.isEmpty() -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(Dimens.xxl)
            ) {
                ShapeIcon(
                    icon = Icons.Rounded.Delete,
                    shape = CategoryShape,
                    container = MaterialTheme.colorScheme.secondaryContainer,
                    content = MaterialTheme.colorScheme.onSecondaryContainer,
                    size = 120.dp,
                    iconSize = 48.dp
                )
                Spacer(Modifier.height(Dimens.xl))
                Text(stringResource(R.string.recycle_bin_empty), style = MaterialTheme.typography.titleLargeEmphasized)
            }
            else -> FileList(
                items = state.items,
                selection = state.selection,
                state = rememberLazyListState(),
                contentPadding = contentPadding,
                // Bin files can't be opened in place; a tap shows what can be done with them.
                onClick = { if (state.selection.isNotEmpty()) viewModel.toggle(it.path) else actionsFor = it },
                onMore = { actionsFor = it },
                onSelectionChange = viewModel::setSelection,
                header = {
                    Text(
                        formatSize(state.totalBytes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Dimens.xs, bottom = Dimens.sm)
                    )
                }
            )
        }
    }

    actionsFor?.let { item ->
        ItemActionSheet(
            item = item,
            allowed = setOf(ItemAction.RESTORE, ItemAction.DELETE),
            onDismiss = { actionsFor = null },
            onAction = { action ->
                actionsFor = null
                when (action) {
                    ItemAction.RESTORE -> viewModel.restore(listOf(item.path))
                    ItemAction.DELETE -> deletePaths = listOf(item.path)
                    else -> Unit
                }
            }
        )
    }
    deletePaths?.let { paths ->
        DeleteDialog(
            count = paths.size,
            permanentOnly = true,
            onConfirm = { viewModel.deleteForever(paths); deletePaths = null },
            onDismiss = { deletePaths = null }
        )
    }
}
