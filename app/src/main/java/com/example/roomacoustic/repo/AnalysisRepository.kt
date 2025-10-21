package com.example.roomacoustic.repo

import com.example.roomacoustic.data.*
import kotlinx.coroutines.flow.Flow

class AnalysisRepository(
    private val recDao: RecordingDao,
    private val measureDao: MeasureDao,
    private val speakerDao: SpeakerDao
) {
    suspend fun saveRecording(roomId: Int, filePath: String, peak: Float, rms: Float, duration: Float) {
        recDao.insert(
            RecordingEntity(
                roomId = roomId,
                filePath = filePath,
                peakDbfs = peak,
                rmsDbfs = rms,
                durationSec = duration
            )
        )
    }

    fun latestRecording(roomId: Int): Flow<RecordingEntity?> =
        recDao.latestByRoom(roomId)

    fun listRecordings(roomId: Int): Flow<List<RecordingEntity>> =
        recDao.listByRoom(roomId)

    suspend fun saveMeasure(roomId: Int, w: Float, d: Float, h: Float) {
        measureDao.insert(MeasureEntity(roomId = roomId, width = w, depth = d, height = h))
    }

    fun latestMeasure(roomId: Int): Flow<MeasureEntity?> =
        measureDao.latestByRoom(roomId)

    suspend fun replaceSpeakers(roomId: Int, worldPositions: List<FloatArray>) {
        speakerDao.deleteByRoom(roomId)
        val rows = worldPositions.map {
            SpeakerEntity(roomId = roomId, x = it[0], y = it[1], z = it[2])
        }
        if (rows.isNotEmpty()) speakerDao.insertAll(rows)
    }

    fun speakers(roomId: Int): Flow<List<SpeakerEntity>> =
        speakerDao.listByRoom(roomId)
}
