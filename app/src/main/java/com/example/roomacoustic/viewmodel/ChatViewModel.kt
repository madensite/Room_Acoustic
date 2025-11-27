package com.example.roomacoustic.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.roomacoustic.BuildConfig
import com.example.roomacoustic.model.ChatMessage
import com.example.roomacoustic.model.GPTRequest
import com.example.roomacoustic.model.GPTResponse
import com.example.roomacoustic.model.Message
import com.example.roomacoustic.util.RetrofitClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.example.roomacoustic.repo.ChatRepository

enum class ChatMode {
    FREE_TALK,      // 가볍게 잡담
    ANALYSIS        // 방 구조 기반 분석/추천
}

class ChatViewModel(
    private val chatRepository: ChatRepository   // ✅ 추가
) : ViewModel() {

    // ✅ 채팅 메시지 상태
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    // ✅ roomId별로 대화를 메모리에 저장하는 캐시 (앱이 살아있는 동안만 유지)
    private val conversationCache: MutableMap<Int, List<ChatMessage>> = mutableMapOf()

    // ✅ 현재 보고 있는 roomId (필요하면 활용)
    private var currentRoomId: Int? = null

    fun setMessages(initial: List<ChatMessage>) {
        _messages.value = initial
    }

    fun clearConversation() {
        _messages.value = emptyList()
    }

    // ✅ 현재 모드 상태
    private val _mode = MutableStateFlow(ChatMode.FREE_TALK)
    val mode: StateFlow<ChatMode> = _mode

    fun setMode(mode: ChatMode) {
        _mode.value = mode
    }

    // ✅ GPT 응답 대기 중 여부 (로딩 말풍선 용)
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    /**
     * visibleUserText : UI에 보일 사용자 메시지
     * payloadForModel : GPT에 보낼 실제 텍스트 (CONTEXT_JSON + USER_MESSAGE 포함)
     * appendUser      : true면 유저 말풍선 추가, false면 백그라운드 호출용
     */
    fun sendPrompt(
        roomId: Int,
        systemPrompt: String,
        visibleUserText: String?,
        payloadForModel: String,
        appendUser: Boolean = true,
        onError: (String) -> Unit
    ) {
        // 1) 유저 말풍선 추가
        if (appendUser && !visibleUserText.isNullOrBlank()) {
            appendMessage(
                roomId = roomId,
                sender = "user",
                content = visibleUserText
            )
        }

        val token = "Bearer ${BuildConfig.OPENAI_API_KEY}"

        // 2) 최근 대화 히스토리 (원하면 개수 줄일 수 있음)
        val history = _messages.value
            .filter { it.roomId == roomId }      // 같은 방의 대화만
            .takeLast(6)
            .map { msg ->
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

        // GPT 생각 중 → 로딩 말풍선 ON
        _isLoading.value = true

        RetrofitClient.api.sendPrompt(token, request)
            .enqueue(object : Callback<GPTResponse> {
                override fun onResponse(
                    call: Call<GPTResponse>,
                    resp: Response<GPTResponse>
                ) {
                    // ✅ 응답 받은 순간, 일단 "생각 중" 상태 종료
                    _isLoading.value = false

                    if (resp.isSuccessful) {
                        val fullContent = resp.body()
                            ?.choices
                            ?.firstOrNull()
                            ?.message
                            ?.content
                            ?.trim()

                        if (!fullContent.isNullOrBlank()) {
                            // ✅ 이제는 오직 타이핑 애니메이션만 진행
                            startTypingAnimation(roomId, fullContent)
                        } else {
                            appendMessage(
                                roomId = roomId,
                                sender = "assistant",
                                content = "⚠️ GPT 응답이 비었습니다."
                            )
                        }
                    } else {
                        onError("OpenAI 오류: ${resp.code()}")
                    }
                }

                override fun onFailure(call: Call<GPTResponse>, t: Throwable) {
                    _isLoading.value = false
                    onError("네트워크 오류: ${t.message}")
                }
            })
    }

    /**
     * ✅ GPT 타이핑 액션
     *  - 우선 content = "" 인 assistant 메시지를 하나 추가
     *  - 이후 한 글자씩 content를 업데이트
     */
    private fun startTypingAnimation(roomId: Int, fullText: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()

            // 1) 우선 content = "" 인 GPT 메시지 하나 추가 (DB에도 저장됨)
            val typingId = appendMessage(
                roomId = roomId,
                sender = "assistant",
                content = "",
                createdAt = now
            )

            var current = ""
            for ((idx, ch) in fullText.withIndex()) {
                current += ch

                // 화면용 상태만 업데이트
                _messages.update { list ->
                    list.map { msg ->
                        if (msg.id == typingId) msg.copy(content = current)
                        else msg
                    }
                }

                // 마지막 글자에서만 DB에 최종본 저장
                if (idx == fullText.lastIndex) {
                    val finalMsg = ChatMessage(
                        id = typingId,
                        roomId = roomId,
                        sender = "assistant",
                        content = current,
                        createdAt = now
                    )
                    // REPLACE 전략이라 기존 "" 레코드를 덮어씀
                    viewModelScope.launch {
                        chatRepository.upsertMessage(finalMsg)
                    }
                }

                delay(15)
            }
        }
    }


    /**
     * ✅ 메시지 추가 helper
     *  - id와 createdAt 자동 생성 (createdAt은 외부에서 넘겨줄 수도 있음)
     *  - 반환값: 새로 추가된 메시지의 id
     */
    private fun appendMessage(
        roomId: Int,
        sender: String,
        content: String,
        createdAt: Long = System.currentTimeMillis()
    ): Long {
        var newId: Long = 0

        _messages.update { list ->
            val nextId = (list.maxOfOrNull { it.id } ?: 0L) + 1L
            newId = nextId

            val msg = ChatMessage(
                id = nextId,
                roomId = roomId,
                sender = sender,
                content = content,
                createdAt = createdAt
            )

            // ✅ 메모리 상태 업데이트
            val updated = list + msg

            // ✅ DB에도 비동기로 저장
            viewModelScope.launch {
                chatRepository.upsertMessage(msg)
            }

            updated
        }

        return newId
    }


    /**
     * ✅ 새 대화 시작
     *  - 해당 roomId의 이전 대화 캐시를 지우고
     *  - 화면에 보이는 리스트도 비워 준다.
     */
    fun startNewConversation(roomId: Int) {
        viewModelScope.launch {
            // 1) DB에서 해당 방의 이전 대화 날리기
            chatRepository.clearConversation(roomId)

            // 2) UI 상태도 비우기
            _messages.value = emptyList()

            // 3) 모드는 ANALYSIS로 시작
            _mode.value = ChatMode.ANALYSIS
        }
    }


    /**
     * ✅ 기존 대화 불러오기
     *  - 지금은 DB 대신, ViewModel이 들고 있는 메모리 캐시를 사용
     *  - 앱이 살아 있는 동안에는 방별 대화를 유지할 수 있다.
     */
    fun loadConversation(roomId: Int) {
        viewModelScope.launch {
            val saved = chatRepository.loadConversation(roomId)
            _messages.value = saved
        }
    }

    // 🔹 RoomScreen 에서 사용할 조회 함수
    suspend fun hasConversation(roomId: Int): Boolean {
        return chatRepository.hasConversation(roomId)
    }
}
