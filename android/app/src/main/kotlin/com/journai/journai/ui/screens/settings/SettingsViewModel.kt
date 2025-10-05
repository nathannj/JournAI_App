package com.journai.journai.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journai.journai.auth.SecurePrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val useLocalTranscription: Boolean = true,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securePrefs: SecurePrefs
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    
    init {
        loadSettings()
    }
    
    private fun loadSettings() {
        viewModelScope.launch {
            try {
                val useLocalTranscription = securePrefs.getBoolean(
                    "use_local_transcription", 
                    true // Default to local transcription
                )
                
                _uiState.value = _uiState.value.copy(
                    useLocalTranscription = useLocalTranscription,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to load settings",
                    isLoading = false
                )
            }
        }
    }
    
    fun setUseLocalTranscription(useLocal: Boolean) {
        viewModelScope.launch {
            try {
                securePrefs.putBoolean("use_local_transcription", useLocal)
                _uiState.value = _uiState.value.copy(
                    useLocalTranscription = useLocal
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to save setting"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
