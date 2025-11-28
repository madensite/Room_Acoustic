package com.example.roomacoustic.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 방 하나당 청취 평가 결과 1개를 저장하는 엔티티
 * roomId를 PK로 써서 1:1 관계로 관리 (같은 roomId에 다시 저장하면 덮어쓰기)
 */
@Entity(tableName = "listening_eval")
data class ListeningEvalEntity(
    @PrimaryKey
    val roomId: Int,

    val total: Int,

    // metric1
    val metric1Name: String,
    val metric1Score: Int,
    val metric1Detail: String,

    // metric2
    val metric2Name: String,
    val metric2Score: Int,
    val metric2Detail: String,

    // metric3
    val metric3Name: String,
    val metric3Score: Int,
    val metric3Detail: String,

    // metric4
    val metric4Name: String,
    val metric4Score: Int,
    val metric4Detail: String,

    // 조언들을 "\n"로 합쳐서 저장
    val notes: String,

    // 🔹 청취 위치 (nullable)
    val listenerX: Float?,   // W 방향 (x)
    val listenerZ: Float?,   // D 방향 (z)

    val updatedAt: Long
)
