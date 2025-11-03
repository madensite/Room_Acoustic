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
import kotlin.math.sqrt
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultRenderScreen(
    nav: NavController,
    vm: RoomViewModel,
    roomId: Int
) {
    // ViewModel에서 동일 소스 재사용
    val labeled = vm.labeledMeasures.collectAsState().value
    val frame3D  = vm.measure3DResult.collectAsState().value

    val speakersVersion = vm.speakersVersion.collectAsState(0).value
    val speakers = remember(speakersVersion) { vm.speakers.toList() }

    val autoRoomSize = remember(labeled) { inferRoomSizeFromLabels(labeled) }
    val roomSize = autoRoomSize

    // 월드→로컬
    val toLocal: (FloatArray) -> Vec3? = remember(frame3D) {
        { p ->
            frame3D?.let { m ->
                val o = m.frame.origin; val vx = m.frame.vx; val vy = m.frame.vy; val vz = m.frame.vz
                val d = Vec3(p[0], p[1], p[2]) - o
                Vec3(d.x*vx.x + d.y*vx.y + d.z*vx.z,
                    d.x*vy.x + d.y*vy.y + d.z*vy.z,
                    d.x*vz.x + d.y*vz.y + d.z*vz.z)
            }
        }
    }
    val speakersLocal = remember(speakers, frame3D) { speakers.mapNotNull { toLocal(it.worldPos) } }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("3D 결과", fontWeight = FontWeight.SemiBold) },

                // ← 뒤로: 스택에 Room이 있으면 pop으로 바로 복귀
                navigationIcon = {
                    IconButton(onClick = {
                        val ok = nav.popBackStack(Screen.Room.route, false)
                        if (!ok) {
                            // Room이 스택에 없으면 새로 진입
                            nav.navigate(Screen.Room.route) {
                                launchSingleTop = true
                                restoreState = true
                                popUpTo(Screen.Room.route) { inclusive = false }
                            }
                        }
                    }) { Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로"
                    ) }
                },

                // “방 선택으로”만 남기고 “분석 보기” 제거
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
            modifier = Modifier.padding(pad).fillMaxSize().padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Room #$roomId")
                TextButton(onClick = { nav.popBackStack() }) { Text("뒤로") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = roomSize?.let { "W %.2f · D %.2f · H %.2f (m)".format(it.w, it.d, it.h) }
                        ?: "측정값 없음"
                )
                Text("스피커: ${speakersLocal.size}개")
            }
            Spacer(Modifier.height(12.dp))

            if (roomSize != null) {
                RoomViewport3DGL(
                    room = roomSize,
                    speakersLocal = speakersLocal,
                    modifier = Modifier.fillMaxWidth().weight(1f).aspectRatio(1.2f)
                )
            } else {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("방 크기가 없어 3D 미리보기를 표시할 수 없습니다.", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

/* RenderScreen에 있던 레이블→치수 추론 함수 (그대로 복붙) */
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
