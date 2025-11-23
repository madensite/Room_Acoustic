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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    nav: NavController,
    roomId: Int,
    roomVm: RoomViewModel,
    chatVm: ChatViewModel = viewModel()
) {
    val context = LocalContext.current

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

    // 채팅 메시지
    val msgs by chatVm.messages.collectAsState()

    // 🔹 방 컨텍스트 수집 (지금 코드 그대로)
    val manualRoomSizeMap by roomVm.manualRoomSize.collectAsState()
    val manualSpeakersMap by roomVm.manualSpeakers.collectAsState()
    val manualListenerMap by roomVm.manualListener.collectAsState()

    val roomSize: RoomSize? = manualRoomSizeMap[roomId]
    val speakers: List<Vec3> = manualSpeakersMap[roomId] ?: emptyList()
    val listener: Vec2? = manualListenerMap[roomId]

    val listeningEval: ListeningEval? = roomVm.listeningEvalFor(roomId)

    val contextJson by remember(roomId, roomSize, speakers, listener, listeningEval) {
        mutableStateOf(
            buildRoomContextJson(
                roomId = roomId,
                roomSize = roomSize,
                listener = listener,
                speakers = speakers,
                eval = listeningEval
            )
        )
    }

    val listState = rememberLazyListState()

    // 🔹 화면 진입 시 1회 자동 질문
    var bootstrapped by remember(roomId) { mutableStateOf(false) }

    LaunchedEffect(roomId, roomSize, listeningEval, msgs.size) {
        if (bootstrapped || msgs.isNotEmpty()) return@LaunchedEffect

        if (roomSize != null) {
            // chat_bootstrap.txt 안의 {{CONTEXT_JSON}} 치환
            val firstUserPayload = bootstrapTemplate.replace(
                "{{CONTEXT_JSON}}",
                contextJson
            )

            chatVm.sendPrompt(
                systemPrompt = systemPrompt,
                visibleUserText = null,          // ✅ 사용자 말풍선 X
                payloadForModel = firstUserPayload,
                appendUser = false,              // ✅ user 메시지로 기록 X
                onError = { /* TODO: 에러 처리 */ }
            )
            bootstrapped = true
        }
    }

    Scaffold(
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
        topBar = {
            SmallTopAppBar(
                title = { Text("") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
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
                items(msgs.asReversed()) { ChatBubble(it) }
            }

            LaunchedEffect(msgs.size) {
                listState.animateScrollToItem(0)
            }

            // --- 입력창 + 전송 버튼 ---
            var input by remember { mutableStateOf("") }

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
                        // chat_user_wrapper.txt에 값 집어넣기
                        val payload = userWrapperTemplate
                            .replace("{{CONTEXT_JSON}}", contextJson)
                            .replace("{{USER_MESSAGE}}", input)

                        chatVm.sendPrompt(
                            systemPrompt = systemPrompt,
                            visibleUserText = input,   // ✅ 말풍선에는 이 한 줄만
                            payloadForModel = payload, // ✅ GPT에는 전체 payload
                            onError = { /* TODO: 에러 처리 */ }
                        )
                        input = ""
                    }
                ) {
                    Icon(Icons.Default.Send, contentDescription = "보내기")
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
    eval: ListeningEval?
): String {
    // JSON 라이브러리 없이, 문자열로만 구성
    val sb = StringBuilder()
    sb.append("{\n")
    sb.append("  \"roomId\": $roomId,\n")

    if (roomSize != null) {
        sb.append("  \"roomSize\": {\n")
        sb.append("    \"width_m\": ${roomSize.w},\n")
        sb.append("    \"depth_m\": ${roomSize.d},\n")
        sb.append("    \"height_m\": ${roomSize.h}\n")
        sb.append("  },\n")
    } else {
        sb.append("  \"roomSize\": null,\n")
    }

    if (listener != null) {
        sb.append("  \"listener\": {\n")
        sb.append("    \"x_m_from_left\": ${listener.x},\n")
        sb.append("    \"z_m_from_front\": ${listener.z}\n")
        sb.append("  },\n")
    } else {
        sb.append("  \"listener\": null,\n")
    }

    sb.append("  \"speakers\": [\n")
    speakers.forEachIndexed { idx, s ->
        sb.append(
            "    { \"index\": $idx, \"x_m\": ${s.x}, \"y_m\": ${s.y}, \"z_m\": ${s.z} }"
        )
        if (idx != speakers.lastIndex) sb.append(",")
        sb.append("\n")
    }
    sb.append("  ],\n")

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
