package com.journai.journai.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journai.journai.data.repository.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val entryRepository: EntryRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    
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
                
                // Simulate AI response (placeholder for now)
                // TODO: Integrate with actual AI service
                val aiResponse = generatePlaceholderResponse(message)
                val aiMessage = ChatMessage(text = aiResponse, isUser = false)
                
                _uiState.value = _uiState.value.copy(
                    messages = updatedMessages + aiMessage,
                    isGeneratingResponse = false
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isGeneratingResponse = false,
                    error = e.message ?: "Failed to send message"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    fun clearChat() {
        _uiState.value = _uiState.value.copy(messages = emptyList())
    }
    
    private fun generatePlaceholderResponse(userMessage: String): String {
        // Simple placeholder responses based on keywords
        return when {
            userMessage.contains("mood", ignoreCase = true) -> 
                "I can help you analyze your mood patterns! I can see you have entries with different moods. Would you like me to show you trends over time?"
            
            userMessage.contains("entry", ignoreCase = true) || userMessage.contains("entries", ignoreCase = true) -> 
                "I can help you search through your journal entries. What would you like to find?"
            
            userMessage.contains("tag", ignoreCase = true) -> 
                "I can help you organize entries by tags. What topics are you interested in exploring?"
            
            userMessage.contains("today", ignoreCase = true) || userMessage.contains("recent", ignoreCase = true) -> 
                "Let me look at your recent entries to help you reflect on what's been happening lately."
            
            else -> 
                "I'm your AI journal companion! I can help you explore your entries, analyze patterns, and reflect on your thoughts. What would you like to know about your journal?"
        }
    }
}

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isGeneratingResponse: Boolean = false,
    val error: String? = null
)

data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
