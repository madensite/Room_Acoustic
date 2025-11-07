package com.example.roomacoustic.screens.result

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.roomacoustic.navigation.Screen
import com.example.roomacoustic.screens.components.RoomViewport3DGL
import com.example.roomacoustic.screens.components.RoomSize
import com.example.roomacoustic.viewmodel.RoomViewModel
import com.example.roomacoustic.model.Vec3
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.Color

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultRenderScreen(
    nav: NavController,
    vm: RoomViewModel,
    roomId: Int
) {
    // ── 공통 소스
    val labeled  = vm.labeledMeasures.collectAsState().value
    val frame3D  = vm.measure3DResult.collectAsState().value

    val speakersVersion = vm.speakersVersion.collectAsState(0).value
    val speakersAutoWorld = remember(speakersVersion) { vm.speakers.toList() }

    // 방 제목
    val rooms = vm.rooms.collectAsState().value
    val roomTitle by remember(rooms, roomId) {
        mutableStateOf(rooms.firstOrNull { it.id == roomId }?.title ?: "Room #$roomId")
    }

    // ── 수동 입력(방별) 구독
    val manualSizeMap = vm.manualRoomSize.collectAsState().value    // Map<Int, RoomSize>
    val manualSpkMap  = vm.manualSpeakers.collectAsState().value    // Map<Int, List<Vec3>>
    val manualSize    = manualSizeMap[roomId]       // 수동 RoomSize (m)
    val manualSpks    = manualSpkMap[roomId]        // 수동 Speaker (local, m)

    // 자동 RoomSize 추론 (레이블)
    val autoRoomSize: RoomSize? = remember(labeled) { inferRoomSizeFromLabels(labeled) }

    // 최종 RoomSize: 수동 우선
    val roomSize = manualSize ?: autoRoomSize

    // 월드 → 로컬 (자동 탐지용 변환기) : 수동 스피커는 이미 로컬이므로 변환 불필요
    val toLocal: (FloatArray) -> Vec3? = remember(frame3D) {
        { p ->
            frame3D?.let { m ->
                val o = m.frame.origin
                val vx = m.frame.vx
                val vy = m.frame.vy
                val vz = m.frame.vz
                val d = Vec3(p[0], p[1], p[2]) - o
                Vec3(
                    d.x * vx.x + d.y * vx.y + d.z * vx.z,
                    d.x * vy.x + d.y * vy.y + d.z * vy.z,
                    d.x * vz.x + d.y * vz.y + d.z * vz.z
                )
            }
        }
    }

    // 자동 스피커: 월드→로컬 (frame3D 없으면 빈 리스트)
    val speakersLocalAuto = remember(speakersAutoWorld, frame3D) {
        if (frame3D == null) emptyList()
        else speakersAutoWorld.mapNotNull { toLocal(it.worldPos) }
    }

    // 최종 스피커: 수동(로컬) 우선, 없으면 자동(로컬)
    val speakersForRender = manualSpks ?: speakersLocalAuto

    // 출처 태그
    val sizeTag = when {
        manualSize != null   -> "[수동]"
        autoRoomSize != null -> "[자동]"
        else                 -> "[미지정]"
    }
    val spkTag  = if (manualSpks != null) "[수동]" else "[자동]"

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("3D 결과", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = {
                        val ok = nav.popBackStack(Screen.Room.route, false)
                        if (!ok) {
                            nav.navigate(Screen.Room.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(Screen.Room.route) { inclusive = false }
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로"
                        )
                    }
                },
                actions = {
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
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(roomTitle)
                TextButton(onClick = { nav.popBackStack() }) { Text("뒤로") }
            }

            Spacer(Modifier.height(8.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = roomSize?.let {
                        "W %.2f · D %.2f · H %.2f (m) %s".format(it.w, it.d, it.h, sizeTag)
                    } ?: "측정값 없음 $sizeTag"
                )
                Text("스피커: ${speakersForRender.size}개 $spkTag")
            }

            Spacer(Modifier.height(12.dp))

            if (roomSize != null) {
                // 안전 클램핑
                val clamped = remember(speakersForRender, roomSize) {
                    speakersForRender.map { p ->
                        Vec3(
                            x = p.x.coerceIn(0f, roomSize.w),
                            y = p.y.coerceIn(0f, roomSize.h),
                            z = p.z.coerceIn(0f, roomSize.d)
                        )
                    }
                }

                RoomViewport3DGL(
                    room = roomSize,
                    speakersLocal = clamped,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .aspectRatio(1.2f)
                )
            } else {
                Box(
                    Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "방 크기가 없어 3D 미리보기를 표시할 수 없습니다.",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/* RenderScreen과 동일한 치수 추론 함수 */
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

private operator fun Vec3.minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
