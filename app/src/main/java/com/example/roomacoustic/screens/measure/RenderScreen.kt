package com.example.roomacoustic.screens.measure

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

import androidx.navigation.NavController
import com.example.roomacoustic.model.Vec3
import com.example.roomacoustic.navigation.Screen
import com.example.roomacoustic.viewmodel.RoomViewModel
import kotlin.math.*

import androidx.compose.ui.graphics.Color
import com.example.roomacoustic.screens.components.RoomViewport3DGL
import com.example.roomacoustic.screens.components.RoomSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.lazy.LazyColumn

import com.example.roomacoustic.util.inferRoomSizeFromLabels


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

    // 🔹 수동 입력 값은 VM에서 방별로 꺼내 쓰기
    val manualSizeMap = vm.manualRoomSize.collectAsState().value
    val manualSpkMap  = vm.manualSpeakers.collectAsState().value
    val manualSize    = manualSizeMap[roomId]
    val manualSpks    = manualSpkMap[roomId]

    // 자동 추론 RoomSize
    val autoRoomSize: RoomSize? = remember(labeled) { inferRoomSizeFromLabels(labeled) }

    // 🔹 수동 우선 룸 사이즈
    val roomSize = manualSize ?: autoRoomSize

    // 월드→로컬(W, H, D) 변환
    // - ARCore world(Speaker3D.worldPos) → Room Local(Vec3, 0~W/0~H/0~D)
    val speakersLocalRaw = remember(speakers, frame3D) {
        val frame = frame3D?.frame ?: return@remember emptyList<Vec3>()
        speakers.map { sp ->
            val w = sp.worldPos
            frame.worldToLocal(Vec3(w[0], w[1], w[2]))
        }
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

    // 수동 입력으로 확정된 스피커들(로컬 좌표). null이면 자동 추론 사용.
    var showSpeakerInput by rememberSaveable { mutableStateOf(false) }

    val speakersForRender: List<Vec3> = manualSpks ?: speakersLocal


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
                val clamped = speakersForRender.map { p ->   // ← speakersLocal → speakersForRender
                    Vec3(
                        x = p.x.coerceIn(0f, roomSize.w),
                        y = p.y.coerceIn(0f, roomSize.h),
                        z = p.z.coerceIn(0f, roomSize.d)
                    )
                }
                key(roomSize to clamped) {
                    RoomViewport3DGL(
                        room = roomSize,
                        speakersLocal = clamped,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth()
                            .aspectRatio(1.2f)
                    )
                }
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

                // 🔹 스피커 수동 입력 (roomSize 있어야 가능)
                TextButton(
                    onClick = { showSpeakerInput = true },
                    enabled = roomSize != null
                ) { Text("스피커 수동 입력") }

                // 🔹 수동 스피커 해제
                TextButton(
                    onClick = { vm.clearManualSpeakers(roomId) },
                    enabled = manualSpks != null
                ) { Text("수동 스피커 해제") }

                // 🔹 수동 RoomSize 해제 (있으면 노출하고 싶으면 추가)
                TextButton(
                    onClick = { vm.setManualRoomSize(roomId, null) },
                    enabled = manualSize != null
                ) { Text("수동 RoomSize 해제") }
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
            Button(onClick = { nav.navigate(Screen.RoomAnalysis.route) }) { Text("다음") }
        }
    }

    /* 상세정보 모달 */
    if (showDetail) {
        val sizeTag = when {
            manualSize != null     -> "[수동]"
            autoRoomSize != null   -> "[자동]"
            else                   -> "[미지정]"
        }
        val spkTag = if (manualSpks != null) "[수동]" else "[자동]"

        AlertDialog(
            onDismissRequest = { showDetail = false },
            title = { Text("상세정보") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("측정값")
                    if (labeled.isEmpty()) Text("저장된 길이 측정값이 없습니다.")
                    else labeled.forEach { m ->
                        Text("• ${m.label}: ${"%.2f".format(m.meters)} m")
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("프레임/좌표계")
                    if (frame3D == null) Text("좌표 프레임 없음")
                    else {
                        val f = frame3D.frame
                        Text("origin = (${fmt(f.origin.x)}, ${fmt(f.origin.y)}, ${fmt(f.origin.z)})")
                        Text("vx = (${fmt(f.vx.x)}, ${fmt(f.vy.y)}, ${fmt(f.vz.z)})")
                        Text("vy = (${fmt(f.vy.x)}, ${fmt(f.vy.y)}, ${fmt(f.vy.z)})")
                        Text("vz = (${fmt(f.vz.x)}, ${fmt(f.vz.y)}, ${fmt(f.vz.z)})")
                    }

                    Spacer(Modifier.height(8.dp))
                    // 🔹 RoomSize 출처
                    Text("RoomSize $sizeTag")
                    when (roomSize) {
                        null -> Text("W/D/H 미지정")
                        else -> Text("W ${"%.2f".format(roomSize.w)} · D ${"%.2f".format(roomSize.d)} · H ${"%.2f".format(roomSize.h)} (m)")
                    }

                    Spacer(Modifier.height(8.dp))
                    // 🔹 스피커 출처
                    val listForInfo = speakersForRender
                    Text("스피커(로컬) $spkTag")
                    if (listForInfo.isEmpty()) Text("스피커 없음")
                    else listForInfo.forEachIndexed { i, p ->
                        Text("• #${i + 1} (W,D,H)=(${fmt(p.x)}, ${fmt(p.z)}, ${fmt(p.y)}) m")
                    }
                }
            },
            confirmButton = { TextButton({ showDetail = false }) { Text("닫기") } }
        )
    }



    /* 수동 입력 다이얼로그 */
    if (showInput) {
        RoomSizeInputDialog(
            initial = manualSize ?: autoRoomSize,
            onDismiss = { showInput = false },
            onConfirmMeters = { w, d, h ->
                vm.setManualRoomSize(roomId, RoomSize(w, d, h)) // 🔹 VM에 m로 저장
                showInput = false
            }
        )
    }

    /* 스피커 수동 입력 다이얼로그 */
    if (showSpeakerInput && roomSize != null) {
        ManualSpeakersDialog(
            room = roomSize, // (m)
            onDismiss = { showSpeakerInput = false },
            onConfirm = { list -> // list: List<Vec3> in meters
                vm.setManualSpeakers(roomId, list) // 🔹 VM에 저장
                showSpeakerInput = false
            }
        )
    }


}


