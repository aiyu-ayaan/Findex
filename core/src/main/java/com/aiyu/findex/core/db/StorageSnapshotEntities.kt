package com.aiyu.findex.core.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "storage_snapshots", indices = [Index(value = ["volumePath", "day"], unique = true)])
data class StorageSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val volumePath: String,
    /** LocalDate.toEpochDay() in the device zone. */
    val day: Long,
    val takenAt: Long,
    val totalBytes: Long,
    val usedBytes: Long,
    @ColumnInfo(defaultValue = "0") val imageBytes: Long = 0,
    @ColumnInfo(defaultValue = "0") val videoBytes: Long = 0,
    @ColumnInfo(defaultValue = "0") val audioBytes: Long = 0,
    @ColumnInfo(defaultValue = "0") val documentBytes: Long = 0,
    @ColumnInfo(defaultValue = "0") val archiveBytes: Long = 0,
    @ColumnInfo(defaultValue = "0") val apkBytes: Long = 0
)

@Dao
interface StorageSnapshotDao {
    /** REPLACE, not @Upsert: the conflict is on the (volumePath, day) unique index, not the PK. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: StorageSnapshotEntity)

    @Query("SELECT * FROM storage_snapshots WHERE volumePath = :volume AND day >= :fromDay ORDER BY day ASC")
    fun observe(volume: String, fromDay: Long): Flow<List<StorageSnapshotEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM storage_snapshots WHERE day = :day)")
    suspend fun hasDay(day: Long): Boolean

    @Query("DELETE FROM storage_snapshots WHERE day < :beforeDay")
    suspend fun trim(beforeDay: Long)
}
