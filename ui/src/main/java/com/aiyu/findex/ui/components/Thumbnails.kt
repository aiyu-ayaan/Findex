package com.aiyu.findex.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException

/**
 * A small list/grid thumbnail of a local image or video. [lastModified] is part of the cache key,
 * so an edited file gets a new thumbnail and the stale one ages out of the disk cache.
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
 * Serves thumbnails from Coil's disk cache; on a miss builds one with [ThumbnailUtils] (which reuses
 * EXIF/embedded thumbnails when present), stores it as WebP, then serves that.
 */
private class ThumbFetcher(
    private val thumb: Thumb,
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

    private fun generate(): Bitmap? {
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

    class Factory : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher =
            ThumbFetcher(data) { imageLoader.diskCache }
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
fun findexImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
    .components {
        add(ThumbKeyer())
        add(ThumbFetcher.Factory())
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

private const val THUMB_CACHE_BYTES = 150L * 1024 * 1024
