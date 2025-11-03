package com.example.roomacoustic.screens.measure

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.example.roomacoustic.model.Vec3
import com.example.roomacoustic.navigation.Screen
import com.example.roomacoustic.viewmodel.RoomViewModel
import kotlin.math.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import androidx.compose.ui.graphics.Color
import com.example.roomacoustic.screens.components.RoomViewport3DGL
import com.example.roomacoustic.screens.components.RoomSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding


@Composable
fun RenderScreen(
    nav: NavController,
    vm: RoomViewModel,
    detected: Boolean
) {
    val roomId = vm.currentRoomId.collectAsState().value
    if (roomId == null) {
        Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
        return
    }

    val labeled = vm.labeledMeasures.collectAsState().value
    val frame3D  = vm.measure3DResult.collectAsState().value

    // 스피커 재구성 트리거
    val speakersVersion = vm.speakersVersion.collectAsState(0).value
    val speakers = remember(speakersVersion) { vm.speakers.toList() }

    val bannerColor = if (detected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.error

    // 자동 추론 + 수동 입력 우선
    val autoRoomSize: RoomSize? = remember(labeled) { inferRoomSizeFromLabels(labeled) }
    var manualRoomSize by rememberSaveable { mutableStateOf<RoomSize?>(null) }
    val roomSize = manualRoomSize ?: autoRoomSize

    // 월드→로컬(W, H, D) 변환
    val toLocal: (FloatArray) -> Vec3? = remember(frame3D) {
        { p ->
            frame3D?.let { m ->
                val origin = m.frame.origin
                val vx = m.frame.vx
                val vy = m.frame.vy
                val vz = m.frame.vz
                val d = Vec3(p[0], p[1], p[2]) - origin
                Vec3(d.dot(vx), d.dot(vy), d.dot(vz))
            }
            // frame3D가 null이면 let이 실행되지 않으므로 자연스럽게 null 반환
        }
    }

    // 1) 월드 → 로컬
    val speakersLocalRaw = remember(speakers, frame3D) {
        speakers.mapNotNull { sp -> toLocal(sp.worldPos) }
    }

    // 2) 근접 중복 제거(10cm 미만은 같은 점 처리)
    val speakersLocalDedup = remember(speakersLocalRaw) {
        dedupByDistance(speakersLocalRaw, threshold = 0.10f)
    }

    // 3) 방 중심으로 자동 정렬(시각적 안정화; 절대 오프셋은 보정 X)
    val speakersLocal = remember(speakersLocalDedup, roomSize) {
        if (roomSize == null || speakersLocalDedup.isEmpty()) speakersLocalDedup
        else autoCenterToRoom(speakersLocalDedup, roomSize)
    }

    var showInput by rememberSaveable { mutableStateOf(false) }
    var showDetail by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp)) {

        /* 중앙 3D 뷰포트 */
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (roomSize != null) {
                val clamped = speakersLocal.map { p ->
                    Vec3(
                        x = p.x.coerceIn(0f, roomSize.w),
                        y = p.y.coerceIn(0f, roomSize.h),
                        z = p.z.coerceIn(0f, roomSize.d)
                    )
                }
                RoomViewport3DGL(
                    room = roomSize,
                    speakersLocal = clamped,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .aspectRatio(1.2f)
                )
            } else {
                Text(
                    text = "방 크기(W/D/H)가 없어 3D 미리보기를 표시할 수 없습니다. (터치하여 직접 입력)",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clickable { showInput = true }
                )
            }
        }

        // 디버그 표시
        Text(
            text = "스피커(월드): ${speakers.size} / 로컬 변환: ${speakersLocalRaw.size}" +
                    (if (frame3D == null) "  [frame3D 없음]" else ""),
            color = Color(0xFFB0BEC5),
            style = MaterialTheme.typography.bodySmall
        )

        /* 하단 요약 + 상세정보 버튼 */
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (roomSize != null) {
                Text(
                    "W ${"%.2f".format(roomSize.w)}m · D ${"%.2f".format(roomSize.d)}m · H ${"%.2f".format(roomSize.h)}m",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFFEEEEEE)
                )
            } else {
                Text("W/D/H 미지정", style = MaterialTheme.typography.titleMedium)
            }
            Row {
                TextButton(onClick = { showDetail = true }) { Text("상세정보") }
                TextButton(onClick = { showInput = true }) { Text("직접 입력/편집") }
            }
        }

        Spacer(Modifier.height(8.dp))

        /* 중앙 배너 */
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text  = if (detected) "스피커 탐지 완료" else "스피커 미탐지",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = bannerColor
            )
        }

        Spacer(Modifier.height(8.dp))

        /* 하단 우측 '다음' 버튼 */
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = { nav.navigate(Screen.TestGuide.route) }) { Text("다음") }
        }
    }

    /* 상세정보 모달 */
    if (showDetail) {
        AlertDialog(
            onDismissRequest = { showDetail = false },
            title = { Text("상세정보") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("측정값")
                    if (labeled.isEmpty()) Text("저장된 길이 측정값이 없습니다.")
                    else labeled.forEach { m -> Text("• ${m.label}: ${"%.2f".format(m.meters)} m") }

                    Spacer(Modifier.height(8.dp))
                    Text("프레임/좌표계")
                    if (frame3D == null) Text("좌표 프레임 없음")
                    else {
                        val f = frame3D.frame
                        Text("origin = (${fmt(f.origin.x)}, ${fmt(f.origin.y)}, ${fmt(f.origin.z)})")
                        Text("vx = (${fmt(f.vx.x)}, ${fmt(f.vx.y)}, ${fmt(f.vx.z)})")
                        Text("vy = (${fmt(f.vy.x)}, ${fmt(f.vy.y)}, ${fmt(f.vy.z)})")
                        Text("vz = (${fmt(f.vz.x)}, ${fmt(f.vz.y)}, ${fmt(f.vz.z)})")
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("스피커(로컬)")
                    if (speakersLocal.isEmpty()) Text("감지된 스피커 없음")
                    else speakersLocal.forEachIndexed { i, p ->
                        Text("• #${i + 1} (W,D,H)=(${fmt(p.x)}, ${fmt(p.z)}, ${fmt(p.y)}) m")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDetail = false }) { Text("닫기") } }
        )
    }

    /* 수동 입력 다이얼로그 */
    if (showInput) {
        RoomSizeInputDialog(
            initial = manualRoomSize ?: autoRoomSize,
            onDismiss = { showInput = false },
            onConfirm = { w, d, h ->
                manualRoomSize = RoomSize(w, d, h)
                showInput = false
            }
        )
    }
}

