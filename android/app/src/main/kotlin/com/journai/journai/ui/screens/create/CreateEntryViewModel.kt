package com.journai.journai.ui.screens.create

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journai.journai.data.entity.Entry
import com.journai.journai.data.repository.EntryRepository
import com.journai.journai.data.repository.MediaRepository
import com.journai.journai.data.repository.TagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalTime
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import javax.inject.Inject

@HiltViewModel
class CreateEntryViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
    private val speechRecognitionManager: SpeechRecognitionManager,
    private val organizeService: com.journai.journai.network.OrganizeService
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(CreateEntryUiState())
    val uiState: StateFlow<CreateEntryUiState> = _uiState.asStateFlow()

    private val contentHistory: ArrayDeque<String> = ArrayDeque()
    private val maxHistorySize: Int = 100
    private var isContentChangeInternal: Boolean = false
    private var contentHistorySizeAtOrganize: Int? = null
    
    init {
        loadEntryForDate(Clock.System.now())
        
        // Listen to FINAL speech results and append to content de-duplicating overlap
        viewModelScope.launch {
            speechRecognitionManager.finalText.collect { finalText ->
                android.util.Log.d("SpeechDebug", "finalText received: '$finalText'")
                if (finalText.isNotEmpty()) {
                    val current = _uiState.value.content
                    val toAppend = removeOverlapSuffixPrefix(current, finalText)
                    val newContent = if (current.isBlank()) toAppend else "$current $toAppend"
                    android.util.Log.d("SpeechDebug", "current: '$current', toAppend: '$toAppend', newContent: '$newContent'")
                    _uiState.value = _uiState.value.copy(content = newContent)
                }
            }
        }

        // Listen to PARTIAL speech results and show as live preview (not committed)
        viewModelScope.launch {
            // We'll switch to audio level visualizer instead of text preview
            speechRecognitionManager.partialText.collect { _ ->
                _uiState.value = _uiState.value.copy(partialPreview = "")
            }
        }
        
        // Listen to speech recognition state changes
        viewModelScope.launch {
            speechRecognitionManager.recognitionState.collect { speechState ->
                _uiState.value = _uiState.value.copy(
                    isRecording = speechState.isRecording,
                    isTranscribing = speechState.isTranscribing,
                    recordingStartMs = speechState.recordingStartMs,
                    error = speechState.error
                )
            }
        }

        // Audio level for visualizer
        viewModelScope.launch {
            speechRecognitionManager.audioLevel.collect { level ->
                _uiState.value = _uiState.value.copy(audioLevel = level)
            }
        }
    }
    
    fun selectDate(selectedDate: kotlinx.datetime.LocalDate) {
        viewModelScope.launch {
            try {
                // Convert LocalDate to Instant (start of day)
                val instant = LocalDateTime(selectedDate, LocalTime(0, 0)).toInstant(kotlinx.datetime.TimeZone.currentSystemDefault())
                loadEntryForDate(instant)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Failed to load entry for selected date")
            }
        }
    }
    
    private fun loadEntryForDate(date: kotlinx.datetime.Instant) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                
                val existingEntry = entryRepository.getEntryByDate(date)
                
                if (existingEntry != null) {
                    _uiState.value = _uiState.value.copy(
                        selectedDate = date,
                        entryDate = existingEntry.createdAt, // Use the actual entry's date
                        content = existingEntry.richBody,
                        mood = existingEntry.mood,
                        isEditing = true,
                        existingEntryId = existingEntry.id,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        selectedDate = date,
                        entryDate = date, // For new entries, use the selected date
                        content = "",
                        mood = 3,
                        isEditing = false,
                        existingEntryId = null,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load entry for selected date"
                )
            }
        }
    }
    
    fun updateContent(content: String) {
        val prev = _uiState.value.content
        if (!isContentChangeInternal && prev != content) {
            if (contentHistory.isEmpty() || contentHistory.last() != prev) {
                contentHistory.addLast(prev)
                while (contentHistory.size > maxHistorySize) {
                    contentHistory.removeFirst()
                }
            }
        }
        _uiState.value = _uiState.value.copy(
            content = content,
            canUndoContent = contentHistory.isNotEmpty()
        )
    }
    
    fun updateMood(mood: Int) {
        _uiState.value = _uiState.value.copy(mood = mood)
    }
    
    fun saveEntry() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSaving = true, error = null)
                
                val now = Clock.System.now()
                val existingEntryId = _uiState.value.existingEntryId
                val entryDate = _uiState.value.entryDate // Use the actual entry date
                
                val entry = if (_uiState.value.isEditing && existingEntryId != null) {
                    // Update existing entry - preserve the original entry date
                    Entry(
                        id = existingEntryId,
                        createdAt = entryDate, // Use the entry's actual date
                        editedAt = now,
                        richBody = _uiState.value.content,
                        mood = _uiState.value.mood,
                        isArchived = false
                    )
                } else {
                    // Create new entry for the entry date
                    Entry(
                        id = generateId(),
                        createdAt = entryDate, // Use the entry date
                        editedAt = now,
                        richBody = _uiState.value.content,
                        mood = _uiState.value.mood,
                        isArchived = false
                    )
                }
                
                // Save entry
                entryRepository.saveEntry(entry)
                
                // Mark as saved and update entry date if it's a new entry
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    isEditing = true,
                    existingEntryId = entry.id,
                    entryDate = entry.createdAt
                )
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message ?: "Failed to save entry"
                )
            }
        }
    }
    
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun organise() {
        val current = _uiState.value
        if (current.isOrganizing || current.content.isBlank()) return
        viewModelScope.launch {
            try {
                android.util.Log.d("OrganizeVM", "Starting organise; length=" + current.content.length)
                _uiState.value = current.copy(isOrganizing = true, organizePreview = null, error = null)
                val suggestion = organizeService.organizeText(current.content)
                android.util.Log.d("OrganizeVM", "Organise suggestion received; length=" + suggestion.length)
                _uiState.value = _uiState.value.copy(
                    isOrganizing = false,
                    organizePreview = suggestion,
                    lastOriginalBeforeOrganize = current.content
                )
            } catch (t: Throwable) {
                android.util.Log.e("OrganizeVM", "Organise failed", t)
                _uiState.value = _uiState.value.copy(isOrganizing = false, error = t.message ?: "Failed to organise")
            }
        }
    }

    fun applyOrganize() {
        val preview = _uiState.value.organizePreview ?: return
        isContentChangeInternal = true
        try {
            _uiState.value = _uiState.value.copy(
                content = preview,
                organizePreview = null,
                canUndoOrganize = true
            )
            // Mark the boundary where organize was applied so we undo typing first
            contentHistorySizeAtOrganize = contentHistory.size
        } finally {
            isContentChangeInternal = false
        }
    }

    fun discardOrganize() {
        _uiState.value = _uiState.value.copy(organizePreview = null)
    }

    fun undoOrganize() {
        val original = _uiState.value.lastOriginalBeforeOrganize ?: return
        isContentChangeInternal = true
        try {
            _uiState.value = _uiState.value.copy(
                content = original,
                canUndoOrganize = false,
                lastOriginalBeforeOrganize = null
            )
            // Drop any history entries that were added after organize was applied
            val marker = contentHistorySizeAtOrganize
            if (marker != null) {
                while (contentHistory.size > marker) {
                    contentHistory.removeLast()
                }
            }
            contentHistorySizeAtOrganize = null
            // Update content undo availability
            _uiState.value = _uiState.value.copy(
                canUndoContent = contentHistory.isNotEmpty()
            )
        } finally {
            isContentChangeInternal = false
        }
    }

    fun undo() {
        val marker = contentHistorySizeAtOrganize
        if (marker != null && contentHistory.size > marker) {
            // There are edits after organize; undo typing first
            undoContent()
        } else if (_uiState.value.canUndoOrganize) {
            // No edits after organize; revert organize now
            undoOrganize()
        } else {
            // Regular content undo
            undoContent()
        }
    }

    private fun undoContent() {
        if (contentHistory.isEmpty()) return
        val previous = contentHistory.removeLast()
        isContentChangeInternal = true
        try {
            _uiState.value = _uiState.value.copy(
                content = previous,
                canUndoContent = contentHistory.isNotEmpty()
            )
        } finally {
            isContentChangeInternal = false
        }
    }

    private fun removeOverlapSuffixPrefix(base: String, addition: String): String {
        if (base.isBlank() || addition.isBlank()) return addition
        val baseTrim = base.trimEnd()
        val addTrim = addition.trimStart()
        val maxOverlap = minOf(baseTrim.length, addTrim.length)
        for (len in maxOverlap downTo 1) {
            if (baseTrim.takeLast(len).equals(addTrim.take(len), ignoreCase = true)) {
                return addTrim.drop(len)
            }
        }
        return addition
    }
    
    fun startVoiceRecording(activity: androidx.activity.ComponentActivity) {
        // Ensure clean state before starting new recording
        speechRecognitionManager.resetState()
        speechRecognitionManager.startListening(activity)
    }
    
    fun stopVoiceRecording() {
        speechRecognitionManager.stopListening()
    }
    
    fun cancelVoiceRecording() {
        speechRecognitionManager.cancelListening()
    }
    
    override fun onCleared() {
        super.onCleared()
        speechRecognitionManager.destroy()
    }
    
    private fun generateId(): String = java.util.UUID.randomUUID().toString()
}

data class CreateEntryUiState(
    val selectedDate: kotlinx.datetime.Instant = Clock.System.now(),
    val entryDate: kotlinx.datetime.Instant = Clock.System.now(), // The actual date of the entry being edited
    val content: String = "",
    val partialPreview: String = "",
    val audioLevel: Float = 0f,
    val mood: Int = 3,
    val isSaving: Boolean = false,
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val existingEntryId: String? = null,
    val isRecording: Boolean = false,
    val isTranscribing: Boolean = false,
    val recordingStartMs: Long? = null,
    val error: String? = null,
    val isOrganizing: Boolean = false,
    val organizePreview: String? = null,
    val lastOriginalBeforeOrganize: String? = null,
    val canUndoOrganize: Boolean = false,
    val canUndoContent: Boolean = false
)
