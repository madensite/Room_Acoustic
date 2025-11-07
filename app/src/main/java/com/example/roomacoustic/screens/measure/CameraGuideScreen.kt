package com.example.roomacoustic.screens.measure

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.roomacoustic.navigation.Screen
import com.example.roomacoustic.viewmodel.RoomViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraGuideScreen(
    nav: NavController,
    vm: RoomViewModel
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("카메라 측정 안내", fontWeight = FontWeight.SemiBold) }
            )
        }
    ) { pad ->
        Column(
            modifier = Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 안내 영역
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("이 화면에서 선택하세요:")
                Text("• 카메라를 사용해서 방 크기를 추정하고,\n  스피커를 자동 탐지합니다.")
                Text("• 카메라 사용이 어렵다면, 바로 3D 렌더 화면으로 이동해\n  방 크기/스피커 위치를 수동 입력할 수 있습니다.")
                Divider()
                Text("Tip", style = MaterialTheme.typography.titleMedium)
                Text("충분히 밝은 환경, 바닥면이 잘 보이는 각도에서\n디바이스를 천천히 움직여 주세요.")
            }

            // 액션 버튼들
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1) 카메라로 측정 시작
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        // (기존 측정 데이터가 남아 있으면 혼동이 생길 수 있으니 초기에 정리)
                        vm.clearMeasure3DResult()
                        vm.clearLabeledMeasures()
                        vm.clearMeasureAndSpeakers()

                        // 폭 → 깊이 → 높이 → 스피커 탐지 순으로 진행
                        nav.navigate(Screen.MeasureWidth.route)
                    }
                ) {
                    Text("카메라로 측정 시작")
                }

                // 2) 카메라 없이 진행 (Render로 점프)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        // 카메라/YOLO 산출물 정리 후 수동 플로우로
                        vm.clearMeasure3DResult()
                        vm.clearLabeledMeasures()
                        vm.clearMeasureAndSpeakers()

                        nav.navigate(Screen.Render.route)
                    }
                ) {
                    Text("카메라 없이 진행 (직접 입력)")
                }
            }
        }
    }
}
