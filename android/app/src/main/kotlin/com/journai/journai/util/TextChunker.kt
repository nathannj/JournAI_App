package com.journai.journai.util

object TextChunker {
    fun chunkByParagraphs(
        text: String,
        targetCharsPerChunk: Int = 1800,
        maxCharsPerChunk: Int = 2400
    ): List<String> {
        if (text.isBlank()) return emptyList()
        val paragraphs = text
            .replace("\r\n", "\n")
            .split("\n\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val chunks = mutableListOf<StringBuilder>()
        var current = StringBuilder()
        for (p in paragraphs) {
            val sep = if (current.isEmpty()) "" else "\n\n"
            if ((current.length + sep.length + p.length) <= targetCharsPerChunk) {
                current.append(sep).append(p)
            } else if (current.isNotEmpty()) {
                chunks.add(current)
                current = StringBuilder(p)
            } else {
                // paragraph too large; hard split into max-sized segments
                var idx = 0
                while (idx < p.length) {
                    val end = (idx + maxCharsPerChunk).coerceAtMost(p.length)
                    chunks.add(StringBuilder(p.substring(idx, end)))
                    idx = end
                }
                current = StringBuilder()
            }
        }
        if (current.isNotEmpty()) chunks.add(current)
        return chunks.map { it.toString() }
    }
}


