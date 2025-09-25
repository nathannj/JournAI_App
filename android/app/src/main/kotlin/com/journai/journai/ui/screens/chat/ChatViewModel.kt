package com.journai.journai.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journai.journai.data.repository.EntryRepository
import com.journai.journai.data.repository.ChatRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.Job
import com.journai.journai.chat.ChatService
import com.journai.journai.network.ChatMessage as NetChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    private val chatService: ChatService,
    private val chatRepository: ChatRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
    private var currentThreadId: String? = null
    private var messagesJob: Job? = null

    fun sendMessage(message: String) {
        if (message.isBlank()) return
        
        viewModelScope.launch {
            try {
                // Add user message
                val userMessage = ChatMessage(text = message, isUser = true)
                val updatedMessages = _uiState.value.messages + userMessage
                _uiState.value = _uiState.value.copy(
                    messages = updatedMessages,
                    isGeneratingResponse = true,
                    error = null
                )

                // Ensure thread exists; create on first message
                val threadId = currentThreadId ?: run {
                    val t = chatRepository.createThread(initialTitle = message.take(60))
                    currentThreadId = t.id
                    t.id
                }
                chatRepository.addMessage(threadId, role = "user", content = message)
                
                // Build messages and call proxy via ChatService
                val netMessages = buildNetMessages(updatedMessages)
                val aiResponse = chatService.complete(netMessages)
                val aiMessage = ChatMessage(text = aiResponse, isUser = false)
                
                _uiState.value = _uiState.value.copy(
                    messages = updatedMessages + aiMessage,
                    isGeneratingResponse = false
                )

                chatRepository.addMessage(threadId, role = "assistant", content = aiResponse)

                // Title refinement: let AI propose a concise title based on first exchange
                if (updatedMessages.size == 1) {
                    runCatching {
                        val titlePrompt = listOf(
                            com.journai.journai.network.ChatMessage(role = "system", content = "Generate a 3-6 word concise title for this chat. Return only the title."),
                            com.journai.journai.network.ChatMessage(role = "user", content = message + "\n\nAssistant: " + aiResponse.take(200))
                        )
                        val title = chatService.complete(titlePrompt, useCache = false).lines().firstOrNull()?.take(60)?.trim('"',' ')
                        if (!title.isNullOrBlank()) chatRepository.setTitle(threadId, title)
                    }
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingResponse = false,
                    error = e.message ?: "Failed to send message"
                )
            }
        }
    }

    init {
        viewModelScope.launch {
            combine(
                chatRepository.getThreads(),
                _uiState
            ) { threads, ui ->
                val q = ui.threadSearchQuery.trim()
                val filtered = if (q.isBlank()) threads else threads.filter { it.title.contains(q, ignoreCase = true) }
                filtered.map { ChatThreadItem(it.id, it.title, it.updatedAt.toEpochMilliseconds()) }
            }.collectLatest { list ->
                _uiState.value = _uiState.value.copy(threads = list)
            }
        }
    }

    fun updateThreadSearch(query: String) {
        _uiState.value = _uiState.value.copy(threadSearchQuery = query)
    }

    fun selectThread(threadId: String) {
        currentThreadId = threadId
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.getMessages(threadId).collectLatest { list ->
                val mapped = list.map {
                    ChatMessage(
                        text = it.content,
                        isUser = it.role.equals("user", ignoreCase = true),
                        timestamp = it.createdAt.toEpochMilliseconds()
                    )
                }
                _uiState.value = _uiState.value.copy(
                    messages = mapped,
                    isGeneratingResponse = false,
                    error = null
                )
            }
        }
    }

    fun setSidebarVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showSidebar = visible)
    }

    fun requestDeleteThread(threadId: String) {
        _uiState.value = _uiState.value.copy(confirmDeleteThreadId = threadId)
    }

    fun cancelDeleteThread() {
        _uiState.value = _uiState.value.copy(confirmDeleteThreadId = null)
    }

    fun confirmDeleteThread() {
        val id = _uiState.value.confirmDeleteThreadId ?: return
        viewModelScope.launch {
            runCatching { chatRepository.deleteThread(id) }
            _uiState.value = _uiState.value.copy(confirmDeleteThreadId = null)
            if (currentThreadId == id) {
                currentThreadId = null
                _uiState.value = _uiState.value.copy(messages = emptyList())
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearChat() {
        _uiState.value = _uiState.value.copy(messages = emptyList())
    }
    
    private fun buildNetMessages(messages: List<ChatMessage>): List<NetChatMessage> {
        val system = NetChatMessage(role = "system", content = "You are JournAI, a helpful, private journal assistant.")
        val mapped = messages.map {
            if (it.isUser) NetChatMessage(role = "user", content = it.text)
            else NetChatMessage(role = "assistant", content = it.text)
        }
        return listOf(system) + mapped
    }
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGeneratingResponse: Boolean = false,
    val error: String? = null,
    val threads: List<ChatThreadItem> = emptyList(),
    val threadSearchQuery: String = "",
    val showSidebar: Boolean = false,
    val confirmDeleteThreadId: String? = null
)

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatThreadItem(
    val id: String,
    val title: String,
    val updatedAt: Long
)

