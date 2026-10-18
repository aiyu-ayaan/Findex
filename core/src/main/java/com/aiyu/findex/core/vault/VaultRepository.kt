package com.aiyu.findex.core.vault

import com.aiyu.findex.core.db.VaultDao
import com.aiyu.findex.core.db.VaultItemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** A vault row with its name and original path decrypted. */
data class VaultEntry(
    val id: Long,
    val name: String,
    val originalPath: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val addedAt: Long
)

@Singleton
class VaultRepository @Inject constructor(
    private val dao: VaultDao,
    private val session: VaultSession
) {
    /** Decrypted entries while unlocked, empty while locked. */
    fun items(): Flow<List<VaultEntry>> = combine(dao.observeItems(), session.unlocked) { rows, unlocked ->
        if (!unlocked) return@combine emptyList()
        runCatching {
            session.withKey { dek ->
                val index = VaultFormat.indexKey(dek)
                try { rows.map { it.decrypt(index) } } finally { index.fill(0) }
            }
        }.getOrDefault(emptyList())
    }.flowOn(Dispatchers.Default)

    fun decrypt(row: VaultItemEntity): VaultEntry = session.withKey { dek ->
        val index = VaultFormat.indexKey(dek)
        try { row.decrypt(index) } finally { index.fill(0) }
    }

    private fun VaultItemEntity.decrypt(index: ByteArray) = VaultEntry(
        id, VaultFormat.openString(index, nameCipher), VaultFormat.openString(index, originalPathCipher),
        mimeType, size, lastModified, addedAt
    )

    /** Blob files on disk; with an empty index this means a reinstall that can be restored. */
    suspend fun blobCount(): Int = withContext(Dispatchers.IO) {
        VaultKeyManager.vaultDir().listFiles { f -> f.name.endsWith(VaultKeyManager.BLOB_EXT) }?.size ?: 0
    }

    /** Rebuilds `vault_items` from the encrypted blob headers. Call only while the index is empty. */
    suspend fun rebuildIndex(): Int = withContext(Dispatchers.IO) {
        val blobs = VaultKeyManager.vaultDir().listFiles { f -> f.name.endsWith(VaultKeyManager.BLOB_EXT) }.orEmpty()
        var restored = 0
        for (blob in blobs) {
            val row = runCatching {
                session.withKey { dek ->
                    val meta = blob.inputStream().buffered().use { VaultFormat.readMeta(it, dek) }
                    val index = VaultFormat.indexKey(dek)
                    try { meta.toRow(blob.name, index) } finally { index.fill(0) }
                }
            }.getOrNull() ?: continue
            dao.insert(row)
            restored++
        }
        restored
    }

    companion object {
        fun VaultMeta.toRow(blobName: String, index: ByteArray) = VaultItemEntity(
            blobName = blobName,
            nameCipher = VaultFormat.sealString(index, name),
            originalPathCipher = VaultFormat.sealString(index, originalPath),
            mimeType = mime,
            size = size,
            lastModified = mtime
        )

        fun blobFile(blobName: String): File = File(VaultKeyManager.vaultDir(), blobName)
    }
}
