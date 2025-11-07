package com.example.roomacoustic.screens.measure

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.roomacoustic.model.Vec2
import com.example.roomacoustic.model.Vec3
import com.example.roomacoustic.navigation.Screen
import com.example.roomacoustic.screens.components.RoomSize
import com.example.roomacoustic.viewmodel.RoomViewModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.foundation.lazy.LazyColumn


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomAnalysisScreen(
    nav: NavController,
    vm: RoomViewModel
) {
    val roomId = vm.currentRoomId.collectAsState().value
    if (roomId == null) {
        Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        return
    }

    val labeled = vm.labeledMeasures.collectAsState().value
    val manualSizeMap = vm.manualRoomSize.collectAsState().value
    val manualSpkMap  = vm.manualSpeakers.collectAsState().value
    val manualListenerMap = vm.manualListener.collectAsState().value

    val manualSize = manualSizeMap[roomId]
    val autoRoomSize = remember(labeled) { inferRoomSizeFromLabels(labeled) }
    val roomSize = manualSize ?: autoRoomSize

    if (roomSize == null) {
        MissingRoomSizePanel(nav)
        return
    }

    val manualSpks = manualSpkMap[roomId]
    val speakersLocal: List<Vec3> = manualSpks ?: emptyList()

    var listener by rememberSaveable(roomId, stateSaver = Vec2Saver) {
        mutableStateOf(manualListenerMap[roomId] ?: Vec2(roomSize.w * 0.5f, roomSize.d * 0.5f))
    }

    var xToLeftCm by rememberSaveable { mutableStateOf((listener.x * 100).roundToInt().toString()) }
    var zToFrontCm by rememberSaveable { mutableStateOf((listener.z * 100).roundToInt().toString()) }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }

    // 평가 실행 (캔버스 드래그/입력 변화에 반응)
    val eval = remember(roomSize, speakersLocal, listener) {
        evaluateListeningSetup(roomSize, speakersLocal, listener)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("청취 위치 지정 (Top-Down)") }) },
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                        ),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(onClick = {
                        vm.currentRoomId.value?.let { id -> vm.setManualListener(id, null) }
                    }) { Text("뒤로") }

                    Button(onClick = {
                        if (listener.x !in 0f..roomSize.w || listener.z !in 0f..roomSize.d) {
                            inputError = "청취 위치가 방 경계를 벗어났습니다."
                            return@Button
                        }
                        vm.setManualListener(roomId, listener)
                        nav.navigate(Screen.TestGuide.route)
                    }) { Text("다음") }
                }
            }
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)                 // Scaffold가 내려준 안전영역
                .consumeWindowInsets(pad)     // 🔸 중복 인셋 제거 (여백 사라짐)
                .fillMaxSize()
        ) {
            // ───────── 상단: Top-Down 캔버스 (항상 보이게 고정) ─────────
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp)   // 살짝 키움
                    .weight(1.2f)             // 🔸 상단 캔버스 비중
                    .padding(16.dp)
            ) {
                TopDownRoomCanvas(
                    room = roomSize,
                    speakersXZ = speakersLocal.map { it.x to it.z },
                    listener = listener,
                    onListenerChange = { p ->
                        listener = p.coerceInRoom(roomSize)
                        xToLeftCm = (listener.x * 100).roundToInt().toString()
                        zToFrontCm = (listener.z * 100).roundToInt().toString()
                    }
                )
            }

            // ───────── 하단: 평가/입력 섹션 (세로 스크롤) ─────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // 하단 영역도 충분히 확보
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(   // 🔸 바텀 여백 제거
                        top = 0.dp, bottom = 0.dp, start = 0.dp, end = 0.dp
                    )
                ) {
                    item {
                        // 방/스피커 요약
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("방: W ${"%.2f".format(roomSize.w)} · D ${"%.2f".format(roomSize.d)} · H ${"%.2f".format(roomSize.h)} (m)")
                            Text("스피커: ${speakersLocal.size}개")
                        }
                    }

                    item {
                        // 수동(cm) 입력
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            NumberField(
                                value = xToLeftCm,
                                onValueChange = { s ->
                                    if (s.isEmpty() || s.all { it.isDigit() }) {
                                        xToLeftCm = s
                                        s.toIntOrNull()?.let { v ->
                                            listener = listener.copy(x = (v / 100f)).coerceInRoom(roomSize)
                                        }
                                    }
                                },
                                label = "좌측 벽까지(cm)",
                                supporting = "0 ~ ${(roomSize.w * 100).roundToInt()}"
                            )
                            NumberField(
                                value = zToFrontCm,
                                onValueChange = { s ->
                                    if (s.isEmpty() || s.all { it.isDigit() }) {
                                        zToFrontCm = s
                                        s.toIntOrNull()?.let { v ->
                                            listener = listener.copy(z = (v / 100f)).coerceInRoom(roomSize)
                                        }
                                    }
                                },
                                label = "전면 벽까지(cm)",
                                supporting = "0 ~ ${(roomSize.d * 100).roundToInt()}"
                            )
                            Spacer(Modifier.weight(1f))
                            FilledTonalButton(onClick = {
                                listener = Vec2(roomSize.w * 0.5f, roomSize.d * 0.5f)
                                xToLeftCm = (listener.x * 100).roundToInt().toString()
                                zToFrontCm = (listener.z * 100).roundToInt().toString()
                            }) { Text("중앙") }
                        }
                        inputError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }

                    item {
                        Text(
                            "청취 위치 (W,D) = (${fmt(listener.x)} m, ${fmt(listener.z)} m) · " +
                                    "방(W×D×H) = ${fmt(roomSize.w)} × ${fmt(roomSize.d)} × ${fmt(roomSize.h)} m"
                        )
                    }

                    item {
                        Surface(
                            tonalElevation = 1.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 220.dp)   // ← 평가 영역 최소 높이
                        ) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("평가 점수: ${eval.total}/100", style = MaterialTheme.typography.titleMedium)

                                eval.metrics.forEach { m ->
                                    AssistChip(onClick = {}, label = { Text("${m.name} · ${m.score}점") })
                                    LinearProgressIndicator(
                                        progress = m.score / 100f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(m.detail, color = Color(0xFFB0BEC5))
                                    Spacer(Modifier.height(4.dp))
                                }

                                if (eval.notes.isNotEmpty()) {
                                    Divider()
                                    Text("권고 사항", style = MaterialTheme.typography.titleSmall)
                                    eval.notes.forEach { Text("• $it") }
                                }

                                eval.suggestedListener?.let { sug ->
                                    Divider()
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("권장 청취 위치 → (W,D)=(${fmt(sug.x)} , ${fmt(sug.z)}) m")
                                        TextButton(onClick = {
                                            val clamped = Vec2(
                                                x = sug.x.coerceIn(0f, roomSize.w),
                                                z = sug.z.coerceIn(0f, roomSize.d)
                                            )
                                            listener = clamped
                                            xToLeftCm = (listener.x * 100).roundToInt().toString()
                                            zToFrontCm = (listener.z * 100).roundToInt().toString()
                                        }) { Text("청취 위치 적용") }
                                    }
                                }
                            }
                        }
                    }

                    if (eval.moveSuggestions.isNotEmpty()) {
                        item {
                            Surface(
                                tonalElevation = 1.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 160.dp)
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("스피커 이동 권고 (참고용)", style = MaterialTheme.typography.titleSmall)
                                    eval.moveSuggestions.forEach { s ->
                                        val from = s.from
                                        val to   = s.to
                                        val dxCm = ((to.x - from.x) * 100).roundToInt()
                                        val dzCm = ((to.z - from.z) * 100).roundToInt()
                                        Text(
                                            "• ${s.label}: (${fmt(from.x)}, ${fmt(from.z)}) → (${fmt(to.x)}, ${fmt(to.z)}) m  " +
                                                    "Δx=${dxCm}cm, Δz=${dzCm}cm"
                                        )
                                    }
                                    Text(
                                        "※ 실제 이동은 사용자가 스피커를 옮겨 반영하세요.",
                                        color = Color(0xFF90A4AE)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


/* ────────────────────────────── */
/* Top-Down 캔버스                */
/* ────────────────────────────── */

@Composable
private fun TopDownRoomCanvas(
    room: RoomSize,
    speakersXZ: List<Pair<Float, Float>>,
    listener: Vec2,
    onListenerChange: (Vec2) -> Unit
) {
    // 캔버스 안에서 방 크기 비율대로 스케일/센터링
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val pad = 24f

        // 방(m)을 픽셀로: 여백을 뺀 뒤, 짧은 변 기준으로 맞춤
        val scale = min((wPx - pad * 2) / room.w, (hPx - pad * 2) / room.d)
        val offsetX = (wPx - room.w * scale) / 2f
        val offsetY = (hPx - room.d * scale) / 2f

        fun worldToCanvasX(x: Float) = offsetX + x * scale
        fun worldToCanvasY(z: Float) = offsetY + z * scale
        fun canvasToWorldX(px: Float) = ((px - offsetX) / scale).coerceIn(0f, room.w)
        fun canvasToWorldZ(py: Float) = ((py - offsetY) / scale).coerceIn(0f, room.d)

        val listenerRadiusPx = 10f

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            onListenerChange(Vec2(canvasToWorldX(pos.x), canvasToWorldZ(pos.y)))
                        },
                        onDrag = { change, _ ->
                            onListenerChange(
                                Vec2(
                                    canvasToWorldX(change.position.x),
                                    canvasToWorldZ(change.position.y)
                                )
                            )
                        }
                    )
                }
        ) {
            // 배경
            drawRect(color = Color(0xFF101214))

            // 방 외곽
            drawRoundRect(
                color = Color(0xFF2A2F35),
                topLeft = Offset(worldToCanvasX(0f), worldToCanvasY(0f)),
                size = androidx.compose.ui.geometry.Size(room.w * scale, room.d * scale),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f)
            )

            // 스피커 점
            speakersXZ.forEach { (sx, sz) ->
                drawCircle(
                    color = Color(0xFFFFA726),
                    radius = 6f,
                    center = Offset(worldToCanvasX(sx), worldToCanvasY(sz))
                )
            }

            // 청취자 (드래그 가능한 원)
            drawCircle(
                color = Color(0xFF64B5F6),
                radius = listenerRadiusPx,
                center = Offset(worldToCanvasX(listener.x), worldToCanvasY(listener.z))
            )
        }
    }
}

