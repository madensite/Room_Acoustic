package com.example.roomacoustic.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasureDao {
    @Insert
    suspend fun insert(entity: MeasureEntity): Long

    @Query("SELECT * FROM measures WHERE roomId = :roomId ORDER BY createdAt DESC LIMIT 1")
    fun latestByRoom(roomId: Int): Flow<MeasureEntity?>
}
