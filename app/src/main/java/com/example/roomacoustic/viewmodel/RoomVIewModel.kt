package com.example.roomacoustic.viewmodel

import android.app.Application
import android.graphics.RectF
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomacoustic.data.*
import com.example.roomacoustic.model.Speaker3D
import com.example.roomacoustic.repo.RoomRepository
import com.example.roomacoustic.repo.AnalysisRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.roomacoustic.model.Vec3
import com.example.roomacoustic.model.Measure3DResult

class RoomViewModel(app: Application) : AndroidViewModel(app) {

    // ───────── DB/Repo 초기화 ─────────
    private val db = AppDatabase.get(app)

    private val repo = RoomRepository(db.roomDao())
    private val analysisRepo = AnalysisRepository(
        db.recordingDao(),
        db.measureDao(),
        db.speakerDao()
    )

    // ───────── 방 리스트 (기존) ─────────
    val rooms = repo.rooms.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val currentRoomId = MutableStateFlow<Int?>(null)
    fun select(roomId: Int) { currentRoomId.value = roomId }

    fun addRoom(title: String, onAdded: (Int) -> Unit) = viewModelScope.launch {
        onAdded(repo.add(title))
    }
    fun rename(id: Int, newTitle: String) = viewModelScope.launch { repo.rename(id, newTitle) }
    fun setMeasure(id: Int, flag: Boolean) = viewModelScope.launch { repo.setMeasure(id, flag) }
    fun setChat(id: Int, flag: Boolean)    = viewModelScope.launch { repo.setChat(id, flag) }
    fun delete(room: RoomEntity)           = viewModelScope.launch { repo.delete(room) }

    // ───────── MiDaS (기존) ─────────
    private val _measuredDimensions = MutableStateFlow<Triple<Float, Float, Float>?>(null)
    val measuredDimensions: StateFlow<Triple<Float, Float, Float>?> = _measuredDimensions.asStateFlow()
    fun setMeasuredRoomDimensions(w: Float, h: Float, d: Float) {
        _measuredDimensions.value = Triple(w, h, d)
    }

    // ───────── YOLO (기존) ─────────
    private val _inferenceTime = MutableStateFlow<Long?>(null)
    val inferenceTime: StateFlow<Long?> = _inferenceTime.asStateFlow()
    fun setInferenceTime(ms: Long) { _inferenceTime.value = ms }

    private val _speakerBoxes = MutableStateFlow<List<RectF>>(emptyList())
    val speakerBoxes: StateFlow<List<RectF>> = _speakerBoxes.asStateFlow()
    fun setSpeakerBoxes(boxes: List<RectF>) { _speakerBoxes.value = boxes }

    // ───────── 종합 측정 결과 (기존) ─────────
    data class MeasureResult(
        val width: Float?,
        val height: Float?,
        val depth: Float?,
        val speakerBoxes: List<RectF>
    )
    private val _roomResult = MutableStateFlow<MeasureResult?>(null)
    val roomResult: StateFlow<MeasureResult?> = _roomResult.asStateFlow()
    fun setMeasureResult(w: Float?, h: Float?, d: Float?, boxes: List<RectF>) {
        _roomResult.value = MeasureResult(w, h, d, boxes)
    }

    fun deleteAllRooms() = viewModelScope.launch {
        repo.deleteAll()
        currentRoomId.value = null
    }

    // ── 스피커 변경 버전 카운터 (Render 재구성 트리거)
    private val _speakersVersion = MutableStateFlow(0)
    val speakersVersion: StateFlow<Int> = _speakersVersion.asStateFlow()

    // ───────── 실시간 스피커(메모리) ─────────
    private val _speakers = mutableStateListOf<Speaker3D>()
    val speakers: List<Speaker3D> get() = _speakers

    fun upsertSpeaker(id: Int, pos: FloatArray, frameNs: Long) {
        _speakers.firstOrNull { it.id == id }?.apply {
            worldPos   = pos
            lastSeenNs = frameNs
        } ?: _speakers.add(Speaker3D(id, pos, frameNs))
        _speakersVersion.value = _speakersVersion.value + 1
    }

    // ───────── AR 6점 측정 결과 (기존) ─────────
    private val _measure3DResult = MutableStateFlow<Measure3DResult?>(null)
    val measure3DResult: StateFlow<Measure3DResult?> = _measure3DResult.asStateFlow()
    fun setMeasure3DResult(result: Measure3DResult) { _measure3DResult.value = result }
    fun clearMeasure3DResult() { _measure3DResult.value = null }

    // 라벨링된 길이 측정값 (기존)
    data class LabeledMeasure(val label: String, val meters: Float)
    private val _labeledMeasures = MutableStateFlow<List<LabeledMeasure>>(emptyList())
    val labeledMeasures = _labeledMeasures.asStateFlow()
    fun addLabeledMeasure(label: String, meters: Float) {
        _labeledMeasures.value = _labeledMeasures.value + LabeledMeasure(label, meters)
    }
    fun clearLabeledMeasures() { _labeledMeasures.value = emptyList() }

