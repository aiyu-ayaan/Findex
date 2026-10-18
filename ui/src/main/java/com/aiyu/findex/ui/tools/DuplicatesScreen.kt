package com.aiyu.findex.ui.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.scanner.DuplicateSet
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.FileThumbnail
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.explorer.DeleteDialog
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.util.formatDate
import com.aiyu.findex.ui.util.formatSize
import java.io.File

@Composable
fun DuplicatesScreen(onBack: () -> Unit, onOpenFolder: (String) -> Unit, viewModel: DuplicatesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirm by remember { mutableStateOf(false) }
    val selectedSize = formatSize(state.selectedBytes)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.dup_title)) },
                subtitle = { Text(stringResource(R.string.dup_desc)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
                actions = {
                    IconButton(onClick = viewModel::rescan, enabled = !state.isScanning) {
                        Icon(Icons.Rounded.Refresh, stringResource(R.string.tool_rescan))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            AnimatedVisibility(visible = !state.isScanning && state.selected.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { confirm = true },
                    icon = { Icon(Icons.Rounded.Delete, null) },
                    text = { Text(stringResource(R.string.dup_delete_button, selectedSize)) }
                )
            }
        }
    ) { padding ->
        when {
            state.isScanning -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                LoadingIndicator(Modifier.size(96.dp))
                Spacer(Modifier.height(Dimens.lg))
                Text(stringResource(R.string.dup_hashing), style = MaterialTheme.typography.titleMedium)
            }
            state.sets.isEmpty() -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(padding).padding(Dimens.xxl)
            ) {
                ShapeIcon(
                    icon = Icons.Rounded.TaskAlt,
                    shape = MaterialShapes.Cookie12Sided,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    size = 120.dp,
                    iconSize = 52.dp
                )
                Spacer(Modifier.height(Dimens.xl))
                Text(stringResource(R.string.tool_all_clean_title), style = MaterialTheme.typography.headlineSmallEmphasized)
                Text(stringResource(R.string.dup_none), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = readableGutter(), end = readableGutter(),
                    top = padding.calculateTopPadding() + Dimens.sm,
                    bottom = padding.calculateBottomPadding() + 120.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Dimens.md)
            ) {
                item(key = "summary") {
                    Card(
                        shape = MaterialTheme.shapes.extraLargeIncreased,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(Dimens.xl)) {
                            Text(selectedSize, style = MaterialTheme.typography.displayMediumEmphasized)
                            Text(stringResource(R.string.tool_can_free), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(Dimens.sm))
                            Text(
                                stringResource(R.string.tool_selected_of, selectedSize, formatSize(state.wastedBytes)),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(stringResource(R.string.dup_keep_hint), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                items(state.sets, key = { it.hash }) { set ->
                    DuplicateSetCard(
                        set = set,
                        selected = state.selected,
                        onToggle = { viewModel.toggle(set, it) },
                        onOpenFolder = onOpenFolder
                    )
                }
            }
        }
    }

    if (confirm) {
        DeleteDialog(
            count = state.selected.size,
            onConfirm = { permanent -> confirm = false; viewModel.delete(permanent) },
            onDismiss = { confirm = false }
        )
    }
}

@Composable
private fun DuplicateSetCard(
    set: DuplicateSet,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onOpenFolder: (String) -> Unit
) {
    val first = set.files.first()
    val keptCount = set.files.count { it.path !in selected }
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = Dimens.lg, end = Dimens.lg, top = Dimens.md, bottom = Dimens.sm)
            ) {
                FileThumbnail(first.path, false, first.extension)
                Spacer(Modifier.width(Dimens.md))
                Column(Modifier.weight(1f)) {
                    Text(first.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                    Text(
                        "${formatSize(set.size)} · " + pluralStringResource(R.plurals.dup_copies, set.files.size, set.files.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            set.files.forEach { file ->
                val marked = file.path in selected
                CopyRow(
                    file = file,
                    marked = marked,
                    // The last kept copy can't be marked.
                    enabled = marked || keptCount > 1,
                    onToggle = { onToggle(file.path) },
                    onOpenFolder = { File(file.path).parent?.let(onOpenFolder) }
                )
            }
        }
    }
}

@Composable
private fun CopyRow(file: StorageItem, marked: Boolean, enabled: Boolean, onToggle: () -> Unit, onOpenFolder: () -> Unit) {
    val container by animateColorAsState(
        if (marked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceContainerLow,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "copyColor"
    )
    Surface(color = container) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .toggleable(value = marked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
                .padding(start = Dimens.xs, end = Dimens.sm)
        ) {
            Checkbox(checked = marked, onCheckedChange = null, enabled = enabled, modifier = Modifier.padding(Dimens.md))
            Column(Modifier.weight(1f)) {
                Text(
                    File(file.path).parent.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
                Text(formatDate(file.lastModified), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onOpenFolder) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = stringResource(R.string.action_show_in_folder))
            }
        }
    }
}
