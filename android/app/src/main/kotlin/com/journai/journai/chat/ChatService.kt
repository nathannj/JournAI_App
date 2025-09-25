package com.journai.journai.chat

import com.journai.journai.network.ChatMessage
import com.journai.journai.network.ChatRequest
import com.journai.journai.network.ProxyApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import retrofit2.HttpException
import android.util.Log
import com.journai.journai.util.DateRangeParser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatService @Inject constructor(
    private val api: ProxyApi,
    private val tools: ChatTools,
    private val agent: AgentOrchestrator
) {
    suspend fun complete(messages: List<ChatMessage>, useCache: Boolean = true): String = withContext(Dispatchers.IO) {
        // Build tool context via agentic planner; falls back to built-in heuristics
        val lastUser = messages.lastOrNull { it.role == "user" }?.content.orEmpty()
        Log.d(TAG, "ChatService.complete: lastUser='${lastUser.take(120)}' messages=${messages.size}")
        // Parse an optional date range to bias tool planning
        val dateRange = DateRangeParser.parse(lastUser)
        if (dateRange != null) {
            Log.d(TAG, "ChatService.complete: parsedRange start=${dateRange.start} end=${dateRange.end}")
        }
        val plannedContext = runCatching { agent.planAndGather(lastUser) }.getOrDefault("")
        Log.d(TAG, "ChatService.complete: plannedContextSize=${plannedContext.length}")
        val toolContext = StringBuilder(plannedContext)
        if (dateRange != null) {
            val rangeSummary = runCatching { tools.entriesSummaryRange(dateRange.start, dateRange.end, 10) }.getOrDefault("")
            if (rangeSummary.isNotBlank()) {
                toolContext.append("\n\n[Tool: entriesSummaryRange]\n").append(rangeSummary)
            }
        }

        // Strengthened system guidance so the assistant uses context rather than claiming no access
        val guidance = ChatMessage(
            role = "system",
            content = "You are JournAI. You are given local journal context below. Use it to answer. Do not claim you lack access; cite from provided context. Be concise."
        )
        val rangeMsg = if (dateRange != null) ChatMessage(role = "system", content = "Focus on range ${dateRange.start} to ${dateRange.end}.") else null
        val contextMsg = if (toolContext.isNotBlank()) ChatMessage(role = "system", content = toolContext.toString()) else null
        // Ensure tool context appears BEFORE the final user message so the model sees it first
        val enriched = buildList {
            add(guidance)
            if (rangeMsg != null) add(rangeMsg)
            if (contextMsg != null) add(contextMsg)
            addAll(messages)
        }
        Log.d(TAG, "ChatService.complete: enrichedMessages=${enriched.size} hasContext=${contextMsg != null}")

        // Retry on transient 503 from proxy (cold start) with exponential backoff
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < 3) {
            try {
                val resp = api.chat(
                    ChatRequest(
                        messages = enriched,
                        blacklist = null,
                        stream = false,
                        useCache = useCache
                    )
                )
                val text = resp.choices.firstOrNull()?.message?.content ?: ""
                Log.d(TAG, "ChatService.complete: success on attempt=${attempt + 1} responseLen=${text.length}")
                // TODO: Remove AI response content logging - for debugging only
                Log.d(TAG, "ChatService.complete: AI response content='${text.take(500)}${if (text.length > 500) "..." else ""}'")
                return@withContext text
            } catch (e: Throwable) {
                val retryable = (e is HttpException && e.code() == 503) || e is java.io.IOException
                Log.w(TAG, "ChatService.complete: error attempt=${attempt + 1} retryable=$retryable type=${e::class.java.simpleName} msg=${e.message}")
                if (!retryable || attempt == 2) {
                    lastError = e
                    break
                }
                val backoffMs = 500L * (1L shl attempt)
                Log.d(TAG, "ChatService.complete: backing off ${backoffMs}ms before retry")
                delay(backoffMs)
                attempt++
            }
        }
        throw lastError ?: RuntimeException("Unknown chat error")
    }
}

private const val TAG = "ChatService"


