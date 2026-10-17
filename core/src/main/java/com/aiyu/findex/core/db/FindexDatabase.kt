package com.aiyu.findex.core.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
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

@Database(
    entities = [BookmarkEntity::class, RecentFileEntity::class],
    version = 1,
    exportSchema = true
)
abstract class FindexDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun recentFileDao(): RecentFileDao
}
