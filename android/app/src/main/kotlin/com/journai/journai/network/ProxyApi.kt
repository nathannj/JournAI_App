package com.journai.journai.network

import retrofit2.http.Body
import retrofit2.http.POST

interface ProxyApi {
    @POST("/chat")
    suspend fun chat(@Body body: ChatRequest): ChatResponse

    @POST("/transcribe")
    suspend fun transcribe(@Body body: TranscribeRequest): TranscribeResponse

    @POST("/embed")
    suspend fun embed(@Body body: EmbedRequest): EmbedResponse

    @POST("/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse
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

// Embeddings
data class EmbedRequest(
    val input: List<String>,
    val blacklist: List<BlacklistItem>? = null
)

data class EmbedItem(
    val embedding: List<Double>,
    val index: Int,
    val `object`: String? = null
)

data class EmbedResponse(
    val model: String,
    val data: List<EmbedItem>
)

// Registration
data class RegisterRequest(
    val attestation: String,
    val devicePublicKeyJwk: Map<String, Any?>? = null
)

data class RegisterResponse(
    val token: String,
    val deviceId: String,
    val kid: String? = null,
    val expiresIn: Long? = null
)


