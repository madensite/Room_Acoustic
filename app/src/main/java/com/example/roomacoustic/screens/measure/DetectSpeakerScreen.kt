package com.example.roomacoustic.screens.measure

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.example.roomacoustic.viewmodel.RoomViewModel
import com.example.roomacoustic.yolo.BoundingBox
import com.example.roomacoustic.yolo.Constants
import com.example.roomacoustic.yolo.Detector
import com.example.roomacoustic.yolo.OverlayView
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.AspectRatio

import com.example.roomacoustic.navigation.Screen
import com.example.roomacoustic.screens.components.RoomSize
import com.example.roomacoustic.model.Vec3
import com.example.roomacoustic.util.inferRoomSizeFromLabels

/**
 * YOLO로 스피커를 탐지한 뒤,
 * 박스의 가로 위치(cxNorm)를 방(W/D/H) 기준 로컬 좌표로 매핑하여
 * manualSpeakers 에 바로 저장하는 버전.
 */

// YOLO 결과를 방 좌표계로 바꾸기 위한 2D 정보
private data class Detection2D(
    val cxNorm: Float,    // 중심 x (0..1, 뷰 가로 기준)
    val cyNorm: Float,    // 중심 y (0..1, 뷰 세로 기준) - 지금은 거의 안씀
    val wNorm:  Float     // 박스 폭 / 뷰 폭 (0..1)       - 필요하면 크기 기반 보정용
)

