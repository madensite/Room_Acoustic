package com.example.roomacoustic.data

import androidx.room.*

@Entity(
    tableName = "measures",
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
data class MeasureEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomId: Int,
    val width: Float,
    val depth: Float,
    val height: Float,
    val createdAt: Long = System.currentTimeMillis()
)