/* ────────────────────────────── */
/* 유틸/입력/평가(라이트 버전)    */
/* ────────────────────────────── */

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supporting: String,
    ime: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        supportingText = { Text(supporting) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ime), // ← 여기
        modifier = Modifier.widthIn(min = 140.dp)
    )
}

private fun Vec2.coerceInRoom(room: RoomSize): Vec2 =
    Vec2(x.coerceIn(0f, room.w), z.coerceIn(0f, room.d))

// RenderScreen에서 쓰던 레이블→치수 추론 로직 간단 이식
private fun normalizeLabel(s: String): String =
    s.lowercase().replace("\\s+".toRegex(), "")
        .replace("[()\\[\\]{}:：=~_\\-]".toRegex(), "")
private val W_KEYS = setOf("w", "width", "가로", "폭", "넓이")
private val D_KEYS = setOf("d", "depth", "세로", "길이", "방길이", "방깊이", "전장", "장변")
private val H_KEYS = setOf("h", "height", "높이", "천장", "층고")

private fun inferRoomSizeFromLabels(labeled: List<RoomViewModel.LabeledMeasure>): RoomSize? {
    if (labeled.isEmpty()) return null
    fun pick(keys: Set<String>): Float? =
        labeled.firstOrNull { m ->
            val norm = normalizeLabel(m.label)
            keys.any { k -> norm.contains(k) || k.contains(norm) }
        }?.meters
    val w = pick(W_KEYS); val d = pick(D_KEYS); val h = pick(H_KEYS)
    return if (w != null && d != null && h != null) RoomSize(w, d, h) else null
}

