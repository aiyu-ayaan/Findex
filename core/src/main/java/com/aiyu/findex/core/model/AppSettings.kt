package com.aiyu.findex.core.model

import com.aiyu.findex.core.update.UpdateChannel

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val showHidden: Boolean = false,
    val gridView: Boolean = false,
    val sort: SortSpec = SortSpec(),
    /** Set once the user leaves onboarding, so a declined notification prompt isn't shown again. */
    val onboardingDone: Boolean = false,
    val autoUpdate: Boolean = true,
    /** Null until chosen: the channel then follows the installed build (see UpdateChannel.forVersion). */
    val updateChannel: UpdateChannel? = null
)

data class ClipboardContent(val paths: List<String>, val isCut: Boolean)

data class Bookmark(val path: String, val name: String)

data class RecentFile(val path: String, val name: String, val size: Long, val openedAt: Long)

data class TrashItem(
    val id: Long,
    val name: String,
    val originalPath: String,
    val trashPath: String,
    val size: Long,
    val isDirectory: Boolean,
    val deletedAt: Long
)
