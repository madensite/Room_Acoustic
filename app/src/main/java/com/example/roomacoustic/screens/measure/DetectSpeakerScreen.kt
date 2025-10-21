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
import com.example.roomacoustic.tracker.SimpleTracker
import com.example.roomacoustic.viewmodel.RoomViewModel
import com.example.roomacoustic.yolo.BoundingBox
import com.example.roomacoustic.yolo.Constants
import com.example.roomacoustic.yolo.Detector
import com.example.roomacoustic.yolo.OverlayView
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import io.github.sceneview.ar.ARSceneView
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import com.google.ar.core.Pose
import com.google.ar.core.Coordinates2d
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.acos
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.example.roomacoustic.util.YuvToRgbConverter
import kotlin.math.roundToInt

import com.example.roomacoustic.model.AxisFrame
import com.example.roomacoustic.model.Measure3DResult
import com.example.roomacoustic.model.Vec3 as MVec3
import com.google.ar.core.TrackingState



private enum class Phase { DETECT, MAP }

// 탐지 결과에서 MAP 단계로 넘길 최소 정보
private data class Detection2D(
    val cxNorm: Float,    // 중심 x (0..1)
    val cyNorm: Float,    // 중심 y (0..1)
    val wNorm:  Float     // 박스 폭 / View 폭 (0..1)
)

