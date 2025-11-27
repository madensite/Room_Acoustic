package com.example.roomacoustic.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ChatDao {

    @Query("""
        SELECT * FROM chat_messages
        WHERE roomId = :roomId
        ORDER BY createdAt ASC
    """)
    suspend fun getMessagesForRoom(roomId: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE roomId = :roomId")
    suspend fun deleteByRoomId(roomId: Int)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE roomId = :roomId")
    suspend fun countMessages(roomId: Int): Int
}
