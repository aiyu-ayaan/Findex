package com.aiyu.findex.ui.viewer

import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.ui.components.isMediaExtension
import com.aiyu.findex.ui.navigation.AudioPlayerRoute
import com.aiyu.findex.ui.navigation.PdfViewerRoute
import com.aiyu.findex.ui.navigation.TextViewerRoute

/** Picks the built-in viewer for a file. */
object InAppViewers {
    private val TEXT = setOf(
        "txt", "md", "log", "json", "xml", "csv", "html", "htm", "css", "js", "ts", "kt", "kts", "java", "py", "sh", "c", "h", "cpp", "hpp",
        "yml", "yaml", "toml", "ini", "conf", "properties", "gradle", "sql", "srt", "gitignore"
    )
    val AUDIO = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus")

    /**
     * Settings "Built-in viewers"; off sends photos, videos, PDF, text and audio to the default app instead.
     * shortcut: a process-wide flag set by FindexApp from settings, instead of threading the setting through every screen.
     */
    @Volatile
    var enabled = true

    /** Photos and videos open in the built-in media viewer. */
    fun opensMedia(extension: String) = enabled && isMediaExtension(extension)

    /** TextViewerRoute / PdfViewerRoute / AudioPlayerRoute, or null to open in another app. Local files only. */
    fun routeFor(item: StorageItem): Any? = if (item.isDirectory) null else routeFor(item.path)

    /** [force] ignores the setting: a file another app opened *with Findex* always uses the built-in viewer. */
    fun routeFor(path: String, force: Boolean = false): Any? {
        if ("://" in path || !(enabled || force)) return null
        return when (path.substringAfterLast('/').substringAfterLast('.', "").lowercase()) {
            in TEXT -> TextViewerRoute(path)
            "pdf" -> PdfViewerRoute(path)
            in AUDIO -> AudioPlayerRoute(path)
            else -> null
        }
    }
}
