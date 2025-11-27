package com.example.roomacoustic.repo

import com.example.roomacoustic.model.ChatMessage
import com.example.roomacoustic.data.ChatMessageEntity
import com.example.roomacoustic.data.ChatDao

class ChatRepository(
    private val chatDao: ChatDao
) {

    // Entity ↔ Model 변환 도우미
    private fun ChatMessageEntity.toModel(): ChatMessage =
        ChatMessage(
            id = id,
            roomId = roomId,
            sender = sender,
            content = content,
            createdAt = createdAt
        )

    private fun ChatMessage.toEntity(): ChatMessageEntity =
        ChatMessageEntity(
            id = id,
            roomId = roomId,
            sender = sender,
            content = content,
            createdAt = createdAt
        )

    // ✅ 특정 방 대화 불러오기
    suspend fun loadConversation(roomId: Int): List<ChatMessage> {
        return chatDao.getMessagesForRoom(roomId)
            .map { it.toModel() }
    }

    // ✅ 메시지 1개 저장 / 갱신
    suspend fun upsertMessage(msg: ChatMessage) {
        chatDao.upsert(msg.toEntity())
    }

    // ✅ 특정 방 대화 삭제
    suspend fun clearConversation(roomId: Int) {
        chatDao.deleteByRoomId(roomId)
    }

    suspend fun hasConversation(roomId: Int): Boolean {
        return chatDao.countMessages(roomId) > 0
    }
}