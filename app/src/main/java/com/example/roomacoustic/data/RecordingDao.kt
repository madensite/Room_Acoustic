package com.example.roomacoustic.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordingDao {
    @Insert
    suspend fun insert(entity: RecordingEntity): Long

    @Query("SELECT * FROM recordings WHERE roomId = :roomId ORDER BY createdAt DESC LIMIT 1")
    fun latestByRoom(roomId: Int): Flow<RecordingEntity?>

    @Query("SELECT * FROM recordings WHERE roomId = :roomId ORDER BY createdAt DESC")
    fun listByRoom(roomId: Int): Flow<List<RecordingEntity>>
}
