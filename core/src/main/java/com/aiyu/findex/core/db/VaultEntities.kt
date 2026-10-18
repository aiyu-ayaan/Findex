package com.aiyu.findex.core.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Singleton row (id = 1). Mirrors <vault dir>/vault.key so a reinstall can recover with the password. */
@Entity(tableName = "vault_meta")
data class VaultMetaEntity(
    @PrimaryKey val id: Int = 1,
    val formatVersion: Int,
    /** base64, 16 bytes. */
    val kdfSalt: String,
    /** PBKDF2-HMAC-SHA256, calibrated, >= 310_000. */
    val kdfIterations: Int,
    /** base64(iv12 || AES-GCM(KEK, DEK)). */
    val passwordWrappedKey: String,
    /** base64(iv12 || AES-GCM(bioKey, DEK)); null = biometric off. */
    val biometricWrappedKey: String?,
    @ColumnInfo(defaultValue = "0") val failedAttempts: Int = 0,
    @ColumnInfo(defaultValue = "0") val lockoutUntil: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "vault_items", indices = [Index(value = ["blobName"], unique = true)])
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** "<32 hex>.fdxv", random, says nothing about the file. */
    val blobName: String,
    /** base64 AES-GCM(index key, display name). */
    val nameCipher: String,
    val originalPathCipher: String,
    val mimeType: String,
    val size: Long,
    val lastModified: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_meta WHERE id = 1")
    suspend fun meta(): VaultMetaEntity?

    @Query("SELECT * FROM vault_meta WHERE id = 1")
    fun observeMeta(): Flow<VaultMetaEntity?>

    @Upsert
    suspend fun upsertMeta(meta: VaultMetaEntity)

    @Query("SELECT * FROM vault_items ORDER BY addedAt DESC")
    fun observeItems(): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<VaultItemEntity>

    @Query("SELECT * FROM vault_items WHERE id = :id")
    suspend fun byId(id: Long): VaultItemEntity?

    @Query("SELECT COUNT(*) FROM vault_items")
    suspend fun itemCount(): Int

    @Insert
    suspend fun insert(item: VaultItemEntity): Long

    @Query("DELETE FROM vault_items WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM vault_items")
    suspend fun clearItems()
}
