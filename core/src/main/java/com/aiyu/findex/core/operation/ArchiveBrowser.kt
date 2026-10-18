package com.aiyu.findex.core.operation

import android.content.Context
import com.aiyu.findex.core.model.ArchiveEntry
import com.aiyu.findex.core.model.ArchiveFormat
import com.aiyu.findex.core.model.FailureReason
import com.aiyu.findex.core.model.FileOperationException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only access to archive contents for in-archive preview: list entries and pull a single
 * entry into the cache to open it, without extracting the whole archive.
 */
@Singleton
class ArchiveBrowser @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val previewDir get() = File(context.cacheDir, "archive-preview")

    /** @throws FileOperationException with WRONG_PASSWORD or UNSUPPORTED_ARCHIVE when it can't be read. */
    suspend fun list(archivePath: String): List<ArchiveEntry> = withContext(Dispatchers.IO) {
        val archive = File(archivePath)
        val format = ArchiveFormat.of(archive.name) ?: throw FileOperationException(FailureReason.UNSUPPORTED_ARCHIVE)
        ArchiveCodecs.list(format, archive)
    }

    /** Extracts one entry into the preview cache and returns the file, ready to hand to a viewer. */
    suspend fun extractToCache(archivePath: String, entryPath: String, password: String?): File = withContext(Dispatchers.IO) {
        val archive = File(archivePath)
        val format = ArchiveFormat.of(archive.name) ?: throw FileOperationException(FailureReason.UNSUPPORTED_ARCHIVE)
        // Previews from earlier sessions are throwaway; keep recent ones a viewer might still be reading.
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
        previewDir.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
        val slot = File(previewDir, "${archivePath.hashCode().toUInt()}-${entryPath.hashCode().toUInt()}")
        val target = ArchiveCodecs.safeTarget(slot, entryPath.substringAfterLast('/'))
        if (!target.isFile) {
            try {
                ArchiveCodecs.extractEntry(format, archive, entryPath, target, password)
            } catch (e: Exception) {
                target.delete()
                throw e
            }
        }
        target
    }
}
