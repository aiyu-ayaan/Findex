package com.aiyu.findex.ui.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.FileThumbnail
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.components.titleRes
import com.aiyu.findex.ui.explorer.DeleteDialog
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.util.formatDate
import com.aiyu.findex.ui.util.formatSize
import java.io.File

private fun ageLabel(days: Int?): Int = when (days) {
    30 -> R.string.bigfiles_age_30d
    90 -> R.string.bigfiles_age_90d
    180 -> R.string.bigfiles_age_6mo
    365 -> R.string.bigfiles_age_1y
    else -> R.string.bigfiles_age_any
}

@Composable
fun LargeFilesScreen(onBack: () -> Unit, onOpenFolder: (String) -> Unit, viewModel: LargeFilesViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var confirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.bigfiles_title)) },
                subtitle = { Text(stringResource(R.string.bigfiles_hint_modified)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
                actions = {
                    IconButton(onClick = viewModel::selectAll, enabled = state.items.isNotEmpty()) {
                        Icon(Icons.Rounded.SelectAll, stringResource(R.string.action_select_all))
                    }
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
                    text = {
                        Text(pluralStringResource(R.plurals.bigfiles_selected, state.selected.size, state.selected.size, formatSize(state.selectedBytes)))
                    }
                )
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 120.dp
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "filters") { Filters(state, viewModel) }
            when {
                state.isScanning -> item(key = "loading") {
                    Row(Modifier.fillMaxWidth().padding(Dimens.xxl), horizontalArrangement = Arrangement.Center) {
                        LoadingIndicator(Modifier.size(72.dp))
                    }
                }
                state.items.isEmpty() -> item(key = "empty") {
                    Text(
                        stringResource(R.string.bigfiles_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(Dimens.xxl)
                    )
                }
                else -> items(state.items, key = { it.path }) { item ->
                    FileRow(
                        item = item,
                        checked = item.path in state.selected,
                        onToggle = { viewModel.toggle(item.path) },
                        onOpenFolder = { File(item.path).parent?.let(onOpenFolder) }
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
private fun Filters(state: LargeFilesUiState, vm: LargeFilesViewModel) {
    Column(Modifier.padding(horizontal = readableGutter()), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        ChipRow {
            LARGE_FILE_SIZES.forEach { bytes ->
                FilterChip(state.minBytes == bytes, { vm.setMinBytes(bytes) }, { Text("≥ " + formatSize(bytes)) })
            }
        }
        ChipRow {
            LARGE_FILE_AGES.forEach { days ->
                FilterChip(state.olderThanDays == days, { vm.setAge(days) }, { Text(stringResource(ageLabel(days))) })
            }
        }
        ChipRow {
            FilterChip(state.category == null, { vm.setCategory(null) }, { Text(stringResource(R.string.bigfiles_category_any)) })
            FileCategory.entries.filter { it != FileCategory.DOWNLOADS }.forEach { c ->
                FilterChip(state.category == c, { vm.setCategory(c) }, { Text(stringResource(c.titleRes)) })
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouch)
        ) {
            Text(stringResource(R.string.bigfiles_include_hidden), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(state.includeHidden, vm::setIncludeHidden)
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        modifier = Modifier.horizontalScroll(rememberScrollState())
    ) { content() }
}

@Composable
private fun FileRow(item: StorageItem, checked: Boolean, onToggle: () -> Unit, onOpenFolder: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(start = readableGutter(), end = Dimens.sm)
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(Dimens.md))
        FileThumbnail(item.path, false, item.extension, lastModified = item.lastModified)
        Spacer(Modifier.width(Dimens.md))
        Column(Modifier.weight(1f)) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(
                "${formatSize(item.size)} · ${formatDate(item.lastModified)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onOpenFolder) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = stringResource(R.string.action_show_in_folder))
        }
    }
}