/* ──────────────────────────────────────────── */
/* 평가 모델                                   */
/* ──────────────────────────────────────────── */

private data class EvalMetric(
    val name: String,
    val score: Int,       // 0~100 서브점수
    val weight: Int,      // 가중치 (총점 계산에 반영)
    val detail: String
)

private data class MoveSuggestion(
    val index: Int,       // speakersLocal의 원본 인덱스
    val label: String,    // "L" / "R" / "S1" 등
    val from: Vec3,
    val to: Vec3          // 이동 권고 좌표 (x,z만 바뀌고 y는 그대로)
) {
    val dx get() = to.x - from.x
    val dz get() = to.z - from.z
}

private data class EvaluationResult(
    val total: Int,                   // 가중합 총점 0~100
    val metrics: List<EvalMetric>,    // 세부 항목
    val notes: List<String>,          // 경고/권고 메시지
    val suggestedListener: Vec2?,     // 청취 위치 권고가 있으면 제시
    val moveSuggestions: List<MoveSuggestion> // 스피커 이동 권고(개별 Δ)
)

/* ──────────────────────────────────────────── */
/* 규칙: 스테레오 2ch 중심 (n개도 동작은 함)     */
/* ──────────────────────────────────────────── */

// ====== [REPLACE ALL] evaluateListeningSetup ======
private fun evaluateListeningSetup(
    room: RoomSize,
    speakers: List<Vec3>,
    listener: Vec2
): EvaluationResult {
    val notes = mutableListOf<String>()
    val metrics = mutableListOf<EvalMetric>()
    val moves   = mutableListOf<MoveSuggestion>()
    var suggestListener: Vec2? = null

    // 안전 여유(벽 이격 권고치)
    val sideMin = 0.50f       // 좌/우 벽에서 ≥ 0.5m 권장
    val backMin = 1.00f       // 뒤 벽에서 ≥ 1.0m 권장
    val backMax = 2.20f       // 뒤 벽에서 ≤ 2.2m 권장

    // (1) 청취 깊이: 38% 고정 → 30~45% 밴드로 완화 + 선형 스무딩
    val bandMin = room.d * 0.30f
    val bandMax = room.d * 0.45f
    val taperD  = room.d * 0.15f // 밴드 밖에서 여기까지는 60점까지 선형 감점, 그 이상 floor

    val m1Score = smoothToBand(
        value = listener.z,
        bandMin = bandMin,
        bandMax = bandMax,
        taper = taperD,
        floor = 40
    )
    if (m1Score < 100) {
        notes += "청취 깊이는 방 깊이의 30~45% 권장(≈ ${fmt(bandMin)}~${fmt(bandMax)} m)."
        // 권장 청취 깊이: 밴드 중앙(= 37.5%)
        suggestListener = (suggestListener ?: listener).copy(z = (bandMin + bandMax) * 0.5f)
    }
    metrics += EvalMetric(
        name = "청취 깊이(30~45%)",
        score = m1Score,
        weight = 3,
        detail = "현재 D=${fmt(listener.z)}m / 권장 ${fmt(bandMin)}~${fmt(bandMax)}m"
    )

    // 스피커가 없으면 여기서 총점 산출하고 종료
    if (speakers.isEmpty()) {
        return packEval(metrics, notes, suggestListener, moves)
    }

    // (2) 스테레오(2개) 기준이 가장 효과적 — n개도 동작은 함
    if (speakers.size >= 2) {
        val indexed = speakers.mapIndexed { i, v -> i to v }.sortedBy { it.second.x }
        val (lIdx, L) = indexed.first()
        val (rIdx, R) = indexed.last()

        // 중앙선(스피커 중앙 기준). 방 중앙을 쓰고 싶으면 room.w * 0.5f 로 교체
        val centerX = ((L.x + R.x) * 0.5f)

        // 🔸 권장 청취 x를 중앙선으로 고정 (z는 앞에서 38%로 제안해 둔 값 유지)
        suggestListener = (suggestListener ?: listener.copy()).copy(x = centerX)

        // 2-1) 좌/우 중앙선 정렬 (deltaCenter가 0에 가까울수록 좋음)
        val midX = (L.x + R.x) * 0.5f
        val deltaCenter = kotlin.math.abs(midX - centerX)
        val m2Score = smoothToTarget(
            value = deltaCenter,
            target = 0f,
            soft   = room.w * 0.02f,  // 2% 이내면 100
            hard   = room.w * 0.10f,  // 10%에서 60
            floor  = 40
        )
        if (m2Score < 100) {
            notes += "두 스피커의 중앙이 방 중앙선(W/2)에 가깝도록 좌/우를 맞추세요."
            val shift = centerX - midX
            val lTo = L.copy(x = (L.x + shift).coerceIn(sideMin, room.w - sideMin))
            val rTo = R.copy(x = (R.x + shift).coerceIn(sideMin, room.w - sideMin))
            moves += MoveSuggestion(lIdx, "L", L, lTo)
            moves += MoveSuggestion(rIdx, "R", R, rTo)
        }
        metrics += EvalMetric(
            name = "좌/우 중앙 정렬",
            score = m2Score,
            weight = 2,
            detail = "midX=${fmt(midX)}m, center=${fmt(centerX)}m, Δ=${fmt(deltaCenter)}m"
        )

        // 2-2) 전후(z) 정렬 (dz가 0에 가까울수록 좋음)
        val dz = kotlin.math.abs(L.z - R.z)
        val m3Score = smoothToTarget(
            value = dz,
            target = 0f,
            soft   = room.d * 0.02f,  // 2% 이내 100
            hard   = room.d * 0.10f,  // 10%에서 60
            floor  = 50
        )
        if (m3Score < 100) {
            notes += "두 스피커의 전후(z)를 맞추면 스테레오 이미징이 좋아집니다."
            val avgZ = ((L.z + R.z) * 0.5f).coerceIn(backMin, backMax.coerceAtMost(room.d - 0.2f))
            val lBase = moves.find { it.index == lIdx }?.to ?: L
            val rBase = moves.find { it.index == rIdx }?.to ?: R
            moves += MoveSuggestion(lIdx, "L", lBase, lBase.copy(z = avgZ))
            moves += MoveSuggestion(rIdx, "R", rBase, rBase.copy(z = avgZ))
        }
        metrics += EvalMetric(
            name = "전후(z) 일치",
            score = m3Score,
            weight = 2,
            detail = "|zL - zR| = ${fmt(dz)}m"
        )

        // (3) 삼각형 균형: 등변성 + 길이 밸런스 모두 스무딩
        val dL  = hypot2D(listener.x - L.x, listener.z - L.z)
        val dR  = hypot2D(listener.x - R.x, listener.z - R.z)
        val dLR = hypot2D(R.x - L.x, R.z - L.z)

        val avgLR = ((dL + dR) * 0.5f).coerceAtLeast(1e-4f)

        // 등변성: |dL - dR| / 평균거리  → 0이 좋음
        val isoRatio = kotlin.math.abs(dL - dR) / avgLR
        val isoScore = smoothToTarget(
            value = isoRatio,
            target = 0f,
            soft = 0.05f,   // 5% 이내 100
            hard = 0.25f,   // 25%에서 60
            floor = 40
        )

        // 길이 밸런스: dLR / 평균(dL,dR) → 1이 좋음(정삼각형)
        val ratioEq = dLR / avgLR
        val eqScore = smoothRatioToOne(
            r = ratioEq,
            tol = 0.05f,    // ±5% 이내 100
            maxTol = 0.25f, // ±25%에서 60
            floor = 40
        )

        val m4Score = ((isoScore + eqScore) / 2f).roundToInt().coerceIn(0, 100)
        if (isoScore < 100) notes += "좌/우 스피커-청취자 거리 편차를 줄이면 등변에 가까워집니다."
        if (eqScore  < 100) notes += "스피커 간 거리와 청취자 거리의 밸런스를 더 맞춰 보세요."
        metrics += EvalMetric(
            name = "삼각형 균형",
            score = m4Score,
            weight = 3,
            detail = "dL=${fmt(dL)}m, dR=${fmt(dR)}m, LR=${fmt(dLR)}m"
        )

        // (4) 벽 이격 안전성 힌트
        fun wallHints(p: Vec3): List<String> {
            val res = mutableListOf<String>()
            val left = p.x; val right = room.w - p.x
            val back = room.d - p.z; val front = p.z
            if (min(left, right) < sideMin) res += "측면 벽과 ≥ ${fmt(sideMin)}m 이격 권장."
            if (back < backMin)           res += "뒤 벽과 ≥ ${fmt(backMin)}m 이격 권장."
            if (back > backMax)           res += "뒤 벽과 ≤ ${fmt(backMax)}m 권장."
            if (front < 0.20f)            res += "전면(청취자쪽) 여유가 너무 적습니다(≥0.2m 권장)."
            return res
        }
        val lFinal = moves.find { it.index == lIdx }?.to ?: L
        val rFinal = moves.find { it.index == rIdx }?.to ?: R
        val hL = wallHints(lFinal)
        val hR = wallHints(rFinal)
        if (hL.isNotEmpty()) notes += "Left: " + hL.joinToString(" ")
        if (hR.isNotEmpty()) notes += "Right: " + hR.joinToString(" ")
    } else {
        // n != 2 (모노/멀티) — 라이트 권고
        notes += "스피커가 2개가 아니어서(현재 ${speakers.size}개) 스테레오 대칭 규칙 일부만 적용했습니다."
        speakers.forEachIndexed { i, s ->
            val back = room.d - s.z
            if (back !in backMin..backMax) {
                notes += "S${i+1}: 뒤 벽 이격을 ${fmt(backMin)}~${fmt(backMax)}m 범위로 맞춰 보세요."
            }
        }
    }

    return packEval(metrics, notes, suggestListener, moves)
}


