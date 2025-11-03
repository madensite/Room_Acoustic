package com.example.roomacoustic.screens.result

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.roomacoustic.navigation.Screen
import com.example.roomacoustic.util.*
import com.example.roomacoustic.viewmodel.RoomViewModel
import com.example.roomacoustic.util.AcousticMetrics
import com.example.roomacoustic.util.computeAcousticMetrics
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultAnalysisScreen(
    nav: NavController,
    vm: RoomViewModel,
    roomId: Int
) {
    val latestRec = vm.latestRecording.collectAsState().value
    val latestMeasure = vm.latestMeasure.collectAsState().value
    val savedSpeakers = vm.savedSpeakers.collectAsState().value

    var specBmp by remember { mutableStateOf<Bitmap?>(null) }
    var sr by remember { mutableIntStateOf(0) }
    var sampleCount by remember { mutableIntStateOf(0) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var metrics by remember { mutableStateOf<AcousticMetrics?>(null) }

    val wavPath = latestRec?.filePath
    LaunchedEffect(wavPath) {
        specBmp = null; loadError = null; metrics = null; sr = 0; sampleCount = 0
        if (wavPath.isNullOrBlank()) return@LaunchedEffect
        runCatching {
            val (pcm, sampleRate) = readMono16Wav(wavPath)
            sr = sampleRate; sampleCount = pcm.size
            metrics = computeAcousticMetrics(pcm, sampleRate)
            val spec = computeSpectrogram(pcm, sampleRate)
            specBmp = spectrogramToBitmap(spec)
        }.onFailure { e -> loadError = e.message ?: "WAV 로드 실패" }
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("녹음 분석", fontWeight = FontWeight.SemiBold) },

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
    ) { inner ->
        Column(
            modifier = Modifier.padding(inner).fillMaxSize().padding(16.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Room #$roomId")
                TextButton(onClick = { nav.popBackStack() }) { Text("뒤로") }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = latestMeasure?.let {
                        "W %.2f · D %.2f · H %.2f (m)".format(it.width, it.depth, it.height)
                    } ?: "측정값 없음"
                )
                Text("스피커: ${savedSpeakers.size}개")
            }
            Spacer(Modifier.height(12.dp))

            if (wavPath.isNullOrBlank()) {
                Text("저장된 녹음이 없습니다.", color = MaterialTheme.colorScheme.error)
            } else {
                if (loadError != null) {
                    Text("오류: $loadError", color = MaterialTheme.colorScheme.error)
                } else {
                    specBmp?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Spectrogram",
                            modifier = Modifier.fillMaxWidth().height(240.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text("샘플레이트: $sr Hz · 샘플수: $sampleCount")
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Peak: ${"%.1f".format(latestRec?.peakDbfs ?: 0f)} dBFS")
                            Text("RMS: ${"%.1f".format(latestRec?.rmsDbfs ?: 0f)} dBFS")
                            Text("길이: ${"%.2f".format(latestRec?.durationSec ?: 0f)} s")
                        }
                        Spacer(Modifier.height(8.dp))
                        metrics?.let { m ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("RT60: " + (m.rt60Sec?.let { String.format("%.2f s (%s)", it, m.tMethod ?: "") } ?: "계산 불가"))
                                Text("C50: " + (m.c50dB?.let { String.format("%.1f dB", it) } ?: "—"))
                                Text("C80: " + (m.c80dB?.let { String.format("%.1f dB", it) } ?: "—"))
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = { playWav(wavPath) }) { Text("녹음 파일 재생") }
                    } ?: Box(
                        Modifier.fillMaxWidth().height(240.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
            }
        }
    }
}
