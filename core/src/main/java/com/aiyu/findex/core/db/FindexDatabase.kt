package com.aiyu.findex.core.db

import androidx.room.AutoMigration
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

@Database(
    entities = [BookmarkEntity::class, RecentFileEntity::class, TrashEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
abstract class FindexDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun recentFileDao(): RecentFileDao
    abstract fun trashDao(): TrashDao
}
