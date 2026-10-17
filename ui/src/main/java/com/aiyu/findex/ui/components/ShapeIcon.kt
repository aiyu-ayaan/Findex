package com.aiyu.findex.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath

/** Icon inside an M3 Expressive [RoundedPolygon] container (e.g. `MaterialShapes.Cookie9Sided`). */
@Composable
fun ShapeIcon(
    icon: ImageVector,
    shape: RoundedPolygon,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    contentDescription: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(shape.toShape())
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = content, modifier = Modifier.size(iconSize))
    }
}

/**
 * Container that morphs from [from] to [to] when [morphed] flips, using the theme's
 * fast spatial spring so the change has the expressive overshoot.
 */
@Composable
fun MorphingShapeIcon(
    icon: ImageVector,
    from: RoundedPolygon,
    to: RoundedPolygon,
    morphed: Boolean,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 24.dp,
    contentDescription: String? = null
) {
    val morph = remember(from, to) { Morph(from, to) }
    val progress by animateFloatAsState(
        targetValue = if (morphed) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "shapeMorph"
    )
    val shape = remember(morph, progress) { MorphShape(morph, progress) }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = content, modifier = Modifier.size(iconSize))
    }
}

/** Shape for a [Morph] at [progress]; the morph is defined in a unit square and scaled to the bounds. */
class MorphShape(private val morph: Morph, private val progress: Float) : Shape {
    private val matrix = Matrix()
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        matrix.reset()
        matrix.scale(size.width, size.height)
        val path: Path = morph.toPath(progress).asComposePath()
        path.transform(matrix)
        return Outline.Generic(path)
    }
}
