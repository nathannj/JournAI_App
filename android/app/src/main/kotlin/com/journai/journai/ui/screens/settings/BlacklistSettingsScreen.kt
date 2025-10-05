package com.journai.journai.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.journai.journai.R
import kotlinx.serialization.Serializable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlacklistSettingsScreen(
    navController: NavController,
    viewModel: BlacklistSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTerm by remember { mutableStateOf<String?>(null) }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Top App Bar
        TopAppBar(
            title = { Text("Blacklist & Privacy") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Configure terms that should be redacted before sending to AI services",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Add new term button
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Redaction Rule")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Terms list
            if (uiState.blacklistTerms.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No redaction rules yet",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add terms you want to keep private",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.blacklistTerms) { term ->
                        BlacklistTermCard(
                            term = term,
                            onEdit = { editingTerm = term.term },
                            onDelete = { viewModel.removeTerm(term.term) }
                        )
                    }
                }
            }
        }
    }
    
    // Add/Edit Dialog
    if (showAddDialog || editingTerm != null) {
        AddBlacklistTermDialog(
            initialTerm = editingTerm?.let { term ->
                uiState.blacklistTerms.find { it.term == term }
            },
            onDismiss = { 
                showAddDialog = false
                editingTerm = null
            },
            onSave = { term, replacement ->
                if (editingTerm != null) {
                    viewModel.updateTerm(editingTerm!!, term, replacement)
                } else {
                    viewModel.addTerm(term, replacement)
                }
                showAddDialog = false
                editingTerm = null
            }
        )
    }
}

@Composable
fun BlacklistTermCard(
    term: BlacklistTerm,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = term.term,
                    style = MaterialTheme.typography.bodyLarge
                )
                if (term.replacement.isNotEmpty()) {
                    Text(
                        text = "→ ${term.replacement}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "→ [REDACTED]",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Composable
fun AddBlacklistTermDialog(
    initialTerm: BlacklistTerm? = null,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var termText by remember { mutableStateOf(initialTerm?.term ?: "") }
    var replacementText by remember { mutableStateOf(initialTerm?.replacement ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialTerm != null) "Edit Redaction Rule" else "Add Redaction Rule") },
        text = {
            Column {
                OutlinedTextField(
                    value = termText,
                    onValueChange = { termText = it },
                    label = { Text("Term to redact") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = replacementText,
                    onValueChange = { replacementText = it },
                    label = { Text("Replacement (leave empty for [REDACTED])") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (termText.isNotBlank()) {
                        onSave(termText.trim(), replacementText.trim())
                    }
                },
                enabled = termText.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Serializable
data class BlacklistTerm(
    val term: String,
    val replacement: String = ""
)
