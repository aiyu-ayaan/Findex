package com.aiyu.findex.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.aiyu.findex.ui.theme.Dimens
import kotlinx.coroutines.delay

/**
 * Expressive grouped-list shape: large outer corners on the first/last row of a group,
 * small inner corners between rows (DESIGN_SYSTEM.md §4).
 */
fun groupedShape(index: Int, count: Int): Shape {
    val outer = 20.dp
    val inner = 4.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner
    )
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = Dimens.xs, top = Dimens.md, bottom = Dimens.xs)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface
        )
        action?.invoke()
    }
}

/** Remembers which list items already played their entrance, so scrolling back doesn't replay it. */
class StaggerState {
    internal val played = mutableSetOf<Any>()
}

@Composable
fun rememberStaggerState() = remember { StaggerState() }

/** Fade + rise entrance, 30 ms apart per index (max 10 staggered) — DESIGN_SYSTEM.md §5. */
fun Modifier.staggeredEnter(state: StaggerState, key: Any, index: Int): Modifier = composed {
    val alreadyPlayed = remember(key) { key in state.played }
    val progress = remember(key) { Animatable(if (alreadyPlayed) 1f else 0f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(key) {
        if (alreadyPlayed) return@LaunchedEffect
        state.played += key
        delay(30L * index.coerceAtMost(10))
        progress.animateTo(1f, spec)
    }
    graphicsLayer {
        alpha = progress.value.coerceIn(0f, 1f)
        translationY = (1f - progress.value) * 24.dp.toPx()
    }
}

/**
 * Settings row with the icon centred against the whole text block. M3 `ListItem` top-aligns the
 * leading icon once the supporting text wraps to a third line.
 */
@Composable
fun SettingItem(
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable () -> Unit,
    leadingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.lg),
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .heightIn(min = 56.dp)
            .padding(horizontal = Dimens.lg, vertical = Dimens.md)
    ) {
        leadingContent()
        Column(Modifier.weight(1f)) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) { headlineContent() }
            ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)) { supportingContent() }
        }
        trailingContent?.invoke()
    }
}
