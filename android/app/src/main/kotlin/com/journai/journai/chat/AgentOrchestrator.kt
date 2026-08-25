package com.journai.journai.chat

import com.journai.journai.network.ChatMessage
import com.journai.journai.network.ChatRequest
import com.journai.journai.network.ProxyApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentOrchestrator @Inject constructor(
    private val api: ProxyApi,
    private val tools: ChatTools,
    private val moshi: Moshi
) {
    suspend fun planAndGather(userQuery: String): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "Agent.plan start: query='${userQuery.take(120)}'")
        val schema = """
You are a planner for a local journal assistant. Decide which LOCAL tools to run to best answer the user's question.
Return ONLY a compact JSON object (no prose) matching this schema:
{
  "tools": [
    {"tool": "semanticSearch", "params": {"query": "<string>", "k": 8}},
    {"tool": "timelineSummary", "params": {"days": 7}},
    {"tool": "timelineSummaryRange", "params": {"start": "<ISO-8601>", "end": "<ISO-8601>"}},
    {"tool": "entriesSummaryRange", "params": {"start": "<ISO-8601>", "end": "<ISO-8601>", "k": 10}},
    {"tool": "minePatterns", "params": {"windowDays": 30}}
  ]
}
Use semanticSearch for questions about specific events, people, topics, or memories.
Use timelineSummary for recent activity and minePatterns for recurring themes.
Return valid JSON only; never wrap it in Markdown fences.
""".trimIndent()

        val planMessages = listOf(
            ChatMessage(role = "system", content = schema),
            ChatMessage(role = "user", content = userQuery)
        )
        val planStr = runCatching {
            val resp = api.chat(ChatRequest(messages = planMessages, blacklist = null, stream = false, useCache = false))
            resp.choices.firstOrNull()?.message?.content.orEmpty()
        }.getOrDefault("")
        Log.d(TAG, "Agent.plan raw plan length=${planStr.length}")

        val contextSb = StringBuilder()
        val seenEntries = mutableSetOf<String>()

        // Always perform a local retrieval pass. The model planner is useful for
        // choosing additional tools, but it must not be able to suppress the
        // basic journal lookup when its JSON is malformed or unavailable.
        val baselineSearch = runCatching { tools.semanticSearch(userQuery, 8) }.getOrDefault(emptyList())
        if (baselineSearch.isNotEmpty()) {
            contextSb.append("\n\n[Tool: semanticSearch]\n")
            for (c in baselineSearch) {
                if (seenEntries.add(c.entryId)) {
                    contextSb.append("- Entry ").append(c.entryId)
                        .append(" (").append(c.createdAt).append("): ")
                        .append(c.snippet.orEmpty()).append('\n')
                }
            }
        }

        val lowerQuery = userQuery.lowercase()
        if (listOf("recent", "today", "yesterday", "this week", "last week", "timeline", "what happened", "when did")
                .any { lowerQuery.contains(it) }) {
            val days = if (lowerQuery.contains("month")) 30 else 7
            val summary = runCatching { tools.timelineSummary(days) }.getOrDefault("")
            if (summary.isNotBlank()) contextSb.append("\n\n[Tool: timelineSummary]\n").append(summary)
        }
        if (listOf("pattern", "recurring", "often", "trend", "frequency", "habit")
                .any { lowerQuery.contains(it) }) {
            val patterns = runCatching { tools.minePatterns(30) }.getOrDefault("")
            if (patterns.isNotBlank()) contextSb.append("\n\n[Tool: minePatterns]\n").append(patterns)
        }

        var currentPlan = planStr
        var iterations = 0
        while (iterations < 2) {
            iterations++
            if (currentPlan.isBlank()) break
            val plan = parsePlan(currentPlan)
            if (plan == null) break
            Log.d(TAG, "Agent.plan iter=$iterations tools=${plan.tools.map{it.tool}}")
            var newSignals = StringBuilder()
            for (step in plan.tools) {
                when (step.tool) {
                        "timelineSummary" -> {
                            val days = step.params?.days ?: 7
                            val summary = runCatching { tools.timelineSummary(days) }.getOrDefault("")
                            if (summary.isNotBlank()) {
                                contextSb.append("\n\n[Tool: timelineSummary]\n").append(summary)
                                newSignals.append("\nTL: ").append(summary.take(200))
                            }
                        }
                        "semanticSearch" -> {
                            val q = step.params?.query?.ifBlank { userQuery } ?: userQuery
                            val k = step.params?.k ?: 8
                            val sem = runCatching { tools.semanticSearch(q, k) }.getOrDefault(emptyList())
                            if (sem.isNotEmpty()) {
                                contextSb.append("\n\n[Tool: semanticSearch]\n")
                                for (c in sem) {
                                    if (seenEntries.add(c.entryId)) {
                                        contextSb.append("- Entry ").append(c.entryId).append(": ").append(c.snippet.orEmpty()).append('\n')
                                    }
                                }
                                newSignals.append("\nSS: ").append(sem.joinToString("; ") { it.snippet.orEmpty().take(50) })
                            }
                        }
                        "minePatterns" -> {
                            val w = step.params?.windowDays ?: 30
                            val pat = runCatching { tools.minePatterns(w) }.getOrDefault("")
                            if (pat.isNotBlank()) {
                                contextSb.append("\n\n[Tool: minePatterns]\n").append(pat)
                                newSignals.append("\nMP: ").append(pat.take(200))
                            }
                        }
                        "timelineSummaryRange" -> {
                            val start = step.params?.start
                            val end = step.params?.end
                            if (!start.isNullOrBlank() && !end.isNullOrBlank()) {
                                runCatching {
                                    val s = kotlinx.datetime.Instant.parse(start)
                                    val e = kotlinx.datetime.Instant.parse(end)
                                    val summary = tools.timelineSummaryRange(s, e)
                                    if (summary.isNotBlank()) {
                                        contextSb.append("\n\n[Tool: timelineSummaryRange]\n").append(summary)
                                        newSignals.append("\nTR: ").append(summary.take(200))
                                    }
                                }
                            }
                        }
                        "entriesSummaryRange" -> {
                            val start = step.params?.start
                            val end = step.params?.end
                            if (!start.isNullOrBlank() && !end.isNullOrBlank()) {
                                runCatching {
                                    val s = kotlinx.datetime.Instant.parse(start)
                                    val e = kotlinx.datetime.Instant.parse(end)
                                    val summary = tools.entriesSummaryRange(s, e, step.params.k ?: 10)
                                    if (summary.isNotBlank()) {
                                        contextSb.append("\n\n[Tool: entriesSummaryRange]\n").append(summary)
                                        newSignals.append("\nER: ").append(summary.take(200))
                                    }
                                }
                            }
                        }
                    }
            }
            // If new signals were gathered, ask for another plan iteration
            if (newSignals.isNotBlank() && iterations < 2) {
                val followUp = listOf(
                    ChatMessage(role = "system", content = "You may decide to run more tools based on the new context below. Return JSON only."),
                    ChatMessage(role = "user", content = userQuery + "\n\nContext:" + newSignals.toString())
                )
                currentPlan = runCatching {
                    val r = api.chat(ChatRequest(messages = followUp, blacklist = null, stream = false, useCache = false))
                    r.choices.firstOrNull()?.message?.content.orEmpty()
                }.getOrDefault("")
            } else {
                break
            }
        }

        // Fallback if planner failed or returned nothing
        if (contextSb.isBlank()) {
            runCatching {
                val sem = tools.semanticSearch(userQuery, 5)
                if (sem.isNotEmpty()) {
                    contextSb.append("\n\n[Tool: semanticSearch]\n")
                    for (c in sem) contextSb.append("- Entry ").append(c.entryId).append(": ").append(c.snippet.orEmpty()).append('\n')
                }
            }
            if (userQuery.contains("week", true) || userQuery.contains("recent", true)) {
                val summary = runCatching { tools.timelineSummary(7) }.getOrDefault("")
                if (summary.isNotBlank()) contextSb.append("\n\n[Tool: timelineSummary]\n").append(summary)
            }
        }

        val out = contextSb.toString()
        Log.d(TAG, "Agent.plan context length=${out.length}")
        out
    }

    private fun parsePlan(json: String): AgentPlan? {
        return try {
            val adapter = moshi.adapter(AgentPlan::class.java)
            adapter.fromJson(json)
        } catch (_: Throwable) { null }
    }
}

private const val TAG = "AgentOrchestrator"

@JsonClass(generateAdapter = true)
data class AgentPlan(val tools: List<AgentTool> = emptyList())

@JsonClass(generateAdapter = true)
data class AgentTool(val tool: String, val params: AgentToolParams? = null)

@JsonClass(generateAdapter = true)
data class AgentToolParams(
    val query: String? = null,
    val k: Int? = null,
    val days: Int? = null,
    val windowDays: Int? = null,
    val start: String? = null,
    val end: String? = null
)


