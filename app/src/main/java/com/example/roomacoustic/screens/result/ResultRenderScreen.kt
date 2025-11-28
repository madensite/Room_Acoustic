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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultRenderScreen(
    nav: NavController,
    vm: RoomViewModel,
    roomId: Int
) {
    // ✅ 들어올 때 현재 방 선택 고정
    LaunchedEffect(roomId) {
        vm.select(roomId)
    }

    // ✅ DB 기반 값들 (현재 roomId 기준)
    val latestMeasure = vm.latestMeasure.collectAsState().value
    val savedSpeakers = vm.savedSpeakers.collectAsState().value

    // 방 제목
    val rooms = vm.rooms.collectAsState().value
    val roomTitle by remember(rooms, roomId) {
        mutableStateOf(rooms.firstOrNull { it.id == roomId }?.title ?: "Room #$roomId")
    }

    // ── 수동 입력(방별) ──
    val manualSizeMap = vm.manualRoomSize.collectAsState().value    // Map<Int, RoomSize>
    val manualSpkMap  = vm.manualSpeakers.collectAsState().value    // Map<Int, List<Vec3>>

    val manualSize    = manualSizeMap[roomId]       // 수동 RoomSize (m)
    val manualSpks    = manualSpkMap[roomId]        // 수동 Speaker (local, m)

    // ✅ DB에 저장된 측정값을 RoomSize 로 변환 (m 단위라고 가정)
    val dbRoomSize: RoomSize? = latestMeasure?.let {
        RoomSize(it.width, it.depth, it.height)
    }

    // ✅ 최종 RoomSize 우선순위: 수동 > DB 저장
    val roomSize: RoomSize? = manualSize ?: dbRoomSize

    // ✅ DB에 저장된 스피커를 로컬 좌표 Vec3 로 변환 (x,y,z 가 local 로 저장된다고 가정)
    val speakersFromDbLocal: List<Vec3> = savedSpeakers.map {
        Vec3(it.x, it.y, it.z)
    }

    // ✅ 최종 스피커 우선순위: 수동(local) > DB(local)
    val speakersForRender: List<Vec3> =
        manualSpks ?: speakersFromDbLocal

    // ✅ 출처 태그
    val sizeTag = when {
        manualSize != null   -> "[수동]"
        dbRoomSize != null   -> "[저장]"
        else                 -> "[미지정]"
    }

    val spkTag  = when {
        manualSpks != null                 -> "[수동]"
        speakersFromDbLocal.isNotEmpty()   -> "[저장]"
        else                               -> "[없음]"
    }

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
                TextButton(
                    onClick = {
                        nav.navigate(Screen.ResultSpeaker.of(roomId))
                    }
                ) {
                    Text("분석으로")
                }
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
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
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