/* ──────────────────────────────────────────── */
/* 강건한 레이블 매칭                          */
/* ──────────────────────────────────────────── */

private fun normalizeLabel(s: String): String =
    s.lowercase().replace("\\s+".toRegex(), "")
        .replace("[()\\[\\]{}:：=~_\\-]".toRegex(), "")

private val W_KEYS = setOf("w", "width", "가로", "폭", "넓이")
private val D_KEYS = setOf("d", "depth", "세로", "길이", "방길이", "방깊이", "전장", "장변")
private val H_KEYS = setOf("h", "height", "높이", "천장", "층고")

private fun inferRoomSizeFromLabels(
    labeled: List<RoomViewModel.LabeledMeasure>
): RoomSize? {
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
/* 수동 입력                                   */
/* ──────────────────────────────────────────── */

@Composable
private fun RoomSizeInputDialog(
    initial: RoomSize?,
    onDismiss: () -> Unit,
    onConfirm: (Float, Float, Float) -> Unit
) {
    var wText by rememberSaveable { mutableStateOf(initial?.w?.toString() ?: "") }
    var dText by rememberSaveable { mutableStateOf(initial?.d?.toString() ?: "") }
    var hText by rememberSaveable { mutableStateOf(initial?.h?.toString() ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("방 크기 직접 입력 (미터)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = wText, onValueChange = { wText = it },
                    label = { Text("가로 W (m)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number, imeAction = ImeAction.Next
                    )
                )
                OutlinedTextField(
                    value = dText, onValueChange = { dText = it },
                    label = { Text("세로 D (m)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number, imeAction = ImeAction.Next
                    )
                )
                OutlinedTextField(
                    value = hText, onValueChange = { hText = it },
                    label = { Text("높이 H (m)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(
                        keyboardType = KeyboardType.Number, imeAction = ImeAction.Done
                    )
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = wText.toFloatOrNull()
                val d = dText.toFloatOrNull()
                val h = hText.toFloatOrNull()
                if (w == null || d == null || h == null || w <= 0 || d <= 0 || h <= 0) {
                    error = "모든 값을 0보다 큰 숫자로 입력하세요."
                } else onConfirm(w, d, h)
            }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}

/* ──────────────────────────────────────────── */
/* 확장/수학 유틸                               */
/* ──────────────────────────────────────────── */

private fun fmt(v: Float) = String.format("%.2f", v)
private operator fun Vec3.minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
private fun Vec3.dot(o: Vec3) = x * o.x + y * o.y + z * o.z

/* ── 새로 추가된 도우미 ── */

// 포인트 간 거리가 threshold 미만이면 중복으로 제거
private fun dedupByDistance(points: List<Vec3>, threshold: Float): List<Vec3> {
    if (points.size <= 1) return points
    val out = mutableListOf<Vec3>()
    for (p in points) {
        val dup = out.any { q ->
            val dx = p.x - q.x; val dy = p.y - q.y; val dz = p.z - q.z
            sqrt(dx*dx + dy*dy + dz*dz) < threshold
        }
        if (!dup) out += p
    }
    return out
}

// 로컬 포인트들의 무게중심을 방 중심(W/2,H/2,D/2)으로 평행이동
private fun autoCenterToRoom(points: List<Vec3>, room: RoomSize): List<Vec3> {
    if (points.isEmpty()) return points
    val cx = points.map { it.x }.average().toFloat()
    val cy = points.map { it.y }.average().toFloat()
    val cz = points.map { it.z }.average().toFloat()
    val tx = room.w * 0.5f - cx
    val ty = room.h * 0.5f - cy
    val tz = room.d * 0.5f - cz
    return points.map { Vec3(it.x + tx, it.y + ty, it.z + tz) }
}
