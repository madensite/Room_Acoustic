package com.example.roomacoustic.screens.measure

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex

import androidx.navigation.NavController
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.Session
import io.github.sceneview.ar.ARSceneView
import com.example.roomacoustic.model.Vec3
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding

@Composable
fun TwoPointMeasureScreen(
    nav: NavController,
    title: String,                  // 상단 안내 제목
    labelKey: String,               // 저장 라벨("폭","깊이","높이")
    nextRoute: String,              // 다음 화면 route
    onSave: (Float, Vec3, Vec3) -> Unit   // 🔥 거리 + 두 점(World 좌표)
) {
    val ctx = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    val sceneView = remember {
        ARSceneView(context = ctx, sharedLifecycle = lifecycleOwner.lifecycle).apply {
            configureSession { _, cfg ->
                cfg.depthMode        = Config.DepthMode.RAW_DEPTH_ONLY
                cfg.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
            }
        }
    }

    val tapQueue = remember { mutableStateListOf<PointF>() }
    var viewW by remember { mutableIntStateOf(0) }
    var viewH by remember { mutableIntStateOf(0) }

    var firstPoint by remember { mutableStateOf<Vec3?>(null) }
    var secondPoint by remember { mutableStateOf<Vec3?>(null) }

    var hoverPoint by remember { mutableStateOf<Vec3?>(null) }
    var firstScreen by remember { mutableStateOf<Offset?>(null) }
    var hoverScreen by remember { mutableStateOf<Offset?>(null) }

    var showDialog by remember { mutableStateOf(false) }
    var lastDist by remember { mutableFloatStateOf(0f) }

    /* ── UI ── */
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .wrapContentSize(Alignment.Center)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        AndroidView(
            factory = { sceneView },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f/4f)
                .onSizeChanged { viewW = it.width; viewH = it.height }
        )
        // 탭 입력
        Box(
            modifier = Modifier
                .fillMaxWidth().aspectRatio(3f/4f).zIndex(2f)
                .pointerInput(Unit) { detectTapGestures { tapQueue.add(PointF(it.x, it.y)) } }
        )

        // 레티클 + 월드 고정 점 + 선
        Canvas(
            modifier = Modifier.fillMaxWidth().aspectRatio(3f/4f).zIndex(3f)
        ) {
            val center = Offset(size.width/2f, size.height/2f)
            drawLine(Color.White, center.copy(x=center.x-20f), center.copy(x=center.x+20f), 2f)
            drawLine(Color.White, center.copy(y=center.y-20f), center.copy(y=center.y+20f), 2f)
            firstScreen?.let { drawCircle(Color.Cyan, 6.dp.toPx(), it) }
            if (firstScreen != null && hoverScreen != null) {
                drawLine(Color.White, firstScreen!!, hoverScreen!!, 4f)
            }
        }

        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(12.dp).zIndex(4f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text(if (firstPoint==null) "첫 번째 점을 탭하세요" else "두 번째 점을 조준(가운데 십자) 후 탭하세요", color = Color.White)
            if (firstPoint!=null && hoverPoint!=null) {
                Text("현재 거리: ${"%.2f".format(distanceMeters(firstPoint!!, hoverPoint!!))} m", color = Color.White)
            }
        }

        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).zIndex(4f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { firstPoint=null; firstScreen=null; secondPoint = null; hoverPoint=null; hoverScreen=null }) {
                Text("다시 지정")
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = { nav.navigate(nextRoute) }) { Text("건너뛰기") }
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("$labelKey 저장") },
                text  = { Text("측정값: ${"%.2f".format(lastDist)} m") },
                confirmButton = {
                    TextButton(onClick = {
                        val p1 = firstPoint
                        val p2 = secondPoint
                        if (p1 != null && p2 != null) {
                            onSave(lastDist, p1, p2)
                        }
                        showDialog = false
                        nav.navigate(nextRoute)
                    }) { Text("저장 후 다음") }
                },

                dismissButton = { TextButton(onClick = { showDialog = false }) { Text("취소") } }
            )
        }
    }

    /* ── 프레임 루프 ── */
    LaunchedEffect(sceneView) {
        val update: (Session, Frame) -> Unit = { _, frame ->

            if (tapQueue.isNotEmpty()) {
                val taps = tapQueue.toList()
                tapQueue.clear()
                taps.forEach { pt ->
                    val p = hitTestOrDepth(frame, pt.x, pt.y) ?: return@forEach
                    if (firstPoint == null) {
                        // 첫 번째 점
                        firstPoint = p
                        secondPoint = null
                    } else {
                        // 두 번째 점
                        secondPoint = p
                        lastDist = distanceMeters(firstPoint!!, p)
                        showDialog = true
                    }
                }
            }

            val cx = viewW/2f; val cy = viewH/2f
            val h  = hitTestOrDepth(frame, cx, cy)
            hoverPoint  = h
            hoverScreen = h?.let { worldToScreen(frame, it, viewW.toFloat(), viewH.toFloat()) }
            firstScreen = firstPoint?.let { worldToScreen(frame, it, viewW.toFloat(), viewH.toFloat()) }
        }
        sceneView.onSessionUpdated = update
    }

    DisposableEffect(Unit) {
        onDispose { sceneView.onSessionUpdated = null; sceneView.destroy() }
    }
}
