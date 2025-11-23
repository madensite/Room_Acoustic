package com.example.roomacoustic.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningEvalDao {

    @Query("SELECT * FROM listening_eval WHERE roomId = :roomId LIMIT 1")
    fun getByRoom(roomId: Int): Flow<ListeningEvalEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(eval: ListeningEvalEntity)

    @Query("DELETE FROM listening_eval WHERE roomId = :roomId")
    suspend fun deleteForRoom(roomId: Int)

    @Query("DELETE FROM listening_eval")
    suspend fun deleteAll()
}
