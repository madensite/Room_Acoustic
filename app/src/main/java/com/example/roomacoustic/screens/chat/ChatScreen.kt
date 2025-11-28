package com.example.roomacoustic.screens.chat

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send

import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.roomacoustic.model.ChatMessage
import com.example.roomacoustic.viewmodel.ChatViewModel
import com.example.roomacoustic.viewmodel.RoomViewModel
import com.example.roomacoustic.util.PromptLoader

// 방 정보 모델들
import com.example.roomacoustic.screens.components.RoomSize
import com.example.roomacoustic.model.Vec2
import com.example.roomacoustic.model.Vec3
import com.example.roomacoustic.model.ListeningEval

import androidx.compose.animation.core.*
import androidx.compose.runtime.getValue

// ✅ DB + Repo + Factory import
import com.example.roomacoustic.data.AppDatabase
import com.example.roomacoustic.data.RecordingEntity
import com.example.roomacoustic.repo.ChatRepository
import com.example.roomacoustic.viewmodel.ChatViewModelFactory

import com.example.roomacoustic.util.AcousticMetrics


enum class ChatMode { NEW, CONTINUE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    nav: NavController,
    roomId: Int,
    roomVm: RoomViewModel,
    mode: ChatMode,
) {
    val context = LocalContext.current
    val appCtx = context.applicationContext

    // ✅ 1) DB → Repo 한 번만 생성
    val chatRepository = remember {
        val db = AppDatabase.get(appCtx)
        ChatRepository(db.chatDao())
    }

    // ✅ 2) Repo를 넘겨서 ViewModelFactory 생성
    val chatVm: ChatViewModel = viewModel(
        factory = ChatViewModelFactory(chatRepository)
    )

    // 🔹 프롬프트들 한 번만 로드
    val systemPrompt by remember {
        mutableStateOf(
            PromptLoader.load(context, "prompt/chat_system.txt")
        )
    }
    val bootstrapTemplate by remember {
        mutableStateOf(
            PromptLoader.load(context, "prompt/chat_bootstrap.txt")
        )
    }
    val userWrapperTemplate by remember {
        mutableStateOf(
            PromptLoader.load(context, "prompt/chat_user_wrapper.txt")
        )
    }

    // 🔹 모드에 따른 초기화 (NEW / CONTINUE)
    LaunchedEffect(roomId, mode) {
        when (mode) {
            ChatMode.NEW -> {
                chatVm.startNewConversation(roomId)
            }
            ChatMode.CONTINUE -> {
                chatVm.loadConversation(roomId)
            }
        }
    }


    // 채팅 메시지 상태
    val msgs by chatVm.messages.collectAsState()
    val isLoading by chatVm.isLoading.collectAsState()

    // 🔹 이 방(roomId)에 해당하는 메시지만 따로 뽑기
    val roomMessages = msgs.filter { it.roomId == roomId }

    // 🔹 방 컨텍스트 수집
    val manualRoomSizeMap by roomVm.manualRoomSize.collectAsState()
    val manualSpeakersMap by roomVm.manualSpeakers.collectAsState()
    val manualListenerMap by roomVm.manualListener.collectAsState()

    val roomSize: RoomSize? = manualRoomSizeMap[roomId]
    val speakers: List<Vec3> = manualSpeakersMap[roomId] ?: emptyList()
    val listener: Vec2? = manualListenerMap[roomId]

    val listeningEval: ListeningEval? = roomVm.listeningEvalFor(roomId)
    val latestRecording: RecordingEntity? = roomVm.latestRecording.collectAsState().value
    val acoustic: AcousticMetrics? = roomVm.acousticMetricsFor(roomId)

    val contextJson by remember(
        roomId,
        roomSize,
        speakers,
        listener,
        listeningEval,
        latestRecording,
        acoustic
    ) {
        mutableStateOf(
            buildRoomContextJson(
                roomId = roomId,
                roomSize = roomSize,
                listener = listener,
                speakers = speakers,
                eval = listeningEval,
                recording = latestRecording,
                acoustic = acoustic
            )
        )
    }


    val listState = rememberLazyListState()

    // --- 입력창 + 전송 버튼 ---
    var input by remember { mutableStateOf("") }

    // 🔹 부트스트랩 여부 플래그
    var bootstrapped by remember(roomId, mode) { mutableStateOf(false) }

    // ✅ 방별 메시지 개수를 기준으로 부트스트랩
    LaunchedEffect(roomId, mode, roomSize, listeningEval, roomMessages.size) {
        // 1) 기존 대화 이어가기 모드에서는 절대 부트스트랩 X
        if (mode == ChatMode.CONTINUE) return@LaunchedEffect

        // 2) 이미 한 번 보냈으면 다시 보내지 않음
        if (bootstrapped) return@LaunchedEffect

        // 3) NEW 모드이고, 이 방에 아직 메시지가 없을 때만 실행
        if (roomSize != null && roomMessages.isEmpty()) {
            val firstUserPayload = bootstrapTemplate.replace(
                "{{CONTEXT_JSON}}",
                contextJson
            )

            chatVm.sendPrompt(
                roomId = roomId,
                systemPrompt = systemPrompt,
                visibleUserText = null,   // 사용자 말풍선 X
                payloadForModel = firstUserPayload,
                onError = { /* TODO: 에러 처리 */ }
            )
            bootstrapped = true
        }
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            TopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            // --- 메시지 리스트 ---
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(8.dp)
            ) {
                // 1) GPT "생각 중" ... 말풍선
                if (isLoading) {
                    item { TypingIndicatorBubble() }
                }

                // 2) 사용자 입력 중일 때 ... 말풍선 (오른쪽)
                if (input.isNotBlank()) {
                    item { UserTypingIndicatorBubble() }
                }

                // 3) 실제 메시지들 (이 방의 메시지들만)
                items(roomMessages.asReversed()) { ChatBubble(it) }


            }

            // 새로운 메시지 생기면 맨 아래로 스크롤
            LaunchedEffect(roomMessages.size) {
                if (roomMessages.isNotEmpty()) {
                    listState.animateScrollToItem(0)
                }
            }

            // --- 입력창 + 전송 버튼 ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets
                            .ime
                            .union(WindowInsets.navigationBars)
                            .only(WindowInsetsSides.Bottom)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .border(1.dp, Color.Gray, MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (input.isBlank()) {
                        Text("메시지 입력", color = Color.Gray, fontSize = 14.sp)
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                IconButton(
                    enabled = input.isNotBlank(),
                    onClick = {
                        val payload = userWrapperTemplate
                            .replace("{{CONTEXT_JSON}}", contextJson)
                            .replace("{{USER_MESSAGE}}", input)

                        chatVm.sendPrompt(
                            roomId = roomId,
                            systemPrompt = systemPrompt,
                            visibleUserText = input,
                            payloadForModel = payload,
                            onError = { /* TODO: 에러 처리 */ }
                        )

                        input = ""
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "보내기")
                }
            }
        }
    }
}