@Composable
fun DetectSpeakerScreen(nav: NavController, vm: RoomViewModel) {
    val ctx = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val uiScope = rememberCoroutineScope()

    var phase by rememberSaveable { mutableStateOf(Phase.DETECT) }

    var pendingDetections by remember { mutableStateOf<List<Detection2D>>(emptyList()) }

    /* ─────────────────────────────────────────────
       PHASE 1: CameraX + Detector (MeasureScreen 로직)
       ───────────────────────────────────────────── */
    if (phase == Phase.DETECT) {
        val previewView = remember { PreviewView(ctx) }
        var overlayView by remember { mutableStateOf<OverlayView?>(null) }

        val camExecutor = remember { Executors.newSingleThreadExecutor() }
        var detector by remember { mutableStateOf<Detector?>(null) }

        // ★ 추가: 안전 종료용 참조 & 플래그
        var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
        var analysisUseCase by remember { mutableStateOf<ImageAnalysis?>(null) }
        val analyzing = remember { AtomicBoolean(false) }

        var processing by remember { mutableStateOf(true) }
        var bmpReuse by remember { mutableStateOf<Bitmap?>(null) }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .wrapContentSize(Alignment.Center)
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
            )
            AndroidView(
                factory = { c -> OverlayView(c) },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .zIndex(1f)
            ) { overlayView = it }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(12.dp)
                    .zIndex(2f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "스피커를 화면 가운데로 맞춰주세요",
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = {
                    uiScope.launch {
                        // 안전 종료 시퀀스
                        processing = false
                        analysisUseCase?.clearAnalyzer()
                        try { cameraProvider?.unbindAll() } catch (_: Exception) {}
                        withContext(Dispatchers.IO) {
                            camExecutor.shutdown()
                            camExecutor.awaitTermination(1500, TimeUnit.MILLISECONDS)
                        }
                        while (analyzing.get()) delay(10)
                        try { detector?.close() } catch (_: Exception) {}
                        phase = Phase.MAP
                    }
                }) { Text("AR 매핑으로 진행") }
            }
        }

        // Detector 초기화
        LaunchedEffect(Unit) {
            detector = Detector(
                ctx,
                Constants.MODEL_PATH,
                Constants.LABELS_PATH,
                detectorListener = object : Detector.DetectorListener {
                    override fun onEmptyDetect() {
                        overlayView?.clear()
                        vm.setSpeakerBoxes(emptyList())
                    }
                    override fun onDetect(boxes: List<BoundingBox>, inferenceTime: Long) {
                        overlayView?.setResults(boxes)
                        vm.setSpeakerBoxes(boxes.map { RectF(it.x1, it.y1, it.x2, it.y2) })

                        overlayView?.let { ov ->
                            val w = ov.width.coerceAtLeast(1)
                            val h = ov.height.coerceAtLeast(1)

                            // ① IoU로 중복 제거
                            val kept = dedupBoxesByIoU(boxes, iouThresh = 0.6f)

                            // ② 최대 2개까지만 사용(스테레오 가정)
                            val top2 = kept.take(2)

                            pendingDetections = top2.map { bb ->
                                val cx = ((bb.x1 + bb.x2) * 0.5f / w).coerceIn(0f, 1f)
                                val cy = ((bb.y1 + bb.y2) * 0.5f / h).coerceIn(0f, 1f)
                                val bw = (kotlin.math.abs(bb.x2 - bb.x1) / w).coerceIn(0f, 1f)
                                Detection2D(cx, cy, bw)
                            }
                        }
                    }
                },
                message = { /* log */ }
            ).apply {
                restart(useGpu = true)
                warmUp()
            }
        }

        // CameraX 세팅
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
                        if (!processing) { px.close(); return@setAnalyzer }
                        analyzing.set(true)
                        try {
                            if (bmpReuse == null || bmpReuse!!.width != px.width || bmpReuse!!.height != px.height) {
                                bmpReuse = Bitmap.createBitmap(px.width, px.height, Bitmap.Config.ARGB_8888)
                            }
                            val bmp = bmpReuse!!
                            bmp.copyPixelsFromBuffer(px.planes[0].buffer)

                            val rotated = if (px.imageInfo.rotationDegrees != 0) {
                                val m = Matrix().apply { postRotate(px.imageInfo.rotationDegrees.toFloat()) }
                                Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
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

    /* ─────────────── PHASE 2: MAP (AR frame re-detect) ─────────────── */
    if (phase == Phase.MAP) {
        val sceneView = remember {
            ARSceneView(context = ctx, sharedLifecycle = lifecycleOwner.lifecycle).apply {
                configureSession { _, cfg ->
                    try { cfg.depthMode = Config.DepthMode.AUTOMATIC } catch (_: Throwable) {}
                    cfg.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                }
            }
        }

        var viewW by remember { mutableIntStateOf(0) }
        var viewH by remember { mutableIntStateOf(0) }

        // ✅ “후보별 1회만 성공”을 위한 Set
        // 후보 인덱스(0..n-1) 중 이미 성공한 것들
        var solvedCandidates by remember { mutableStateOf(setOf<Int>()) }
        // UI에 바로 쓰는 카운트
        var solvedCount by remember { mutableIntStateOf(0) }

        var isRunning by remember { mutableStateOf(true) }
        var info by remember { mutableStateOf("AR 프레임에서 재탐지 중… 기기를 천천히 움직여 주세요") }
        var errorMsg by remember { mutableStateOf<String?>(null) }

        var speakerWidthCm by remember { mutableStateOf(18f) }
        var basisSet by remember { mutableStateOf(false) } // 기준 프레임 1회만 세팅

        Box(
            Modifier.fillMaxSize().background(Color.Black).wrapContentSize(Alignment.Center)
        ) {
            AndroidView(
                factory = { sceneView },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .onSizeChanged { viewW = it.width; viewH = it.height }
            )

            Column(
                Modifier.align(Alignment.BottomCenter).padding(12.dp).zIndex(2f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    if (isRunning) "$info  (완료: $solvedCount)"
                    else "완료: $solvedCount 개",
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("스피커 폭(cm): ", color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = "%.1f".format(speakerWidthCm),
                        onValueChange = { s -> s.toFloatOrNull()?.let { if (it in 3f..80f) speakerWidthCm = it } },
                        singleLine = true,
                        modifier = Modifier.width(120.dp)
                    )
                }
                if (errorMsg != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(errorMsg!!, color = Color(0xFFFFB4A9))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !isRunning,
                        onClick = {
                            val detected = vm.speakers.isNotEmpty()
                            nav.navigate("Render?detected=$detected") {
                                popUpTo("MeasureGraph") { inclusive = false }
                            }
                        }
                    ) { Text("렌더로 이동") }
                    OutlinedButton(onClick = {
                        try { sceneView.onSessionUpdated = null } catch (_: Throwable) {}
                        phase = Phase.DETECT
                    }) { Text("다시 탐지") }
                }
            }
        }

        // AR 프레임 루프
        LaunchedEffect(sceneView, viewW, viewH, pendingDetections) {
            if (viewW <= 0 || viewH <= 0) return@LaunchedEffect
            if (pendingDetections.isEmpty()) {
                isRunning = false
                info = "탐지된 스피커 후보가 없습니다."
                return@LaunchedEffect
            }

            sceneView.onSessionUpdated = upd@ { _: Session, frame: Frame ->
                if (!isRunning) return@upd

                if (frame.camera.trackingState != com.google.ar.core.TrackingState.TRACKING) {
                    info = "카메라 추적 중…(패턴/텍스처 보이는 곳을 향해 천천히 이동)"
                    return@upd
                }

                // 기준 좌표계 1회 세팅 (카메라 pose 기반)
                if (!basisSet && vm.measure3DResult.value == null) {
                    val pose = frame.camera.pose
                    val origin = MVec3(pose.tx(), pose.ty(), pose.tz())
                    val vxV3 = rotateByPose(pose, V3(1f, 0f, 0f)).normalize()
                    val vyV3 = rotateByPose(pose, V3(0f, 1f, 0f)).normalize()
                    val vzV3 = rotateByPose(pose, V3(0f, 0f,-1f)).normalize()
                    vm.setMeasure3DResult(
                        Measure3DResult(
                            frame = AxisFrame(
                                origin = origin,
                                vx = MVec3(vxV3.x, vxV3.y, vxV3.z),
                                vy = MVec3(vyV3.x, vyV3.y, vyV3.z),
                                vz = MVec3(vzV3.x, vzV3.y, vzV3.z)
                            ),
                            width = 0f, depth = 0f, height = 0f
                        )
                    )
                    basisSet = true
                }

                try {
                    val nowTs = System.nanoTime()
                    val usedIds = hashSetOf<Int>() // 같은 프레임 내 id 중복 방지

                    // 후보별 1회만 성공 처리
                    pendingDetections.forEachIndexed { i, det ->
                        if (i in solvedCandidates) return@forEachIndexed // 이미 끝난 후보 스킵

                        val sx = (det.cxNorm * viewW).coerceIn(0f, viewW.toFloat())
                        val sy = (det.cyNorm * viewH).coerceIn(0f, viewH.toFloat())

                        val depthPx = viewToDepthPx(frame, sx, sy)
                        val depthMeters = if (depthPx != null) {
                            val (dx, dy) = depthPx
                            try { frame.acquireDepthImage16Bits() } catch (_: Throwable) { null }?.use { dimg ->
                                sampleDepthWindowMeters(
                                    depthImage = dimg,
                                    dx = dx, dy = dy,
                                    winRadius = 3,
                                    validMin = 0.2f,
                                    validMax = 10f
                                )
                            }
                        } else null

                        fun commit(world: FloatArray) {
                            val id = SimpleTracker.assignId(world)
                            if (usedIds.add(id)) {
                                if (i !in solvedCandidates) {
                                    solvedCandidates = solvedCandidates + i   // 새 Set로 교체 → Compose 감지
                                    solvedCount += 1
                                    vm.upsertSpeaker(id, world, nowTs)
                                    vm.pruneSpeakers(nowTs)
                                }
                            }
                        }

                        if (depthMeters != null) {
                            val imgPts = FloatArray(2)
                            frame.transformCoordinates2d(
                                Coordinates2d.VIEW,
                                floatArrayOf(sx, sy),
                                Coordinates2d.IMAGE_PIXELS,
                                imgPts
                            )
                            val world = backprojectToWorld(frame, imgPts[0], imgPts[1], depthMeters)
                            commit(world)
                        } else {
                            val hp = hitTestOrDepth(frame, sx, sy)
                            if (hp != null) commit(floatArrayOf(hp.x, hp.y, hp.z))
                        }
                    }

                    if (solvedCandidates.size >= pendingDetections.size) {
                        isRunning = false
                        info = "완료: ${solvedCandidates.size} 개"
                        try { sceneView.onSessionUpdated = null } catch (_: Throwable) {}
                    } else {
                        info = "깊이 샘플링 중… (완료: ${solvedCandidates.size} / 총 ${pendingDetections.size})"
                    }
                } catch (t: Throwable) {
                    errorMsg = "깊이 역투영 오류: ${t.message ?: "unknown"}"
                    isRunning = false
                    try { sceneView.onSessionUpdated = null } catch (_: Throwable) {}
                }
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                try { sceneView.onSessionUpdated = null } catch (_: Throwable) {}
                sceneView.destroy()
            }
        }
    }

}

