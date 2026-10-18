package com.aiyu.findex.ui.explorer

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.ui.R
import com.aiyu.findex.ui.components.FileThumbnail
import com.aiyu.findex.ui.components.MorphingShapeIcon
import com.aiyu.findex.ui.components.groupedShape
import com.aiyu.findex.ui.theme.Dimens
import com.aiyu.findex.ui.util.formatDate
import com.aiyu.findex.ui.util.formatSize
import kotlinx.coroutines.delay

/**
 * Google Photos-style selection: long-press selects the item under the finger and keeps
 * selecting the range between that anchor and the finger while dragging, auto-scrolling near edges.
 */
private class DragSelectState {
    var anchor = -1
    var base: Set<String> = emptySet()
    var pointer: Offset? = null
    var viewportHeight = 0f
    var speed by mutableFloatStateOf(0f)
}

@Composable
private fun Modifier.dragToSelect(
    items: List<StorageItem>,
    selection: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    keyAt: (Offset) -> Any?,
    scrollBy: suspend (Float) -> Unit
): Modifier {
    val haptics = LocalHapticFeedback.current
    val state = remember { DragSelectState() }
    val currentItems by rememberUpdatedState(items)
    val currentSelection by rememberUpdatedState(selection)
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
    val maxSpeed = with(LocalDensity.current) { 18.dp.toPx() }

    fun indexAt(offset: Offset): Int {
        val key = keyAt(offset) as? String ?: return -1
        return currentItems.indexOfFirst { it.path == key }
    }

    fun applyRange() {
        val pointer = state.pointer ?: return
        val current = indexAt(pointer)
        if (state.anchor < 0 || current < 0) return
        val range = minOf(state.anchor, current)..maxOf(state.anchor, current)
        onSelectionChange(state.base + range.map { currentItems[it].path })
    }

    LaunchedEffect(state.speed) {
        while (state.speed != 0f) {
            scrollBy(state.speed)
            applyRange()
            delay(16)
        }
    }

    // Reads the Initial pass (before the rows) so that, once a long-press starts a selection,
    // the rest of the gesture is consumed: rows never see a click and the list doesn't scroll.
    return pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Non-null when the finger lifts or moves past touch slop before the long-press timeout.
            val endedEarly = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                do {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                } while (change != null && !change.changedToUp() &&
                    (change.position - down.position).getDistance() <= viewConfiguration.touchSlop)
            }
            if (endedEarly != null) return@awaitEachGesture // a tap or a scroll: leave it to the rows / list

            val index = indexAt(down.position)
            if (index < 0) return@awaitEachGesture
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            state.viewportHeight = size.height.toFloat()
            state.anchor = index
            state.base = currentSelection
            state.pointer = down.position
            onSelectionChange(currentSelection + currentItems[index].path)

            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                change.consume()
                if (change.changedToUp()) break
                state.pointer = change.position
                val y = change.position.y
                state.speed = when {
                    y < edge -> -maxSpeed * (1f - (y / edge).coerceIn(0f, 1f))
                    y > state.viewportHeight - edge -> maxSpeed * (1f - ((state.viewportHeight - y) / edge).coerceIn(0f, 1f))
                    else -> 0f
                }
                applyRange()
            }
            state.anchor = -1
            state.speed = 0f
        }
    }
}

private fun LazyListState.keyAt(offset: Offset): Any? {
    val y = offset.y.toInt() + layoutInfo.viewportStartOffset
    return layoutInfo.visibleItemsInfo.firstOrNull { y in it.offset until it.offset + it.size }?.key
}

private fun LazyGridState.keyAt(offset: Offset): Any? {
    val y = offset.y.toInt() + layoutInfo.viewportStartOffset
    // Row by y, then nearest column by x (robust to horizontal content padding).
    return layoutInfo.visibleItemsInfo
        .filter { y in it.offset.y until it.offset.y + it.size.height }
        .minByOrNull { kotlin.math.abs(it.offset.x + it.size.width / 2 - offset.x) }
        ?.key
}

