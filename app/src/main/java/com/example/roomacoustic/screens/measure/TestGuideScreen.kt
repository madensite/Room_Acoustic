package com.example.roomacoustic.screens.measure

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.roomacoustic.viewmodel.RoomViewModel
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding

@Composable
fun TestGuideScreen(
    nav: NavController,
    vm: RoomViewModel
) {
    // Surface로 전체를 감싸 테마에 맞는 배경색과 글자색을 자동으로 적용합니다.
    Surface(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            // Modifier.fillMaxSize()는 부모인 Surface로 옮겨졌으므로
            // 여기서는 패딩만 적용해주는 것이 더 명확합니다.
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 이 아래 Text들은 이제 Surface의 영향을 받아 자동으로 흰색 계열로 보입니다.
                Text("테스트 가이드", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("1) 스마트폰을 청취 위치(귀 높이)에 두세요.")
                Text("2) 주변을 조용히 해주세요. 냉장고/에어컨/선풍기 소음이 크면 결과가 나빠집니다.")
                Text("3) 볼륨은 70~80% 권장. 클리핑 의심 시 낮춰 재시도하세요.")
                Text("4) [테스트 시작]을 누르면 폰 스피커로 스윕을 재생하고 마이크로 동시에 녹음합니다.")
                Text("5) 완료 후 레벨(peak/RMS)과 WAV 파일 경로를 제공합니다.")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = { nav.navigate("KeepTest") }) { Text("테스트 시작") }
            }
        }
    }
}