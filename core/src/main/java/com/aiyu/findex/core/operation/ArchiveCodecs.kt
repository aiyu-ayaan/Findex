package com.aiyu.findex.core.operation

import com.aiyu.findex.core.model.ArchiveFormat
import com.aiyu.findex.core.model.FailureReason
import com.aiyu.findex.core.model.FileOperationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import org.apache.commons.compress.PasswordRequiredException
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile

/**
 * Archive codecs besides ZIP writing (zip4j, in [FileOperationExecutor]): 7z and tar via Commons Compress,
 * ZIP and RAR reading via native 7-Zip.
 * [onBytes] receives (current entry name, bytes processed since the last call).
 */
internal object ArchiveCodecs {

    private const val BUFFER_SIZE = 256 * 1024

    // region Write

    suspend fun write7z(sources: List<File>, archive: File, onBytes: (String, Long) -> Unit) {
        SevenZOutputFile(archive).use { out ->
            forEachEntry(sources) { file, name ->
                out.putArchiveEntry(out.createArchiveEntry(file, name))
                if (file.isFile) file.inputStream().use { copy(it, file.name, onBytes) { b, n -> out.write(b, 0, n) } }
                out.closeArchiveEntry()
            }
        }
    }

    suspend fun writeTar(sources: List<File>, archive: File, gzip: Boolean, onBytes: (String, Long) -> Unit) {
        val sink = archive.outputStream().buffered().let { if (gzip) GzipCompressorOutputStream(it) else it }
        TarArchiveOutputStream(sink).use { out ->
            out.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            out.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX)
            forEachEntry(sources) { file, name ->
                out.putArchiveEntry(TarArchiveEntry(file, name))
                if (file.isFile) file.inputStream().use { copy(it, file.name, onBytes) { b, n -> out.write(b, 0, n) } }
                out.closeArchiveEntry()
            }
            out.finish()
        }
    }

    /** Every source and everything under it, named relative to the source's parent folder. */
    private suspend fun forEachEntry(sources: List<File>, action: suspend (File, String) -> Unit) {
        for (root in sources) {
            val base = root.parentFile
            for (file in root.walkTopDown()) {
                currentCoroutineContext().ensureActive()
                action(file, if (base == null) file.name else file.relativeTo(base).invariantSeparatorsPath)
            }
        }
    }

    // endregion

    // region Read

    suspend fun read(
        format: ArchiveFormat,
        archive: File,
        dest: File,
        password: String?,
        onTotal: (Long) -> Unit,
        onBytes: (String, Long) -> Unit
    ) = when (format) {
        ArchiveFormat.SEVEN_Z -> read7z(archive, dest, password, onTotal, onBytes)
        ArchiveFormat.ZIP, ArchiveFormat.RAR -> readNative(archive, dest, password, onTotal, onBytes)
        ArchiveFormat.TAR, ArchiveFormat.TAR_GZ, ArchiveFormat.TAR_XZ, ArchiveFormat.TAR_BZ2 -> readTar(format, archive, dest, onTotal, onBytes)
    }

    private suspend fun read7z(archive: File, dest: File, password: String?, onTotal: (Long) -> Unit, onBytes: (String, Long) -> Unit) {
        val builder = SevenZFile.builder().setFile(archive)
        if (password != null) builder.setPassword(password.toCharArray())
        try {
            builder.get().use { sz ->
                onTotal(sz.entries.sumOf { it.size })
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val entry = sz.nextEntry ?: break
                    val target = safeTarget(dest, entry.name)
                    if (entry.isDirectory) {
                        target.mkdirs()
                        continue
                    }
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = sz.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            onBytes(entry.name, read.toLong())
                        }
                    }
                }
            }
        } catch (e: PasswordRequiredException) {
            throw FileOperationException(FailureReason.WRONG_PASSWORD, e)
        } catch (e: IOException) {
            // shortcut: a bad AES key surfaces as a corrupt LZMA stream / checksum error, not a typed exception.
            if (password != null && e.message?.contains("ENOSPC") != true) throw FileOperationException(FailureReason.WRONG_PASSWORD, e)
            throw e
        }
    }

    private suspend fun readTar(format: ArchiveFormat, archive: File, dest: File, onTotal: (Long) -> Unit, onBytes: (String, Long) -> Unit) {
        // Tar has no index, so progress follows the compressed bytes read from disk.
        onTotal(archive.length())
        var current = archive.name
        val raw = CountingInputStream(archive.inputStream().buffered()) { onBytes(current, it) }
        val decompressed: InputStream = when (format) {
            ArchiveFormat.TAR_GZ -> GzipCompressorInputStream(raw, true)
            ArchiveFormat.TAR_XZ -> XZCompressorInputStream(raw)
            ArchiveFormat.TAR_BZ2 -> BZip2CompressorInputStream(raw)
            else -> raw
        }
        TarArchiveInputStream(decompressed).use { tar ->
            while (true) {
                val entry = tar.nextEntry ?: break
                current = entry.name
                // Links could point outside the destination; skip them.
                if (entry.isSymbolicLink || entry.isLink || !tar.canReadEntryData(entry)) continue
                val target = safeTarget(dest, entry.name)
                if (entry.isDirectory) {
                    target.mkdirs()
                    continue
                }
                target.parentFile?.mkdirs()
                target.outputStream().use { out -> copy(tar, entry.name, { _, _ -> }) { b, n -> out.write(b, 0, n) } }
            }
        }
    }

    /**
     * Native 7-Zip engine: RAR4/RAR5 and every zip method (Deflate64, BZip2, LZMA, PPMd, AES, ZipCrypto).
     * Not covered by JVM unit tests: the engine is an Android .so.
     */
    private suspend fun readNative(archive: File, dest: File, password: String?, onTotal: (Long) -> Unit, onBytes: (String, Long) -> Unit) {
        val job = currentCoroutineContext()[Job]
        RandomAccessFile(archive, "r").use { raf ->
            val opened = try {
                // Null format = autodetect from the content, not the file name.
                SevenZip.openInArchive(null, RandomAccessFileInStream(raf), password)
            } catch (e: SevenZipException) {
                // Encrypted headers (RAR -hp, 7z -mhe) fail to open without the right password.
                throw FileOperationException(if (password != null) FailureReason.WRONG_PASSWORD else FailureReason.UNSUPPORTED_ARCHIVE, e)
            }
            opened.use { inArchive ->
                val items = inArchive.simpleInterface.archiveItems
                onTotal(items.sumOf { it.size ?: 0L })
                for (item in items) {
                    currentCoroutineContext().ensureActive()
                    val name = item.path.replace('\\', '/')
                    val target = safeTarget(dest, name)
                    if (item.isFolder) {
                        target.mkdirs()
                        continue
                    }
                    if (item.isEncrypted && password == null) throw FileOperationException(FailureReason.WRONG_PASSWORD)
                    target.parentFile?.mkdirs()
                    val result = target.outputStream().buffered().use { out ->
                        item.extractSlow({ data ->
                            // Throwing from the callback is the only way to stop the native loop mid-file.
                            if (job?.isActive == false) throw SevenZipException("cancelled")
                            out.write(data)
                            onBytes(name, data.size.toLong())
                            data.size
                        }, password)
                    }
                    currentCoroutineContext().ensureActive()
                    when (result) {
                        ExtractOperationResult.OK -> Unit
                        ExtractOperationResult.WRONG_PASSWORD -> throw FileOperationException(FailureReason.WRONG_PASSWORD)
                        ExtractOperationResult.UNSUPPORTEDMETHOD -> throw FileOperationException(FailureReason.UNSUPPORTED_ARCHIVE)
                        // A bad key on an encrypted entry shows up as a data or CRC error.
                        else -> throw FileOperationException(if (item.isEncrypted) FailureReason.WRONG_PASSWORD else FailureReason.UNKNOWN)
                    }
                }
            }
        }
    }

    /** Rejects entries like `../../x` that would land outside [dest] ("zip slip"). */
    internal fun safeTarget(dest: File, entryName: String): File {
        val target = File(dest, entryName)
        val root = dest.canonicalPath
        val path = target.canonicalPath
        if (path != root && !path.startsWith(root + File.separator)) throw FileOperationException(FailureReason.UNSUPPORTED_ARCHIVE)
        return target
    }

    // endregion

    private suspend inline fun copy(input: InputStream, name: String, onBytes: (String, Long) -> Unit, write: (ByteArray, Int) -> Unit) {
        val buffer = ByteArray(BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break
            write(buffer, read)
            onBytes(name, read.toLong())
        }
    }

    private class CountingInputStream(input: InputStream, private val onRead: (Long) -> Unit) : FilterInputStream(input) {
        override fun read(): Int = super.read().also { if (it >= 0) onRead(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) onRead(it.toLong()) }
        override fun skip(n: Long): Long = super.skip(n).also { if (it > 0) onRead(it) }
    }
}