private fun packEval(
    metrics: List<EvalMetric>,
    notes: List<String>,
    suggestedListener: Vec2?,
    moves: List<MoveSuggestion>
): EvaluationResult {
    val totalWeight = metrics.sumOf { it.weight }.coerceAtLeast(1)
    val wSum = metrics.sumOf { it.score * it.weight }
    val total = (wSum / totalWeight.toFloat()).roundToInt().coerceIn(0, 100)
    return EvaluationResult(
        total = total,
        metrics = metrics,
        notes = notes.distinct(),
        suggestedListener = suggestedListener,
        moveSuggestions = mergeMoves(moves) // 같은 스피커에 여러 규칙이 제안하면 최종 to로 합쳐서 정리
    )
}

/* 같은 index의 MoveSuggestion이 여러 번 생기면 마지막 to 기준으로 합치기 */
private fun mergeMoves(moves: List<MoveSuggestion>): List<MoveSuggestion> {
    if (moves.isEmpty()) return moves
    val byIdx = moves.groupBy { it.index }
    return byIdx.map { (_, list) ->
        val first = list.first()
        val last  = list.last()
        MoveSuggestion(
            index = last.index,
            label = first.label,
            from  = first.from,
            to    = last.to
        )
    }.sortedBy { it.label }
}

/* 유틸 */
private fun hypot2D(x: Float, z: Float) = kotlin.math.hypot(x.toDouble(), z.toDouble()).toFloat()
private fun fmt(v: Float) = String.format("%.2f", v)

