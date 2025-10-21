package com.example.roomacoustic.data

import androidx.room.*
import java.util.*

@Entity(
    tableName = "recordings",
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
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val roomId: Int,
    val filePath: String,
    val peakDbfs: Float,
    val rmsDbfs: Float,
    val durationSec: Float,
    val createdAt: Long = System.currentTimeMillis()
)
