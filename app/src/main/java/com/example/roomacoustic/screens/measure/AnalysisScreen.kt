// path: app/src/main/java/com/example/roomacoustic/screens/measure/AnalysisScreen.kt
package com.example.roomacoustic.screens.measure

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.roomacoustic.util.readMono16Wav
import com.example.roomacoustic.util.computeSpectrogram
import com.example.roomacoustic.util.spectrogramToBitmap
import com.example.roomacoustic.util.playWav
import com.example.roomacoustic.viewmodel.RoomViewModel
import com.example.roomacoustic.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    nav: NavController,
    vm: RoomViewModel
) {
    val roomId = vm.currentRoomId.collectAsState().value
    val latestRec = vm.latestRecording.collectAsState().value
    val latestMeasure = vm.latestMeasure.collectAsState().value
    val savedSpeakers = vm.savedSpeakers.collectAsState().value

    var specBmp by remember { mutableStateOf<Bitmap?>(null) }
    var sr by remember { mutableIntStateOf(0) }
    var sampleCount by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val wavPath = latestRec?.filePath
    LaunchedEffect(wavPath) {
        specBmp = null
        loadError = null
        sr = 0
        sampleCount = 0

        if (wavPath.isNullOrBlank()) return@LaunchedEffect
        runCatching {
            val (pcm, sampleRate) = readMono16Wav(wavPath)
            sr = sampleRate
            sampleCount = pcm.size
            val spec = computeSpectrogram(pcm, sampleRate)
            specBmp = spectrogramToBitmap(spec)
        }.onFailure { e ->
            loadError = e.message ?: "WAV 로드 실패"
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("녹음 분석", fontWeight = FontWeight.SemiBold) },
                // navigationIcon를 비워두면(생략) 뒤로가기 아이콘이 기본으로 안 보입니다.
                // 문제가 있었던 null 전달은 아예 하지 않습니다.
                actions = {}
            )
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 헤더 라인: 방 정보 + 뒤로/방 선택 버튼
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (roomId != null) "Room #$roomId" else "Room 미선택",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { nav.popBackStack() }) {
                            Text("뒤로")
                        }
                        // ✅ 방 선택으로: 측정 서브그래프 pop 후 Room으로 진입
                        TextButton(onClick = {
                            nav.navigate(Screen.Room.route) {
                                popUpTo(Screen.MeasureGraph.route) {
                                    inclusive = true
                                }
                                launchSingleTop = true
                            }
                        }) {
                            Text("방 선택으로")
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))

                // 측정값 + 스피커 개수
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = latestMeasure?.let {
                            "W ${"%.2f".format(it.width)} · D ${"%.2f".format(it.depth)} · H ${"%.2f".format(it.height)} (m)"
                        } ?: "측정값 없음",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text("스피커: ${savedSpeakers.size}개", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))

                // 본문: 녹음 파일 & 스펙트로그램
                if (wavPath.isNullOrBlank()) {
                    Text("저장된 녹음이 없습니다.", color = MaterialTheme.colorScheme.error)
                } else {
                    Text("파일: ${wavPath.substringAfterLast('/')}")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { playWav(wavPath) }) { Text("녹음 파일 재생") }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (loadError != null) {
                        Text("오류: $loadError", color = MaterialTheme.colorScheme.error)
                    } else {
                        specBmp?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Spectrogram",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp)
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("샘플레이트: $sr Hz · 샘플수: $sampleCount")
                            Spacer(Modifier.height(8.dp))

                            // latestRec가 null일 수 있으니 안전 처리
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Peak: ${"%.1f".format(latestRec?.peakDbfs ?: 0f)} dBFS")
                                Text("RMS: ${"%.1f".format(latestRec?.rmsDbfs ?: 0f)} dBFS")
                                Text("길이: ${"%.2f".format(latestRec?.durationSec ?: 0f)} s")
                            }
                        } ?: run {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
