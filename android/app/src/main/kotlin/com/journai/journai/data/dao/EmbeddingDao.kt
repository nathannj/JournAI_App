package com.journai.journai.data.dao

import androidx.room.*
import com.journai.journai.data.entity.Embedding
import kotlinx.coroutines.flow.Flow

@Dao
interface EmbeddingDao {
    @Query("SELECT * FROM embeddings WHERE entryId = :entryId")
    fun getEmbeddingsForEntry(entryId: String): Flow<List<Embedding>>
    
    @Query("SELECT * FROM embeddings WHERE id = :id")
    suspend fun getEmbeddingById(id: String): Embedding?
    
    @Query("SELECT * FROM embeddings WHERE model = :model ORDER BY createdAt DESC")
    fun getEmbeddingsByModel(model: String): Flow<List<Embedding>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbedding(embedding: Embedding)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmbeddings(embeddings: List<Embedding>)
    
    @Update
    suspend fun updateEmbedding(embedding: Embedding)
    
    @Delete
    suspend fun deleteEmbedding(embedding: Embedding)
    
    @Query("DELETE FROM embeddings WHERE entryId = :entryId")
    suspend fun deleteEmbeddingsForEntry(entryId: String)
    
    @Query("DELETE FROM embeddings WHERE model = :model")
    suspend fun deleteEmbeddingsByModel(model: String)

    @Query("DELETE FROM embeddings WHERE entryId = :entryId AND model = :model")
    suspend fun deleteEmbeddingsForEntryAndModel(entryId: String, model: String)

    @Query("SELECT COUNT(*) FROM embeddings WHERE entryId = :entryId AND model = :model")
    suspend fun countEmbeddingsForEntryAndModel(entryId: String, model: String): Int

    @Query("SELECT * FROM embeddings WHERE model = :model")
    suspend fun getAllByModel(model: String): List<Embedding>

    @Query("SELECT * FROM embeddings")
    suspend fun getAll(): List<Embedding>
}
