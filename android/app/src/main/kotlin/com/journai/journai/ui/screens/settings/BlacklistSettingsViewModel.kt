package com.journai.journai.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journai.journai.auth.SecurePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class BlacklistSettingsUiState(
    val blacklistTerms: List<BlacklistTerm> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BlacklistSettingsViewModel @Inject constructor(
    private val securePrefs: SecurePrefs
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(BlacklistSettingsUiState())
    val uiState: StateFlow<BlacklistSettingsUiState> = _uiState.asStateFlow()
    
    private val json = Json { ignoreUnknownKeys = true }
    
    init {
        loadBlacklistTerms()
    }
    
    private fun loadBlacklistTerms() {
        viewModelScope.launch {
            try {
                val termsJson = securePrefs.getString("blacklist_terms", "[]")
                val terms = json.decodeFromString<List<BlacklistTerm>>(termsJson)
                
                _uiState.value = _uiState.value.copy(
                    blacklistTerms = terms,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to load blacklist terms",
                    isLoading = false
                )
            }
        }
    }
    
    private fun saveBlacklistTerms(terms: List<BlacklistTerm>) {
        viewModelScope.launch {
            try {
                val termsJson = json.encodeToString(terms)
                securePrefs.putString("blacklist_terms", termsJson)
                
                _uiState.value = _uiState.value.copy(
                    blacklistTerms = terms
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to save blacklist terms"
                )
            }
        }
    }
    
    fun addTerm(term: String, replacement: String) {
        val newTerm = BlacklistTerm(term, replacement)
        val updatedTerms = _uiState.value.blacklistTerms.toMutableList()
        
        // Remove existing term with same text (case-insensitive)
        updatedTerms.removeAll { it.term.equals(term, ignoreCase = true) }
        updatedTerms.add(newTerm)
        
        saveBlacklistTerms(updatedTerms)
    }
    
    fun updateTerm(oldTerm: String, newTerm: String, replacement: String) {
        val updatedTerms = _uiState.value.blacklistTerms.toMutableList()
        val index = updatedTerms.indexOfFirst { it.term.equals(oldTerm, ignoreCase = true) }
        
        if (index >= 0) {
            updatedTerms[index] = BlacklistTerm(newTerm, replacement)
            saveBlacklistTerms(updatedTerms)
        }
    }
    
    fun removeTerm(term: String) {
        val updatedTerms = _uiState.value.blacklistTerms.filter { 
            !it.term.equals(term, ignoreCase = true) 
        }
        saveBlacklistTerms(updatedTerms)
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
