package com.aiyu.findex.ui.dualpane

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Rule
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.core.compare.CompareStatus
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.network.SmbPath
import com.aiyu.findex.ui.LocalSnackbarHostState
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.OperationBanner
import com.aiyu.findex.ui.explorer.DeleteDialog
import com.aiyu.findex.ui.explorer.FileList
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.util.formatSize
import com.aiyu.findex.ui.util.openFile

@Composable
fun DualPaneScreen(onBack: () -> Unit, viewModel: DualPaneViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbarHostState.current
    var confirmDelete by remember { mutableStateOf(false) }
    val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val inSelection = state.selection.isNotEmpty()

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(context.getString(it)) } }

    // Back: clear selection → active pane goes up → leave the screen.
    PredictiveBackHandler(enabled = inSelection || state.activePane.canGoUp) { events ->
        events.collect { }
        if (inSelection) viewModel.clearSelection() else viewModel.goUp()
    }

    fun open(index: Int, item: StorageItem) {
        when {
            state.panes[index].selection.isNotEmpty() -> viewModel.toggleSelection(index, item.path)
            item.isDirectory -> viewModel.openFolder(index, item.path)
            else -> { viewModel.activate(index); context.openFile(item.path, item.size) }
        }
    }

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = inSelection,
                transitionSpec = { fadeIn(fastEffects) togetherWith fadeOut(fastEffects) },
                label = "dualTopBar"
            ) { selecting ->
                if (selecting) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.selected_count, state.selection.size), style = MaterialTheme.typography.titleLargeEmphasized) },
                        navigationIcon = { IconButton(onClick = viewModel::clearSelection) { Icon(Icons.Rounded.Close, stringResource(R.string.cd_clear_selection)) } },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    )
                } else {
                    TopAppBar(
                        title = { Text(stringResource(R.string.nav_dual_pane), style = MaterialTheme.typography.titleLargeEmphasized) },
                        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
                        actions = {
                            if (state.comparing) {
                                IconButton(onClick = viewModel::selectMissing) { Icon(Icons.AutoMirrored.Rounded.Rule, stringResource(R.string.dual_select_missing)) }
                            }
                            FilledIconToggleButton(checked = state.comparing, onCheckedChange = viewModel::setComparing) {
                                Icon(Icons.Rounded.Difference, stringResource(R.string.dual_compare))
                            }
                            IconButton(onClick = viewModel::swap) { Icon(Icons.Rounded.SwapHoriz, stringResource(R.string.dual_swap)) }
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = state.activeOperation != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    state.activeOperation?.let {
                        OperationBanner(it, onCancel = viewModel::cancelOperation, modifier = Modifier.padding(horizontal = Dimens.gutter, vertical = Dimens.xs))
                    }
                }
                // Side by side when wider than tall (landscape, tablets), stacked on phones in portrait.
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val horizontal = maxWidth > maxHeight
                    val paneModifier = Modifier.padding(Dimens.xs)
                    val panes: @Composable (Modifier) -> Unit = { weight ->
                        state.panes.forEachIndexed { index, pane ->
                            PaneView(
                                pane = pane,
                                active = index == state.active,
                                comparing = state.comparing,
                                onActivate = { viewModel.activate(index) },
                                onUp = { viewModel.activate(index); viewModel.goUp(index) },
                                onClick = { open(index, it) },
                                onSelectionChange = { viewModel.setSelection(index, it) },
                                modifier = weight.then(paneModifier)
                            )
                        }
                    }
                    if (horizontal) Row(Modifier.fillMaxSize().padding(horizontal = Dimens.xs)) { panes(Modifier.weight(1f)) }
                    else Column(Modifier.fillMaxSize().padding(horizontal = Dimens.xs)) { panes(Modifier.weight(1f)) }
                }
            }

            AnimatedVisibility(
                visible = inSelection,
                enter = fadeIn(fastEffects) + expandVertically(),
                exit = fadeOut(fastEffects) + shrinkVertically(),
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = Dimens.lg)
            ) {
                HorizontalFloatingToolbar(expanded = true, colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()) {
                    IconButton(onClick = { viewModel.transferToOther(move = false) }) { Icon(Icons.Rounded.ContentCopy, stringResource(R.string.dual_copy_to)) }
                    IconButton(onClick = { viewModel.transferToOther(move = true) }) { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, stringResource(R.string.dual_move_to)) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, stringResource(R.string.action_delete)) }
                }
            }
        }
    }

    if (confirmDelete) {
        DeleteDialog(
            count = state.selection.size,
            // Shares have no recycle bin.
            permanentOnly = SmbPath.parse(state.activePane.root) != null,
            onConfirm = { permanent -> confirmDelete = false; viewModel.delete(permanent) },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun PaneView(
    pane: Pane,
    active: Boolean,
    comparing: Boolean,
    onActivate: () -> Unit,
    onUp: () -> Unit,
    onClick: (StorageItem) -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "paneBorder"
    )
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(if (active) 2.dp else 1.dp, borderColor),
        modifier = modifier.fillMaxSize()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onActivate).padding(start = Dimens.xs, end = Dimens.md)
            ) {
                IconButton(onClick = onUp, enabled = pane.canGoUp) { Icon(Icons.Rounded.ArrowUpward, stringResource(R.string.dual_up)) }
                Column(Modifier.weight(1f)) {
                    Text(
                        pane.title,
                        style = if (active) MaterialTheme.typography.titleSmallEmphasized else MaterialTheme.typography.titleSmall,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        pane.path.removePrefix(pane.root).ifEmpty { "/" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis
                    )
                }
            }
            Box(Modifier.weight(1f)) {
                // A fresh list state per folder, so every folder opens at the top.
                key(pane.path) {
                    FileList(
                        items = pane.items,
                        selection = pane.selection,
                        state = rememberLazyListState(),
                        contentPadding = PaddingValues(start = Dimens.xs, end = Dimens.xs, bottom = 96.dp),
                        onClick = onClick,
                        onMore = { onSelectionChange(pane.selection + it.path) },
                        onSelectionChange = onSelectionChange,
                        header = {},
                        subtitle = if (comparing) { item -> pane.status[item.path]?.let { compareLine(it, item) } } else null
                    )
                }
                if (pane.loading && pane.items.isEmpty()) LoadingIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun compareLine(status: CompareStatus, item: StorageItem): String {
    val label = stringResource(
        when (status) {
            CompareStatus.ONLY_HERE -> R.string.cmp_only_here
            CompareStatus.NEWER -> R.string.cmp_newer
            CompareStatus.OLDER -> R.string.cmp_older
            CompareStatus.DIFFERENT -> R.string.cmp_different
            CompareStatus.SAME -> R.string.cmp_same
        }
    )
    return if (item.isDirectory) label else label + " · " + formatSize(item.size)
}