/* --------- 단일 메시지 버블 ---------- */
@Composable
private fun ChatBubble(msg: ChatMessage) {
    val isUser = msg.sender == "user"
    val bg = if (isUser) Color(0xFFE0E0E0) else Color(0xFF4CAF50)
    val align = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    val txtColor = if (isUser) Color.Black else Color.White
    val hPadStart = if (isUser) 52.dp else 8.dp
    val hPadEnd = if (isUser) 8.dp else 52.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = hPadStart, end = hPadEnd, top = 6.dp, bottom = 6.dp),
        contentAlignment = align
    ) {
        Text(
            text = msg.content,
            color = txtColor,
            modifier = Modifier
                .background(bg, shape = MaterialTheme.shapes.medium)
                .padding(10.dp)
        )
    }
}

/* --------- 방 컨텍스트 JSON 빌더 ---------- */

private fun buildRoomContextJson(
    roomId: Int,
    roomSize: RoomSize?,
    listener: Vec2?,
    speakers: List<Vec3>,
    eval: ListeningEval?,
    recording: RecordingEntity?,
    acoustic: AcousticMetrics?
): String {
    val sb = StringBuilder()
    sb.append("{\n")
    sb.append("  \"roomId\": $roomId,\n")

    // 방 크기
    if (roomSize != null) {
        sb.append("  \"roomSize\": {\n")
        sb.append("    \"width_m\": ${roomSize.w},\n")
        sb.append("    \"depth_m\": ${roomSize.d},\n")
        sb.append("    \"height_m\": ${roomSize.h}\n")
        sb.append("  },\n")
    } else {
        sb.append("  \"roomSize\": null,\n")
    }

    // 청취자 위치
    if (listener != null) {
        sb.append("  \"listener\": {\n")
        sb.append("    \"x_m_from_left\": ${listener.x},\n")
        sb.append("    \"z_m_from_front\": ${listener.z}\n")
        sb.append("  },\n")
    } else {
        sb.append("  \"listener\": null,\n")
    }

    // 🔹 추가: 녹음 요약 + (있다면) 음향 지표들
    if (recording != null) {
        val escapedPath = recording.filePath.replace("\"", "\\\"")
        sb.append("  \"recordingSummary\": {\n")
        sb.append("    \"filePath\": \"$escapedPath\",\n")
        sb.append("    \"duration_sec\": ${recording.durationSec},\n")
        sb.append("    \"peak_dbfs\": ${recording.peakDbfs},\n")
        sb.append("    \"rms_dbfs\": ${recording.rmsDbfs}\n")
        // 🔸 만약 RecordingEntity에 RT60 / C50 / C80 같은 필드가 이미 있다면,
        //    여기 아래에 형식 맞춰서 추가해 주면 됨 (예시는 아래에 따로 적어둘게)
        sb.append("  },\n")
    } else {
        sb.append("  \"recordingSummary\": null,\n")
    }

    // 🔹 추가: RT60 / C50 / C80 등 녹음 기반 음향 지표
    if (acoustic != null) {
        sb.append("  \"acousticMetrics\": {\n")
        sb.append("    \"rt60_sec\": ${acoustic.rt60Sec ?: "null"},\n")
        sb.append(
            "    \"rt60_method\": " +
                    (acoustic.tMethod?.let { "\"$it\"" } ?: "null") +
                    ",\n"
        )
        sb.append("    \"c50_db\": ${acoustic.c50dB ?: "null"},\n")
        sb.append("    \"c80_db\": ${acoustic.c80dB ?: "null"}\n")
        sb.append("  },\n")
    } else {
        sb.append("  \"acousticMetrics\": null,\n")
    }

    // 스피커 목록
    sb.append("  \"speakers\": [\n")
    speakers.forEachIndexed { idx, s ->
        sb.append(
            "    { \"index\": $idx, \"x_m\": ${s.x}, \"y_m\": ${s.y}, \"z_m\": ${s.z} }"
        )
        if (idx != speakers.lastIndex) sb.append(",")
        sb.append("\n")
    }
    sb.append("  ],\n")

    // 청취 평가
    if (eval != null) {
        sb.append("  \"listeningEval\": {\n")
        sb.append("    \"totalScore\": ${eval.total},\n")
        sb.append("    \"metrics\": [\n")
        eval.metrics.forEachIndexed { i, m ->
            sb.append(
                "      { \"name\": \"${m.name}\", \"score\": ${m.score}, \"detail\": \"${m.detail.replace("\"", "\\\"")}\" }"
            )
            if (i != eval.metrics.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("    ],\n")
        sb.append("    \"notes\": [\n")
        eval.notes.forEachIndexed { i, n ->
            sb.append("      \"${n.replace("\"", "\\\"")}\"")
            if (i != eval.notes.lastIndex) sb.append(",")
            sb.append("\n")
        }
        sb.append("    ]\n")
        sb.append("  }\n")
    } else {
        sb.append("  \"listeningEval\": null\n")
    }

    sb.append("}")
    return sb.toString()
}


/* --------- GPT 타이핑 ... 버블 ---------- */
@Composable
private fun TypingIndicatorBubble() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 52.dp, top = 6.dp, bottom = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = "...",
            color = Color.White.copy(alpha = alpha),
            modifier = Modifier
                .background(Color(0xFF4CAF50), shape = MaterialTheme.shapes.medium)
                .padding(10.dp)
        )
    }
}

/* --------- 사용자 입력 중 ... 버블 ---------- */
@Composable
private fun UserTypingIndicatorBubble() {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 52.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(
            text = "...",
            color = Color.Black.copy(alpha = alpha),
            modifier = Modifier
                .background(Color(0xFFE0E0E0), shape = MaterialTheme.shapes.medium)
                .padding(10.dp)
        )
    }
}
