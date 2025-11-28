package com.example.roomacoustic.repo

import com.example.roomacoustic.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.roomacoustic.data.ListeningEvalEntity
import com.example.roomacoustic.data.ListeningEvalDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.example.roomacoustic.model.ListeningEval
import com.example.roomacoustic.model.ListeningMetric
import com.example.roomacoustic.model.Vec2


class AnalysisRepository(
    private val recDao: RecordingDao,
    private val measureDao: MeasureDao,
    private val speakerDao: SpeakerDao,
    private val listeningEvalDao: ListeningEvalDao
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

    // ───────────────── 청취 평가 저장/조회 ─────────────────

    /** 방별 청취 평가 조회 (Flow) */
    fun listeningEval(roomId: Int): Flow<ListeningEval?> =
        listeningEvalDao.getByRoom(roomId).map { entity ->
            entity?.toModel()
        }

    /** 방별 청취 평가 저장 (덮어쓰기) */
    suspend fun saveListeningEval(roomId: Int, eval: ListeningEval) {
        listeningEvalDao.upsert(eval.toEntity(roomId))
    }

    /** 방별 청취 평가 삭제 */
    suspend fun deleteListeningEval(roomId: Int) {
        listeningEvalDao.deleteForRoom(roomId)
    }

    // ────────────────────────────────
    // 청취 평가 매핑 헬퍼 (파일 내부 전용)
    // ────────────────────────────────

    private fun ListeningEval.toEntity(roomId: Int): ListeningEvalEntity {
        // metrics 리스트가 4개보다 적더라도 안전하게 채우기
        val safeMetrics = this.metrics.take(4).let { list ->
            if (list.size >= 4) list
            else list + List(4 - list.size) { ListeningMetric("N/A", 0, "") }
        }

        val m0 = safeMetrics[0]
        val m1 = safeMetrics[1]
        val m2 = safeMetrics[2]
        val m3 = safeMetrics[3]

        val (lx, lz) = listener?.let { it.x to it.z } ?: (null to null)

        return ListeningEvalEntity(
            roomId = roomId,
            total = total,

            metric1Name = m0.name,
            metric1Score = m0.score,
            metric1Detail = m0.detail,

            metric2Name = m1.name,
            metric2Score = m1.score,
            metric2Detail = m1.detail,

            metric3Name = m2.name,
            metric3Score = m2.score,
            metric3Detail = m2.detail,

            metric4Name = m3.name,
            metric4Score = m3.score,
            metric4Detail = m3.detail,

            notes = notes.joinToString("\n"),

            // 🔹 청취 위치 같이 저장
            listenerX = lx,
            listenerZ = lz,

            updatedAt = System.currentTimeMillis()
        )
    }


    private fun ListeningEvalEntity.toModel(): ListeningEval {
        val metrics = listOf(
            ListeningMetric(metric1Name, metric1Score, metric1Detail),
            ListeningMetric(metric2Name, metric2Score, metric2Detail),
            ListeningMetric(metric3Name, metric3Score, metric3Detail),
            ListeningMetric(metric4Name, metric4Score, metric4Detail),
        )

        val notesList =
            if (notes.isBlank()) emptyList()
            else notes.split("\n")

        val listenerVec =
            if (listenerX != null && listenerZ != null) Vec2(listenerX, listenerZ)
            else null

        return ListeningEval(
            total = total,
            metrics = metrics,
            notes = notesList,
            listener = listenerVec
        )
    }


}
