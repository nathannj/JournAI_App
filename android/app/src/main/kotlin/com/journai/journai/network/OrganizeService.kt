package com.journai.journai.network

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi

class OrganizeService(
    private val api: ProxyApi,
    private val moshi: Moshi,
    private val securePrefs: com.journai.journai.auth.SecurePrefs
) {
    suspend fun organizeText(rawText: String): String {
        val instruction = runCatching { securePrefs.getString("org_pref_instruction", "") }.getOrDefault("")
        val prefsGuidance = if (instruction.isNotBlank()) {
            "\n\nUser organize instruction (follow exactly; this is the user's explicit request):\n" + instruction.trim()
        } else {
            ""
        }
        val system = ChatMessage(
            role = "system",
            content = ORGANIZE_SYSTEM_PROMPT + prefsGuidance
        )
        val user = ChatMessage(
            role = "user",
            content = rawText
        )
        val req = ChatRequest(
            messages = listOf(system, user),
            stream = false,
            useCache = true
        )
        val resp = api.chat(req)
        val text = resp.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        return text.ifBlank { rawText }
    }

    companion object {
        private const val ORGANIZE_SYSTEM_PROMPT = """
You are an assistant that rewrites a single journal entry into a clean, organized draft with the following rules:
- Keep the user's voice and meaning.
- Improve structure: paragraphs, bullets for lists, headings if clear.
- Fix obvious grammar/typos; keep slang if intentional.
- Do NOT invent new facts.
- Return ONLY the rewritten entry text. No preface, no explanation.
"""
    }
}