@Composable
fun DetectSpeakerScreen(
    nav: NavController,
    vm: RoomViewModel
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiScope = rememberCoroutineScope()

    // CameraX / YOLO 상태
    val previewView = remember { PreviewView(ctx) }
    var overlayView by remember { mutableStateOf<OverlayView?>(null) }

    val camExecutor = remember { Executors.newSingleThreadExecutor() }
    var detector by remember { mutableStateOf<Detector?>(null) }

    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var analysisUseCase by remember { mutableStateOf<ImageAnalysis?>(null) }
    val analyzing = remember { AtomicBoolean(false) }

    var processing by remember { mutableStateOf(true) }
    var bmpReuse by remember { mutableStateOf<Bitmap?>(null) }

    // YOLO → 방 좌표계 매핑용 2D 결과
    var pendingDetections by remember { mutableStateOf<List<Detection2D>>(emptyList()) }

    // UI 상태
    var viewW by remember { mutableIntStateOf(0) }
    var viewH by remember { mutableIntStateOf(0) }
    var infoText by rememberSaveable { mutableStateOf("스피커를 화면 가운데에 두고 카메라를 향하게 해 주세요.") }
    var errorText by rememberSaveable { mutableStateOf<String?>(null) }

    val accumulatedSpeakers = remember { mutableStateListOf<Vec3>() }


    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .wrapContentSize(Alignment.Center)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {

        // CameraX 프리뷰
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .onSizeChanged { sz ->
                    viewW = sz.width
                    viewH = sz.height
                }
        )

        // YOLO 박스 오버레이
        AndroidView(
            factory = { c -> OverlayView(c) },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .zIndex(1f)
        ) { overlayView = it }

        // 하단 안내/버튼
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
                .zIndex(2f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                infoText,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            if (errorText != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    errorText!!,
                    color = Color(0xFFFFB4A9),
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ① 이 화면 추가 (누적)
                OutlinedButton(
                    onClick = {
                        val roomId = vm.currentRoomId.value
                        if (roomId == null) {
                            errorText = "현재 방 ID가 없어 스피커를 저장할 수 없습니다."
                            return@OutlinedButton
                        }

                        if (pendingDetections.isEmpty()) {
                            errorText = "현재 화면에서 인식된 스피커가 없습니다."
                            return@OutlinedButton
                        }

                        val labeled = vm.labeledMeasures.value
                        val manualSizeMap = vm.manualRoomSize.value
                        val roomSize = manualSizeMap[roomId] ?: inferRoomSizeFromLabels(labeled)

                        if (roomSize == null) {
                            errorText = "방 크기(W/D/H) 정보가 없어 스피커 위치를 배치할 수 없습니다.\n" +
                                    "먼저 길이 측정 또는 수동 입력으로 방 크기를 지정해 주세요."
                            return@OutlinedButton
                        }

                        val shotLocal = mapDetectionsToRoomLocal(pendingDetections, roomSize)
                        val merged = mergeSpeakers(accumulatedSpeakers, shotLocal)

                        accumulatedSpeakers.clear()
                        accumulatedSpeakers.addAll(merged)

                        infoText = "현재 화면에서 스피커 ${shotLocal.size}개 추가됨. 총 ${accumulatedSpeakers.size}개 수집됨."
                        errorText = null
                    }
                ) {
                    Text("이 화면 추가")
                }

                // ② 전체 초기화
                OutlinedButton(
                    onClick = {
                        accumulatedSpeakers.clear()
                        pendingDetections = emptyList()
                        infoText = "모든 스피커 위치를 초기화했습니다. 처음부터 다시 측정해 주세요."
                        errorText = null
                    }
                ) {
                    Text("전체 초기화")
                }

                // ③ 위치 적용 → Render (카메라 정리 + VM 저장 + 이동)
                Button(
                    onClick = {
                        uiScope.launch {
                            // 1) CameraX / YOLO 정리 (기존 코드 그대로)
                            processing = false
                            analysisUseCase?.clearAnalyzer()
                            try { cameraProvider?.unbindAll() } catch (_: Exception) {}
                            withContext(Dispatchers.IO) {
                                camExecutor.shutdown()
                                camExecutor.awaitTermination(1500, TimeUnit.MILLISECONDS)
                            }
                            while (analyzing.get()) delay(10)
                            try { detector?.close() } catch (_: Exception) {}

                            // 2) 방 크기
                            val roomId = vm.currentRoomId.value
                            if (roomId == null) {
                                errorText = "현재 방 ID가 없어 스피커를 저장할 수 없습니다."
                                return@launch
                            }

                            val labeled = vm.labeledMeasures.value
                            val manualSizeMap = vm.manualRoomSize.value
                            val roomSize = manualSizeMap[roomId] ?: inferRoomSizeFromLabels(labeled)

                            if (roomSize == null) {
                                errorText = "방 크기(W/D/H) 정보가 없어 스피커 위치를 배치할 수 없습니다.\n" +
                                        "먼저 길이 측정 또는 수동 입력으로 방 크기를 지정해 주세요."
                                return@launch
                            }

                            // 3) 마지막 화면 결과도 같이 반영
                            val currentShot = mapDetectionsToRoomLocal(pendingDetections, roomSize)
                            val finalSpeakers =
                                if (accumulatedSpeakers.isEmpty())
                                    currentShot
                                else
                                    mergeSpeakers(accumulatedSpeakers, currentShot)

                            if (finalSpeakers.isEmpty()) {
                                errorText = "저장할 스피커가 없습니다. '이 화면 추가'를 먼저 눌러 주세요."
                                return@launch
                            }

                            // 4) manualSpeakers로 저장
                            vm.setManualSpeakers(roomId, finalSpeakers)

                            // 5) Render로 이동
                            nav.navigate("${Screen.Render.route}?detected=true") {
                                popUpTo(Screen.MeasureGraph.route) { inclusive = false }
                            }
                        }
                    }
                ) {
                    Text("위치 적용 → Render")
                }
            }
        }
    }

    /* ───────────────────────── Detector 초기화 ───────────────────────── */

    LaunchedEffect(Unit) {
        detector = Detector(
            ctx,
            Constants.MODEL_PATH,
            Constants.LABELS_PATH,
            detectorListener = object : Detector.DetectorListener {
                override fun onEmptyDetect() {
                    overlayView?.clear()
                    vm.setSpeakerBoxes(emptyList())
                    pendingDetections = emptyList()
                    infoText = "스피커가 인식되지 않았습니다. 스피커가 잘 보이도록 카메라를 조정해 주세요."
                }

                override fun onDetect(boxes: List<BoundingBox>, inferenceTime: Long) {
                    overlayView?.setResults(boxes)
                    vm.setSpeakerBoxes(boxes.map { RectF(it.x1, it.y1, it.x2, it.y2) })

                    overlayView?.let { ov ->
                        val w = ov.width.coerceAtLeast(1)
                        val h = ov.height.coerceAtLeast(1)

                        val kept = dedupBoxesByIoU(boxes, iouThresh = 0.6f)

                        // 신뢰도 높은 순 → 상한까지 자르고 → 좌우 정렬
                        val sorted = kept
                            .sortedByDescending { it.cnf }
                            .take(8)
                            .sortedBy { it.x1 + it.x2 }

                        pendingDetections = sorted.map { bb ->
                            val cx = ((bb.x1 + bb.x2) * 0.5f / w).coerceIn(0f, 1f)
                            val cy = ((bb.y1 + bb.y2) * 0.5f / h).coerceIn(0f, 1f)
                            val bw = (kotlin.math.abs(bb.x2 - bb.x1) / w).coerceIn(0f, 1f)
                            Detection2D(cx, cy, bw)
                        }

                        val total = boxes.size
                        val using = pendingDetections.size

                        infoText = when (using) {
                            0 -> "스피커가 인식되지 않았습니다. 화면에 스피커가 잘 들어오도록 조정해 주세요."
                            1 -> "스피커 1개 인식됨. '자동 위치 적용'을 눌러 Render로 이동하세요."
                            else -> {
                                if (total > using) {
                                    "스피커 ${total}개 인식됨. 이 중 신뢰도 상위 ${using}개만 사용합니다."
                                } else {
                                    "스피커 ${using}개 인식됨. '자동 위치 적용'을 눌러 Render로 이동하세요."
                                }
                            }
                        }
                    }
                }
            },
            message = { /* 로그 필요 시 사용 */ }
        ).apply {
            restart(useGpu = true)
            warmUp()
        }
    }

    /* ───────────────────────── CameraX 세팅 ───────────────────────── */

    LaunchedEffect(detector) {
        val provider = ProcessCameraProvider.getInstance(ctx).get()
        cameraProvider = provider
        provider.unbindAll()

        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(android.util.Size(320, 240))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .apply {
                setAnalyzer(camExecutor) { px ->
                    if (!processing) {
                        px.close()
                        return@setAnalyzer
                    }
                    analyzing.set(true)
                    try {
                        if (bmpReuse == null ||
                            bmpReuse!!.width != px.width ||
                            bmpReuse!!.height != px.height
                        ) {
                            bmpReuse = Bitmap.createBitmap(
                                px.width,
                                px.height,
                                Bitmap.Config.ARGB_8888
                            )
                        }
                        val bmp = bmpReuse!!
                        bmp.copyPixelsFromBuffer(px.planes[0].buffer)

                        val rotated = if (px.imageInfo.rotationDegrees != 0) {
                            val m = Matrix().apply {
                                postRotate(px.imageInfo.rotationDegrees.toFloat())
                            }
                            Bitmap.createBitmap(
                                bmp, 0, 0, bmp.width, bmp.height, m, true
                            )
                        } else bmp

                        val square = Bitmap.createScaledBitmap(
                            rotated,
                            detector!!.inputSize,
                            detector!!.inputSize,
                            false
                        )

                        detector?.detect(square, rotated.width, rotated.height)
                    } finally {
                        analyzing.set(false)
                        px.close()
                    }
                }
            }

        analysisUseCase = analysis
        provider.bindToLifecycle(
            lifecycleOwner,
            CameraSelector.DEFAULT_BACK_CAMERA,
            preview,
            analysis
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            try { ProcessCameraProvider.getInstance(ctx).get().unbindAll() } catch (_: Exception) {}
            try { detector?.close() } catch (_: Exception) {}
        }
    }
}

