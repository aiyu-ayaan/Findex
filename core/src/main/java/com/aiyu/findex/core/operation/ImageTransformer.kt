package com.aiyu.findex.core.operation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.os.Build
import com.aiyu.findex.core.model.FailureReason
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.FileOperationException
import com.aiyu.findex.core.model.ImageOutputFormat
import com.aiyu.findex.core.model.ImageOutputMode
import com.aiyu.findex.core.model.ImageTransformSpec
import com.aiyu.findex.core.model.OperationProgress
import java.io.File
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Executor handler for [FileOperation.ImageTransform]. Local files only. */
@Singleton
class ImageTransformer @Inject constructor() {

    private enum class Fmt(val ext: String) { JPEG("jpg"), WEBP("webp"), PNG("png") }
    private enum class Outcome { TRANSFORMED, SKIPPED, UNSUPPORTED }

    companion object {
        /** True when [bytes] (at least 21 bytes) is a RIFF/WEBP header with the VP8X animation flag set. */
        internal fun isAnimatedWebp(bytes: ByteArray, len: Int = bytes.size): Boolean =
            len >= 21 &&
                bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
                bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte() &&
                bytes[8] == 'W'.code.toByte() && bytes[9] == 'E'.code.toByte() &&
                bytes[10] == 'B'.code.toByte() && bytes[11] == 'P'.code.toByte() &&
                bytes[12] == 'V'.code.toByte() && bytes[13] == 'P'.code.toByte() &&
                bytes[14] == '8'.code.toByte() && bytes[15] == 'X'.code.toByte() &&
                (bytes[20].toInt() and 0x02) != 0

        internal fun isAnimatedWebp(file: File): Boolean = try {
            val buf = ByteArray(21)
            val n = file.inputStream().use { it.read(buf) }
            isAnimatedWebp(buf, n)
        } catch (_: Exception) {
            false
        }
    }

    suspend fun run(op: FileOperation.ImageTransform, touched: MutableList<String>, report: (OperationProgress) -> Unit) {
        val spec = op.spec
        val outDir = if (spec.output == ImageOutputMode.INTO_FOLDER) {
            File(spec.outputDir ?: throw FileOperationException(FailureReason.INVALID_NAME)).also { it.mkdirs() }
        } else null
        var skipped = 0
        var unsupported = 0
        withContext(Dispatchers.IO) {
            op.sources.forEachIndexed { i, path ->
                currentCoroutineContext().ensureActive()
                val src = File(path)
                report(OperationProgress(op.id, src.name, processedItems = i, totalItems = op.sources.size))
                when (processOne(src, spec, outDir, touched)) {
                    Outcome.TRANSFORMED -> Unit
                    Outcome.SKIPPED -> skipped++
                    Outcome.UNSUPPORTED -> { skipped++; unsupported++ }
                }
            }
        }
        if (unsupported == op.sources.size) throw FileOperationException(FailureReason.UNSUPPORTED_IMAGE)
        report(OperationProgress(op.id, processedItems = op.sources.size - skipped, totalItems = op.sources.size, isComplete = true))
    }

    /** Estimated total output bytes, from encoding up to 3 sources in memory. Null if none decodable. */
    suspend fun estimateBytes(sources: List<String>, spec: ImageTransformSpec): Long? = withContext(Dispatchers.IO) {
        var sampleIn = 0L
        var sampleOut = 0L
        for (p in sources.take(3)) {
            val f = File(p)
            val (bmp, fmt) = render(f, spec) ?: continue
            val counter = object : OutputStream() {
                var n = 0L
                override fun write(b: Int) { n++ }
                override fun write(b: ByteArray, off: Int, len: Int) { n += len }
            }
            encode(bmp, fmt, spec.quality, counter)
            bmp.recycle()
            sampleIn += f.length(); sampleOut += counter.n
        }
        if (sampleIn == 0L) null else (sources.sumOf { File(it).length() } * (sampleOut.toDouble() / sampleIn)).toLong()
    }

