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

    fun pruneSpeakers(frameNs: Long, timeoutSec: Int = 3) {
        _speakers.removeAll { (frameNs - it.lastSeenNs) / 1e9 > timeoutSec }
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
}
