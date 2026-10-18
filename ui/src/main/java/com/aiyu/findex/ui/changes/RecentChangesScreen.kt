package com.aiyu.findex.ui.changes

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.FileThumbnail
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.components.titleRes
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.util.formatSize
import com.aiyu.findex.ui.util.openFile
import com.aiyu.findex.ui.viewer.InAppViewers
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentChangesScreen(
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenFile: (Any) -> Unit = {},
    viewModel: RecentChangesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val today = androidx.compose.runtime.remember { LocalDate.now() }
    val grouped = state.items.groupBy { LocalDate.ofInstant(java.time.Instant.ofEpochMilli(it.lastModified), ZoneId.systemDefault()) }.toSortedMap(compareByDescending { it })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.changes_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = readableGutter()), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                listOf(1 to R.string.changes_range_1d, 7 to R.string.changes_range_7d, 30 to R.string.changes_range_30d).forEach { (d, label) ->
                    FilterChip(state.days == d, { viewModel.setDays(d) }, { Text(stringResource(label)) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = readableGutter()), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                FileCategory.entries.filter { it != FileCategory.DOWNLOADS }.forEach { c ->
                    FilterChip(state.category == c, { viewModel.setCategory(if (state.category == c) null else c) }, { Text(stringResource(c.titleRes)) })
                }
            }
            Box(Modifier.fillMaxSize()) {
                when {
                    state.loading -> LoadingIndicator(Modifier.align(Alignment.Center))
                    grouped.isEmpty() -> Text(stringResource(R.string.changes_empty), Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> LazyColumn(contentPadding = PaddingValues(start = readableGutter(), end = readableGutter(), bottom = padding.calculateBottomPadding() + Dimens.xxl)) {
                        grouped.forEach { (day, files) ->
                            stickyHeader(key = "h$day") {
                                Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface) {
                                    Text(
                                        dayLabel(day, today), Modifier.padding(vertical = Dimens.sm, horizontal = Dimens.lg),
                                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            items(files, key = { it.path }) { item ->
                                ListItem(
                                    headlineContent = { Text(item.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                                    supportingContent = { Text(formatSize(item.size) + " · " + File(item.path).parent.orEmpty(), maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                                    leadingContent = { FileThumbnail(item.path, false, item.extension, lastModified = item.lastModified) },
                                    trailingContent = { IconButton(onClick = { File(item.path).parent?.let(onOpenFolder) }) { Icon(Icons.Rounded.FolderOpen, stringResource(R.string.action_show_in_folder)) } },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier.clip(MaterialTheme.shapes.large).clickable {
                                        InAppViewers.routeFor(item)?.let(onOpenFile) ?: context.openFile(item.path, item.size)
                                    }
                                )
                            }
                        }
                        if (state.limited) item("limit") { Text(stringResource(R.string.changes_limit_hint), Modifier.padding(Dimens.lg), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@Composable
private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> stringResource(R.string.changes_today)
    today.minusDays(1) -> stringResource(R.string.changes_yesterday)
    else -> DateUtils.formatDateTime(LocalContext.current, day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(), DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_WEEKDAY)
}

