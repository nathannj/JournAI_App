package com.journai.journai.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.journai.journai.data.dao.EmbeddingDao
import com.journai.journai.data.dao.EntryDao
import com.journai.journai.data.entity.Embedding
import com.journai.journai.network.EmbedRequest
import com.journai.journai.network.ProxyApi
import com.journai.journai.util.TextChunker
import com.journai.journai.util.EntityExtractor
import com.journai.journai.util.TimelineExtractor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.journai.journai.data.dao.EntityDao
import com.journai.journai.data.dao.EntryEntityDao
import com.journai.journai.data.dao.TimelineDao
import com.journai.journai.data.entity.Entity
import com.journai.journai.data.entity.EntryEntity
import com.journai.journai.data.entity.TimelineItem
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

@HiltWorker
class IndexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val entryDao: EntryDao,
    private val embeddingDao: EmbeddingDao,
    private val api: ProxyApi,
    private val entityDao: EntityDao,
    private val entryEntityDao: EntryEntityDao,
    private val timelineDao: TimelineDao
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val now = Clock.System.now()
            val prefs = encryptedPrefs()
            val lastRunMs = prefs.getLong(KEY_LAST_RUN_MS, 0L)
            val entries = if (lastRunMs > 0L) {
                val since: Instant = Instant.fromEpochMilliseconds(lastRunMs)
                entryDao.getEntriesEditedSince(since)
            } else {
                entryDao.getActiveEntriesOnce()
            }
            for (entry in entries) {
                val chunks = TextChunker.chunkByParagraphs(entry.richBody)
                if (chunks.isEmpty()) continue
                val response = api.embed(EmbedRequest(input = chunks))
                val model = response.model
                embeddingDao.deleteEmbeddingsForEntryAndModel(entry.id, model)
                val dims = response.data.firstOrNull()?.embedding?.size ?: 0
                val toInsert = response.data.mapIndexed { idx, item ->
                    val norm = l2Normalize(item.embedding.map { it.toFloat() }.toFloatArray())
                    Embedding(
                        id = UUID.randomUUID().toString(),
                        entryId = entry.id,
                        chunkId = "${entry.id}#${idx}",
                        vector = floatArrayToLeBytes(norm),
                        dims = dims,
                        model = model,
                        createdAt = now
                    )
                }
                embeddingDao.insertEmbeddings(toInsert)

                // Entities (simple heuristic)
                entryEntityDao.deleteAllEntitiesForEntry(entry.id)
                val extracted = EntityExtractor.extract(entry.richBody)
                // Count frequencies, keep items with frequency >= 2, take top 15
                val freq = mutableMapOf<Pair<com.journai.journai.data.entity.EntityType, String>, Int>()
                for (p in extracted) freq[p] = (freq[p] ?: 0) + 1
                val top = freq.entries
                    .filter { it.value >= 2 }
                    .sortedByDescending { it.value }
                    .map { it.key }
                    .take(15)
                for ((type, name) in top) {
                    val existing = entityDao.getEntityByName(name)
                    val entityId = if (existing == null) {
                        val e = Entity(
                            id = UUID.randomUUID().toString(),
                            type = type,
                            name = name,
                            createdAt = now
                        )
                        val row = entityDao.insertEntity(e)
                        e.id
                    } else existing.id
                    entryEntityDao.insertEntryEntity(EntryEntity(entryId = entry.id, entityId = entityId, salience = 0.5f))
                }

                // Timeline extraction (simple dates)
                timelineDao.deleteTimelineForEntry(entry.id)
                val times = TimelineExtractor.extractTimestamps(entry.richBody).take(5)
                val items = times.map {
                    TimelineItem(
                        id = UUID.randomUUID().toString(),
                        entryId = entry.id,
                        timestamp = it,
                        summary = "Event on ${it}"
                    )
                }
                if (items.isNotEmpty()) timelineDao.insertTimelineItems(items)
            }
            prefs.edit().putLong(KEY_LAST_RUN_MS, now.toEpochMilliseconds()).apply()
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    private fun encryptedPrefs(): android.content.SharedPreferences {
        val masterKey = MasterKey.Builder(applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private fun floatArrayToLeBytes(values: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in values) buffer.putFloat(f)
        return buffer.array()
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

private const val PREFS_NAME = "indexer_prefs"
private const val KEY_LAST_RUN_MS = "last_run_epoch_ms"


