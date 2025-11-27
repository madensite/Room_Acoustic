package com.example.roomacoustic.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey
    val id: Long,          // ViewModel에서 생성하는 id 그대로 사용
    val roomId: Int,
    val sender: String,    // "user" or "assistant"
    val content: String,
    val createdAt: Long
)
