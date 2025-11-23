package com.example.roomacoustic.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        RoomEntity::class,                 // 기존 테이블
        RecordingEntity::class,            // ★ 추가
        MeasureEntity::class,              // ★ 추가
        SpeakerEntity::class,               // ★ 추가
        ListeningEvalEntity::class,
    ],
    version = 3,                           // ★ 기존보다 +1
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    // 기존
    abstract fun roomDao(): RoomDao

    // ★ 추가 DAO
    abstract fun recordingDao(): RecordingDao
    abstract fun measureDao(): MeasureDao
    abstract fun speakerDao(): SpeakerDao
    abstract fun listeningEvalDao(): ListeningEvalDao


    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "roomacoustic.db"
                )
                    // 개발 중 파괴적 마이그레이션 허용 (출시 전 정식 migration 권장)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
