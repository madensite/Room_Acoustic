package com.example.roomacoustic.viewmodel

import android.app.Application
import android.graphics.RectF
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomacoustic.data.*
import com.example.roomacoustic.model.LayoutEval
import com.example.roomacoustic.model.Listener2D
import com.example.roomacoustic.model.Speaker3D
import com.example.roomacoustic.repo.RoomRepository
import com.example.roomacoustic.repo.AnalysisRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.example.roomacoustic.model.Vec3
import com.example.roomacoustic.model.Measure3DResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.example.roomacoustic.screens.components.RoomSize
import com.example.roomacoustic.model.Vec2
import kotlinx.coroutines.flow.update
import com.example.roomacoustic.model.ListeningEval
import com.example.roomacoustic.model.ListeningMetric


class RoomViewModel(app: Application) : AndroidViewModel(app) {

    // ───────── DB/Repo 초기화 ─────────
    private val db = AppDatabase.get(app)

    private val repo = RoomRepository(db.roomDao())
    private val analysisRepo = AnalysisRepository(
        db.recordingDao(),
        db.measureDao(),
        db.speakerDao(),
        db.listeningEvalDao()
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
        // ★ 수동 입력들도 모두 초기화
        _manualSpeakers.value = emptyMap()
        _manualRoomSize.value  = emptyMap()
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

    // 방별 수동 스피커 좌표 (로컬 좌표, m)
    private val _manualSpeakers = MutableStateFlow<Map<Int, List<Vec3>>>(emptyMap())
    val manualSpeakers: StateFlow<Map<Int, List<Vec3>>> = _manualSpeakers.asStateFlow()

    fun setManualSpeakers(roomId: Int, list: List<Vec3>?) {
        _manualSpeakers.value =
            if (list == null) _manualSpeakers.value - roomId
            else _manualSpeakers.value + (roomId to list)
    }

    fun manualSpeakersFor(roomId: Int): List<Vec3>? = _manualSpeakers.value[roomId]

    fun clearManualSpeakers(roomId: Int) {
        _manualSpeakers.value = _manualSpeakers.value - roomId
    }


    // ───── 수동 RoomSize (roomId -> RoomSize(m)) ─────
    private val _manualRoomSize = MutableStateFlow<Map<Int, RoomSize>>(emptyMap())
    val manualRoomSize: StateFlow<Map<Int, RoomSize>> = _manualRoomSize.asStateFlow()

    /** 방별 수동 RoomSize 설정/해제 (m 단위) */
    fun setManualRoomSize(roomId: Int, size: RoomSize?) {
        _manualRoomSize.value =
            if (size == null) _manualRoomSize.value - roomId
            else _manualRoomSize.value + (roomId to size)
    }

    /** 방별 수동 RoomSize 조회 */
    fun manualRoomSizeFor(roomId: Int): RoomSize? = _manualRoomSize.value[roomId]

    /** 방별 수동 RoomSize 제거 */
    fun clearManualRoomSize(roomId: Int) {
        _manualRoomSize.value = _manualRoomSize.value - roomId
    }

    /** 방별 수동 입력 전체 초기화(스피커 + RoomSize 동시 제거) */
    fun clearManualFor(roomId: Int) {
        _manualSpeakers.value = _manualSpeakers.value - roomId
        _manualRoomSize.value = _manualRoomSize.value - roomId
    }

    // 방별 청취 위치 (x,z)
    private val _manualListener = MutableStateFlow<Map<Int, Vec2>>(emptyMap())
    val manualListener: StateFlow<Map<Int, Vec2>> = _manualListener

    fun setManualListener(roomId: Int, p: Vec2?) {
        _manualListener.update { old ->
            val next = old.toMutableMap()
            if (p == null) next.remove(roomId) else next[roomId] = p
            next
        }
    }

    // ───── 방별 청취 위치 평가 결과 (ListeningEval) ─────
    private val _listeningEval = MutableStateFlow<Map<Int, ListeningEval>>(emptyMap())
    val listeningEval: StateFlow<Map<Int, ListeningEval>> = _listeningEval.asStateFlow()

    fun setListeningEval(roomId: Int, eval: ListeningEval?) {
        _listeningEval.update { old ->
            val next = old.toMutableMap()
            if (eval == null) next.remove(roomId) else next[roomId] = eval
            next
        }
        // 2) DB에도 반영
        viewModelScope.launch {
            if (eval == null) {
                analysisRepo.deleteListeningEval(roomId)
            } else {
                analysisRepo.saveListeningEval(roomId, eval)
            }
        }
    }

    fun listeningEvalFor(roomId: Int): ListeningEval? =
        _listeningEval.value[roomId]

    // ① 방별 청취자 위치
    private val _listener2D = MutableStateFlow<Map<Int, Listener2D>>(emptyMap())
    val listener2D: StateFlow<Map<Int, Listener2D>> = _listener2D

    fun setListener2D(roomId: Int, p: Listener2D?) {
        _listener2D.update { m ->
            if (p == null) m - roomId else m + (roomId to p)
        }
    }

    // room: RoomSize (m), speakersLocal: List<Vec3> (m, y는 무시), listener: Listener2D (m)
    fun evaluateLayout2ch(
        room: com.example.roomacoustic.screens.components.RoomSize,
        speakersLocal: List<Vec3>,
        listener: Listener2D
    ): LayoutEval {

        if (speakersLocal.isEmpty()) {
            return LayoutEval(null, null, null, null, null, 0f, listOf("스피커가 없습니다. 최소 1개를 지정하세요."))
        }

        // 상면 투영
        val pts = speakersLocal.map { it.copy(y = 0f) }
        val L: Vec3? = pts.getOrNull(0)
        val R: Vec3? = pts.getOrNull(1)

        // 거리 계산
        fun distXZ(a: Vec3, x: Float, z: Float): Float {
            val dx = a.x - x; val dz = a.z - z
            return kotlin.math.sqrt(dx*dx + dz*dz)
        }

        val lDist = L?.let { distXZ(it, listener.x, listener.z) }
        val rDist = R?.let { distXZ(it, listener.x, listener.z) }
        val avgDist = listOfNotNull(lDist, rDist).takeIf { it.isNotEmpty() }?.average()?.toFloat()

        // 좌우 거리 차이
        val distanceDelta = if (lDist != null && rDist != null) kotlin.math.abs(lDist - rDist) else null

        // 60° 등가 삼각형 기준 간단 점수 (너무 빡세지 않게 가중치)
        // 목표: 좌우거리 유사, 벽에서 너무 가깝지 않음, 등가삼각형(스피커 간 거리와 청취 거리 비율 ~1:1~1:1.2)
        val notes = mutableListOf<String>()
        var score = 100f

        // 벽에서 최소 여유 20cm 권장
        fun marginOK(x: Float, z: Float): Boolean =
            x >= 0.20f && z >= 0.20f && (room.w - x) >= 0.20f && (room.d - z) >= 0.20f

        if (!marginOK(listener.x, listener.z)) {
            notes += "청취 위치를 벽에서 20cm 이상 띄우면 반사 영향이 줄어듭니다."
            score -= 10
        }

        if (speakersLocal.size >= 2 && L != null && R != null) {
            val speakerGap = kotlin.math.abs(L.x - R.x).coerceAtLeast(0.001f)
            if (avgDist != null) {
                val ratio = avgDist / speakerGap
                // 1.0 ~ 1.2 대역을 sweet로 간주
                val sweet = when {
                    ratio < 0.8f -> { notes += "청취자가 스피커에 너무 가깝습니다. 약간 뒤로 이동해 보세요."; 15 }
                    ratio > 1.3f -> { notes += "청취자가 너무 뒤에 있습니다. 조금 앞으로 이동해 보세요."; 15 }
                    else -> 0
                }
                score -= sweet
            }
            if (distanceDelta != null) {
                when {
                    distanceDelta > 0.40f -> { notes += "좌/우 거리 차이가 큽니다(>40cm). 중심선에 가깝게 이동하세요."; score -= 25 }
                    distanceDelta > 0.20f -> { notes += "좌/우 거리 차이가 다소 큽니다(>20cm). 약간 중심으로 이동하세요."; score -= 10 }
                }
            }

            // 간단 Toe-in 권장치: 스피커→청취자 벡터를 기준으로 yaw 추정
            fun toeInDeg(spk: Vec3, lis: Listener2D): Float {
                val vx = lis.x - spk.x
                val vz = lis.z - spk.z
                val ang = Math.toDegrees(kotlin.math.atan2(vx.toDouble(), vz.toDouble())).toFloat()
                // 0°가 전면(+Z) 바라봄이라고 보면 좌우 각각 ± 로 나옴 → 절대각으로 5~15° 추천
                return kotlin.math.abs(ang)
            }
            val toeL = L?.let { toeInDeg(it, listener) }
            val toeR = R?.let { toeInDeg(it, listener) }
            val toeAvg = listOfNotNull(toeL, toeR).takeIf { it.isNotEmpty() }?.average()?.toFloat()

            toeAvg?.let { t ->
                if (t < 3f) notes += "스피커를 청취자 쪽으로 약간 Toe-in(5~15°) 해보세요."
                else if (t > 25f) notes += "Toe-in 각도가 크면 스테이징이 좁아질 수 있어요. 5~15° 권장."
            }

            return LayoutEval(
                avgDist = avgDist,
                lDist = lDist,
                rDist = rDist,
                distanceDelta = distanceDelta,
                toeInDeg = toeAvg,
                sweetSpotScore = score.coerceIn(0f, 100f),
                notes = notes
            )
        }

        // 모노/1ch 등: 기본 메시지
        return LayoutEval(
            avgDist = avgDist,
            lDist = lDist,
            rDist = rDist,
            distanceDelta = distanceDelta,
            toeInDeg = null,
            sweetSpotScore = score.coerceIn(0f, 100f),
            notes = notes.ifEmpty { listOf("스피커가 2개가 아니라면 좌우 대칭 기준은 생략됩니다.") }
        )
    }





}
