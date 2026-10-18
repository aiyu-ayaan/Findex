package com.aiyu.findex.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.disk.DiskCache
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.memory.MemoryCache
import coil3.request.Options
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import com.aiyu.findex.core.model.FileCategory
import com.aiyu.findex.core.network.SmbPath
import com.aiyu.findex.core.network.SmbRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException
import java.util.Collections

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ThumbEntryPoint {
    fun smbRepository(): SmbRepository
}

/**
 * A small list/grid thumbnail of a local or network file (photo, video, audio cover art, PDF first page, or APK icon).
 * [lastModified] is part of the cache key, so an edited file gets a new thumbnail and the stale one ages out.
 */
data class Thumb(val path: String, val lastModified: Long, val isVideo: Boolean = false)

/** Edge length of generated thumbnails: enough for a 72dp grid cell at xxxhdpi. */
private const val THUMB_PX = 256

/** Decoding frames is CPU-heavy; a few at a time keeps fast scrolling smooth. */
private val thumbDispatcher = Dispatchers.IO.limitedParallelism(3)

/** Remembers files that have no embedded thumbnail (e.g. audio without cover art) for the session. */
private val missingThumbKeys: MutableSet<String> = Collections.synchronizedSet(
    object : LinkedHashMap<String, Boolean>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > 2048
    }.let { Collections.newSetFromMap(it) }
)

private class ThumbKeyer : Keyer<Thumb> {
    override fun key(data: Thumb, options: Options) = "thumb:${data.path}:${data.lastModified}"
}

/**
 * Serves thumbnails from Coil's disk cache; on a miss builds one with [ThumbnailUtils], [PdfRenderer],
 * [android.content.pm.PackageManager], or [MediaMetadataRetriever] (for local files) or over a seekable
 * SMB reader (for network shares), stores it as WebP, then serves that.
 */
