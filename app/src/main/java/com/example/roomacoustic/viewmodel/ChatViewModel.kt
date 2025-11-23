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

class ChatViewModel : ViewModel() {

    // ✅ 1) 현재 방 ID (Room별 대화 구분용)
    private val _currentRoomId = MutableStateFlow<Int?>(null)
    val currentRoomId: StateFlow<Int?> = _currentRoomId

    // ✅ 2) 채팅 메시지 상태 (현재 방에 대한 메시지 리스트)
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    fun setMessages(initial: List<ChatMessage>) {
        _messages.value = initial
    }

    // ✅ 3) "새 대화 시작" (RoomScreen에서 mode = NEW일 때 호출)
    fun startNewConversation(roomId: Int) {
        _currentRoomId.value = roomId

        // 이 방에 대한 이전 대화는 모두 비운다
        _messages.value = emptyList()

        // TODO:
        // 여기에 Room DB를 쓴다면,
        // - 해당 roomId의 기존 채팅 레코드를 삭제하는 로직을 넣으면 된다.
        //   ex) chatRepository.deleteMessagesForRoom(roomId)
    }

    // ✅ 4) "기존 대화 이어가기" (RoomScreen에서 mode = CONTINUE일 때 호출)
    fun loadConversation(roomId: Int) {
        _currentRoomId.value = roomId

        // TODO:
        // 여기에서 DB 또는 로컬 저장소에서 roomId에 해당하는 메시지들을 불러와서
        // _messages.value = 불러온 목록
        //
        // 예시 (Room DB가 있다고 가정하면):
        // viewModelScope.launch {
        //     val history = chatRepository.getMessagesForRoom(roomId)
        //     _messages.value = history
        // }
        //
        // 지금은 DB 코드가 없으니, 빈 구현으로 두고
        // 나중에 실제 저장소 붙일 때 이 부분만 손보면 된다.
    }

    fun clearConversation() {
        _messages.value = emptyList()
    }

    /**
     * ✅ 핵심 포인트
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
                        append("assistant", content)
                    } else {
                        append("assistant", "⚠️ GPT 응답이 비었습니다.")
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
        val roomId = _currentRoomId.value ?: -1   // ⚠️ roomId 없으면 임시값(-1). 원래는 NEW/CONTINUE 진입 때 꼭 셋팅해야 함
        val now = System.currentTimeMillis()

        val msg = ChatMessage(
            id = 0L,              // 새로 생성된 메시지 → DB에서 auto id 줄 거면 0L 써도 됨
            roomId = roomId,
            sender = sender,      // "user" or "assistant"
            content = content,
            createdAt = now
        )

        _messages.update { it + msg }
    }

}
