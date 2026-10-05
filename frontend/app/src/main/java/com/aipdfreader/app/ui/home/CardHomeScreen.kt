package com.aipdfreader.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aipdfreader.app.data.remote.dto.CardDto
import com.aipdfreader.app.data.remote.dto.DirectInvitationDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardHomeScreen(
    onOpenLibrary: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenCard: (CardDto) -> Unit,
    viewModel: CardHomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    var showInvitations by remember { mutableStateOf(false) }
    val shared = selectedTab == 1
    val visibleCards = if (shared) state.sharedCards else state.personalCards.filterNot { it.isShared }

    if (showCreate) {
        CreateCardDialog(
            busy = state.busy,
            onDismiss = { showCreate = false },
            onCreate = { name, courseSpace ->
                viewModel.createCard(name, "#6687E8", courseSpace)
                showCreate = false
            }
        )
    }
    if (showJoin) {
        JoinCourseSpaceDialog(
            busy = state.busy,
            onDismiss = { showJoin = false },
            onJoin = { link -> viewModel.joinCourseSpace(link); showJoin = false }
        )
    }

    if (showInvitations) {
        DirectInvitationInboxDialog(
            state = state,
            onDismiss = { showInvitations = false },
            onAccept = viewModel::acceptInvitation,
            onDecline = viewModel::declineInvitation,
            onRefresh = viewModel::refresh
        )
    }
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Lumira")
                        Text(
                            if (shared) "Learn together" else "Your learning space",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showInvitations = true }) {
                        BadgedBox(badge = {
                            if (state.invitations.isNotEmpty()) {
                                Badge { Text(if (state.invitations.size > 9) "9+" else state.invitations.size.toString()) }
                            }
                        }) {
                            Icon(Icons.Filled.MailOutline, contentDescription = "Course Space invitations")
                        }
                    }
                    IconButton(onClick = onOpenAccount) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Account")
                    }
                    IconButton(onClick = onOpenNotifications) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                    }
                    IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Create Card")
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) }, label = { Text("Cards") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Groups, contentDescription = null) }, label = { Text("Course Spaces") })
                NavigationBarItem(selected = false, onClick = onOpenLibrary,
                    icon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) }, label = { Text("PDF library") })
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { HomeWelcome(shared) }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (shared) "Shared with you" else "Your Cards",
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("${visibleCards.size}", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.loading && visibleCards.isEmpty()) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                } }
            } else if (state.error && visibleCards.isEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Text(state.message.orEmpty(), color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = viewModel::refresh) { Text("Try again") }
                        }
                    }
                }
            } else if (visibleCards.isEmpty()) {
                item { EmptyCardsState(shared, onCreate = { showCreate = true }, onJoin = { showJoin = true }) }
            } else {
                items(visibleCards, key = { it.id }) { card ->
                    HomeCardItem(card = card, onClick = { onOpenCard(card) })
                }
            }
            state.message?.let { message ->
                item {
                    Text(message, color = if (state.error) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (shared) item { TextButton(onClick = { showJoin = true }) { Text("Join with an invite link") } }
        }
    }
}

@Composable
private fun DirectInvitationInboxDialog(
    state: CardHomeUiState,
    onDismiss: () -> Unit,
    onAccept: (String) -> Unit,
    onDecline: (String) -> Unit,
    onRefresh: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Course Space invitations") },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (state.invitationsLoading && state.invitations.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (state.invitations.isEmpty()) {
                    item {
                        Column {
                            Text(
                                state.invitationMessage ?: "No pending invitations.",
                                color = if (state.invitationError) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (state.invitationError) TextButton(onClick = onRefresh) { Text("Try again") }
                        }
                    }
                } else {
                    state.invitationMessage?.let { message ->
                        item {
                            Column {
                                Text(
                                    message,
                                    color = if (state.invitationError) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                                if (state.invitationError) TextButton(onClick = onRefresh) { Text("Try again") }
                            }
                        }
                    }
                    items(state.invitations, key = { it.membershipId }) { invitation ->
                        val responding = state.invitationActionMembershipId != null
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text("You’ve been invited to a Course Space", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Space ID ending in ${invitation.cardId.takeLast(8)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                invitation.createdAt?.substringBefore('T')?.takeIf { it.isNotBlank() }?.let { date ->
                                    Text("Received $date", style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Row {
                                    TextButton(enabled = !responding, onClick = { onAccept(invitation.membershipId) }) {
                                        Text("Accept")
                                    }
                                    TextButton(enabled = !responding, onClick = { onDecline(invitation.membershipId) }) {
                                        Text("Decline")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
@Composable
private fun HomeWelcome(showingCourseSpaces: Boolean) {
    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (showingCourseSpaces) "Study is better together" else "Pick up where you left off",
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(6.dp))
                Text(if (showingCourseSpaces) "Your shared Cards and study materials, all in one place."
                else "Keep each subject and its materials together in a Card.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Box(Modifier.padding(start = 12.dp).size(52.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .7f)), contentAlignment = Alignment.Center) {
                Icon(if (showingCourseSpaces) Icons.Filled.Groups else Icons.Filled.AutoStories,
                    contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun HomeCardItem(card: CardDto, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(if (card.isShared) Icons.Filled.Groups else Icons.Filled.AutoStories,
                    contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(27.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text("${if (card.isShared) "Course Space" else "Personal Card"}${if (card.isShared) card.role?.let { " · $it" }.orEmpty() else ""}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun EmptyCardsState(showingCourseSpaces: Boolean, onCreate: () -> Unit, onJoin: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(if (showingCourseSpaces) Icons.Filled.Groups else Icons.Filled.AutoStories, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(42.dp))
        Spacer(Modifier.height(12.dp))
        Text(if (showingCourseSpaces) "No Course Spaces yet" else "No Cards yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(if (showingCourseSpaces) "Join a Course Space with an invite link."
        else "Create a Card for a subject and its study materials.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        Button(onClick = if (showingCourseSpaces) onJoin else onCreate) {
            Text(if (showingCourseSpaces) "Join Course Space" else "Create your first Card")
        }
    }
}

@Composable
private fun CreateCardDialog(busy: Boolean, onDismiss: () -> Unit, onCreate: (String, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var shared by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (shared) "Create Course Space" else "Create Card") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Share as a Course Space", style = MaterialTheme.typography.bodyMedium)
                        Text("Invite others to study together", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = shared, onCheckedChange = { shared = it })
                }
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank() && !busy, onClick = { onCreate(name, shared) }) {
            Text(if (busy) "Creating…" else "Create")
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun JoinCourseSpaceDialog(busy: Boolean, onDismiss: () -> Unit, onJoin: (String) -> Unit) {
    var link by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Join a Course Space") },
        text = { OutlinedTextField(value = link, onValueChange = { link = it }, label = { Text("Invite link or token") },
            singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(enabled = link.isNotBlank() && !busy, onClick = { onJoin(link) }) {
            Text(if (busy) "Joining…" else "Join")
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
