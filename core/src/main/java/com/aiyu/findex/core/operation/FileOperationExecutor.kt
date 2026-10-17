package com.aiyu.findex.core.operation

import android.content.Context
import com.aiyu.findex.core.db.TrashDao
import com.aiyu.findex.core.db.TrashEntity
import com.aiyu.findex.core.model.FailureReason
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.FileOperationException
import com.aiyu.findex.core.model.OperationProgress
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ensureActive
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.EncryptionMethod
import net.lingala.zip4j.progress.ProgressMonitor
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs one [FileOperation] on the calling (IO) coroutine, reporting progress.
 * Cancellation is cooperative: cancel the coroutine and the executor stops at the next chunk.
 */
@Singleton
class FileOperationExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trashDao: TrashDao
) {

    private companion object {
        const val BUFFER_SIZE = 256 * 1024
        const val REPORT_INTERVAL_MS = 120L
        const val TRASH_DIR = ".FindexTrash"
    }

    /** @return every path created or removed, so the caller can refresh MediaStore. */
    suspend fun execute(operation: FileOperation, report: (OperationProgress) -> Unit): List<String> {
        val touched = mutableListOf<String>()
        try {
            when (operation) {
                is FileOperation.Copy -> transfer(operation.id, operation.sources, operation.destinationDir, move = false, touched, report)
                is FileOperation.Move -> transfer(operation.id, operation.sources, operation.destinationDir, move = true, touched, report)
                is FileOperation.Delete -> delete(operation.id, operation.targets, touched, report)
                    .also { trashDao.deleteByTrashPaths(operation.targets) }
                is FileOperation.Trash -> trash(operation, touched, report)
                is FileOperation.Restore -> restore(operation, touched, report)
                is FileOperation.Clean -> delete(operation.id, operation.targets, touched, report)
                is FileOperation.Compress -> compress(operation, report).also { touched += operation.destinationArchive }
                is FileOperation.Extract -> extract(operation, report)
                    .also { File(operation.destinationDir).walkTopDown().mapTo(touched) { it.absolutePath } }
            }
            return touched
        } catch (e: FileOperationException) {
            throw e
        } catch (e: SecurityException) {
            throw FileOperationException(FailureReason.ACCESS_DENIED, e)
        } catch (e: IOException) {
            val reason = if (e.message?.contains("ENOSPC") == true) FailureReason.NO_SPACE else FailureReason.UNKNOWN
            throw FileOperationException(reason, e)
        }
    }

    // region Copy / Move

    private suspend fun transfer(
        id: String,
        sources: List<String>,
        destinationDir: String,
        move: Boolean,
        touched: MutableList<String>,
        report: (OperationProgress) -> Unit
    ) {
        val destDir = File(destinationDir)
        val files = sources.map(::File).filter { it.exists() }
        files.forEach { src ->
            if (src.isDirectory && destDir.canonicalPath.startsWith(src.canonicalPath + File.separator) ||
                src.isDirectory && destDir.canonicalPath == src.canonicalPath
            ) throw FileOperationException(FailureReason.INTO_ITSELF)
        }

        report(OperationProgress(id, isIndeterminate = true))
        val totalBytes = files.sumOf { it.totalSize() }
        val totalItems = files.sumOf { it.walkTopDown().count() }
        val tracker = ProgressTracker(id, totalBytes, totalItems, report)

        for (src in files) {
            // Moving within the same volume is a cheap rename: try that before copying bytes.
            // Moving into the folder the file already lives in is a no-op.
            if (move && src.parentFile?.canonicalPath == destDir.canonicalPath) continue
            val target = uniqueTarget(destDir, src.name)
            if (move) src.walkTopDown().mapTo(touched) { it.absolutePath }
            if (move && src.renameTo(target)) {
                target.walkTopDown().mapTo(touched) { it.absolutePath }
                tracker.addItems(src.name, items = target.walkTopDown().count(), bytes = target.totalSize())
                continue
            }
            copyRecursively(src, target, tracker)
            target.walkTopDown().mapTo(touched) { it.absolutePath }
            if (move) src.deleteRecursively()
        }
        tracker.finish()
    }

    private suspend fun copyRecursively(src: File, target: File, tracker: ProgressTracker) {
        currentCoroutineContext().ensureActive()
        if (src.isDirectory) {
            if (!target.exists() && !target.mkdirs()) throw IOException("mkdirs failed: $target")
            tracker.addItems(src.name, items = 1, bytes = 0)
            src.listFiles()?.forEach { copyRecursively(it, File(target, it.name), tracker) }
            return
        }
        try {
            src.inputStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        tracker.addBytes(src.name, read.toLong())
                    }
                }
            }
        } catch (e: Throwable) {
            target.delete() // a half-copied file is worse than none
            throw e
        }
        target.setLastModified(src.lastModified())
        tracker.addItems(src.name, items = 1, bytes = 0)
    }

    // endregion

    // region Delete

    private suspend fun delete(id: String, targets: List<String>, touched: MutableList<String>, report: (OperationProgress) -> Unit) {
        val files = targets.map(::File).filter { it.exists() }
        report(OperationProgress(id, isIndeterminate = true))
        val tracker = ProgressTracker(id, totalBytes = 0, totalItems = files.sumOf { it.walkTopDown().count() }, report)
        for (root in files) {
            // Bottom-up so folders are empty when their turn comes.
            for (file in root.walkBottomUp()) {
                currentCoroutineContext().ensureActive()
                if (!file.delete() && file.exists()) throw FileOperationException(FailureReason.ACCESS_DENIED)
                touched += file.absolutePath
                tracker.addItems(file.name, items = 1, bytes = 0)
            }
        }
        tracker.finish()
    }

    // endregion

    // region Recycle bin

    private suspend fun trash(operation: FileOperation.Trash, touched: MutableList<String>, report: (OperationProgress) -> Unit) {
        val files = operation.targets.map(::File).filter { it.exists() }
        report(OperationProgress(operation.id, isIndeterminate = true))
        val tracker = ProgressTracker(operation.id, totalBytes = 0, totalItems = files.sumOf { it.walkTopDown().count() }, report)
        val alreadyInBin = mutableListOf<String>()
        for (src in files) {
            currentCoroutineContext().ensureActive()
            val bin = trashDirFor(src)
            // Deleting something that is already in the bin (browsed via hidden files) is permanent.
            if (src.canonicalPath == bin.canonicalPath || src.canonicalPath.startsWith(bin.canonicalPath + File.separator)) {
                alreadyInBin += src.absolutePath
                continue
            }
            val target = uniqueTarget(bin, src.name)
            // Row first: if the process dies mid-move the file is still findable from the bin.
            val rowId = trashDao.insert(
                TrashEntity(
                    originalPath = src.absolutePath, trashPath = target.absolutePath, name = src.name,
                    size = src.totalSize(), isDirectory = src.isDirectory, batchId = operation.id
                )
            )
            src.walkTopDown().mapTo(touched) { it.absolutePath }
            if (!moveFile(src, target, tracker)) {
                if (!target.exists()) trashDao.delete(rowId)
                throw FileOperationException(FailureReason.ACCESS_DENIED)
            }
        }
        if (alreadyInBin.isNotEmpty()) {
            delete(operation.id, alreadyInBin, touched) {}
            trashDao.deleteByTrashPaths(alreadyInBin)
        }
        tracker.finish()
    }

    private suspend fun restore(operation: FileOperation.Restore, touched: MutableList<String>, report: (OperationProgress) -> Unit) {
        val entries = operation.ids.chunked(500).flatMap { trashDao.byIds(it) }
        report(OperationProgress(operation.id, isIndeterminate = true))
        val tracker = ProgressTracker(operation.id, totalBytes = 0, totalItems = entries.sumOf { File(it.trashPath).walkTopDown().count() }, report)
        for (entry in entries) {
            currentCoroutineContext().ensureActive()
            val src = File(entry.trashPath)
            if (!src.exists()) {
                trashDao.delete(entry.id)
                continue
            }
            val original = File(entry.originalPath)
            val parent = original.parentFile ?: throw FileOperationException(FailureReason.ACCESS_DENIED)
            if (!parent.exists() && !parent.mkdirs()) throw FileOperationException(FailureReason.ACCESS_DENIED)
            val target = uniqueTarget(parent, original.name)
            if (!moveFile(src, target, tracker)) throw FileOperationException(FailureReason.ACCESS_DENIED)
            trashDao.delete(entry.id)
            target.walkTopDown().mapTo(touched) { it.absolutePath }
        }
        tracker.finish()
    }

    /** Rename when on the same volume, otherwise copy then delete. False when the source could not be fully removed. */
    private suspend fun moveFile(src: File, target: File, tracker: ProgressTracker): Boolean {
        if (src.renameTo(target)) {
            tracker.addItems(src.name, items = target.walkTopDown().count(), bytes = 0)
            return true
        }
        copyRecursively(src, target, tracker)
        // The copy is kept even if the source is only partly removed, so nothing gets lost.
        return src.deleteRecursively()
    }

    /** `<volume root>/.FindexTrash`, so trashing stays a cheap rename on the same volume. */
    private fun trashDirFor(file: File): File {
        val roots = context.getExternalFilesDirs(null).filterNotNull().map { it.absolutePath.substringBefore("/Android/data/") }
        val root = roots.filter { file.absolutePath.startsWith(it + File.separator) }.maxByOrNull { it.length } ?: roots.first()
        val dir = File(root, TRASH_DIR)
        if (!dir.exists() && !dir.mkdirs()) throw FileOperationException(FailureReason.ACCESS_DENIED)
        File(dir, ".nomedia").createNewFile()
        return dir
    }

    // endregion

    // region Archives

    private suspend fun compress(op: FileOperation.Compress, report: (OperationProgress) -> Unit) {
        val archive = File(op.destinationArchive)
        val password = op.password?.takeIf { it.isNotEmpty() }
        val zip = ZipFile(archive, password?.toCharArray()).apply { isRunInThread = true }
        val params = ZipParameters().apply {
            if (password != null) {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
            }
        }
        val sources = op.targets.map(::File).filter { it.exists() }
        val totalBytes = sources.sumOf { it.totalSize() }
        var doneBytes = 0L
        try {
            zip.use {
                for (src in sources) {
                    if (src.isDirectory) zip.addFolder(src, params) else zip.addFile(src, params)
                    awaitMonitor(zip.progressMonitor) { fraction ->
                        val bytes = doneBytes + (src.totalSize() * fraction).toLong()
                        report(OperationProgress(op.id, src.name, processedBytes = bytes, totalBytes = totalBytes))
                    }
                    doneBytes += src.totalSize()
                }
            }
        } catch (e: Throwable) {
            archive.delete() // never leave a half-written archive behind
            throw e
        }
        report(OperationProgress(op.id, processedBytes = totalBytes, totalBytes = totalBytes, isComplete = true))
    }

    private suspend fun extract(op: FileOperation.Extract, report: (OperationProgress) -> Unit) {
        val zip = ZipFile(File(op.archivePath), op.password?.toCharArray()).apply { isRunInThread = true }
        zip.use {
            try {
                if (zip.isEncrypted && op.password.isNullOrEmpty()) throw FileOperationException(FailureReason.WRONG_PASSWORD)
                val dest = File(op.destinationDir).apply { mkdirs() }
                zip.extractAll(dest.absolutePath)
                awaitMonitor(zip.progressMonitor) { fraction ->
                    report(OperationProgress(op.id, File(op.archivePath).name, processedItems = (fraction * 100).toInt(), totalItems = 100))
                }
            } catch (e: ZipException) {
                if (e.type == ZipException.Type.WRONG_PASSWORD) throw FileOperationException(FailureReason.WRONG_PASSWORD, e)
                throw FileOperationException(FailureReason.UNKNOWN, e)
            }
        }
        report(OperationProgress(op.id, processedItems = 100, totalItems = 100, isComplete = true))
    }

    /** Polls a zip4j background task, forwarding progress and cancelling it if the coroutine is cancelled. */
    private suspend fun awaitMonitor(monitor: ProgressMonitor, onFraction: (Float) -> Unit) {
        try {
            while (monitor.state == ProgressMonitor.State.BUSY) {
                onFraction(monitor.percentDone / 100f)
                delay(REPORT_INTERVAL_MS)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            monitor.isCancelAllTasks = true
            throw e
        }
        when (monitor.result) {
            ProgressMonitor.Result.ERROR -> {
                val cause = monitor.exception
                if (cause is ZipException) throw cause
                throw IOException(cause)
            }
            ProgressMonitor.Result.CANCELLED -> throw kotlinx.coroutines.CancellationException()
            else -> onFraction(1f)
        }
    }

    // endregion

    private fun File.totalSize(): Long =
        if (isDirectory) walkTopDown().filter { it.isFile }.sumOf { it.length() } else length()

    private class ProgressTracker(
        private val id: String,
        private val totalBytes: Long,
        private val totalItems: Int,
        private val report: (OperationProgress) -> Unit
    ) {
        private var bytes = 0L
        private var items = 0
        private var lastReport = 0L

        fun addBytes(name: String, delta: Long) {
            bytes += delta
            maybeReport(name)
        }

        fun addItems(name: String, items: Int, bytes: Long) {
            this.items += items
            this.bytes += bytes
            maybeReport(name)
        }

        fun finish() = report(
            OperationProgress(id, processedBytes = totalBytes, totalBytes = totalBytes, processedItems = totalItems, totalItems = totalItems, isComplete = true)
        )

        private fun maybeReport(name: String) {
            val now = System.currentTimeMillis()
            if (now - lastReport < REPORT_INTERVAL_MS) return
            lastReport = now
            report(OperationProgress(id, name, bytes, totalBytes, items, totalItems))
        }
    }
}

/** `name.ext`, then `name (1).ext`, `name (2).ext`… — never overwrites. */
fun uniqueTarget(dir: File, name: String): File {
    var candidate = File(dir, name)
    if (!candidate.exists()) return candidate
    val base = name.substringBeforeLast('.', name)
    val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
    var i = 1
    while (candidate.exists()) candidate = File(dir, "$base (${i++})$ext")
    return candidate
}
