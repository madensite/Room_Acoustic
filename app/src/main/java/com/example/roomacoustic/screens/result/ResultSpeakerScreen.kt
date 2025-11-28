// path: app/src/main/java/com/example/roomacoustic/screens/result/ResultSpeakerScreen.kt
package com.example.roomacoustic.screens.result

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.roomacoustic.navigation.Screen
import com.example.roomacoustic.screens.components.RoomSize
import com.example.roomacoustic.viewmodel.RoomViewModel
import com.example.roomacoustic.model.Vec2
import com.example.roomacoustic.model.Vec3
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultSpeakerScreen(
    nav: NavController,
    vm: RoomViewModel,
    roomId: Int
) {
    // ✅ 들어올 때 현재 방 선택 고정
    LaunchedEffect(roomId) {
        vm.select(roomId)
    }

    // 1) 방 제목
    val rooms = vm.rooms.collectAsState().value
    val roomTitle by remember(rooms, roomId) {
        mutableStateOf(rooms.firstOrNull { it.id == roomId }?.title ?: "Room #$roomId")
    }

    // 2) 수동 입력 맵들
    val manualSizeMap   = vm.manualRoomSize.collectAsState().value
    val manualSpkMap    = vm.manualSpeakers.collectAsState().value
    val manualListener  = vm.manualListener.collectAsState().value
    val evalMap         = vm.listeningEval.collectAsState().value

    // 🔹 DB에서 끌어온 값들 (currentRoomId 기준)
    val latestMeasure   = vm.latestMeasure.collectAsState().value
    val savedSpeakers   = vm.savedSpeakers.collectAsState().value
    val latestEval      = vm.latestListeningEval.collectAsState().value

    // ✅ 4) 최종 RoomSize / 스피커 결정 로직
    val manualSize  = manualSizeMap[roomId]
    val roomSize: RoomSize? =
        manualSize ?: latestMeasure?.let { RoomSize(it.width, it.depth, it.height) }

    val manualSpk   = manualSpkMap[roomId]
    val speakers: List<Vec3> =
        manualSpk ?: savedSpeakers.map { Vec3(it.x, it.y, it.z) }

    val listener: Vec2? = manualListener[roomId]

    // 🔹 방별 최신 청취 평가 (DB → View)
    val eval = latestEval


    // 출처 태그
    val sizeTag = when {
        manualSize != null      -> "[수동]"
        latestMeasure != null   -> "[저장]"
        else                    -> "[미지정]"
    }
    val spkTag  = when {
        manualSpk != null               -> "[수동]"
        savedSpeakers.isNotEmpty()      -> "[저장]"
        else                            -> "[없음]"
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("스피커 · 청취 결과", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로"
                        )
                    }
                },
                actions = {
                    // "방 선택으로" : RoomScreen으로 복귀
                    TextButton(onClick = {
                        val ok = nav.popBackStack(Screen.Room.route, false)
                        if (!ok) {
                            nav.navigate(Screen.Room.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(Screen.Room.route) { inclusive = false }
                            }
                        }
                    }) { Text("방 선택으로") }
                }
            )
        }
    ) { pad ->

        // 🔴 정말 아무 RoomSize 정보도 없을 때만 에러 화면
        if (roomSize == null) {
            Box(
                modifier = Modifier
                    .padding(pad)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "저장된 방 크기 정보가 없습니다.\n" +
                            "RoomAnalysis / 측정 화면에서 먼저 방 크기를 설정해 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ───────── 상단: 방 / 스피커 / 청취 위치 요약 + 렌더링 버튼 ─────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 왼쪽: 방 제목 + 방 크기
                Column {
                    Text(roomTitle, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "방: W ${fmt(roomSize.w)} · D ${fmt(roomSize.d)} · H ${fmt(roomSize.h)} (m) $sizeTag",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                // 오른쪽: 스피커 개수 + 렌더링으로
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "스피커 ${speakers.size}개 $spkTag",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    TextButton(
                        onClick = {
                            nav.navigate(Screen.ResultRender.of(roomId))
                        }
                    ) {
                        Text("렌더링으로")
                    }
                }
            }

            // 청취 위치 텍스트 (nullable 대응)
            Text(
                text = listener?.let {
                    "청취 위치 (W, D) = (${fmt(it.x)} m, ${fmt(it.z)} m)"
                } ?: "청취 위치: 미지정",
                style = MaterialTheme.typography.bodySmall
            )

            // ───────── 중단: 2D Top-Down (조금 작게) ─────────
            Card(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.8f)           // 🔸 RoomAnalysis보다 살짝 줄인 비율
                    .heightIn(min = 200.dp)
            ) {
                TopDownRoomCanvasReadOnly(
                    room = roomSize,
                    speakersXZ = speakers.map { it.x to it.z },
                    listener = listener
                )
            }

            // ───────── 하단: 평가 상세 (조금 넓게) ─────────
            Surface(
                tonalElevation = 1.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f)
            ) {
                val scrollState = rememberScrollState()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (eval != null) {
                        Text(
                            "총점: ${eval.total} / 100",
                            style = MaterialTheme.typography.titleMedium
                        )

                        eval.metrics.forEach { m ->
                            AssistChip(
                                onClick = {},
                                label = { Text("${m.name} · ${m.score}점") }
                            )
                            LinearProgressIndicator(
                                progress = m.score / 100f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text(
                                m.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFB0BEC5)
                            )
                            Spacer(Modifier.height(4.dp))
                        }

                        if (eval.notes.isNotEmpty()) {
                            Divider()
                            Text("조언", style = MaterialTheme.typography.titleSmall)
                            eval.notes.forEach { note ->
                                Text("• $note", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else {
                        // 🔸 평가 결과가 아직 없을 때
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "저장된 청취 위치 평가 결과가 없습니다.\n" +
                                        "RoomAnalysis 화면에서 스피커 배치 평가를 먼저 수행해 주세요.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * RoomAnalysisScreen의 TopDownRoomCanvas에서
 * 드래그/입력 부분을 제거한 "읽기 전용" 버전
 */
@Composable
private fun TopDownRoomCanvasReadOnly(
    room: RoomSize,
    speakersXZ: List<Pair<Float, Float>>,
    listener: Vec2?   // ✅ nullable 로 변경
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val pad = 24f

        // 방 비율에 맞게 스케일
        val scale = kotlin.math.min(
            (wPx - pad * 2) / room.w,
            (hPx - pad * 2) / room.d
        )
        val offsetX = (wPx - room.w * scale) / 2f
        val offsetY = (hPx - room.d * scale) / 2f

        fun worldToCanvasX(x: Float) = offsetX + x * scale
        fun worldToCanvasY(z: Float) = offsetY + z * scale

        Canvas(modifier = Modifier.fillMaxSize()) {
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

            // 스피커
            speakersXZ.forEach { (sx, sz) ->
                drawCircle(
                    color = Color(0xFFFFA726),
                    radius = 6f,
                    center = Offset(worldToCanvasX(sx), worldToCanvasY(sz))
                )
            }

            // 청취자 (있을 때만)
            listener?.let { lis ->
                drawCircle(
                    color = Color(0xFF64B5F6),
                    radius = 10f,
                    center = Offset(worldToCanvasX(lis.x), worldToCanvasY(lis.z))
                )
            }
        }
    }
}

private fun fmt(v: Float) = String.format("%.2f", v)
