package com.aiyu.findex.core.model

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val showHidden: Boolean = false,
    val gridView: Boolean = false,
    val sort: SortSpec = SortSpec()
)

data class ClipboardContent(val paths: List<String>, val isCut: Boolean)
