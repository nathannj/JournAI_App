package com.journai.journai.data.repository

import com.journai.journai.data.dao.EmbeddingDao
import com.journai.journai.data.dao.EntryDao
import com.journai.journai.data.entity.Entry
import com.journai.journai.network.EmbedRequest
import com.journai.journai.network.ProxyApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SemanticSearchRepository @Inject constructor(
    private val embeddingDao: com.journai.journai.data.dao.EmbeddingDao,
    private val entryDao: EntryDao,
    private val api: ProxyApi
) {
    suspend fun search(query: String, topK: Int = 5): List<Entry> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val embed = api.embed(EmbedRequest(input = listOf(query)))
        val vector = embed.data.firstOrNull()?.embedding ?: return@withContext emptyList()
        val queryVec = l2Normalize(vector.map { it.toFloat() }.toFloatArray())

        // Gather embeddings by model; if empty (cold start), fallback to any embeddings
        val model = embed.model
        val byEntry = mutableMapOf<String, Float>()
        val dims = queryVec.size
        val allByModel = embeddingDao.getAllByModel(model)
        val all = if (allByModel.isNotEmpty()) allByModel else embeddingDao.getAll()
        for (emb in all) {
            val vec = byteArrayToFloatArray(emb.vector) // already normalized at index time
            if (vec.size != dims) continue
            // With normalized vectors, dot = cosine
            val sim = dotProduct(queryVec, vec)
            val agg = byEntry.getOrDefault(emb.entryId, -1f)
            if (sim > agg) byEntry[emb.entryId] = sim
        }
        // Lower threshold for recall, favoring recall over precision for context
        val filtered = byEntry.entries
            .filter { it.value >= 0.05f }
            .sortedByDescending { it.value }
            .take(topK)
            .map { it.key }
            .toSet()
        val active = entryDao.getActiveEntriesOnce()
        // Preserve ranking order from filtered
        val order = filtered.toList()
        val map = active.associateBy { it.id }
        order.mapNotNull { map[it] }
    }

    fun byteArrayToFloatArray(bytes: ByteArray): FloatArray {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val arr = FloatArray(bytes.size / 4)
        var i = 0
        while (buffer.hasRemaining()) {
            arr[i++] = buffer.getFloat()
        }
        return arr
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var i = 0
        val n = a.size
        while (i < n) { dot += a[i] * b[i]; i++ }
        return dot
    }

    private fun l2Normalize(vec: FloatArray): FloatArray {
        var sum = 0f
        for (v in vec) sum += v * v
        if (sum <= 0f) return vec
        val inv = 1f / kotlin.math.sqrt(sum)
        val out = FloatArray(vec.size)
        var i = 0
        while (i < vec.size) { out[i] = vec[i] * inv; i++ }
        return out
    }
}


