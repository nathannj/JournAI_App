package com.journai.journai.chat

import com.journai.journai.network.ChatMessage
import com.journai.journai.network.ChatRequest
import com.journai.journai.network.ProxyApi
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
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
    {"tool": "timelineSummary", "params": {"days": 7}},
    {"tool": "semanticSearch", "params": {"query": "<string>", "k": 5}},
    {"tool": "minePatterns", "params": {"windowDays": 30}}
  ]
}
Prefer relevant tools; omit irrelevant ones.
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
                            val days = step.params?.get("days")?.toIntSafe() ?: 7
                            val summary = runCatching { tools.timelineSummary(days) }.getOrDefault("")
                            if (summary.isNotBlank()) {
                                contextSb.append("\n\n[Tool: timelineSummary]\n").append(summary)
                                newSignals.append("\nTL: ").append(summary.take(200))
                            }
                        }
                        "semanticSearch" -> {
                            val q = (step.params?.get("query") as? String)?.ifBlank { userQuery } ?: userQuery
                            val k = step.params?.get("k")?.toIntSafe() ?: 7
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
                            val w = step.params?.get("windowDays")?.toIntSafe() ?: 30
                            val pat = runCatching { tools.minePatterns(w) }.getOrDefault("")
                            if (pat.isNotBlank()) {
                                contextSb.append("\n\n[Tool: minePatterns]\n").append(pat)
                                newSignals.append("\nMP: ").append(pat.take(200))
                            }
                        }
                        "timelineSummaryRange" -> {
                            val start = (step.params?.get("start") as? String)
                            val end = (step.params?.get("end") as? String)
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

data class AgentPlan(val tools: List<AgentTool> = emptyList())
data class AgentTool(val tool: String, val params: Map<String, Any>? = null)

private fun Any.toIntSafe(): Int? = when (this) {
    is Int -> this
    is Long -> this.toInt()
    is Double -> this.toInt()
    is Float -> this.toInt()
    is String -> this.toIntOrNull()
    else -> null
}


