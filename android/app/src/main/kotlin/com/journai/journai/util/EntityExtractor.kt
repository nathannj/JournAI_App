package com.journai.journai.util

import com.journai.journai.data.entity.EntityType

object EntityExtractor {
    // Simple heuristic extractor for MVP; replace with LLM or better rules later
    fun extract(text: String): List<Pair<EntityType, String>> {
        if (text.isBlank()) return emptyList()
        val tokens = text.split(Regex("\\s+"))
        val results = mutableListOf<Pair<EntityType, String>>()
        // Heuristic: capitalized words as potential PERSON/PLACE/ORG
        for (t in tokens) {
            val w = t.trim().trim(',', '.', '!', '?', ';', ':', '"', '\'')
            if (w.length in 3..40 && w[0].isUpperCase()) {
                results.add(EntityType.OTHER to w)
            }
        }
        return results.distinct()
    }
}