    // 수동 스피커 페어 (기존)
    private val _manualSpeakerPair = MutableStateFlow<Pair<Vec3, Vec3>?>(null)
    val manualSpeakerPair = _manualSpeakerPair.asStateFlow()
    fun setManualSpeakerPair(left: Vec3, right: Vec3) { _manualSpeakerPair.value = left to right }
    fun clearManualSpeakerPair() { _manualSpeakerPair.value = null }

    fun clearMeasureAndSpeakers() {
        _speakers.clear()
        _speakerBoxes.value = emptyList()
        _speakersVersion.value = _speakersVersion.value + 1
    }

    // ──────────────── ★★★ 여기부터 “로컬 저장(분석)” 추가 ★★★ ────────────────

    /** 최신 녹음/측정/스피커: currentRoomId에 따라 자동 전환 */
    val latestRecording: StateFlow<RecordingEntity?> =
        currentRoomId.flatMapLatest { id ->
            if (id == null) flowOf(null) else analysisRepo.latestRecording(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val latestMeasure: StateFlow<MeasureEntity?> =
        currentRoomId.flatMapLatest { id ->
            if (id == null) flowOf(null) else analysisRepo.latestMeasure(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val savedSpeakers: StateFlow<List<SpeakerEntity>> =
        currentRoomId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else analysisRepo.speakers(id)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 저장 함수 */
    fun saveRecordingForCurrentRoom(filePath: String, peak: Float, rms: Float, duration: Float) =
        viewModelScope.launch {
            val id = currentRoomId.value ?: return@launch
            analysisRepo.saveRecording(id, filePath, peak, rms, duration)
        }

    fun saveMeasureForCurrentRoom(width: Float, depth: Float, height: Float) =
        viewModelScope.launch {
            val id = currentRoomId.value ?: return@launch
            analysisRepo.saveMeasure(id, width, depth, height)
        }

    fun saveSpeakersSnapshot(worldPositions: List<FloatArray>) =
        viewModelScope.launch {
            val id = currentRoomId.value ?: return@launch
            analysisRepo.replaceSpeakers(id, worldPositions)
        }


    // ====== [ADD] 프레임간 어소시에이션 + EMA 스무딩 파라미터 ======
    private companion object {
        // 프레임 간 같은 스피커로 본다고 판단할 최대 3D 거리(게이트)
        const val GATE_3D_M = 0.25f        // 25cm 정도 (0.2~0.35 사이 튜닝 권장)
        // 지수평활(EMA) 계수: 새 관측값을 얼마나 빠르게 따라갈지
        const val EMA_ALPHA = 0.35f        // 0.2~0.5 구간에서 현장 튜닝
        // 트랙 타임아웃: N초 이상 보이지 않으면 제거
        const val TIMEOUT_SEC_DEFAULT = 3  // 기존 pruneSpeakers와 일치
    }

    private fun dist3(a: FloatArray, b: FloatArray): Float {
        val dx = a[0] - b[0]; val dy = a[1] - b[1]; val dz = a[2] - b[2]
        return kotlin.math.sqrt(dx*dx + dy*dy + dz*dz)
    }

    private fun emaUpdate(old: FloatArray, obs: FloatArray, alpha: Float = EMA_ALPHA): FloatArray {
        // old <- alpha*obs + (1-alpha)*old
        return floatArrayOf(
            old[0] + alpha * (obs[0] - old[0]),
            old[1] + alpha * (obs[1] - old[1]),
            old[2] + alpha * (obs[2] - old[2])
        )
    }

    /**
     * 월드 좌표 한 점을 현재 트랙과 매칭해 스무딩 반영/신규 생성한다.
     * - 가장 가까운 스피커가 GATE_3D_M 이내면 같은 트랙으로 간주하여 EMA 스무딩
     * - 아니면 새 트랙 생성 (id는 SimpleTracker로 발급)
     * - 변경 시 speakersVersion 증가
     */
    fun associateAndUpsert(world: FloatArray, nowNs: Long) {
        // 1) 최근접 후보 찾기
        var bestIdx = -1
        var bestDist = Float.POSITIVE_INFINITY
        for (i in _speakers.indices) {
            val d = dist3(_speakers[i].worldPos, world)
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }

        if (bestIdx >= 0 && bestDist <= GATE_3D_M) {
            // 2) 기존 트랙에 EMA 스무딩 반영
            val s = _speakers[bestIdx]
            s.worldPos = emaUpdate(s.worldPos, world)
            s.lastSeenNs = nowNs
        } else {
            // 3) 신규 트랙 생성 (SimpleTracker로 id 할당)
            val newId = com.example.roomacoustic.tracker.SimpleTracker.assignId(world)
            _speakers.add(Speaker3D(newId, world.copyOf(), nowNs))
        }
        _speakersVersion.value = _speakersVersion.value + 1
    }

    /** 오래 안 보인 스피커 제거 (기존 함수 시그니처 유지) */
    fun pruneSpeakers(frameNs: Long, timeoutSec: Int = TIMEOUT_SEC_DEFAULT) {
        _speakers.removeAll { (frameNs - it.lastSeenNs) / 1e9 > timeoutSec }
        _speakersVersion.value = _speakersVersion.value + 1
    }


}
