package com.example.roomacoustic.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeakerDao {
    @Insert
    suspend fun insertAll(entities: List<SpeakerEntity>)

    @Query("DELETE FROM speakers WHERE roomId = :roomId")
    suspend fun deleteByRoom(roomId: Int)

    @Query("SELECT * FROM speakers WHERE roomId = :roomId ORDER BY id ASC")
    fun listByRoom(roomId: Int): Flow<List<SpeakerEntity>>
}