// ====== [ADD] 점수 스무딩 유틸 3종 ======

// |value - target| 이 커질수록 선형 감점. soft 이내 100, hard에서 60, 그 이상 floor.
private fun smoothToTarget(
    value: Float,
    target: Float,
    soft: Float,
    hard: Float,
    floor: Int = 40
): Int {
    val d = kotlin.math.abs(value - target)
    return when {
        d <= soft -> 100
        d >= hard -> floor
        else -> {
            val t = (d - soft) / (hard - soft)  // 0..1
            val score = 100f * (1f - t) + 60f * t
            score.roundToInt().coerceIn(floor, 100)
        }
    }
}

// [bandMin, bandMax] 안이면 100. 밖이면 밴드 경계까지 거리로 선형 감점(60까지), 더 멀면 floor.
private fun smoothToBand(
    value: Float,
    bandMin: Float,
    bandMax: Float,
    taper: Float,
    floor: Int = 40
): Int {
    if (value in bandMin..bandMax) return 100
    val dist = if (value < bandMin) (bandMin - value) else (value - bandMax)
    return when {
        dist >= taper -> floor
        else -> {
            val t = dist / taper // 0..1
            val score = 100f * (1f - t) + 60f * t
            score.roundToInt().coerceIn(floor, 100)
        }
    }
}

