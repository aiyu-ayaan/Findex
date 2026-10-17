package com.aiyu.findex.ui.explorer

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale

data class ImageMeta(
    val width: Int,
    val height: Int,
    val megapixels: Float,
    val aspectRatio: String
)

data class VideoMeta(
    val durationMs: Long,
    val width: Int,
    val height: Int
)

data class AudioMeta(
    val durationMs: Long,
    val title: String?,
    val artist: String?,
    val album: String?
)

data class ApkMeta(
    val appName: String?,
    val packageName: String?,
    val versionName: String?,
    val icon: Drawable?
)

data class TextSnippet(
    val content: String,
    val lineCount: Int,
    val isTruncated: Boolean
)

data class FileChecksums(
    val md5: String,
    val sha256: String
)

object FileInspector {

    suspend fun inspectImage(file: File): ImageMeta? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.length() == 0L) return@withContext null
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, options)
            if (options.outWidth > 0 && options.outHeight > 0) {
                val mp = (options.outWidth.toLong() * options.outHeight.toLong()) / 1_000_000f
                val gcd = gcd(options.outWidth, options.outHeight)
                val ratio = "${options.outWidth / gcd}:${options.outHeight / gcd}"
                ImageMeta(options.outWidth, options.outHeight, mp, ratio)
            } else null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun inspectVideo(file: File): VideoMeta? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            VideoMeta(dur, w, h)
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    suspend fun inspectAudio(file: File): AudioMeta? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            AudioMeta(dur, title, artist, album)
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    suspend fun inspectApk(context: Context, file: File): ApkMeta? = withContext(Dispatchers.IO) {
        try {
            val pm = context.packageManager
            val info = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return@withContext null
            val appInfo = info.applicationInfo ?: return@withContext null
            appInfo.sourceDir = file.absolutePath
            appInfo.publicSourceDir = file.absolutePath
            val name = pm.getApplicationLabel(appInfo).toString()
            val icon = pm.getApplicationIcon(appInfo)
            ApkMeta(name, info.packageName, info.versionName, icon)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun inspectText(file: File, maxLines: Int = 120, maxChars: Int = 16384): TextSnippet? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.length() == 0L || file.length() > 20 * 1024 * 1024) return@withContext null
            val lines = mutableListOf<String>()
            var charCount = 0
            var truncated = false
            file.bufferedReader().useLines { sequence ->
                for (line in sequence) {
                    if (lines.size >= maxLines || charCount + line.length > maxChars) {
                        truncated = true
                        break
                    }
                    lines.add(line)
                    charCount += line.length + 1
                }
            }
            TextSnippet(lines.joinToString("\n"), lines.size, truncated)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun calculateChecksums(file: File): FileChecksums? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || !file.isFile) return@withContext null
            val md5Digest = MessageDigest.getInstance("MD5")
            val sha256Digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(65536)
            FileInputStream(file).use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    md5Digest.update(buffer, 0, read)
                    sha256Digest.update(buffer, 0, read)
                }
            }
            val md5 = md5Digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
            val sha256 = sha256Digest.digest().joinToString("") { "%02x".format(Locale.ROOT, it) }
            FileChecksums(md5, sha256)
        } catch (_: Exception) {
            null
        }
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val t = y
            y = x % y
            x = t
        }
        return if (x == 0) 1 else x
    }
}