private class ThumbFetcher(
    private val context: Context,
    private val thumb: Thumb,
    private val smb: SmbRepository,
    private val diskCache: () -> DiskCache?
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val key = "thumb-v2:${thumb.path}:${thumb.lastModified}"
        if (key in missingThumbKeys) throw IOException("No thumbnail for ${thumb.path}")
        val cache = diskCache()
        cache?.openSnapshot(key)?.let { snapshot ->
            return SourceFetchResult(
                ImageSource(snapshot.data, cache.fileSystem, key, snapshot),
                "image/webp",
                DataSource.DISK
            )
        }
        val bitmap = withContext(thumbDispatcher) { generate() }
        if (bitmap == null) {
            missingThumbKeys.add(key)
            throw IOException("No thumbnail for ${thumb.path}")
        }
        cache?.openEditor(key)?.let { editor ->
            try {
                cache.fileSystem.write(editor.data) { bitmap.compress(webp, 80, outputStream()) }
                editor.commitAndOpenSnapshot()?.let { snapshot ->
                    return SourceFetchResult(ImageSource(snapshot.data, cache.fileSystem, key, snapshot), "image/webp", DataSource.DISK)
                }
            } catch (e: IOException) {
                editor.abort()
            }
        }
        return ImageFetchResult(bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
    }

    private suspend fun generate(): Bitmap? {
        val ext = thumb.path.substringAfterLast('.', "").lowercase()
        val category = categoryOf(ext)
        SmbPath.parse(thumb.path)?.let { (serverId, inner) ->
            return generateRemote(serverId, inner, category)
        }
        val file = File(thumb.path)
        if (!file.isFile) return null
        return try {
            when {
                ext == "apk" -> generateApkIcon(file)
                ext == "pdf" -> generatePdfPage(file)
                category == FileCategory.AUDIO -> generateLocalAudioArt(file)
                thumb.isVideo || category == FileCategory.VIDEOS -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ThumbnailUtils.createVideoThumbnail(file, Size(THUMB_PX, THUMB_PX), null)
                    } else {
                        MediaMetadataRetriever().run {
                            try {
                                setDataSource(file.absolutePath)
                                getFrameAtTime(-1)?.let {
                                    ThumbnailUtils.extractThumbnail(it, THUMB_PX, THUMB_PX, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
                                }
                            } finally {
                                runCatching { release() }
                            }
                        }
                    }
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    ThumbnailUtils.createImageThumbnail(file, Size(THUMB_PX, THUMB_PX), null)
                }
                else -> {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= THUMB_PX && bounds.outHeight / (sample * 2) >= THUMB_PX) sample *= 2
                    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun generateApkIcon(file: File): Bitmap? {
        val pm = context.packageManager
        val info = pm.getPackageArchiveInfo(file.absolutePath, 0) ?: return null
        val appInfo = info.applicationInfo ?: return null
        appInfo.sourceDir = file.absolutePath
        appInfo.publicSourceDir = file.absolutePath
        val drawable = pm.getApplicationIcon(appInfo)
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: THUMB_PX
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: THUMB_PX
        val size = maxOf(w, h).coerceIn(96, THUMB_PX)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(canvas)
        return bitmap
    }

    private fun generatePdfPage(file: File): Bitmap? {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (renderer.pageCount <= 0) return null
                renderer.openPage(0).use { page ->
                    val pw = page.width.coerceAtLeast(1)
                    val ph = page.height.coerceAtLeast(1)
                    val scale = THUMB_PX.toFloat() / maxOf(pw, ph).toFloat()
                    val bw = (pw * scale).toInt().coerceAtLeast(1)
                    val bh = (ph * scale).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }

    private fun generateLocalAudioArt(file: File): Bitmap? = MediaMetadataRetriever().run {
        try {
            setDataSource(file.absolutePath)
            val art = embeddedPicture ?: return null
            decodeBytes(art)
        } finally {
            runCatching { release() }
        }
    }

    private suspend fun generateRemote(serverId: Long, inner: String, category: FileCategory?): Bitmap? = try {
        if (category != FileCategory.PHOTOS && category != FileCategory.VIDEOS && category != FileCategory.AUDIO && !thumb.isVideo) {
            return null
        }
        smb.openRandomAccess(serverId, inner).use { raf ->
            if (thumb.isVideo || category == FileCategory.VIDEOS || category == FileCategory.AUDIO) {
                val dataSource = object : MediaDataSource() {
                    override fun getSize(): Long = raf.size
                    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
                        raf.readAt(position, buffer, offset, size)
                    override fun close() {}
                }
                MediaMetadataRetriever().run {
                    try {
                        setDataSource(dataSource)
                        if (category == FileCategory.AUDIO && !thumb.isVideo) {
                            val art = embeddedPicture ?: return@use null
                            return@use decodeBytes(art)
                        }
                        val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                            getScaledFrameAtTime(-1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMB_PX, THUMB_PX)
                                ?: getFrameAtTime(-1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } else {
                            getFrameAtTime(-1L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        } ?: getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        frame?.let { ThumbnailUtils.extractThumbnail(it, THUMB_PX, THUMB_PX, ThumbnailUtils.OPTIONS_RECYCLE_INPUT) }
                    } finally {
                        runCatching { release() }
                    }
                }
            } else {
                val exif = runCatching { ExifInterface(raf.asInputStream(0L)) }.getOrNull()
                val rotation = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                val embedded = exif?.let { if (it.hasThumbnail()) it.thumbnailBitmap else null }
                if (embedded != null) {
                    return@use embedded.rotate(rotation).let {
                        ThumbnailUtils.extractThumbnail(it, THUMB_PX, THUMB_PX, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
                    }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(raf.asInputStream(0L), null, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@use null
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= THUMB_PX && bounds.outHeight / (sample * 2) >= THUMB_PX) {
                    sample *= 2
                }
                val decoded = BitmapFactory.decodeStream(
                    raf.asInputStream(0L),
                    null,
                    BitmapFactory.Options().apply { inSampleSize = sample }
                ) ?: return@use null
                decoded.rotate(rotation).let {
                    ThumbnailUtils.extractThumbnail(it, THUMB_PX, THUMB_PX, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
                }
            }
        }
    } catch (_: Exception) {
        null
    }

    private fun decodeBytes(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= THUMB_PX && bounds.outHeight / (sample * 2) >= THUMB_PX) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return ThumbnailUtils.extractThumbnail(decoded, THUMB_PX, THUMB_PX, ThumbnailUtils.OPTIONS_RECYCLE_INPUT)
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        if (degrees == 0f) return this
        val m = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(this, 0, 0, width, height, m, true)
        if (rotated !== this) recycle()
        return rotated
    }

    class Factory(
        private val context: Context,
        private val smb: SmbRepository
    ) : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbFetcher(context, data, smb) { imageLoader.diskCache }
    }

    private companion object {
        @Suppress("DEPRECATION")
        val webp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
    }
}

/**
 * App-wide Coil loader: persistent thumbnail cache (size-bounded LRU, so the least recently seen
 * thumbnails are deleted first and the folder never grows past [THUMB_CACHE_BYTES]), a memory cache,
 * and video frame decoding for full-size previews.
 */
fun findexImageLoader(context: Context): ImageLoader {
    val appContext = context.applicationContext
    val smb = EntryPointAccessors.fromApplication(
        appContext,
        ThumbEntryPoint::class.java
    ).smbRepository()
    return ImageLoader.Builder(appContext)
        .components {
            add(ThumbKeyer())
            add(ThumbFetcher.Factory(appContext, smb))
            add(VideoFrameDecoder.Factory())
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(appContext, 0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(File(appContext.cacheDir, "thumbnails").toOkioPath())
                .maxSizeBytes(THUMB_CACHE_BYTES)
                .build()
        }
        .crossfade(true)
        .build()
}

private const val THUMB_CACHE_BYTES = 150L * 1024 * 1024
