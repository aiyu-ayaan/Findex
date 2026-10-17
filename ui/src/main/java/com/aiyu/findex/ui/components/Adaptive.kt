package com.aiyu.findex.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import com.aiyu.findex.ui.theme.Dimens

/** Window width breakpoint for Material's "expanded" class: tablets in landscape, unfolded foldables, desktop windows. */
val ExpandedWidth = 840.dp

/** Width left for a screen's content after side panels (permanent drawer, tab rail); null = whole window. */
val LocalContentWidth = compositionLocalOf<Dp?> { null }

/** Measures the available width and exposes it as [LocalContentWidth] to [content]. */
@Composable
fun ProvideContentWidth(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        CompositionLocalProvider(LocalContentWidth provides maxWidth) { content() }
    }
}

@Composable
fun windowWidth(): Dp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }

/** Expanded windows get a permanent drawer and the explorer's tab rail. */
@Composable
fun isExpandedWidth(): Boolean = windowWidth() >= ExpandedWidth

/**
 * Horizontal list padding that caps content at [Dimens.contentMaxWidth] on wide windows,
 * so the list still scrolls edge to edge instead of shrinking into a centred column.
 */
@Composable
fun readableGutter(): Dp = max(Dimens.gutter, ((LocalContentWidth.current ?: windowWidth()) - Dimens.contentMaxWidth) / 2)
