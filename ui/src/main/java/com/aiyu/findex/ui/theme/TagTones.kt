package com.aiyu.findex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** [accent] is the dot color, [container]/[onContainer] the chip colors. */
data class TagTone(val container: Color, val onContainer: Color, val accent: Color)

private val light = listOf(
    TagTone(Color(0xFFFFDAD6), Color(0xFF93000A), Color(0xFFBA1A1A)),
    TagTone(Color(0xFFFFDBCE), Color(0xFF753317), Color(0xFF924A2C)),
    TagTone(Color(0xFFFFDDB8), Color(0xFF653E00), Color(0xFF855402)),
    TagTone(Color(0xFFCAEE9B), Color(0xFF334E0D), Color(0xFF4A6724)),
    TagTone(Color(0xFF91F4E6), Color(0xFF005049), Color(0xFF006A61)),
    TagTone(Color(0xFFD3E4FF), Color(0xFF0F487C), Color(0xFF306096)),
    TagTone(Color(0xFFE2DFFF), Color(0xFF3F3A8C), Color(0xFF5B55A8)),
    TagTone(Color(0xFFFDD6FF), Color(0xFF62356D), Color(0xFF7C4C86)),
)
private val dark = listOf(
    TagTone(Color(0xFF93000A), Color(0xFFFFDAD6), Color(0xFFFFB4AB)),
    TagTone(Color(0xFF753317), Color(0xFFFFDBCE), Color(0xFFFFB599)),
    TagTone(Color(0xFF653E00), Color(0xFFFFDDB8), Color(0xFFFCBA66)),
    TagTone(Color(0xFF334E0D), Color(0xFFCAEE9B), Color(0xFFAFD282)),
    TagTone(Color(0xFF005049), Color(0xFF91F4E6), Color(0xFF75D7CA)),
    TagTone(Color(0xFF0F487C), Color(0xFFD3E4FF), Color(0xFFA2C9FF)),
    TagTone(Color(0xFF3F3A8C), Color(0xFFE2DFFF), Color(0xFFC3C0FF)),
    TagTone(Color(0xFF62356D), Color(0xFFFDD6FF), Color(0xFFECB3F5)),
)

const val TAG_TONE_COUNT = 8

object TagTones {
    @Composable
    @ReadOnlyComposable
    operator fun get(index: Int): TagTone {
        val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        return (if (isDark) dark else light)[index.coerceIn(0, TAG_TONE_COUNT - 1)]
    }
}
