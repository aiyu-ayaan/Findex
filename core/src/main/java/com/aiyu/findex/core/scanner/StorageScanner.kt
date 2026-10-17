package com.aiyu.findex.core.scanner

import android.os.Environment
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.repository.toStorageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton

enum class JunkType { EMPTY_FOLDERS, THUMBNAILS, TEMP_FILES, APK_FILES }

enum class WhatsAppMediaType { IMAGES, VIDEOS, AUDIO, VOICE_NOTES, DOCUMENTS, STICKERS, GIFS, BACKUPS }

/** A cleanable group of files with their total size. */
data class ScanGroup<T>(val type: T, val files: List<StorageItem>) {
    val bytes: Long = files.sumOf { it.size }
}

/** Read-only scans of primary storage for the analyzer and cleaners. */
@Singleton
class StorageScanner @Inject constructor() {

    private val root: File get() = Environment.getExternalStorageDirectory()

    /** Android/data and Android/obb are private to other apps and mostly unreadable. */
    private fun File.isPrivateAppDir() =
        absolutePath.startsWith(File(root, "Android/data").absolutePath) ||
            absolutePath.startsWith(File(root, "Android/obb").absolutePath)

    private fun walk(): Sequence<File> = root.walkTopDown().onEnter { !it.isPrivateAppDir() }

    suspend fun largestFiles(limit: Int = 30): List<StorageItem> = withContext(Dispatchers.IO) {
        val heap = PriorityQueue<File>(limit + 1, compareBy { it.length() })
        for (file in walk()) {
            currentCoroutineContext().ensureActive()
            if (!file.isFile) continue
            heap.add(file)
            if (heap.size > limit) heap.poll()
        }
        heap.toList().sortedByDescending { it.length() }.map { it.toStorageItem() }
    }

    suspend fun scanJunk(): List<ScanGroup<JunkType>> = withContext(Dispatchers.IO) {
        val empty = mutableListOf<File>()
        val thumbs = mutableListOf<File>()
        val temp = mutableListOf<File>()
        val apks = mutableListOf<File>()
        for (file in walk()) {
            currentCoroutineContext().ensureActive()
            when {
                file.isDirectory && file != root && file.name == ".thumbnails" -> thumbs += file
                file.isDirectory && file != root && file.list()?.isEmpty() == true && !file.isTopLevelStandardDir() -> empty += file
                file.isFile && file.extension.lowercase() in TEMP_EXTENSIONS -> temp += file
                file.isFile && file.extension.equals("apk", ignoreCase = true) -> apks += file
            }
        }
        listOf(
            ScanGroup(JunkType.THUMBNAILS, thumbs.map { it.withTreeSize() }),
            ScanGroup(JunkType.TEMP_FILES, temp.map { it.toStorageItem() }),
            ScanGroup(JunkType.APK_FILES, apks.map { it.toStorageItem() }),
            ScanGroup(JunkType.EMPTY_FOLDERS, empty.map { it.toStorageItem() }),
        ).filter { it.files.isNotEmpty() }
    }

    suspend fun scanWhatsApp(): List<ScanGroup<WhatsAppMediaType>> = withContext(Dispatchers.IO) {
        val bases = listOf(
            "Android/media/com.whatsapp/WhatsApp",
            "Android/media/com.whatsapp.w4b/WhatsApp Business",
            "WhatsApp"
        ).map { File(root, it) }.filter { it.isDirectory }

        WhatsAppMediaType.entries.map { type ->
            val files = bases.flatMap { base ->
                type.folders(base).filter { it.isDirectory }.flatMap { dir ->
                    // Includes the "Sent" and "Private" sub-folders; skips .nomedia markers.
                    dir.walkTopDown().filter { it.isFile && it.name != ".nomedia" }.map { it.toStorageItem() }.toList()
                }
            }
            ScanGroup(type, files.sortedByDescending { it.size })
        }.filter { it.files.isNotEmpty() }
    }

    private fun WhatsAppMediaType.folders(base: File): List<File> {
        val media = File(base, "Media")
        fun m(name: String) = File(media, name)
        val prefix = if (base.name == "WhatsApp Business") "WhatsApp Business" else "WhatsApp"
        return when (this) {
            WhatsAppMediaType.IMAGES -> listOf(m("$prefix Images"))
            WhatsAppMediaType.VIDEOS -> listOf(m("$prefix Video"), m("$prefix Video Notes"))
            WhatsAppMediaType.AUDIO -> listOf(m("$prefix Audio"))
            WhatsAppMediaType.VOICE_NOTES -> listOf(m("$prefix Voice Notes"))
            WhatsAppMediaType.DOCUMENTS -> listOf(m("$prefix Documents"))
            WhatsAppMediaType.STICKERS -> listOf(m("$prefix Stickers"))
            WhatsAppMediaType.GIFS -> listOf(m("$prefix Animated Gifs"))
            WhatsAppMediaType.BACKUPS -> listOf(File(base, "Databases"), File(base, "Backups"))
        }
    }

    private fun File.withTreeSize(): StorageItem =
        toStorageItem().copy(size = walkTopDown().filter { it.isFile }.sumOf { it.length() })

    /** Never offer to delete the standard top-level folders (DCIM, Music, …) even when empty. */
    private fun File.isTopLevelStandardDir() = parentFile == root

    private companion object {
        val TEMP_EXTENSIONS = setOf("tmp", "temp", "log", "bak", "old", "crdownload", "part")
    }
}
