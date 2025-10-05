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

data class OrganizationPreferencesUiState(
    val organizeInstruction: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class OrganizationPreferencesViewModel @Inject constructor(
    private val securePrefs: SecurePrefs
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(OrganizationPreferencesUiState())
    val uiState: StateFlow<OrganizationPreferencesUiState> = _uiState.asStateFlow()
    
    init {
        loadPreferences()
    }
    
    private fun loadPreferences() {
        viewModelScope.launch {
            try {
                val instruction = securePrefs.getString("org_pref_instruction", "")
                _uiState.value = _uiState.value.copy(
                    organizeInstruction = instruction,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to load preferences",
                    isLoading = false
                )
            }
        }
    }

    fun setOrganizeInstruction(instruction: String) {
        viewModelScope.launch {
            try {
                securePrefs.putString("org_pref_instruction", instruction)
                _uiState.value = _uiState.value.copy(organizeInstruction = instruction)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = e.message ?: "Failed to save preference"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
