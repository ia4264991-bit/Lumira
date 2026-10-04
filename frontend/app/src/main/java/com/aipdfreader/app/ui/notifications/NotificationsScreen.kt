package com.aipdfreader.app.ui.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit, viewModel: NotificationsViewModel = hiltViewModel()) {
    val items by viewModel.items.collectAsState()
    val message by viewModel.message.collectAsState()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Notifications") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, "Refresh") } })
    }) { padding ->
        if (items.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(message ?: "You’re all caught up.", style = MaterialTheme.typography.bodyLarge)
            }
        } else LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { notification ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(notification.type.replace('_', ' '), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${notification.createdAt}${if (notification.readAt == null) " · New" else " · Read"}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (notification.readAt == null) TextButton(onClick = { viewModel.markRead(notification.id) }) { Text("Mark read") }
                    }
                }
            }
        }
    }
}
