package com.example.roomacoustic.screens.measure

import android.Manifest
import android.content.pm.PackageManager
import android.os.Environment
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.roomacoustic.audio.DuplexMeasurer
import com.example.roomacoustic.audio.TestConfig
import com.example.roomacoustic.viewmodel.RoomViewModel
import kotlinx.coroutines.launch
import java.io.File
import com.example.roomacoustic.navigation.Screen
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding

@Composable
fun KeepTestScreen(
    nav: NavController,
    vm: RoomViewModel
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val roomId = vm.currentRoomId.collectAsState().value

    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("대기 중") }
    var peak by remember { mutableStateOf<Float?>(null) }
    var rms by remember { mutableStateOf<Float?>(null) }
    var recPath by remember { mutableStateOf<String?>(null) }
    var playPath by remember { mutableStateOf<String?>(null) }

    fun haveMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    // ✅ Surface로 전체를 감싸서 테마에 맞는 배경색과 글자색을 적용합니다.
    Surface(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        Column(
            // ✅ Modifier.fillMaxSize()는 Surface로 옮겨주고, 여기는 패딩만 남깁니다.
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            Text("실시간 테스트", style = MaterialTheme.typography.titleLarge)
            Text("상태: $msg")
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = !busy,
                    onClick = {
                        if (!haveMicPermission()) {
                            ActivityCompat.requestPermissions(
                                (ctx as? android.app.Activity) ?: return@Button,
                                arrayOf(Manifest.permission.RECORD_AUDIO), 1001
                            )
                            return@Button
                        }
                        scope.launch {
                            try {
                                busy = true
                                msg = "스윕 재생 + 녹음 중…"

                                val outDir: File = ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                                    ?: ctx.cacheDir

                                val cfg = TestConfig(
                                    sampleRate = 48000,
                                    sweepSec = 6.0f,
                                    headSilenceSec = 0.5f,
                                    tailSilenceSec = 0.5f,
                                    volume = 0.9f
                                )

                                val engine = DuplexMeasurer(ctx)
                                val result = engine.runOnce(cfg, outDir)

                                peak = result.peakDbfs
                                rms = result.rmsDbfs
                                recPath = result.recordedWav.absolutePath
                                playPath = result.playedWav.absolutePath
                                msg = "완료"

                                // ✅ 이 방을 '측정됨'으로 표시
                                vm.currentRoomId.value?.let { roomId ->
                                    vm.setMeasure(roomId, true)
                                }

                                // ★ DB 저장: 현재 roomId에 귀속
                                vm.saveRecordingForCurrentRoom(
                                    filePath = result.recordedWav.absolutePath,
                                    peak = result.peakDbfs,
                                    rms = result.rmsDbfs,
                                    duration = result.durationSec
                                )

                            } catch (t: Throwable) {
                                msg = "오류: ${t.message ?: "unknown"}"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("측정 시작") }

                OutlinedButton(enabled = !busy, onClick = { nav.popBackStack() }) { Text("뒤로") }
            }

            Divider()

            if (recPath != null) {
                Text("결과 요약")
                Text("• Peak: ${String.format("%.1f", peak)} dBFS")
                Text("• RMS:  ${String.format("%.1f", rms)} dBFS")
                Spacer(Modifier.height(6.dp))
                Text("파일")
                Text("• 재생 신호: $playPath")
                Text("• 녹음 신호: $recPath")
                Spacer(Modifier.height(12.dp))

                // ★ Analysis 로 이동 (딥링크 금지! 그래프에 등록된 route 그대로)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        enabled = roomId != null && !busy,
                        onClick = {
                            // Screen.Analysis.route = "analysis/{roomId}" 라는 전제
                            val id = roomId ?: return@Button
                            nav.navigate("analysis/$id")
                        }
                    ) { Text("분석으로 이동") }
                }
            }
        }
    }
}

