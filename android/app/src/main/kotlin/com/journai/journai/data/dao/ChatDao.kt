package com.journai.journai.data.dao

import androidx.room.*
import com.journai.journai.data.entity.ChatThread
import com.journai.journai.data.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_threads ORDER BY updatedAt DESC")
    fun getThreads(): Flow<List<ChatThread>>

    @Query("SELECT * FROM chat_threads ORDER BY updatedAt DESC")
    suspend fun getThreadsOnce(): List<ChatThread>

    @Query("SELECT * FROM chat_threads WHERE title LIKE '%' || :query || '%' ORDER BY updatedAt DESC")
    fun searchThreads(query: String): Flow<List<ChatThread>>

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun getMessages(threadId: String): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThread(thread: ChatThread)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<ChatMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity)

    @Query("UPDATE chat_threads SET title = :title, updatedAt = :updatedAt WHERE id = :threadId")
    suspend fun updateThreadTitle(threadId: String, title: String, updatedAt: kotlinx.datetime.Instant)

    @Query("DELETE FROM chat_threads WHERE id = :threadId")
    suspend fun deleteThreadById(threadId: String)
}


