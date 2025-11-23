package com.example.roomacoustic.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import com.example.roomacoustic.model.ChatMessage
import com.example.roomacoustic.model.GPTRequest
import com.example.roomacoustic.model.GPTResponse
import com.example.roomacoustic.model.Message
import com.example.roomacoustic.util.RetrofitClient
import com.example.roomacoustic.BuildConfig
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

// ✅ 1) 모드 enum (같은 파일 맨 위쪽에 두면 됨)
enum class ChatMode {
    FREE_TALK,      // 가볍게 대화하는 모드
    ANALYSIS        // 방 분석/추천 모드
}

class ChatViewModel : ViewModel() {

    // ✅ 2) 채팅 메시지 상태
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    // ✅ 3) 현재 모드 상태
    private val _mode = MutableStateFlow(ChatMode.FREE_TALK)
    val mode: StateFlow<ChatMode> = _mode

    fun setMode(mode: ChatMode) {
        _mode.value = mode
    }

    fun clearConversation() {
        _messages.value = emptyList()
    }

    /**
     * ✅ 핵심 변경 포인트
     *  - visibleUserText: UI에 보일 사용자 메시지 (짧은 한국어 문장)
     *  - payloadForModel: GPT에 보낼 실제 텍스트 (CONTEXT_JSON + USER_MESSAGE 래핑 포함)
     *  - appendUser: true일 때만 사용자 말풍선 추가 (false면 백그라운드 호출용)
     */
    fun sendPrompt(
        systemPrompt: String,
        visibleUserText: String?,
        payloadForModel: String,
        appendUser: Boolean = true,
        onError: (String) -> Unit
    ) {
        // 1) 화면에 찍힐 유저 메시지
        if (appendUser && !visibleUserText.isNullOrBlank()) {
            append("user", visibleUserText)
        }

        val token = "Bearer ${BuildConfig.OPENAI_API_KEY}"

        // 2) 직전 대화 히스토리 (원하면 개수 줄여도 됨)
        val history = _messages.value.takeLast(6).map { msg ->
            val role = if (msg.sender == "user") "user" else "assistant"
            Message(role = role, content = msg.content)
        }

        // 3) GPT용 messages 구성
        val messagesForApi = buildList {
            add(Message("system", systemPrompt))
            addAll(history)
            add(Message("user", payloadForModel))
        }

        val request = GPTRequest(messages = messagesForApi)

        // 4) Retrofit 호출
        RetrofitClient.api.sendPrompt(token, request).enqueue(object : Callback<GPTResponse> {
            override fun onResponse(call: Call<GPTResponse>, resp: Response<GPTResponse>) {
                if (resp.isSuccessful) {
                    val content = resp.body()
                        ?.choices
                        ?.firstOrNull()
                        ?.message
                        ?.content
                        ?.trim()

                    if (!content.isNullOrBlank()) {
                        append("gpt", content)
                    } else {
                        append("gpt", "⚠️ GPT 응답이 비었습니다.")
                    }
                } else {
                    onError("OpenAI 오류: ${resp.code()}")
                }
            }

            override fun onFailure(call: Call<GPTResponse>, t: Throwable) {
                onError("네트워크 오류: ${t.message}")
            }
        })
    }

    // 내부에서 메시지 추가
    private fun append(sender: String, content: String) {
        _messages.update { it + ChatMessage(sender, content) }
    }
}
