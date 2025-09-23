package com.journai.journai.ui.screens.create

import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journai.journai.R
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateEntryScreen(
    viewModel: CreateEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    val selectedDate = uiState.selectedDate.toLocalDateTime(TimeZone.currentSystemDefault()).date
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    
    // Permission launcher for microphone access
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceRecording(context as ComponentActivity)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Date header - tappable
        Card(
            onClick = { showDatePicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (selectedDate == today) {
                        "Today, $selectedDate"
                    } else {
                        "$selectedDate"
                    },
                    style = MaterialTheme.typography.titleLarge
                )
                if (uiState.isEditing) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(Existing entry)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (selectedDate != today) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(New entry)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (uiState.isLoading) {
                    Spacer(modifier = Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
        
        // Date picker dialog
        if (showDatePicker) {
            Dialog(onDismissRequest = { showDatePicker = false }) {
                Card(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Select Date",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        val datePickerState = rememberDatePickerState(
                            initialSelectedDateMillis = uiState.selectedDate.toEpochMilliseconds()
                        )
                        
                        DatePicker(state = datePickerState)
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { showDatePicker = false }) {
                                Text("Cancel")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    datePickerState.selectedDateMillis?.let { millis ->
                                        val selectedLocalDate = LocalDate.fromEpochDays((millis / (1000 * 60 * 60 * 24)).toInt())
                                        viewModel.selectDate(selectedLocalDate)
                                    }
                                    showDatePicker = false
                                }
                            ) {
                                Text("OK")
                            }
                        }
                    }
                }
            }
        }
        
        // Content input (committed text only)
        OutlinedTextField(
            value = uiState.content,
            onValueChange = { viewModel.updateContent(it) },
            label = { Text(stringResource(R.string.entry_content)) },
            placeholder = { Text("What's on your mind today?") },
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp),
            maxLines = 15
        )
        
        // Action buttons row
        if (uiState.isRecording) {
            // Recording layout: Stop + streaming visual in the same row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val now by rememberUpdatedState(Clock.System.now().toEpochMilliseconds())
                        val startedAt = uiState.recordingStartMs
                        var elapsedText by remember { mutableStateOf("00:00") }
                        LaunchedEffect(startedAt) {
                            if (startedAt != null) {
                                while (true) {
                                    val elapsedMs = System.currentTimeMillis() - startedAt
                                    val totalSeconds = (elapsedMs / 1000).toInt()
                                    val mm = (totalSeconds / 60).toString().padStart(2, '0')
                                    val ss = (totalSeconds % 60).toString().padStart(2, '0')
                                    elapsedText = "$mm:$ss"
                                    delay(250)
                                }
                            }
                        }
                        Text(
                            text = "Recording · $elapsedText",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        AudioWave(
                            level = uiState.audioLevel,
                            bars = 72,
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { viewModel.stopVoiceRecording() }
                ) {
                    Icon(
                        Icons.Default.Stop,
                        contentDescription = "Stop recording",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        } else if (uiState.isTranscribing) {
            // Post-stop transcribing state
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Transcribing...",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            // Idle layout: Organise + Undo + Mic
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        android.util.Log.d("OrganizeUI", "Organise tapped")
                        viewModel.organise()
                    },
                    enabled = uiState.content.isNotBlank() && !uiState.isOrganizing
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (uiState.isOrganizing) "Organising..." else "Organise")
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = uiState.canUndoOrganize || uiState.canUndoContent
                    ) {
                        Icon(
                            Icons.Default.Undo,
                            contentDescription = "Undo",
                            tint = if (uiState.canUndoOrganize || uiState.canUndoContent) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO) }
                    ) {
                        Icon(
                            Icons.Default.Mic,
                            contentDescription = "Record voice note",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        
        // Removed separate recording indicator (now inline with Stop button)
        
        // Mood picker
        Card {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Mood, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "How are you feeling today?",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    (1..5).forEach { mood ->
                        FilterChip(
                            onClick = { viewModel.updateMood(mood) },
                            label = { Text(mood.toString()) },
                            selected = uiState.mood == mood,
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Mood,
                                    contentDescription = "Mood $mood"
                                )
                            }
                        )
                    }
                }
            }
        }
        
        // Save button
        Button(
            onClick = { viewModel.saveEntry() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSaving && uiState.content.isNotBlank()
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                when {
                    uiState.isSaving -> "Saving..."
                    uiState.isEditing -> "Update Entry"
                    else -> "Save Entry"
                }
            )
        }
        
        // Error message
        uiState.error?.let { error ->
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }

        // Organize preview dialog
        uiState.organizePreview?.let { preview ->
            OrganizePreviewDialog(
                preview = preview,
                onApply = { viewModel.applyOrganize() },
                onDiscard = { viewModel.discardOrganize() }
            )
        }
    }
}

@Composable
private fun OrganizePreviewDialog(
    preview: String,
    onApply: () -> Unit,
    onDiscard: () -> Unit
) {
    Dialog(onDismissRequest = onDiscard, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Organized draft",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    tonalElevation = 2.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 480.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(text = preview)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDiscard) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onApply) { Text("Apply") }
                }
            }
        }
    }
}

@Composable
private fun AudioWave(level: Float, bars: Int = 48, modifier: Modifier = Modifier) {
    val clamped = level.coerceIn(0f, 1f)
    val latest by rememberUpdatedState(clamped)
    val samples = remember { mutableStateListOf<Float>().apply { repeat(bars) { add(0f) } } }
    // Two-pole IIR smoothing: fast rise, slow decay to show dips
    var smooth by remember { mutableStateOf(0f) }
    var smooth2 by remember { mutableStateOf(0f) }
    LaunchedEffect(bars) {
        val intervalMs = (700L / bars.toLong()).coerceAtLeast(5L)
        while (true) {
            val target = (0.015f + 0.985f * latest).coerceIn(0f, 1f)
            val attack = 0.65f
            val release = 0.15f
            val alpha = if (target > smooth) attack else release
            smooth = smooth + alpha * (target - smooth)
            // second stage to reduce jitter and make a smooth curve
            smooth2 = smooth2 + 0.35f * (smooth - smooth2)
            samples.removeAt(0)
            samples.add(smooth2)
            delay(intervalMs)
        }
    }
    val waveColor = MaterialTheme.colorScheme.onErrorContainer
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val barWidth = w / samples.size
        val pad = 2f
        samples.forEachIndexed { index, v ->
            val x = index * barWidth
            val half = (h / 2f) - pad
            val mag = half * v
            drawRect(
                color = waveColor,
                topLeft = androidx.compose.ui.geometry.Offset(x, (h / 2f) - mag),
                size = androidx.compose.ui.geometry.Size(barWidth * 0.9f, mag * 2f)
            )
        }
    }
}