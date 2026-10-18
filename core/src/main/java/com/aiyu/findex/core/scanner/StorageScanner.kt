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

enum class JunkType { EMPTY_FOLDERS, ZERO_BYTE_FILES, THUMBNAILS, TEMP_FILES, APK_FILES }

enum class WhatsAppMediaType { IMAGES, VIDEOS, AUDIO, VOICE_NOTES, DOCUMENTS, STICKERS, GIFS, BACKUPS }

enum class AppMediaType { IMAGES, VIDEOS, AUDIO, DOCUMENTS }

/** Apps whose shared media folders are cleaned like WhatsApp's; [folders] are relative to primary storage. */
enum class MediaApp(val folders: List<String>) {
    TELEGRAM(
        listOf(
            "Android/media/org.telegram.messenger/Telegram",
            "Android/media/org.telegram.messenger.web/Telegram",
            "Android/media/org.telegram.plus/Telegram",
            "Telegram"
        )
    ),
    INSTAGRAM(listOf("Pictures/Instagram", "Movies/Instagram", "DCIM/Instagram", "Android/media/com.instagram.android"))
}

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
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "avif")
        private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v")
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "flac", "ogg", "aac", "opus", "amr")

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

        internal suspend fun junkFiles(root: File): Map<JunkType, List<File>> {
            val thumbs = mutableListOf<File>()
            val temp = mutableListOf<File>()
            val apks = mutableListOf<File>()
            val zero = mutableListOf<File>()
            val dirs = mutableListOf<File>()
            // Folders with a file somewhere below them; every other folder is an empty tree.
            val holdsFiles = HashSet<File>()
            val android = File(root, "Android")
            for (file in walk(root)) {
                currentCoroutineContext().ensureActive()
                if (file.isDirectory) {
                    when {
                        file == root -> Unit
                        file.name == ".thumbnails" -> thumbs += file
                        // Never offer the standard top-level folders (DCIM, Music, …) or app folders under Android/.
                        file.parentFile != root && !file.startsWith(android) -> dirs += file
                    }
                    continue
                }
                var parent = file.parentFile
                while (parent != null && holdsFiles.add(parent)) parent = parent.parentFile
                val ext = file.extension.lowercase()
                when {
                    ext in TEMP_EXTENSIONS -> temp += file
                    ext == "apk" -> apks += file
                    // Dot-files such as .nomedia are empty on purpose.
                    file.length() == 0L && !file.name.startsWith(".") -> zero += file
                }
            }
            val emptyTrees = dirs.filterTo(HashSet()) { it !in holdsFiles }
            // Only the topmost folder of an empty tree; re-checked on disk so skipped dirs (vault, bin) inside keep it.
            val empty = emptyTrees.filter { it.parentFile !in emptyTrees && it.walkTopDown().none { f -> f.isFile } }
            return mapOf(
                JunkType.THUMBNAILS to thumbs,
                JunkType.TEMP_FILES to temp,
                JunkType.APK_FILES to apks,
                JunkType.EMPTY_FOLDERS to empty.sortedBy { it.absolutePath },
                JunkType.ZERO_BYTE_FILES to zero,
            )
        }

        internal fun appMediaFiles(root: File, app: MediaApp): List<Pair<AppMediaType, List<File>>> =
            app.folders.map { File(root, it) }.filter { it.isDirectory }
                .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.name != ".nomedia" }.toList() }
                .distinctBy { it.absolutePath }
                .groupBy { appMediaTypeOf(it.extension) }
                .toList()
                .sortedBy { it.first.ordinal }

        internal fun appMediaTypeOf(extension: String): AppMediaType = when (extension.lowercase()) {
            in IMAGE_EXTENSIONS -> AppMediaType.IMAGES
            in VIDEO_EXTENSIONS -> AppMediaType.VIDEOS
            in AUDIO_EXTENSIONS -> AppMediaType.AUDIO
            else -> AppMediaType.DOCUMENTS
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
        val junk = junkFiles(root)
        listOf(
            ScanGroup(JunkType.THUMBNAILS, junk[JunkType.THUMBNAILS].orEmpty().map { it.withTreeSize() }),
            ScanGroup(JunkType.TEMP_FILES, junk[JunkType.TEMP_FILES].orEmpty().map { it.toStorageItem() }),
            ScanGroup(JunkType.APK_FILES, junk[JunkType.APK_FILES].orEmpty().map { it.toStorageItem() }),
            ScanGroup(JunkType.EMPTY_FOLDERS, junk[JunkType.EMPTY_FOLDERS].orEmpty().map { it.toStorageItem() }),
            ScanGroup(JunkType.ZERO_BYTE_FILES, junk[JunkType.ZERO_BYTE_FILES].orEmpty().map { it.toStorageItem() }),
        ).filter { it.files.isNotEmpty() }
    }

    /** Shared media of [app] (sent and received), grouped by kind, largest first. */
    suspend fun scanAppMedia(app: MediaApp): List<ScanGroup<AppMediaType>> = withContext(Dispatchers.IO) {
        appMediaFiles(root, app).map { (type, files) -> ScanGroup(type, files.map { it.toStorageItem() }.sortedByDescending { it.size }) }
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
}
