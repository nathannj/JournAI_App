package com.journai.journai.ui.screens.entries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journai.journai.data.repository.EntryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EntriesViewModel @Inject constructor(
    private val entryRepository: EntryRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(EntriesUiState())
    val uiState: StateFlow<EntriesUiState> = _uiState.asStateFlow()
    
    init {
        loadEntries()
    }
    
    fun loadEntries() {
        viewModelScope.launch {
            entryRepository.getAllEntries().collect { entries ->
                _uiState.value = _uiState.value.copy(
                    entries = entries,
                    filteredEntries = entries
                )
            }
        }
    }
    
    fun searchEntries(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        
        viewModelScope.launch {
            if (query.isBlank()) {
                _uiState.value = _uiState.value.copy(filteredEntries = _uiState.value.entries)
            } else {
                entryRepository.searchEntriesSimple(query).collect { searchResults ->
                    _uiState.value = _uiState.value.copy(filteredEntries = searchResults)
                }
            }
        }
    }
    
    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            filteredEntries = _uiState.value.entries
        )
    }
    
    fun filterByMood(mood: Int?) {
        _uiState.value = _uiState.value.copy(selectedMood = mood)
        
        val baseEntries = _uiState.value.entries
        val filtered = if (mood != null) {
            baseEntries.filter { it.mood == mood }
        } else {
            baseEntries
        }
        
        _uiState.value = _uiState.value.copy(filteredEntries = filtered)
    }
}

data class EntriesUiState(
    val entries: List<com.journai.journai.data.entity.Entry> = emptyList(),
    val filteredEntries: List<com.journai.journai.data.entity.Entry> = emptyList(),
    val searchQuery: String = "",
    val selectedMood: Int? = null,
    val isLoading: Boolean = false
)
