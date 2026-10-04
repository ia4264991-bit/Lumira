package com.aipdfreader.app.ui.sarah

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContextualSarahScreen(onBack: () -> Unit, viewModel: ContextualSarahViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Ask Sarah") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            state.selectedText?.let { selected ->
                Card(Modifier.fillMaxWidth()) { Text("Selected passage: $selected", Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall, maxLines = 4) }
            }
            state.usage?.let { Text("Sarah usage: $it", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp)) }
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.messages) { (role, text) ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor =
                        if (role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Text(text, Modifier.padding(14.dp))
                    }
                }
            }
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = state.draft, onValueChange = viewModel::setDraft, label = { Text("Ask about this PDF") },
                    modifier = Modifier.weight(1f), maxLines = 4)
                Button(onClick = viewModel::send, enabled = state.draft.isNotBlank() && !state.busy,
                    modifier = Modifier.padding(start = 8.dp)) { Text(if (state.busy) "…" else "Send") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
