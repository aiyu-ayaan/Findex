package com.aiyu.findex.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import kotlinx.coroutines.launch

/** Open fraction of a [PushDrawer]: 0 closed, 1 open. */
@Stable
class PushDrawerState internal constructor(private val spec: FiniteAnimationSpec<Float>) {
    internal val progress = Animatable(0f)

    val fraction: Float get() = progress.value
    val isOpen: Boolean get() = progress.targetValue > 0.5f

    suspend fun open() = progress.animateTo(1f, spec)
    suspend fun close() = progress.animateTo(0f, spec)
}

@Composable
fun rememberPushDrawerState(): PushDrawerState {
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    return remember { PushDrawerState(spec) }
}

/**
 * Discord-style drawer: the drawer sits underneath and the whole screen slides aside as a
 * rounded, slightly shrunken card. Tap the card or swipe back to close.
 */
@Composable
fun PushDrawer(
    state: PushDrawerState,
    drawerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    gesturesEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val cardShape = MaterialTheme.shapes.extraLarge
    val scrim = MaterialTheme.colorScheme.scrim

    BackHandler(enabled = state.isOpen) { scope.launch { state.close() } }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        val drawerWidth = min(maxWidth * 0.82f, 360.dp)
        val widthPx = with(LocalDensity.current) { drawerWidth.toPx() }
        val sign = if (rtl) -1f else 1f

        // Hidden from accessibility while the card covers it.
        val hidden = if (state.fraction == 0f) Modifier.clearAndSetSemantics {} else Modifier
        Box(Modifier.width(drawerWidth).fillMaxHeight().then(hidden)) { drawerContent() }

        val dragState = rememberDraggableState { delta ->
            scope.launch { state.progress.snapTo((state.progress.value + sign * delta / widthPx).coerceIn(0f, 1f)) }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = state.progress.value
                    translationX = sign * p * widthPx
                    scaleX = 1f - 0.08f * p
                    scaleY = 1f - 0.08f * p
                    transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
                    shadowElevation = 12.dp.toPx() * p
                    shape = if (p > 0f) cardShape else RoundedCornerShape(0.dp)
                    clip = p > 0f
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Horizontal,
                    enabled = gesturesEnabled || state.isOpen,
                    onDragStopped = { velocity ->
                        val v = sign * velocity
                        val open = if (kotlin.math.abs(v) > 800f) v > 0 else state.progress.value > 0.5f
                        if (open) state.open() else state.close()
                    }
                )
        ) {
            content()
            if (state.fraction > 0f) {
                // Dims the card and swallows taps so the first tap only closes the drawer.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(scrim.copy(alpha = 0.24f * state.fraction))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            scope.launch { state.close() }
                        }
                )
            }
        }
    }
}

/**
 * Two-line menu icon with a short bottom stroke. [progress] (0..1) swaps the stroke lengths,
 * so the icon visibly reacts while the drawer opens.
 */
@Composable
fun MenuIcon(progress: () -> Float, modifier: Modifier = Modifier) {
    val color = LocalContentColor.current
    Canvas(modifier.size(24.dp)) {
        val p = progress()
        val stroke = 2.5.dp.toPx()
        val left = 3.dp.toPx()
        val long = 18.dp.toPx()
        val short = 11.dp.toPx()
        val top = long + (short - long) * p
        val bottom = short + (long - short) * p
        drawLine(color, Offset(left, size.height * 0.36f), Offset(left + top, size.height * 0.36f), stroke, StrokeCap.Round)
        drawLine(color, Offset(left, size.height * 0.64f), Offset(left + bottom, size.height * 0.64f), stroke, StrokeCap.Round)
    }
}
