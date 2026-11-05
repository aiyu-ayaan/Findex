package com.aiyu.findex.core.model

import com.aiyu.findex.core.update.UpdateChannel

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** What a browser connected to "Share this phone" may do. */
enum class SharePermission {
    /** Photos, videos, audio, documents… sections. */
    MEDIA,
    /** Full folder browser over every storage volume. */
    FILES,
    /** Open, preview and download file contents. */
    DOWNLOAD,
    /** Upload files and create folders. */
    UPLOAD,
    /** Rename, and delete into the recycle bin. */
    MODIFY;

    companion object {
        val DEFAULT = setOf(MEDIA, FILES, DOWNLOAD)
    }
}

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
    val updateChannel: UpdateChannel? = null,
    /** Weekly junk scan that notifies when there is a lot to free. Opt-in. */
    val cleanupReminder: Boolean = false,
    /** Daily storage snapshots for the growth graph. */
    val storageHistory: Boolean = true,
    /** Open photos, videos, PDF, text and audio in Findex's own viewers; off uses the default app. */
    val builtInViewers: Boolean = true,
    /** Soft wrap in the text viewer. */
    val textWrap: Boolean = true,
    /** Minutes in the background before the vault locks; 0 = as soon as the app leaves. */
    val vaultAutoLockMinutes: Int = 1,
    val shareAccess: Set<SharePermission> = SharePermission.DEFAULT,
    /** Minutes without requests before the share server stops itself; 0 = never. */
    val shareIdleMinutes: Int = 5
)

/** [isImport]: [paths] are `content://` URIs another app shared ("Save to Findex"); pasting saves copies once. */
data class ClipboardContent(val paths: List<String>, val isCut: Boolean, val isImport: Boolean = false)

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