// 비율 r이 1(이상적)에 가까울수록 점수↑. |r-1| ≤ tol → 100, maxTol에서 60, 그 이상 floor.
private fun smoothRatioToOne(
    r: Float,
    tol: Float,
    maxTol: Float,
    floor: Int = 40
): Int {
    val d = kotlin.math.abs(r - 1f)
    return when {
        d <= tol -> 100
        d >= maxTol -> floor
        else -> {
            val t = (d - tol) / (maxTol - tol) // 0..1
            val score = 100f * (1f - t) + 60f * t
            score.roundToInt().coerceIn(floor, 100)
        }
    }
}



/* ────────────────────────────── */
/* 스피커 위치 보정(라이트 규칙)  */
/* ────────────────────────────── */

private data class SpeakerSuggestion(
    val adjustedXZ: List<Pair<Float, Float>>,
    val notes: List<String>
) {
    fun summary(): String = notes.joinToString(" · ")
}

/**
 * 규칙(스테레오 2개 기준, N개 일반화 최소):
 * - 좌우 대칭: listener.x 를 기준으로 대칭
 * - 평균 반경 유지: 현재 리스너-스피커 거리의 평균 r 를 유지
 * - 벽 최소 이격: minWall = 0.30m 확보
 * - 방 경계 내부로 clamp
 */
