package com.example.roomacoustic.model

data class ChatMessage(
    val id: Long = 0L,
    val roomId: Int,
    val sender: String,
    val content: String,
    val createdAt: Long
)