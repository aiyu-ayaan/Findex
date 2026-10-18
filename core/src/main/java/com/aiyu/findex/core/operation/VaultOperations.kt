package com.aiyu.findex.core.operation

import android.webkit.MimeTypeMap
import com.aiyu.findex.core.db.VaultDao
import com.aiyu.findex.core.model.FailureReason
import com.aiyu.findex.core.model.FileOperation
import com.aiyu.findex.core.model.FileOperationException
import com.aiyu.findex.core.model.OperationProgress
import com.aiyu.findex.core.vault.VaultFormat
import com.aiyu.findex.core.vault.VaultKeyManager
import com.aiyu.findex.core.vault.VaultMeta
import com.aiyu.findex.core.vault.VaultRepository
import com.aiyu.findex.core.vault.VaultRepository.Companion.toRow
import com.aiyu.findex.core.vault.VaultSession
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Executor handler for the vault operations. Owned by W2. */
@Singleton
class VaultOperations @Inject constructor(
    private val dao: VaultDao,
    private val session: VaultSession,
    private val repository: VaultRepository
) {
    private class Progress(val id: String, val report: (OperationProgress) -> Unit, val totalBytes: Long, val totalItems: Int) {
        var bytes = 0L
        var items = 0
        var name = ""
        private var last = 0L
        fun emit(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - last < 120) return
            last = now
            report(OperationProgress(id, name, bytes, totalBytes, items, totalItems))
        }
    }

    suspend fun import(op: FileOperation.VaultImport, touched: MutableList<String>, report: (OperationProgress) -> Unit) {
        val job = currentCoroutineContext().job
        session.hold().use { hold ->
            val dek = hold.key
            val dir = VaultKeyManager.vaultDir().apply { mkdirs() }
            File(dir, ".nomedia").createNewFile()
            val (roots, files) = collect(op.sources, dir)
            var failed = 0
            val progress = Progress(op.id, report, files.sumOf { it.length() }, files.size)
            for (src in files) {
                currentCoroutineContext().ensureActive()
                progress.name = src.name; progress.emit(force = true)
                val blobName = hex(VaultFormat.randomBytes(16)) + VaultKeyManager.BLOB_EXT
                val part = File(dir, "$blobName.part")
                val meta = VaultMeta(src.name, mimeOf(src.name), src.length(), src.lastModified(), src.absolutePath)
                try {
                    run {
                        src.inputStream().buffered().use { input ->
                            FileOutputStream(part).use { out ->
                                VaultFormat.encrypt(input, out.buffered(), dek, meta) { n ->
                                    job.ensureActive()
                                    progress.bytes += n; progress.emit()
                                }
                                out.fd.sync()
                            }
                        }
                    }
                    if (!part.renameTo(File(dir, blobName))) throw IOException("rename failed")
                } catch (e: Throwable) {
                    part.delete()
                    throw e
                }
                val index = VaultFormat.indexKey(dek)
                val row = try { meta.toRow(blobName, index) } finally { index.fill(0) }
                val id = dao.insert(row)
                // Permanent delete: the bin would keep the plaintext around.
                if (!src.delete()) {
                    // Plaintext still there: undo, so the file is never shown as vaulted.
                    dao.delete(listOf(id))
                    File(dir, blobName).delete()
                    failed++
                    continue
                }
                touched += src.absolutePath
                progress.items++
            }
            // Folder sources were flattened; drop the now-empty directories.
            roots.filter { it.isDirectory }.forEach { root ->
                root.walkBottomUp().filter { it.isDirectory }.forEach { d -> if (d.list()?.isEmpty() == true) d.delete() }
                touched += root.absolutePath
            }
            progress.emit(force = true)
            if (failed > 0) throw FileOperationException(FailureReason.ACCESS_DENIED)
        }
    }

    suspend fun export(op: FileOperation.VaultExport, touched: MutableList<String>, report: (OperationProgress) -> Unit) {
        val job = currentCoroutineContext().job
        session.hold().use { hold ->
            val dek = hold.key
            val rows = dao.byIds(op.itemIds)
            val dest = File(op.destinationDir).apply { mkdirs() }
            val progress = Progress(op.id, report, rows.sumOf { it.size }, rows.size)
            for (row in rows) {
                currentCoroutineContext().ensureActive()
                val entry = repository.decrypt(row, dek)
                progress.name = entry.name; progress.emit(force = true)
                val target = uniqueTarget(dest, entry.name)
                try {
                    run {
                        VaultRepository.blobFile(row.blobName).inputStream().buffered().use { input ->
                            FileOutputStream(target).use { out ->
                                VaultFormat.decrypt(input, out.buffered(), dek) { n ->
                                    job.ensureActive()
                                    progress.bytes += n; progress.emit()
                                }
                                out.fd.sync()
                            }
                        }
                    }
                } catch (e: Throwable) {
                    target.delete()
                    throw e
                }
                target.setLastModified(row.lastModified)
                touched += target.absolutePath
                if (op.removeFromVault) {
                    VaultRepository.blobFile(row.blobName).delete()
                    dao.delete(listOf(row.id))
                }
                progress.items++
            }
            progress.emit(force = true)
        }
    }

    suspend fun delete(op: FileOperation.VaultDelete, touched: MutableList<String>, report: (OperationProgress) -> Unit) {
        val rows = dao.byIds(op.itemIds)
        rows.forEachIndexed { i, row ->
            currentCoroutineContext().ensureActive()
            VaultRepository.blobFile(row.blobName).delete()
            dao.delete(listOf(row.id))
            report(OperationProgress(op.id, "", processedItems = i + 1, totalItems = rows.size))
        }
    }

    companion object {
        /**
         * Import roots and their files. Never yields anything inside [vault], so `vault.key` and blobs are
         * never re-encrypted or deleted; a root that contains the vault is rejected outright.
         */
        internal fun collect(sources: List<String>, vault: File): Pair<List<File>, List<File>> {
            val vaultPath = vault.canonicalPath
            fun inVault(f: File) = f.canonicalPath.let { it == vaultPath || it.startsWith("$vaultPath/") }
            val roots = sources.map(::File).filterNot(::inVault)
            if (roots.any { vaultPath.startsWith(it.canonicalPath.trimEnd('/') + "/") }) throw FileOperationException(FailureReason.INTO_ITSELF)
            val files = roots.flatMap { root -> root.walkTopDown().onEnter { !inVault(it) }.filter { it.isFile && !inVault(it) }.toList() }
            return roots to files
        }
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }

    private fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
}
