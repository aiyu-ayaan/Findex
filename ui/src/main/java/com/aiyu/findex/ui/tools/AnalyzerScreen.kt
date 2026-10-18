package com.aiyu.findex.ui.tools

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.readableGutter
import com.aiyu.findex.ui.components.FileThumbnail
import com.aiyu.findex.ui.components.SectionHeader
import com.aiyu.findex.ui.components.ShapeIcon
import com.aiyu.findex.ui.components.groupedShape
import com.aiyu.findex.ui.components.icon
import com.aiyu.findex.ui.components.CategoryShape
import com.aiyu.findex.ui.components.titleRes
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.theme.tone
import com.aiyu.findex.ui.util.formatSize
import java.io.File

private data class Slice(val label: String, val bytes: Long, val color: Color)

@Composable
fun AnalyzerScreen(
    onBack: () -> Unit,
    onOpenFolder: (String) -> Unit,
    viewModel: AnalyzerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val drive = state.drive

    val categories = FileCategory.entries.filter { it != FileCategory.DOWNLOADS }
    val slices = categories.mapNotNull { c ->
        state.stats[c]?.takeIf { it.bytes > 0 }?.let { Slice(stringResource(c.titleRes), it.bytes, c.tone.accent) }
    } + Slice(stringResource(R.string.analyzer_other), state.otherBytes, MaterialTheme.colorScheme.secondary)

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text(stringResource(R.string.storage_graph_title)) },
                subtitle = { drive?.let { Text(it.name) } },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) } },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        // Wait for the drive: inserting the chart above an already-shown list would leave it pre-scrolled.
        if (drive == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                start = readableGutter(), end = readableGutter(),
                top = padding.calculateTopPadding() + Dimens.sm,
                bottom = padding.calculateBottomPadding() + Dimens.xxl
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.xxs)
        ) {
            if (drive != null) {
                item(key = "chart") {
                    Card(
                        shape = MaterialTheme.shapes.extraLargeIncreased,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        modifier = Modifier.fillMaxWidth().padding(bottom = Dimens.md)
                    ) {
                        Box(Modifier.fillMaxWidth().padding(Dimens.xl), contentAlignment = Alignment.Center) {
                            val desc = stringResource(R.string.analyzer_chart_desc, formatSize(drive.usedBytes), formatSize(drive.totalBytes))
                            Donut(
                                slices = slices,
                                total = drive.totalBytes,
                                track = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(220.dp).semantics { contentDescription = desc }
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${(drive.usedPercentage * 100).toInt()}%", style = MaterialTheme.typography.displayMediumEmphasized)
                                Text(stringResource(R.string.analyzer_used), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(formatSize(drive.freeBytes) + " " + stringResource(R.string.analyzer_free).lowercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                item(key = "breakdownHeader") { SectionHeader(stringResource(R.string.analyzer_breakdown)) }
                val rows = categories.filter { (state.stats[it]?.bytes ?: 0) > 0 }
                itemsIndexed(rows, key = { _, c -> c.name }) { index, c ->
                    val bytes = state.stats[c]?.bytes ?: 0
                    BreakdownRow(
                        leading = { ShapeIcon(c.icon, CategoryShape, c.tone.container, c.tone.onContainer, size = 40.dp, iconSize = 20.dp) },
                        title = stringResource(c.titleRes),
                        bytes = bytes,
                        fraction = bytes.toFloat() / drive.usedBytes.coerceAtLeast(1),
                        color = c.tone.accent,
                        shape = groupedShape(index, rows.size + 1)
                    )
                }
                item(key = "other") {
                    BreakdownRow(
                        leading = {
                            ShapeIcon(Icons.Rounded.Apps, MaterialShapes.Cookie4Sided, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurfaceVariant, size = 40.dp, iconSize = 20.dp)
                        },
                        title = stringResource(R.string.analyzer_other),
                        bytes = state.otherBytes,
                        fraction = state.otherBytes.toFloat() / drive.usedBytes.coerceAtLeast(1),
                        color = MaterialTheme.colorScheme.secondary,
                        shape = groupedShape(rows.size, rows.size + 1)
                    )
                }
            }

            item(key = "largestHeader") { SectionHeader(stringResource(R.string.analyzer_largest)) }
            if (state.isScanning) {
                item(key = "scanning") {
                    Column(Modifier.fillMaxWidth().padding(Dimens.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                        LoadingIndicator()
                        Text(stringResource(R.string.scanning), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                itemsIndexed(state.largest, key = { _, f -> f.path }) { index, file ->
                    ListItem(
                        headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
                        supportingContent = {
                            val parent = File(file.path).parentFile
                            val folder = if (parent?.absolutePath == drive?.path) drive?.name else parent?.name
                            Text(folder.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = { FileThumbnail(file.path, false, file.extension, lastModified = file.lastModified) },
                        trailingContent = { Text(formatSize(file.size), style = MaterialTheme.typography.labelLarge) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        modifier = Modifier
                            .clip(groupedShape(index, state.largest.size))
                            .clickable { File(file.path).parent?.let(onOpenFolder) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(
    leading: @Composable () -> Unit,
    title: String,
    bytes: Long,
    fraction: Float,
    color: Color,
    shape: androidx.compose.ui.graphics.Shape
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Spacer(Modifier.height(Dimens.xs))
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    color = color,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth().height(8.dp)
                )
            }
        },
        leadingContent = leading,
        trailingContent = { Text(formatSize(bytes), style = MaterialTheme.typography.labelLarge) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clip(shape)
    )
}

/** Segmented donut with small gaps, sweeping in on first show. */
@Composable
private fun Donut(slices: List<Slice>, total: Long, track: Color, modifier: Modifier = Modifier) {
    val sweep = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) { sweep.animateTo(1f, spec) }
    Canvas(modifier) {
        val strokeWidth = 28.dp.toPx()
        val inset = strokeWidth / 2
        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
        val topLeft = Offset(inset, inset)
        drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(strokeWidth))
        val gap = 2f
        var start = -90f
        val visible = slices.filter { it.bytes > 0 }
        val usedAngle = 360f * visible.sumOf { it.bytes } / total.coerceAtLeast(1).toFloat()
        // Small categories still get a visible arc; the space is taken proportionally from the larger ones.
        val minAngle = 6f
        val raw = visible.map { usedAngle * it.bytes / visible.sumOf { s -> s.bytes }.coerceAtLeast(1) }
        val boosted = raw.map { maxOf(it, minAngle) }
        val scale = usedAngle / boosted.sum().coerceAtLeast(1f)
        visible.forEachIndexed { i, slice ->
            val angle = boosted[i] * minOf(1f, scale).let { if (raw[i] < minAngle) 1f else it } * sweep.value
            if (angle > gap) {
                drawArc(slice.color, start + gap / 2, angle - gap, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Butt))
            }
            start += angle
        }
    }
}

