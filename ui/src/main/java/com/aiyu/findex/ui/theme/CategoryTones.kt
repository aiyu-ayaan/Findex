package com.aiyu.findex.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.aiyu.findex.core.model.FileCategory

/** Container/on-container pair for a category. Tones 90/30 (light) and 30/90 (dark) of a fixed hue. */
data class CategoryTone(val container: Color, val onContainer: Color, val accent: Color)

private val lightTones = mapOf(
    FileCategory.PHOTOS to CategoryTone(Color(0xFF91F4E6), Color(0xFF005049), Color(0xFF006A61)),
    FileCategory.VIDEOS to CategoryTone(Color(0xFFFFDBCE), Color(0xFF753317), Color(0xFF924A2C)),
    FileCategory.AUDIO to CategoryTone(Color(0xFFFDD6FF), Color(0xFF62356D), Color(0xFF7C4C86)),
    FileCategory.DOCUMENTS to CategoryTone(Color(0xFFD3E4FF), Color(0xFF0F487C), Color(0xFF306096)),
    FileCategory.DOWNLOADS to CategoryTone(Color(0xFFB6F2AF), Color(0xFF1D511F), Color(0xFF356A35)),
    FileCategory.ARCHIVES to CategoryTone(Color(0xFFFFDDB8), Color(0xFF653E00), Color(0xFF855402)),
    FileCategory.APKS to CategoryTone(Color(0xFFCAEE9B), Color(0xFF334E0D), Color(0xFF4A6724)),
)

private val darkTones = mapOf(
    FileCategory.PHOTOS to CategoryTone(Color(0xFF005049), Color(0xFF91F4E6), Color(0xFF75D7CA)),
    FileCategory.VIDEOS to CategoryTone(Color(0xFF753317), Color(0xFFFFDBCE), Color(0xFFFFB599)),
    FileCategory.AUDIO to CategoryTone(Color(0xFF62356D), Color(0xFFFDD6FF), Color(0xFFECB3F5)),
    FileCategory.DOCUMENTS to CategoryTone(Color(0xFF0F487C), Color(0xFFD3E4FF), Color(0xFFA2C9FF)),
    FileCategory.DOWNLOADS to CategoryTone(Color(0xFF1D511F), Color(0xFFB6F2AF), Color(0xFF9BD594)),
    FileCategory.ARCHIVES to CategoryTone(Color(0xFF653E00), Color(0xFFFFDDB8), Color(0xFFFCBA66)),
    FileCategory.APKS to CategoryTone(Color(0xFF334E0D), Color(0xFFCAEE9B), Color(0xFFAFD282)),
)

/** Follows the app theme (not the system), so it stays correct when the user forces light/dark. */
val FileCategory.tone: CategoryTone
    @Composable @ReadOnlyComposable
    get() {
        val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
        return (if (dark) darkTones else lightTones).getValue(this)
    }