/* ──────────────────────────────────────────── */
/* 수동 입력                                   */
/* ──────────────────────────────────────────── */

@Composable
private fun RoomSizeInputDialog(
    initial: RoomSize?, // (m)
    onDismiss: () -> Unit,
    onConfirmMeters: (Float, Float, Float) -> Unit // (m)로 콜백
) {
    // m → cm 초기 채우기
    var wText by rememberSaveable { mutableStateOf(initial?.w?.times(100)?.roundToInt()?.toString() ?: "") }
    var dText by rememberSaveable { mutableStateOf(initial?.d?.times(100)?.roundToInt()?.toString() ?: "") }
    var hText by rememberSaveable { mutableStateOf(initial?.h?.times(100)?.roundToInt()?.toString() ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("방 크기 직접 입력 (센티미터)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = wText, onValueChange = { if (it.isEmpty() || it.all(Char::isDigit)) wText = it },
                    label = { Text("가로 W (cm)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = dText, onValueChange = { if (it.isEmpty() || it.all(Char::isDigit)) dText = it },
                    label = { Text("세로 D (cm)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = hText, onValueChange = { if (it.isEmpty() || it.all(Char::isDigit)) hText = it },
                    label = { Text("높이 H (cm)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val wcm = wText.toIntOrNull()
                val dcm = dText.toIntOrNull()
                val hcm = hText.toIntOrNull()
                if (wcm == null || dcm == null || hcm == null || wcm <= 0 || dcm <= 0 || hcm <= 0) {
                    error = "모든 값을 0보다 큰 정수(cm)로 입력하세요."
                } else {
                    val w = wcm / 100f
                    val d = dcm / 100f
                    val h = hcm / 100f
                    onConfirmMeters(w, d, h)
                }
            }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}


/* ──────────────────────────────────────────── */
/* 확장/수학 유틸                               */
/* ──────────────────────────────────────────── */

private fun fmt(v: Float) = String.format("%.2f", v)

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

@Composable
private fun ManualSpeakersDialog(
    room: RoomSize,
    initialCount: Int = 2,
    onDismiss: () -> Unit,
    onConfirm: (List<Vec3>) -> Unit
) {
    var countText by rememberSaveable { mutableStateOf(initialCount.coerceIn(1, 8).toString()) }
    val count = countText.toIntOrNull()?.coerceIn(1, 8) ?: 1

    // ✅ Compose가 추적하는 상태로 정의 (mutableStateOf)
    class RowState(
        sideX: SideX = SideX.LEFT,  x: String = "",
        sideZ: SideZ = SideZ.BACK,  z: String = "",
        sideY: SideY = SideY.FLOOR, y: String = ""
    ) {
        var sideX by mutableStateOf(sideX)
        var xCm   by mutableStateOf(x)
        var sideZ by mutableStateOf(sideZ)
        var zCm   by mutableStateOf(z)
        var sideY by mutableStateOf(sideY)
        var yCm   by mutableStateOf(y)
    }

    // ✅ 리스트 자체도 상태 리스트로 유지 (recomposition 시 값 보존)
    val rows = remember { mutableStateListOf<RowState>() }
    LaunchedEffect(count) {
        while (rows.size < count) rows += RowState()
        while (rows.size > count) rows.removeAt(rows.lastIndex)
    }

    val maxXcm = (room.w * 100).roundToInt()
    val maxZcm = (room.d * 100).roundToInt()
    val maxYcm = (room.h * 100).roundToInt()

    var error by rememberSaveable { mutableStateOf<String?>(null) }

    // ✅ 다이얼로그 컨텐츠 높이 제한 + 스크롤
    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val dialogMaxH = screenH * 0.75f

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("스피커 수동 입력 (벽까지 거리, cm)") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = dialogMaxH),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = countText,
                            onValueChange = { s ->
                                if (s.isEmpty() || s.all { it.isDigit() }) countText = s
                            },
                            label = { Text("개수") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            modifier = Modifier.width(96.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("(1~8)")
                    }
                }

                items(rows.size) { idx ->
                    val r = rows[idx]
                    Divider()
                    Text("스피커 #${idx + 1}", style = MaterialTheme.typography.titleSmall)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SegmentedButtonsX(selected = r.sideX, onSelect = { r.sideX = it })
                        NumberField(
                            value = r.xCm,
                            onValueChange = { r.xCm = it },
                            label = if (r.sideX == SideX.LEFT) "좌측까지(cm)" else "우측까지(cm)",
                            supporting = "0~$maxXcm",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SegmentedButtonsZ(selected = r.sideZ, onSelect = { r.sideZ = it })
                        NumberField(
                            value = r.zCm,
                            onValueChange = { r.zCm = it },
                            label = if (r.sideZ == SideZ.FRONT) "전면까지(cm)" else "후면까지(cm)",
                            supporting = "0~$maxZcm",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SegmentedButtonsY(selected = r.sideY, onSelect = { r.sideY = it })
                        NumberField(
                            value = r.yCm,
                            onValueChange = { r.yCm = it },
                            label = if (r.sideY == SideY.FLOOR) "바닥까지(cm)" else "천장까지(cm)",
                            supporting = "0~$maxYcm",
                            ime = ImeAction.Done,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (error != null) {
                    item { Text(error!!, color = MaterialTheme.colorScheme.error) }
                }
                item {
                    Text(
                        "한쪽만 입력하세요. 선택한 면까지의 거리(cm)만 입력하면 반대편은 자동으로 계산됩니다.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        confirmButton = {
            TextButton(onClick = {
                val out = mutableListOf<Vec3>()
                for (r in rows) {
                    val xcm = r.xCm.toIntOrNull()
                    val zcm = r.zCm.toIntOrNull()
                    val ycm = r.yCm.toIntOrNull()
                    if (xcm == null || zcm == null || ycm == null) {
                        error = "모든 거리를 숫자로 입력하세요."
                        return@TextButton
                    }
                    if (xcm !in 0..maxXcm || zcm !in 0..maxZcm || ycm !in 0..maxYcm) {
                        error = "범위를 벗어난 값이 있습니다."
                        return@TextButton
                    }

                    // cm → m 변환 + 선택한 면 기준으로 좌표 환산
                    val x = if (r.sideX == SideX.LEFT)  xcm/100f else room.w - xcm/100f
                    val z = if (r.sideZ == SideZ.FRONT) zcm/100f else room.d - zcm/100f
                    val y = if (r.sideY == SideY.FLOOR) ycm/100f else room.h - ycm/100f

                    out += Vec3(
                        x.coerceIn(0f, room.w),
                        y.coerceIn(0f, room.h),
                        z.coerceIn(0f, room.d)
                    )
                }
                error = null
                onConfirm(out)
            }) { Text("확인") }
        }
    )
}

/** 숫자 전용 TextField (정수 cm) */
@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supporting: String,
    ime: ImeAction = ImeAction.Next,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { s ->
            // 숫자 + 빈 문자열 허용(지우기 가능)
            if (s.isEmpty() || s.all { it.isDigit() }) onValueChange(s)
        },
        label = { Text(label) },
        supportingText = { Text(supporting) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ime
        ),
        modifier = modifier
    )
}

private enum class SideX { LEFT, RIGHT }
private enum class SideZ { FRONT, BACK }
private enum class SideY { FLOOR, CEILING }


@Composable private fun SegmentedButtonsX(selected: SideX, onSelect: (SideX) -> Unit) {
    SegmentedRow(
        items = listOf("좌측 L" to SideX.LEFT, "우측 R" to SideX.RIGHT),
        selected = selected, onSelect = onSelect
    )
}
@Composable private fun SegmentedButtonsZ(selected: SideZ, onSelect: (SideZ) -> Unit) {
    SegmentedRow(
        items = listOf("전면 F" to SideZ.FRONT, "후면 B" to SideZ.BACK),
        selected = selected, onSelect = onSelect
    )
}
@Composable private fun SegmentedButtonsY(selected: SideY, onSelect: (SideY) -> Unit) {
    SegmentedRow(
        items = listOf("바닥" to SideY.FLOOR, "천장" to SideY.CEILING),
        selected = selected, onSelect = onSelect
    )
}

@Composable
private fun <T> SegmentedRow(
    items: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { (label, value) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label) }
            )
        }
    }
}