/* -----------------------------------------------------------
 * Depth 샘플러: VIEW px → (가능하면) DEPTH px 변환, 윈도우 중앙값(m)
 * ----------------------------------------------------------- */
private fun sampleDepthMeters(
    frame: Frame,
    viewX: Float,
    viewY: Float,
    winRadius: Int = 3,          // 2~4 권장
    validMin: Float = 0.2f,      // m
    validMax: Float = 10f        // m
): Float? {
    val depthImage = try { frame.acquireDepthImage16Bits() } catch (_: Throwable) { null } ?: return null
    depthImage.use { img ->
        val dw = img.width
        val dh = img.height
        if (dw <= 0 || dh <= 0) return null

        // 1) VIEW → IMAGE_PIXELS
        val inPts  = floatArrayOf(viewX, viewY)
        val imgPts = FloatArray(2)
        return try {
            frame.transformCoordinates2d(
                Coordinates2d.VIEW,
                inPts,
                Coordinates2d.IMAGE_PIXELS,
                imgPts
            )
            // 2) IMAGE_PIXELS → DEPTH 픽셀 좌표 (해상도 스케일링)
            val intr = frame.camera.imageIntrinsics
            val dims = intr.imageDimensions
            val imgW = dims.getOrNull(0)?.coerceAtLeast(1) ?: return null
            val imgH = dims.getOrNull(1)?.coerceAtLeast(1) ?: return null

            val dx = (imgPts[0] / imgW) * dw
            val dy = (imgPts[1] / imgH) * dh

            // 3) 윈도우 중앙값(m)
            sampleDepthWindow(img, dx, dy, winRadius, validMin, validMax)
        } catch (_: Throwable) {
            null
        }
    }
}


