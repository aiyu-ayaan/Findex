package com.aiyu.findex.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
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

internal enum class DrawerAnchor { Closed, Open }

/** Open/closed state of a [PushDrawer]; always settles on an anchor, never mid-way. */
@Stable
class PushDrawerState internal constructor(internal val spec: AnimationSpec<Float>) {
    internal val anchored = AnchoredDraggableState(DrawerAnchor.Closed)
    internal var widthPx by mutableFloatStateOf(0f)

    /** 0 closed, 1 open; slightly above 1 while the spring overshoots. */
    val fraction: Float
        get() {
            val offset = anchored.offset
            return if (offset.isNaN() || widthPx == 0f) 0f else (offset / widthPx).coerceIn(0f, 1.1f)
        }
    val isOpen: Boolean get() = anchored.targetValue == DrawerAnchor.Open

    suspend fun open() = anchored.animateTo(DrawerAnchor.Open, spec)
    suspend fun close() = anchored.animateTo(DrawerAnchor.Closed, spec)
}

@Composable
fun rememberPushDrawerState(): PushDrawerState {
    // Expressive spatial spring: overshoots a little, so the card lands with an elastic bounce.
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
        Box(Modifier.width(drawerWidth).fillMaxHeight().then(hidden)) {
            // No Surface here, so set the content color a drawer sheet would have provided.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { drawerContent() }
        }

        SideEffect {
            if (state.widthPx != widthPx) {
                state.widthPx = widthPx
                state.anchored.updateAnchors(DraggableAnchors {
                    DrawerAnchor.Closed at 0f
                    DrawerAnchor.Open at widthPx
                })
            }
        }
        val fling = AnchoredDraggableDefaults.flingBehavior(
            state = state.anchored,
            positionalThreshold = { distance -> distance * 0.4f },
            animationSpec = state.spec
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = state.fraction
                    translationX = sign * p * widthPx
                    scaleX = 1f - 0.08f * p
                    scaleY = 1f - 0.08f * p
                    transformOrigin = TransformOrigin(if (rtl) 1f else 0f, 0.5f)
                    shadowElevation = 12.dp.toPx() * p
                    shape = if (p > 0f) cardShape else RoundedCornerShape(0.dp)
                    clip = p > 0f
                }
                .anchoredDraggable(
                    state = state.anchored,
                    reverseDirection = rtl,
                    orientation = Orientation.Horizontal,
                    enabled = gesturesEnabled || state.isOpen,
                    flingBehavior = fling
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
        val p = progress().coerceIn(0f, 1f)
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
