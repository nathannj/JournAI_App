package com.journai.journai.data.repository

import com.journai.journai.data.dao.ChatDao
import com.journai.journai.data.entity.ChatMessageEntity
import com.journai.journai.data.entity.ChatThread
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val chatDao: ChatDao
) {
    fun getThreads(): Flow<List<ChatThread>> = chatDao.getThreads()
    fun searchThreads(query: String): Flow<List<ChatThread>> = chatDao.searchThreads(query)
    fun getMessages(threadId: String): Flow<List<ChatMessageEntity>> = chatDao.getMessages(threadId)

    suspend fun createThread(initialTitle: String): ChatThread {
        val now = Clock.System.now()
        val thread = ChatThread(
            id = java.util.UUID.randomUUID().toString(),
            title = initialTitle,
            createdAt = now,
            updatedAt = now
        )
        chatDao.insertThread(thread)
        return thread
    }

    suspend fun addMessage(threadId: String, role: String, content: String) {
        val msg = ChatMessageEntity(
            id = java.util.UUID.randomUUID().toString(),
            threadId = threadId,
            role = role,
            content = content,
            createdAt = Clock.System.now()
        )
        chatDao.insertMessage(msg)
        chatDao.updateThreadTitle(threadId, title = content.take(60), updatedAt = Clock.System.now())
    }

    suspend fun setTitle(threadId: String, title: String) {
        chatDao.updateThreadTitle(threadId, title, Clock.System.now())
    }

    suspend fun deleteThread(threadId: String) {
        chatDao.deleteThreadById(threadId)
    }
}