private fun sampleDepthWindow(
    img: android.media.Image,
    fx: Float,
    fy: Float,
    R: Int,
    validMin: Float,
    validMax: Float
): Float? {
    val dw = img.width
    val dh = img.height
    if (dw <= 0 || dh <= 0) return null

    val plane = img.planes[0]
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride // 보통 2
    val bb: ByteBuffer = plane.buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)

    fun readDepthMeters(xi: Int, yi: Int): Float {
        if (xi < 0 || yi < 0 || xi >= dw || yi >= dh) return 0f
        val index = yi * rowStride + xi * pixelStride
        if (index < 0 || index + 1 >= bb.limit()) return 0f
        val lo = bb.get(index).toInt() and 0xFF
        val hi = bb.get(index + 1).toInt() and 0xFF
        val mm = (hi shl 8) or lo // little-endian
        return mm / 1000f
    }

    val cx = fx.toInt()
    val cy = fy.toInt()
    val vals = ArrayList<Float>( (2*R+1)*(2*R+1) )
    for (y in cy - R .. cy + R) {
        for (x in cx - R .. cx + R) {
            val m = readDepthMeters(x, y)
            if (m in validMin..validMax) vals.add(m)
        }
    }
    if (vals.size < max(5, ((2*R+1)*(2*R+1))/4)) return null
    vals.sort()
    return vals[vals.size / 2]
}

// ───────── 벡터/선형대수 유틸 ─────────
private data class V3(var x: Float, var y: Float, var z: Float)
private operator fun V3.plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
private operator fun V3.minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
private operator fun V3.times(s: Float) = V3(x * s, y * s, z * s)
private fun V3.dot(o: V3) = x * o.x + y * o.y + z * o.z
private fun V3.norm() = sqrt(this.dot(this))
private fun V3.normalize(): V3 {
    val n = norm()
    return if (n > 1e-8f) this * (1f / n) else this
}

private data class Ray(val ro: V3, val rd: V3) // origin, direction(unit)

// VIEW px -> IMAGE_PIXELS px
private fun viewToImagePixels(frame: Frame, sx: Float, sy: Float): FloatArray {
    val inPts = floatArrayOf(sx, sy)
    val outPts = FloatArray(2)
    frame.transformCoordinates2d(Coordinates2d.VIEW, inPts, Coordinates2d.IMAGE_PIXELS, outPts)
    return outPts
}

// 카메라 내부파라미터
private data class Intr(val fx: Float, val fy: Float, val cx: Float, val cy: Float)
private fun getIntrinsics(frame: Frame): Intr {
    val intr = frame.camera.imageIntrinsics
    val f = intr.focalLength
    val c = intr.principalPoint
    return Intr(fx = f[0], fy = f[1], cx = c[0], cy = c[1])
}

// u,v 픽셀 → 카메라좌표계 방향 벡터 (ARCore는 -Z가 전방)
private fun camRayDirFromPixel(u: Float, v: Float, K: Intr): V3 {
    val x = (u - K.cx) / K.fx
    val y = (v - K.cy) / K.fy
    return V3(x, y, -1f).normalize()
}

