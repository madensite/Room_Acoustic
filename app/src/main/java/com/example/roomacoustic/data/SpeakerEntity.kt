package com.example.roomacoustic.data

import androidx.room.*

@Entity(
    tableName = "speakers",
    foreignKeys = [
        ForeignKey(
            entity = RoomEntity::class,
            parentColumns = ["id"],
            childColumns = ["roomId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("roomId")]
)
data class SpeakerEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomId: Int,
    val x: Float,
    val y: Float,
    val z: Float,
    val createdAt: Long = System.currentTimeMillis()
)