@Composable
fun FileList(
    items: List<StorageItem>,
    selection: Set<String>,
    state: LazyListState,
    contentPadding: PaddingValues,
    onClick: (StorageItem) -> Unit,
    onMore: (StorageItem) -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    focused: String? = null,
    /** Replaces the size/date line, e.g. with the parent folder in search results. */
    subtitle: ((StorageItem) -> String)? = null
) {
    val inSelection = selection.isNotEmpty()
    LazyColumn(
        state = state,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(Dimens.xxs),
        modifier = modifier
            .fillMaxSize()
            .dragToSelect(items, selection, onSelectionChange, state::keyAt) { state.scrollBy(it) }
    ) {
        item(key = "__header") { header() }
        itemsIndexed(items, key = { _, item -> item.path }) { index, item ->
            FileRow(
                item = item,
                selected = item.path in selection,
                focused = item.path == focused,
                inSelection = inSelection,
                shape = groupedShape(index, items.size),
                onClick = { onClick(item) },
                onLongClick = { onSelectionChange(selection + item.path) },
                onMore = { onMore(item) },
                subtitle = subtitle?.invoke(item),
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
fun FileGrid(
    items: List<StorageItem>,
    selection: Set<String>,
    state: LazyGridState,
    contentPadding: PaddingValues,
    onClick: (StorageItem) -> Unit,
    onSelectionChange: (Set<String>) -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    focused: String? = null
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        state = state,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
        verticalArrangement = Arrangement.spacedBy(Dimens.sm),
        modifier = modifier
            .fillMaxSize()
            .dragToSelect(items, selection, onSelectionChange, state::keyAt) { state.scrollBy(it) }
    ) {
        item(key = "__header", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) { header() }
        itemsIndexed(items, key = { _, item -> item.path }) { _, item ->
            FileCell(
                item = item,
                selected = item.path in selection,
                focused = item.path == focused,
                onClick = { onClick(item) },
                onLongClick = { onSelectionChange(selection + item.path) },
                modifier = Modifier.animateItem()
            )
        }
    }
}

@Composable
private fun FileRow(
    item: StorageItem,
    selected: Boolean,
    inSelection: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    focused: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    val container by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            focused -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "rowColor"
    )
    val border = if (focused && !selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
    } else null
    Surface(
        shape = shape,
        color = container,
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            // onLongClick stops the release after a long-press from also firing onClick (which would deselect).
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { this.selected = selected }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .heightIn(min = 64.dp)
                .padding(start = Dimens.md, end = Dimens.xs, top = Dimens.sm, bottom = Dimens.sm)
        ) {
            Box(contentAlignment = Alignment.Center) {
                FileThumbnail(item.path, item.isDirectory, item.extension)
                SelectionBadge(selected)
            }
            Spacer(Modifier.width(Dimens.lg))
            Column(Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
                Text(
                    text = subtitle ?: metaLine(item),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis
                )
            }
            if (!inSelection) {
                IconButton(onClick = onMore) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.cd_more_actions, item.name))
                }
            } else {
                Spacer(Modifier.size(Dimens.minTouch))
            }
        }
    }
}

/** Check mark that morphs in over the thumbnail when an item is selected. */
@Composable
private fun SelectionBadge(selected: Boolean) {
    val alpha by animateFloatAsState(if (selected) 1f else 0f, MaterialTheme.motionScheme.fastEffectsSpec(), label = "badge")
    if (alpha == 0f) return
    MorphingShapeIcon(
        icon = Icons.Rounded.Check,
        from = MaterialShapes.Square,
        to = MaterialShapes.Cookie9Sided,
        morphed = selected,
        container = MaterialTheme.colorScheme.primary,
        content = MaterialTheme.colorScheme.onPrimary,
        size = 44.dp,
        iconSize = 24.dp,
        modifier = Modifier.graphicsLayer { this.alpha = alpha }
    )
}

@Composable
private fun FileCell(
    item: StorageItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    focused: Boolean = false
) {
    val container by animateColorAsState(
        when {
            selected -> MaterialTheme.colorScheme.primaryContainer
            focused -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "cellColor"
    )
    val border = if (focused && !selected) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
    } else null
    Surface(
        shape = MaterialTheme.shapes.large,
        color = container,
        border = border,
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { this.selected = selected }
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(Dimens.sm)) {
            Box(contentAlignment = Alignment.Center) {
                FileThumbnail(item.path, item.isDirectory, item.extension, size = 72.dp)
                if (selected) {
                    MorphingShapeIcon(
                        icon = Icons.Rounded.Check,
                        from = MaterialShapes.Circle,
                        to = MaterialShapes.Cookie9Sided,
                        morphed = true,
                        container = MaterialTheme.colorScheme.primary,
                        content = MaterialTheme.colorScheme.onPrimary,
                        size = 32.dp,
                        iconSize = 18.dp,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }
            Spacer(Modifier.size(Dimens.sm))
            Text(
                item.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun metaLine(item: StorageItem): String {
    val date = formatDate(item.lastModified)
    return if (item.isDirectory) {
        pluralStringResource(R.plurals.item_count, item.itemCount, item.itemCount) + " · " + date
    } else {
        formatSize(item.size) + " · " + date
    }
}