private fun rotateByPose(pose: Pose, v: V3): V3 {
    val x = pose.qx(); val y = pose.qy(); val z = pose.qz(); val w = pose.qw()
    val xx = x + x; val yy = y + y; val zz = z + z
    val wx = w * xx; val wy = w * yy; val wz = w * zz
    val xx2 = x * xx; val yy2 = y * yy; val zz2 = z * zz
    val xy2 = x * yy; val xz2 = x * zz; val yz2 = y * zz
    val m00 = 1 - (yy2 + zz2)
    val m01 = xy2 - wz
    val m02 = xz2 + wy
    val m10 = xy2 + wz
    val m11 = 1 - (xx2 + zz2)
    val m12 = yz2 - wx
    val m20 = xz2 - wy
    val m21 = yz2 + wx
    val m22 = 1 - (xx2 + yy2)
    return V3(
        m00 * v.x + m01 * v.y + m02 * v.z,
        m10 * v.x + m11 * v.y + m12 * v.z,
        m20 * v.x + m21 * v.y + m22 * v.z
    )
}

private fun toV3(p: Pose) = V3(p.tx(), p.ty(), p.tz())

// AR 프레임 + 뷰픽셀 → 월드 레이
private fun buildWorldRay(frame: Frame, sx: Float, sy: Float): Ray? {
    val uv = viewToImagePixels(frame, sx, sy)
    val K = getIntrinsics(frame)
    val dirCam = camRayDirFromPixel(uv[0], uv[1], K) // 카메라 좌표계 방향
    val pose = frame.camera.pose
    val ro = toV3(pose)                    // 카메라 원점(월드)
    val rd = rotateByPose(pose, dirCam)    // 월드 방향
    return Ray(ro, rd.normalize())
}

// ───────────────── DEPTH 샘플링 & 역투영 유틸 ─────────────────

// DEPTH16 이미지에서 (dx,dy) 주변 winRadius 창의 중앙값(m)을 반환
private fun sampleDepthWindowMeters(
    depthImage: android.media.Image,
    dx: Float, dy: Float,
    winRadius: Int = 3,
    validMin: Float = 0.2f,
    validMax: Float = 10f
): Float? {
    val w = depthImage.width
    val h = depthImage.height
    val cx = dx.roundToInt().coerceIn(0, w - 1)
    val cy = dy.roundToInt().coerceIn(0, h - 1)

    val plane = depthImage.planes[0]
    val buf = plane.buffer.duplicate().order(java.nio.ByteOrder.LITTLE_ENDIAN)

    val values = ArrayList<Float>( (2*winRadius+1)*(2*winRadius+1) )
    val rowStride = plane.rowStride
    val pixelStride = plane.pixelStride // DEPTH16 보통 2

    for (iy in (cy - winRadius).coerceAtLeast(0)..(cy + winRadius).coerceAtMost(h - 1)) {
        val rowOff = iy * rowStride
        for (ix in (cx - winRadius).coerceAtLeast(0)..(cx + winRadius).coerceAtMost(w - 1)) {
            val off = rowOff + ix * pixelStride
            if (off + 1 < buf.capacity()) {
                val mm = buf.getShort(off).toInt() and 0xFFFF // unsigned 16-bit mm
                if (mm > 0) {
                    val m = mm * 0.001f
                    if (m in validMin..validMax) values.add(m)
                }
            }
        }
    }
    if (values.isEmpty()) return null
    values.sort()
    return values[values.size / 2]
}

// VIEW(px) → IMAGE_PIXELS(px) → DEPTH(px) 좌표로 변환 (DEPTH 좌표 반환)
private fun viewToDepthPx(frame: Frame, viewX: Float, viewY: Float): Pair<Float, Float>? {
    // VIEW → IMAGE_PIXELS
    val inPts = floatArrayOf(viewX, viewY)
    val imgPts = FloatArray(2)
    frame.transformCoordinates2d(Coordinates2d.VIEW, inPts, Coordinates2d.IMAGE_PIXELS, imgPts)

    // IMAGE_PIXELS → DEPTH 스케일(해상도 스케일링)
    val imgDims = frame.camera.imageIntrinsics.imageDimensions
    val imgW = imgDims[0].coerceAtLeast(1)
    val imgH = imgDims[1].coerceAtLeast(1)

    val depth = try { frame.acquireDepthImage16Bits() } catch (_: Throwable) { null } ?: return null
    depth.use { dimg ->
        val dx = imgPts[0] * dimg.width / imgW
        val dy = imgPts[1] * dimg.height / imgH
        return dx to dy
    }
}

