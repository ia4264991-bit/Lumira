package com.aipdfreader.app.ui.notifications

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aipdfreader.app.R
import com.aipdfreader.app.notifications.PushPermissionPolicy

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(onBack: () -> Unit, viewModel: NotificationsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var permissionGranted by remember {
        mutableStateOf(!PushPermissionPolicy.requiresRuntimePermission(Build.VERSION.SDK_INT) || ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { permissionGranted = it }
    val items by viewModel.items.collectAsState()
    val message by viewModel.message.collectAsState()
    Scaffold(topBar = {
        TopAppBar(title = { Text("Notifications") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { IconButton(onClick = viewModel::refresh) { Icon(Icons.Filled.Refresh, "Refresh") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (PushPermissionPolicy.requiresRuntimePermission(Build.VERSION.SDK_INT) && !permissionGranted) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.push_permission_title), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.push_permission_description), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text(stringResource(R.string.push_permission_action))
                        }
                    }
                }
            }
            if (items.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                    Text(message ?: "You’re all caught up.", style = MaterialTheme.typography.bodyLarge)
                }
            } else LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
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
}