/* ───────────────── YOLO 박스 IoU 중복 제거 ───────────────── */

private fun iou(a: BoundingBox, b: BoundingBox): Float {
    val x1 = max(a.x1, b.x1)
    val y1 = max(a.y1, b.y1)
    val x2 = min(a.x2, b.x2)
    val y2 = min(a.y2, b.y2)
    val inter = max(0f, x2 - x1) * max(0f, y2 - y1)
    val areaA = max(0f, a.x2 - a.x1) * max(0f, a.y2 - a.y1)
    val areaB = max(0f, b.x2 - b.x1) * max(0f, b.y2 - b.y1)
    val union = areaA + areaB - inter
    return if (union <= 0f) 0f else inter / union
}

private fun dedupBoxesByIoU(src: List<BoundingBox>, iouThresh: Float): List<BoundingBox> {
    val out = mutableListOf<BoundingBox>()
    for (b in src.sortedByDescending { it.cnf }) {
        if (out.none { iou(it, b) >= iouThresh }) out += b
    }
    return out
}

/* ───────────────── YOLO 2D → Room Local(Vec3) 매핑 ───────────────── */

private fun mapDetectionsToRoomLocal(
    detections: List<Detection2D>,
    room: RoomSize
): List<Vec3> {
    if (detections.isEmpty()) return emptyList()

    // 좌/우 순 정렬 (cxNorm 기준)
    val sorted = detections.sortedBy { it.cxNorm }

    // 양옆 Margin (방 가로의 15%)
    val marginX = room.w * 0.15f
    val usableW = (room.w - 2f * marginX).coerceAtLeast(room.w * 0.3f)

    // 깊이: 일단 전면에서 약 20% 지점에 놓는다 (D * 0.2)
    // 나중에 RoomAnalysis에서 이동 권고가 z 방향까지 보정 가능.
    val baseZ = (room.d * 0.2f).coerceIn(0.1f, room.d - 0.1f)

    // 높이: 귀/스피커 높이 근처(방 높이의 80%)
    val baseY = (room.h * 0.8f).coerceIn(0.3f, room.h - 0.1f)

    return sorted.map { det ->
        val nx = det.cxNorm.coerceIn(0f, 1f)
        val x = (marginX + nx * usableW)
            .coerceIn(0f, room.w)

        Vec3(
            x = x,
            y = baseY,
            z = baseZ
        )
    }
}

private fun mergeSpeakers(
    base: List<Vec3>,
    newly: List<Vec3>,
    minDist: Float = 0.00f  // 10cm 이내면 같은 스피커로 간주
): List<Vec3> {
    if (newly.isEmpty()) return base
    val out = base.toMutableList()

    for (p in newly) {
        val exists = out.any { q ->
            val dx = p.x - q.x
            val dy = p.y - q.y
            val dz = p.z - q.z
            dx*dx + dy*dy + dz*dz < minDist * minDist
        }
        if (!exists) out += p
    }
    return out
}