// (u,v, Z[m]) + 내부파라미터 + 카메라 pose → 월드 좌표
private fun backprojectToWorld(
    frame: Frame,
    uImg: Float, vImg: Float,
    depthMeters: Float
): FloatArray {
    val K = getIntrinsics(frame) // (fx,fy,cx,cy) : IMAGE_PIXELS 좌표계
    // 카메라 좌표계 (ARCore는 -Z 전방)
    val Xc = (uImg - K.cx) / K.fx * depthMeters
    val Yc = (vImg - K.cy) / K.fy * depthMeters
    val Zc = -depthMeters
    val cam = floatArrayOf(Xc, Yc, Zc)
    val world = frame.camera.pose.transformPoint(cam) // 카메라좌표 → 월드
    return world
}

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
    // ★ cnf(신뢰도) 기준으로 내림차순 정렬
    for (b in src.sortedByDescending { it.cnf }) {
        if (out.none { iou(it, b) >= iouThresh }) out += b
    }
    return out
}


/* ───────── Triangulator (현재는 사용 안 함 / 보관) ───────── */
private class Triangulator(
    private val minAngleDeg: Float = 3f,
    private val minBaselineM: Float = 0.08f,
    private val maxRays: Int = 30
) {
    private val origins = mutableListOf<V3>()
    private val dirs = mutableListOf<V3>() // unit

    private var bestPoint: V3? = null
    private var bestCond: Float = Float.POSITIVE_INFINITY

    fun addRay(ro: V3, rd: V3) {
        if (origins.size >= maxRays) return
        origins += ro
        dirs += rd.normalize()
    }

    fun solveIfReady(): V3? {
        if (origins.size < 2) return null
        var okBaseline = false
        var okAngle = false
        run {
            val ro0 = origins.first()
            val ro1 = origins.last()
            val baseline = (ro1 - ro0).norm()
            if (baseline >= minBaselineM) okBaseline = true

            val d0 = dirs.first()
            val d1 = dirs.last()
            val cos = (d0.dot(d1) / (d0.norm() * d1.norm())).coerceIn(-1f, 1f)
            val ang = Math.toDegrees(acos(cos).toDouble()).toFloat()
            if (ang >= minAngleDeg) okAngle = true
        }
        if (!okBaseline || !okAngle) return null

        val p = solveLeastSquares() ?: return null

        val cond = conditionLike()
        if (cond < bestCond) {
            bestCond = cond
            bestPoint = p
        }
        return bestPoint
    }

    fun best(): V3? = bestPoint

    private fun conditionLike(): Float {
        var minCos = 1f
        for (i in 0 until dirs.size) for (j in i+1 until dirs.size) {
            val c = (dirs[i].dot(dirs[j]) / (dirs[i].norm()*dirs[j].norm())).coerceIn(-1f, 1f)
            minCos = min(minCos, kotlin.math.abs(c))
        }
        return minCos
    }

    private fun solveLeastSquares(): V3? {
        var a00 = 0f; var a01 = 0f; var a02 = 0f
        var a11 = 0f; var a12 = 0f; var a22 = 0f
        var bx = 0f; var by = 0f; var bz = 0f

        for (k in dirs.indices) {
            val d = dirs[k]
            val o = origins[k]
            val dx = d.x; val dy = d.y; val dz = d.z

            val i00 = 1f - dx*dx
            val i01 = -dx*dy
            val i02 = -dx*dz
            val i11 = 1f - dy*dy
            val i12 = -dy*dz
            val i22 = 1f - dz*dz

            a00 += i00; a01 += i01; a02 += i02
            a11 += i11; a12 += i12
            a22 += i22

            bx += i00*o.x + i01*o.y + i02*o.z
            by += i01*o.x + i11*o.y + i12*o.z
            bz += i02*o.x + i12*o.y + i22*o.z
        }

        val det =
            a00*(a11*a22 - a12*a12) -
                    a01*(a01*a22 - a12*a02) +
                    a02*(a01*a12 - a11*a02)

        if (kotlin.math.abs(det) < 1e-6f) return null

        val c00 =  (a11*a22 - a12*a12)
        val c01 = -(a01*a22 - a12*a02)
        val c02 =  (a01*a12 - a11*a02)
        val c10 = c01
        val c11 =  (a00*a22 - a02*a02)
        val c12 = -(a00*a12 - a01*a02)
        val c20 = c02
        val c21 = c12
        val c22 =  (a00*a11 - a01*a01)

        val px = (c00*bx + c01*by + c02*bz) / det
        val py = (c10*bx + c11*by + c12*bz) / det
        val pz = (c20*bx + c21*by + c22*bz) / det
        return V3(px, py, pz)
    }

}