    private fun processOne(src: File, spec: ImageTransformSpec, outDir: File?, touched: MutableList<String>): Outcome {
        if (!src.isFile) return Outcome.UNSUPPORTED
        val (bmp, fmt) = render(src, spec) ?: return Outcome.UNSUPPORTED
        val dir = outDir ?: src.parentFile ?: return Outcome.UNSUPPORTED
        val stem = src.nameWithoutExtension
        val target = when (spec.output) {
            ImageOutputMode.COPY_WITH_SUFFIX -> uniqueTarget(dir, "$stem (resized)", fmt.ext)
            ImageOutputMode.INTO_FOLDER -> uniqueTarget(dir, stem, fmt.ext)
            ImageOutputMode.REPLACE -> File(dir, "$stem.${fmt.ext}").let {
                if (it.exists() && !it.path.equals(src.path, ignoreCase = true)) uniqueTarget(dir, stem, fmt.ext) else it
            }
        }
        val part = File(target.parentFile, target.name + ".part")
        try {
            part.outputStream().buffered().use { encode(bmp, fmt, spec.quality, it) }
            copyExif(src, part, fmt, spec.keepMetadata)
            // REPLACE that did not shrink anything: keep the original and count as skipped.
            if (spec.output == ImageOutputMode.REPLACE && part.length() >= src.length() && target.path == src.path) {
                return Outcome.SKIPPED
            }
            if (!part.renameTo(target)) return Outcome.UNSUPPORTED
            touched += target.absolutePath
            if (spec.output == ImageOutputMode.REPLACE && target.path != src.path) {
                if (src.delete()) touched += src.absolutePath
            }
            return Outcome.TRANSFORMED
        } catch (e: Exception) {
            return Outcome.UNSUPPORTED
        } finally {
            part.delete()
            bmp.recycle()
        }
    }

    /** Decodes, downsizes (never upscales) and rotates by EXIF orientation. Null: unsupported or corrupt. */
    private fun render(src: File, spec: ImageTransformSpec): Pair<Bitmap, Fmt>? {
        val ext = src.extension.lowercase()
        if (ext == "gif" || (ext == "webp" && isAnimatedWebp(src))) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(src.path, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0) return null
        val scale = when {
            spec.maxLongEdgePx != null -> min(1.0, spec.maxLongEdgePx.toDouble() / max(w, h))
            spec.scalePercent != null -> min(1.0, spec.scalePercent / 100.0)
            else -> 1.0
        }
        val tw = max(1, (w * scale).roundToInt())
        val th = max(1, (h * scale).roundToInt())
        var sample = 1
        while (w / (sample * 2) >= tw && h / (sample * 2) >= th) sample *= 2
        while ((w / sample).toLong() * (h / sample) > 48_000_000L) sample *= 2
        val decoded = try {
            BitmapFactory.decodeFile(src.path, BitmapFactory.Options().apply { inSampleSize = sample })
        } catch (e: OutOfMemoryError) {
            null
        } ?: return null
        var bmp = decoded
        if (bmp.width != tw || bmp.height != th) {
            bmp = Bitmap.createScaledBitmap(bmp, tw, th, true).also { if (it !== decoded) decoded.recycle() }
        }
        val matrix = orientationMatrix(src)
        if (matrix != null) {
            val before = bmp
            bmp = Bitmap.createBitmap(before, 0, 0, before.width, before.height, matrix, true)
            if (bmp !== before) before.recycle()
        }
        val fmt = when (spec.format) {
            ImageOutputFormat.JPEG -> Fmt.JPEG
            ImageOutputFormat.WEBP -> Fmt.WEBP
            ImageOutputFormat.PNG -> Fmt.PNG
            ImageOutputFormat.KEEP -> when (ext) { "png" -> Fmt.PNG; "webp" -> Fmt.WEBP; else -> Fmt.JPEG }
        }
        return bmp to fmt
    }

    private fun orientationMatrix(src: File): Matrix? = try {
        val m = Matrix()
        when (ExifInterface(src.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return null
        }
        m
    } catch (e: Exception) {
        null
    }

    @Suppress("DEPRECATION")
    private fun encode(bmp: Bitmap, fmt: Fmt, quality: Int, out: OutputStream) {
        val q = quality.coerceIn(1, 100)
        when (fmt) {
            Fmt.JPEG -> bmp.compress(Bitmap.CompressFormat.JPEG, q, out)
            Fmt.PNG -> bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            Fmt.WEBP -> bmp.compress(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP,
                q, out
            )
        }
    }

    /** Orientation is already baked into the pixels, so it is never copied. GPS only when asked. */
    private fun copyExif(src: File, dst: File, fmt: Fmt, keepGps: Boolean) {
        // Platform ExifInterface can only write PNG/WebP from API 30.
        if (fmt != Fmt.JPEG && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val from = ExifInterface(src.path)
            val to = ExifInterface(dst.path)
            val tags = mutableListOf(
                ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL
            )
            if (keepGps) tags += listOf(
                ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
                ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP
            )
            tags.forEach { t -> from.getAttribute(t)?.let { to.setAttribute(t, it) } }
            to.saveAttributes()
        } catch (e: Exception) {
            // Metadata is best effort; the pixels are what matter.
        }
    }

    private fun uniqueTarget(dir: File, base: String, ext: String): File {
        var f = File(dir, "$base.$ext")
        var n = 2
        while (f.exists()) f = File(dir, "$base $n.$ext").also { n++ }
        return f
    }
}
