package com.journai.journai.chat

import com.journai.journai.data.dao.EmbeddingDao
import com.journai.journai.data.dao.TimelineDao
import com.journai.journai.data.dao.EntryDao
import com.journai.journai.data.repository.SemanticSearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.*
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ChatTools"

data class SemanticChunk(
    val entryId: String,
    val snippet: String? = null,
    val createdAt: Instant,
    val score: Float
)

@Singleton
class ChatTools @Inject constructor(
    private val searchRepo: SemanticSearchRepository,
    private val embeddingDao: EmbeddingDao,
    private val timelineDao: TimelineDao,
    private val entryDao: EntryDao
) {
    suspend fun semanticSearch(query: String, k: Int = 5): List<SemanticChunk> = withContext(Dispatchers.IO) {
        Log.d(TAG, "ChatTools.semanticSearch: q='${query.take(80)}' k=$k")
        var entries = runCatching { searchRepo.search(query, topK = k) }.getOrDefault(emptyList())
        if (entries.isEmpty()) {
            // Fallback: lexical search of entries if embeddings not present yet
            val terms = buildList {
                add(query)
                addAll(query.split(Regex("[^\\p{L}\\p{N}]+")))
            }.map(String::trim).filter { it.length >= 3 }.distinct().take(6)
            val byId = linkedMapOf<String, com.journai.journai.data.entity.Entry>()
            for (term in terms) {
                runCatching { entryDao.searchEntriesSimpleOnce(term) }
                    .getOrDefault(emptyList())
                    .forEach { byId.putIfAbsent(it.id, it) }
                if (byId.size >= k) break
            }
            entries = byId.values.take(k)
            Log.d(TAG, "ChatTools.semanticSearch: lexicalFallback=${entries.size}")
        }
        val out = entries.map {
            SemanticChunk(
                entryId = it.id,
                snippet = it.richBody.replace('\n', ' ').take(500),
                createdAt = it.createdAt,
                score = 0f
            )
        }
        Log.d(TAG, "ChatTools.semanticSearch: results=${out.size}")
        out
    }

    // Summarize timeline over last N days (MVP): collect items and make a short bullet list
    suspend fun timelineSummary(days: Int = 7): String = withContext(Dispatchers.IO) {
        val end = Clock.System.now()
        val start = end.minus(days.toLong() * 24L * 60L * 60L, DateTimeUnit.SECOND)
        Log.d(TAG, "ChatTools.timelineSummary: days=$days start=$start end=$end")
        val items = timelineDao.getTimelineBetweenOnce(start, end)
        val sb = StringBuilder()
        sb.append("Recent timeline (last ").append(days).append(" days):\n")
        if (items.isNotEmpty()) {
            for (item in items.take(15)) {
                sb.append("- ").append(item.timestamp.toString()).append(": ").append(item.summary.take(200)).append('\n')
            }
            val out = sb.toString()
            Log.d(TAG, "ChatTools.timelineSummary: lines=${items.take(15).size}")
            return@withContext out
        }
        // Fallback: derive summary from recent entries if no timeline items yet
        val recentEntries = runCatching { entryDao.getEntriesBetweenOnce(start, end) }.getOrDefault(emptyList())
        for (e in recentEntries.take(10)) {
            sb.append("- ").append(e.createdAt.toString()).append(": ")
                .append(e.richBody.replace('\n', ' ').take(200)).append('\n')
        }
        val out = sb.toString()
        Log.d(TAG, "ChatTools.timelineSummary: fallbackEntries=${recentEntries.size}")
        out
    }

    suspend fun timelineSummaryRange(start: Instant, end: Instant): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "ChatTools.timelineSummaryRange: start=$start end=$end")
        val items = timelineDao.getTimelineBetweenOnce(start, end)
        val sb = StringBuilder()
        sb.append("Timeline between ").append(start).append(" and ").append(end).append(":\n")
        if (items.isNotEmpty()) {
            for (item in items.take(30)) {
                sb.append("- ").append(item.timestamp.toString()).append(": ").append(item.summary.take(200)).append('\n')
            }
            return@withContext sb.toString()
        }
        val recentEntries = runCatching { entryDao.getEntriesBetweenOnce(start, end) }.getOrDefault(emptyList())
        for (e in recentEntries.take(20)) {
            sb.append("- ").append(e.createdAt.toString()).append(": ")
                .append(e.richBody.replace('\n', ' ').take(200)).append('\n')
        }
        sb.toString()
    }

    suspend fun entriesSummaryRange(start: Instant, end: Instant, k: Int = 10): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "ChatTools.entriesSummaryRange: start=$start end=$end")
        val entries = runCatching { entryDao.getEntriesBetweenOnce(start, end) }.getOrDefault(emptyList())
        if (entries.isEmpty()) return@withContext ""
        val sb = StringBuilder()
        sb.append("Entries between ").append(start).append(" and ").append(end).append(":\n")
        for (e in entries.take(k)) {
            sb.append("- ").append(e.createdAt.toString()).append(": ")
                .append(e.richBody.replace('\n', ' ').take(200)).append('\n')
        }
        sb.toString()
    }

    suspend fun minePatterns(windowDays: Int = 30): String = withContext(Dispatchers.IO) {
        val end = Clock.System.now()
        val start = end.minus(windowDays.toLong() * 24L * 60L * 60L, DateTimeUnit.SECOND)
        val entries = runCatching { entryDao.getEntriesBetweenOnce(start, end) }.getOrDefault(emptyList())
        if (entries.isEmpty()) return@withContext "No journal entries found in the last " + windowDays + " days."

        val stopWords = setOf(
            "about", "after", "again", "before", "could", "from", "have", "into", "just",
            "more", "that", "their", "there", "these", "they", "this", "today", "very",
            "what", "when", "where", "which", "with", "would", "your"
        )
        val counts = entries.asSequence()
            .flatMap { it.richBody.lowercase().split(Regex("[^\\p{L}\\p{N}]+")) }
            .filter { it.length >= 4 && it !in stopWords }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(8)
        if (counts.isEmpty()) return@withContext "No recurring word themes found in the last " + windowDays + " days."
        buildString {
            append("Recurring themes in the last ").append(windowDays).append(" days:\n")
            counts.forEach {
                append("- ").append(it.key).append(" (mentioned ").append(it.value).append(" times)\n")
            }
        }
    }

    suspend fun weeklyReview(): String = withContext(Dispatchers.IO) {
        // Placeholder: later compose recent entries summaries
        ""
    }
}


