package com.aiyu.findex.ui.viewer

import com.aiyu.findex.core.model.StorageItem
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

    /** TextViewerRoute / PdfViewerRoute / AudioPlayerRoute, or null to open in another app as today. Local files only. */
    fun routeFor(item: StorageItem): Any? {
        if (item.isDirectory || "://" in item.path) return null
        return when (item.extension.lowercase()) {
            in TEXT -> TextViewerRoute(item.path)
            "pdf" -> PdfViewerRoute(item.path)
            in AUDIO -> AudioPlayerRoute(item.path)
            else -> null
        }
    }
}
