package com.journai.journai.network

import retrofit2.http.Body
import retrofit2.http.POST

interface ProxyApi {
    @POST("/chat")
    suspend fun chat(@Body body: ChatRequest): ChatResponse

    @POST("/transcribe")
    suspend fun transcribe(@Body body: TranscribeRequest): TranscribeResponse
}

data class ChatMessage(
    val role: String,
    val content: String
)

data class BlacklistItem(
    val pattern: String,
    val replacement: String? = null
)

data class ChatRequest(
    val messages: List<ChatMessage>,
    val blacklist: List<BlacklistItem>? = null,
    val stream: Boolean? = null,
    val useCache: Boolean? = true
)

data class ChatChoiceMessage(
    val role: String,
    val content: String
)

data class ChatChoice(
    val index: Int,
    val message: ChatChoiceMessage,
    val finish_reason: String?
)

data class ChatResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<ChatChoice>
)

data class TranscribeRequest(
    val audioBase64: String? = null,
    val chunks: List<String>? = null,
    val sampleRate: Int,
    val language: String? = null
)

data class TranscribeResponse(
    val text: String
)


