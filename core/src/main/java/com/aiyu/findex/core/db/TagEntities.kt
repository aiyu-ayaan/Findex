package com.aiyu.findex.core.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Index into TagTones (0..7). Never a raw color. */
    val colorIndex: Int,
    val position: Int,
    val createdAt: Long = System.currentTimeMillis()
)

/** No FK: TagDao deletes children explicitly, so we do not depend on PRAGMA foreign_keys. */
@Entity(tableName = "file_tags", primaryKeys = ["path", "tagId"], indices = [Index("tagId")])
data class FileTagEntity(
    val path: String,
    val tagId: Long,
    val createdAt: Long = System.currentTimeMillis()
)

data class PathTag(val path: String, val tagId: Long, val name: String, val colorIndex: Int)

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY position ASC, name ASC")
    fun observeTags(): Flow<List<TagEntity>>

    /** Direct children of a folder only. [dirLike] = escaped "$dir/%", [deeperLike] = escaped "$dir/%/%". */
    @Query(
        """SELECT ft.path, t.id AS tagId, t.name, t.colorIndex FROM file_tags ft JOIN tags t ON t.id = ft.tagId
           WHERE ft.path LIKE :dirLike ESCAPE '\' AND ft.path NOT LIKE :deeperLike ESCAPE '\'"""
    )
    fun observeInFolder(dirLike: String, deeperLike: String): Flow<List<PathTag>>

    @Query("SELECT path FROM file_tags WHERE tagId = :tagId ORDER BY createdAt DESC")
    fun observePaths(tagId: Long): Flow<List<String>>

    @Query("SELECT tagId FROM file_tags WHERE path = :path")
    suspend fun tagIdsFor(path: String): List<Long>

    @Upsert
    suspend fun upsertTag(tag: TagEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun assign(rows: List<FileTagEntity>)

    @Query("DELETE FROM file_tags WHERE tagId = :tagId AND path IN (:paths)")
    suspend fun unassign(tagId: Long, paths: List<String>)

    @Query("DELETE FROM file_tags WHERE path IN (:paths)")
    suspend fun forgetPaths(paths: List<String>)

    @Query("DELETE FROM file_tags WHERE tagId = :tagId")
    suspend fun clearTag(tagId: Long)

    @Query("DELETE FROM tags WHERE id = :tagId")
    suspend fun deleteTagRow(tagId: Long)

    @Transaction
    suspend fun deleteTag(tagId: Long) {
        clearTag(tagId)
        deleteTagRow(tagId)
    }
}