private fun suggestPositions(
    listener: Vec2,
    speakers: List<Vec3>,
    room: RoomSize
): SpeakerSuggestion {
    if (speakers.isEmpty()) {
        return SpeakerSuggestion(emptyList(), listOf("스피커 없음"))
    }

    val minWall = 0.30f
    val notes = mutableListOf<String>()

    // 현재 XZ만 사용
    val xz = speakers.map { it.x to it.z }

    // 리스너와의 거리 평균
    val dists = xz.map { (sx, sz) ->
        kotlin.math.hypot((sx - listener.x).toDouble(), (sz - listener.z).toDouble()).toFloat()
    }
    val r = (dists.average()).toFloat().coerceIn(0.40f, max(room.w, room.d)) // 지나치게 작지 않게

    val adjusted: List<Pair<Float, Float>> = when (xz.size) {
        // 스테레오(2개): 좌/우를 listener.x 기준 대칭으로 배치,
        // z는 현재 평균 z 쪽(전면 벽쪽으로 0.3~0.8 사이)으로 유지/조정
        2 -> {
            val meanZ = xz.map { it.second }.average().toFloat()
            val frontBias = meanZ.coerceIn(0.30f, room.d - 0.80f)

            // 리스너 중심 원 위에 놓되 x 대칭, z는 frontBias
            val dx = (r * 0.85f) // 살짝 좁혀서 청취자 정면에 삼각형 형성
            var left = Pair(listener.x - dx, frontBias)
            var right = Pair(listener.x + dx, frontBias)

            // 경계/이격 clamp
            fun clamp(p: Pair<Float, Float>): Pair<Float, Float> {
                val x = p.first.coerceIn(minWall, room.w - minWall)
                val z = p.second.coerceIn(minWall, room.d - minWall)
                return x to z
            }
            left = clamp(left); right = clamp(right)

            // 좌우가 뒤바뀌지 않도록 보정(좌는 listener보다 작아야)
            if (left.first > right.first) {
                val tmp = left; left = right; right = tmp
            }

            notes += "스테레오: 좌/우 대칭 · 평균반경 r=${"%.2f".format(r)}m 유지"
            notes += "전면치우침 z≈${"%.2f".format(frontBias)}m · 벽 이격 ${minWall}m"
            listOf(left, right)
        }

        else -> {
            // N개 일반화(간단): 현재 각 점의 극좌표(리스너 기준 각도/반경)를 구해
            // 반경은 평균 r, 각도는 현재 각도를 유지. 방 경계/이격만 clamp.
            val centered = xz.map { (sx, sz) -> (sx - listener.x) to (sz - listener.z) }
            val adjustedCentered = centered.map { (dx, dz) ->
                val angle = kotlin.math.atan2(dz.toDouble(), dx.toDouble()).toFloat()
                val nx = listener.x + r * kotlin.math.cos(angle)
                val nz = listener.z + r * kotlin.math.sin(angle)
                nx to nz
            }
            val clamped = adjustedCentered.map { (x, z) ->
                x.coerceIn(minWall, room.w - minWall) to z.coerceIn(minWall, room.d - minWall)
            }
            notes += "N=${xz.size}개: 리스너 중심 평균반경 r=${"%.2f".format(r)}m 유지"
            notes += "방 경계/벽 최소 이격 ${minWall}m"
            clamped
        }
    }

    return SpeakerSuggestion(adjusted, notes)
}

/** 보정된 XZ를 기존 스피커의 Y 그대로 결합해 Vec3로 변환 */
private fun SpeakerSuggestion.toVec3WithOriginalY(orig: List<Vec3>): List<Vec3> {
    return adjustedXZ.mapIndexed { i, (x, z) ->
        val y = orig.getOrNull(i)?.y ?: 1.2f // 없으면 1.2m(귀 높이) 기본
        Vec3(x, y, z)
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MissingRoomSizePanel(nav: NavController) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("청취 위치 선택") })
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "방 크기(W/D/H) 정보가 없어 평면도를 표시할 수 없습니다.",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                "Render 화면에서 방 크기를 수동 입력하거나 카메라 측정을 진행해 주세요.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB0BEC5)
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { nav.popBackStack() }) { Text("뒤로 가기") }
                Button(onClick = { nav.navigate(Screen.Render.route) }) { Text("Render로 이동") }
            }
        }
    }
}

private val Vec2Saver: Saver<Vec2, Any> = listSaver(
    save = { value -> listOf(value.x, value.z) },              // List<Any?>
    restore = { list -> Vec2(list[0] as Float, list[1] as Float) }
)