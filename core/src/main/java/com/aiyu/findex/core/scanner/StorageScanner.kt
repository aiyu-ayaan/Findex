package com.aiyu.findex.core.scanner

import android.os.Environment
import com.aiyu.findex.core.model.StorageItem
import com.aiyu.findex.core.repository.toStorageItem
import com.aiyu.findex.core.vault.VaultKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.PriorityQueue
import javax.inject.Inject
import javax.inject.Singleton

enum class JunkType { EMPTY_FOLDERS, THUMBNAILS, TEMP_FILES, APK_FILES }

enum class WhatsAppMediaType { IMAGES, VIDEOS, AUDIO, VOICE_NOTES, DOCUMENTS, STICKERS, GIFS, BACKUPS }

/** Files with identical content; [wastedBytes] is what deleting all but one copy frees. */
data class DuplicateSet(val hash: String, val files: List<StorageItem>) {
    val size: Long get() = files.first().size
    val wastedBytes: Long get() = size * (files.size - 1)
}

/** A cleanable group of files with their total size. */
data class ScanGroup<T>(val type: T, val files: List<StorageItem>) {
    val bytes: Long = files.sumOf { it.size }
}

/** Read-only scans of primary storage for the analyzer and cleaners. */
@Singleton
class StorageScanner @Inject constructor() {

    private val root: File get() = Environment.getExternalStorageDirectory()

    private fun walk(): Sequence<File> = walk(root)

    companion object {
        private const val HEAD_BYTES = 64L * 1024
        private val TEMP_EXTENSIONS = setOf("tmp", "temp", "log", "bak", "old", "crdownload", "part")

        /**
         * Walks [root] while skipping private app directories, the recycle bin and the encrypted vault,
         * so cleaner, duplicate finder and large-files finder never touch `.FindexVault` or `.FindexTrash`.
         */
        internal fun walk(root: File): Sequence<File> {
            val dataDir = File(root, "Android/data").absolutePath
            val obbDir = File(root, "Android/obb").absolutePath
            fun isExcluded(f: File): Boolean =
                f.name == VaultKeyManager.DIR_NAME ||
                    f.name == ".FindexTrash" ||
                    f.absolutePath.startsWith(dataDir) ||
                    f.absolutePath.startsWith(obbDir)
            return root.walkTopDown().onEnter { !isExcluded(it) }.filterNot(::isExcluded)
        }
    }

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

    /**
     * Content duplicates on primary storage: group by size, then by a hash of the first 64 KB,
     * then confirm with a full SHA-256, so only real collisions are read completely.
     */
    suspend fun findDuplicates(minSize: Long = 1024): List<DuplicateSet> = withContext(Dispatchers.IO) {
        val bySize = HashMap<Long, MutableList<File>>()
        for (file in walk()) {
            currentCoroutineContext().ensureActive()
            // Skip the bin: trashed copies are already on their way out.
            if (file.isDirectory || file.absolutePath.contains("/.FindexTrash")) continue
            val length = file.length()
            if (length >= minSize) bySize.getOrPut(length) { mutableListOf() } += file
        }
        bySize.values.filter { it.size > 1 }
            .flatMap { sameSize -> sameSize.groupBy { it.digest(HEAD_BYTES) }.toList() }
            .filter { (head, files) -> head.isNotEmpty() && files.size > 1 }
            .flatMap { (head, files) ->
                // Files up to HEAD_BYTES were hashed whole already.
                if (files.first().length() <= HEAD_BYTES) listOf(head to files)
                else files.groupBy { it.digest(Long.MAX_VALUE) }.toList()
            }
            .filter { (hash, files) -> hash.isNotEmpty() && files.size > 1 }
            .map { (hash, files) -> DuplicateSet(hash, files.map { it.toStorageItem() }.sortedBy { it.lastModified }) }
            .sortedByDescending { it.wastedBytes }
    }

    /** SHA-256 of the first [limit] bytes; empty when the file can't be read. */
    private suspend fun File.digest(limit: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        return try {
            inputStream().use { input ->
                var remaining = limit
                while (remaining > 0) {
                    currentCoroutineContext().ensureActive()
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read < 0) break
                    md.update(buffer, 0, read)
                    remaining -= read
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (_: java.io.IOException) {
            ""
        }
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
}
