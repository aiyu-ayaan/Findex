package com.aiyu.findex.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.os.Build
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

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ThumbEntryPoint {
    fun smbRepository(): SmbRepository
}

/**
 * A small list/grid thumbnail of a local or network image or video. [lastModified] is part of the
 * cache key, so an edited file gets a new thumbnail and the stale one ages out of the disk cache.
 */
data class Thumb(val path: String, val lastModified: Long, val isVideo: Boolean)

/** Edge length of generated thumbnails: enough for a 72dp grid cell at xxxhdpi. */
private const val THUMB_PX = 256

/** Decoding frames is CPU-heavy; a few at a time keeps fast scrolling smooth. */
private val thumbDispatcher = Dispatchers.IO.limitedParallelism(3)

private class ThumbKeyer : Keyer<Thumb> {
    override fun key(data: Thumb, options: Options) = "thumb:${data.path}:${data.lastModified}"
}

/**
 * Serves thumbnails from Coil's disk cache; on a miss builds one with [ThumbnailUtils] (for local
 * files) or over a seekable SMB reader (for network shares, reusing EXIF thumbnails or reading only
 * the video container header + first keyframe), stores it as WebP, then serves that.
 */
private class ThumbFetcher(
    private val thumb: Thumb,
    private val smb: SmbRepository,
    private val diskCache: () -> DiskCache?
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val key = "thumb-v1:${thumb.path}:${thumb.lastModified}"
        val cache = diskCache()
        cache?.openSnapshot(key)?.let { snapshot ->
            return SourceFetchResult(
                ImageSource(snapshot.data, cache.fileSystem, key, snapshot),
                "image/webp",
                DataSource.DISK
            )
        }
        val bitmap = withContext(thumbDispatcher) { generate() } ?: throw IOException("No thumbnail for ${thumb.path}")
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
        SmbPath.parse(thumb.path)?.let { (serverId, inner) ->
            return generateRemote(serverId, inner)
        }
        val file = File(thumb.path)
        if (!file.isFile) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val size = Size(THUMB_PX, THUMB_PX)
                if (thumb.isVideo) ThumbnailUtils.createVideoThumbnail(file, size, null)
                else ThumbnailUtils.createImageThumbnail(file, size, null)
            } else if (thumb.isVideo) {
                MediaMetadataRetriever().run {
                    try {
                        setDataSource(file.absolutePath)
                        getFrameAtTime(-1)?.let { ThumbnailUtils.extractThumbnail(it, THUMB_PX, THUMB_PX, ThumbnailUtils.OPTIONS_RECYCLE_INPUT) }
                    } finally {
                        release()
                    }
                }
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= THUMB_PX && bounds.outHeight / (sample * 2) >= THUMB_PX) sample *= 2
                BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun generateRemote(serverId: Long, inner: String): Bitmap? = try {
        smb.openRandomAccess(serverId, inner).use { raf ->
            if (thumb.isVideo) {
                val dataSource = object : MediaDataSource() {
                    override fun getSize(): Long = raf.size
                    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
                        raf.readAt(position, buffer, offset, size)
                    override fun close() {}
                }
                MediaMetadataRetriever().run {
                    try {
                        setDataSource(dataSource)
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

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        if (degrees == 0f) return this
        val m = Matrix().apply { postRotate(degrees) }
        val rotated = Bitmap.createBitmap(this, 0, 0, width, height, m, true)
        if (rotated !== this) recycle()
        return rotated
    }

    class Factory(private val smb: SmbRepository) : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbFetcher(data, smb) { imageLoader.diskCache }
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
    val smb = EntryPointAccessors.fromApplication(
        context.applicationContext,
        ThumbEntryPoint::class.java
    ).smbRepository()
    return ImageLoader.Builder(context)
        .components {
            add(ThumbKeyer())
            add(ThumbFetcher.Factory(smb))
            add(VideoFrameDecoder.Factory())
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(File(context.cacheDir, "thumbnails").toOkioPath())
                .maxSizeBytes(THUMB_CACHE_BYTES)
                .build()
        }
        .crossfade(true)
        .build()
}

private const val THUMB_CACHE_BYTES = 150L * 1024 * 1024
