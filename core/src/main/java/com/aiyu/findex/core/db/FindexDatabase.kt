package com.aiyu.findex.core.db

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey val path: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_files")
data class RecentFileEntity(
    @PrimaryKey val path: String,
    val name: String,
    val size: Long,
    val openedAt: Long = System.currentTimeMillis()
)

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE path = :path)")
    fun observeIsBookmarked(path: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE path = :path")
    suspend fun delete(path: String)
}

@Dao
interface RecentFileDao {
    @Query("SELECT * FROM recent_files ORDER BY openedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentFileEntity>>

    @Upsert
    suspend fun upsert(file: RecentFileEntity)

    @Query("DELETE FROM recent_files WHERE path IN (:paths)")
    suspend fun delete(paths: List<String>)

    // Keep the table small: only the newest entries matter.
    @Query("DELETE FROM recent_files WHERE path NOT IN (SELECT path FROM recent_files ORDER BY openedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

/** A file or folder moved into a volume's `.FindexTrash` folder; [batchId] is the trashing operation's id. */
@Entity(tableName = "trash", indices = [Index("batchId")])
data class TrashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val size: Long,
    val isDirectory: Boolean,
    val batchId: String,
    val deletedAt: Long = System.currentTimeMillis()
)

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    fun observeAll(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash")
    suspend fun all(): List<TrashEntity>

    @Query("SELECT * FROM trash WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<TrashEntity>

    @Query("SELECT id FROM trash WHERE batchId = :batchId")
    suspend fun idsInBatch(batchId: String): List<Long>

    @Query("SELECT * FROM trash WHERE deletedAt < :before")
    suspend fun olderThan(before: Long): List<TrashEntity>

    @Insert
    suspend fun insert(entry: TrashEntity): Long

    @Query("DELETE FROM trash WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM trash WHERE trashPath IN (:paths)")
    suspend fun deleteByTrashPathsChunk(paths: List<String>)

    // SQLite caps bound parameters at 999; emptying a big bin goes past that.
    @Transaction
    suspend fun deleteByTrashPaths(paths: List<String>) = paths.chunked(500).forEach { deleteByTrashPathsChunk(it) }
}

/** An explorer tab saved across process death; [position] is its slot in the tab strip. */
@Entity(tableName = "open_tabs")
data class OpenTabEntity(
    @PrimaryKey val position: Int,
    val root: String,
    val rootName: String,
    val path: String,
    val isActive: Boolean
)

@Dao
interface OpenTabDao {
    @Query("SELECT * FROM open_tabs ORDER BY position ASC")
    suspend fun all(): List<OpenTabEntity>

    @Query("DELETE FROM open_tabs")
    suspend fun clear()

    @Insert
    suspend fun insert(tabs: List<OpenTabEntity>)

    @Transaction
    suspend fun replaceAll(tabs: List<OpenTabEntity>) {
        clear()
        insert(tabs)
    }
}

/** The cut/copy clipboard, one row per path, so a paste survives the process being killed. */
@Entity(tableName = "clipboard")
data class ClipboardEntity(
    @PrimaryKey val position: Int,
    val path: String,
    val isCut: Boolean
)

@Dao
interface ClipboardDao {
    @Query("SELECT * FROM clipboard ORDER BY position ASC")
    suspend fun all(): List<ClipboardEntity>

    @Query("DELETE FROM clipboard")
    suspend fun clear()

    @Insert
    suspend fun insert(rows: List<ClipboardEntity>)

    @Transaction
    suspend fun replaceAll(rows: List<ClipboardEntity>) {
        clear()
        insert(rows)
    }
}

/**
 * A saved network storage server (SMB, SFTP, FTP, or WebDAV). [passwordCipher] is the password
 * encrypted with an Android Keystore key (`CredentialCipher`); it is never stored in plain text.
 * Null means guest or no password.
 */
@Entity(tableName = "network_servers")
data class NetworkServerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val host: String,
    val share: String,
    val username: String,
    val domain: String,
    val passwordCipher: String?,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "SMB") val protocol: String = "SMB",
    @ColumnInfo(defaultValue = "0") val port: Int = 0,
    /** False: [passwordCipher] stays null and the password is asked for once per app run. */
    @ColumnInfo(defaultValue = "1") val rememberPassword: Boolean = true
)

@Dao
interface NetworkServerDao {
    @Query("SELECT * FROM network_servers ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<NetworkServerEntity>>

    @Query("SELECT * FROM network_servers WHERE id = :id")
    suspend fun byId(id: Long): NetworkServerEntity?

    @Upsert
    suspend fun upsert(server: NetworkServerEntity): Long

    @Query("DELETE FROM network_servers WHERE id = :id")
    suspend fun delete(id: Long)

    /** The browsable entry of a paired Findex computer; [fingerprint] is kept in `username`. */
    @Query("SELECT * FROM network_servers WHERE protocol = 'FINDEX' AND username = :fingerprint LIMIT 1")
    suspend fun byLinkFingerprint(fingerprint: String): NetworkServerEntity?
}

/**
 * A device paired over Findex Link. [tokenInCipher] is the bearer token that device presents to this
 * phone, encrypted with the Keystore (`CredentialCipher`). The token this phone presents to it, its
 * address and port live in its `network_servers` row (protocol FINDEX), which the Explorer browses.
 */
@Entity(tableName = "link_peers")
data class LinkPeerEntity(
    /** Hex SHA-256 of the device's certificate. */
    @PrimaryKey val id: String,
    val name: String,
    val platform: String,
    val tokenInCipher: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface LinkPeerDao {
    @Query("SELECT * FROM link_peers ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<LinkPeerEntity>>

    @Query("SELECT * FROM link_peers")
    suspend fun all(): List<LinkPeerEntity>

    @Query("SELECT * FROM link_peers WHERE id = :id")
    suspend fun byId(id: String): LinkPeerEntity?

    @Upsert
    suspend fun upsert(peer: LinkPeerEntity)

    @Query("DELETE FROM link_peers WHERE id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [
        BookmarkEntity::class, RecentFileEntity::class, TrashEntity::class, OpenTabEntity::class, NetworkServerEntity::class,
        // v7
        TagEntity::class, FileTagEntity::class,
        VaultMetaEntity::class, VaultItemEntity::class,
        StorageSnapshotEntity::class,
        // v8
        ClipboardEntity::class,
        // v9
        LinkPeerEntity::class
    ],
    version = 9,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9)
    ]
)
abstract class FindexDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun recentFileDao(): RecentFileDao
    abstract fun trashDao(): TrashDao
    abstract fun openTabDao(): OpenTabDao
    abstract fun networkServerDao(): NetworkServerDao
    abstract fun tagDao(): TagDao
    abstract fun vaultDao(): VaultDao
    abstract fun storageSnapshotDao(): StorageSnapshotDao
    abstract fun pathIndexDao(): PathIndexDao
    abstract fun clipboardDao(): ClipboardDao
    abstract fun linkPeerDao(): LinkPeerDao
}
