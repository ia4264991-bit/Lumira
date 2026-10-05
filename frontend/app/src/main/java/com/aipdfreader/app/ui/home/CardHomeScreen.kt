package com.aipdfreader.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
    var showMenu by remember { mutableStateOf(false) }
    var showFeedback by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val shared = selectedTab == 1
    val localCards = state.localCards.map { local ->
        CardDto(id = "local:${local.id}", ownerId = local.ownerUid, name = local.name,
            color = local.color, isShared = false, role = "OWNER")
    }
    val mirroredCardIds = state.localCards.mapNotNull { it.remoteCardId }.toSet()
    val allVisibleCards = if (shared) state.sharedCards else
        state.personalCards.filterNot { it.isShared || it.id in mirroredCardIds } + localCards
    val visibleCards = allVisibleCards.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
    val hasCards = if (shared) state.sharedCards.isNotEmpty()
        else state.personalCards.any { !it.isShared } || localCards.isNotEmpty()
    val context = LocalContext.current

    if (showCreate) {
        CreateCardDialog(
            busy = state.busy,
            startAsCourseSpace = shared,
            onDismiss = { showCreate = false },
            onCreate = { name, color, courseSpace ->
                viewModel.createCard(name, color, courseSpace)
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
    if (showFeedback) FeedbackDialog(onDismiss = { showFeedback = false })
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Vision")
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
                    IconButton(onClick = onOpenNotifications) {
                        Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text("Account and settings") },
                                leadingIcon = { Icon(Icons.Filled.AccountCircle, null) },
                                onClick = { showMenu = false; onOpenAccount() })
                            DropdownMenuItem(text = { Text("Share Vision") },
                                onClick = {
                                    showMenu = false
                                    val playUrl = "https://play.google.com/store/apps/details?id=${context.packageName}"
                                    val send = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "I’m using Vision to organize and study my learning materials. Try it here: $playUrl")
                                    }
                                    context.startActivity(Intent.createChooser(send, "Share Vision"))
                                })
                            DropdownMenuItem(text = { Text("Send feedback") },
                                onClick = { showMenu = false; showFeedback = true })
                            DropdownMenuItem(text = { Text("Refresh") },
                                leadingIcon = { Icon(Icons.Filled.Refresh, null) },
                                enabled = !state.loading,
                                onClick = { showMenu = false; viewModel.refresh() })
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = if (shared) "Create Course Space" else "Create Card")
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) }, label = { Text("Cards") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Groups, contentDescription = null) }, label = { Text("Course Spaces") })
                NavigationBarItem(selected = false, onClick = onOpenLibrary,
                    icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) }, label = { Text("On this phone") })
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                HomeWelcome(
                    showingCourseSpaces = shared,
                    learnerName = state.learnerName,
                    hasCards = hasCards,
                    onCreateCard = { showCreate = true },
                    onBrowseFiles = onOpenLibrary,
                    onJoinCourseSpace = { showJoin = true },
                    onCreateCourseSpace = { showCreate = true }
                )
            }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(if (shared) "Search Course Spaces" else "Search Cards") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (searchQuery.isNotEmpty()) ({
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                        }
                    }) else null,
                    shape = RoundedCornerShape(18.dp)
                )
            }
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
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Checking your online Cards…", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
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
            } else if (visibleCards.isEmpty() && searchQuery.isNotBlank()) {
                item { Text("No ${if (shared) "Course Spaces" else "Cards"} match ‘$searchQuery’.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else if (visibleCards.isEmpty()) {
                item { EmptyCardsState(shared, onCreate = { showCreate = true }, onJoin = { showJoin = true }) }
            } else {
                items(visibleCards.distinctBy { it.id }, key = { it.id }) { card ->
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
private fun HomeWelcome(
    showingCourseSpaces: Boolean,
    learnerName: String,
    hasCards: Boolean,
    onCreateCard: () -> Unit,
    onBrowseFiles: () -> Unit,
    onJoinCourseSpace: () -> Unit,
    onCreateCourseSpace: () -> Unit
) {
    val firstName = learnerName.trim().substringBefore(' ').takeIf(String::isNotBlank)
    val greeting = firstName?.let { "Welcome, $it" } ?: "Welcome to Vision"
    val transition = rememberInfiniteTransition(label = "home-welcome-icon")
    val floatDp by transition.animateFloat(
        initialValue = 0f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "welcome-icon-float"
    )
    AnimatedVisibility(visible = true, enter = fadeIn(tween(500)) + slideInVertically(tween(500)) { it / 8 }) {
        Card(modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(greeting, style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            if (showingCourseSpaces) "Share ideas, materials, and study time with your people."
                            else "A bright little space for everything you’re learning.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Box(
                        Modifier.padding(start = 12.dp).size(62.dp).graphicsLayer {
                            translationY = floatDp.dp.toPx()
                        }.clip(CircleShape).background(MaterialTheme.colorScheme.surface.copy(alpha = .75f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (showingCourseSpaces) Icons.Filled.Groups else Icons.Filled.AutoStories,
                            contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp))
                    }
                }
                if (!hasCards) {
                    Spacer(Modifier.height(14.dp))
                    if (showingCourseSpaces) {
                        Text("Your own Cards still work offline. Shared spaces need a connection.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = onJoinCourseSpace) { Text("Join a space") }
                            TextButton(onClick = onCreateCourseSpace) { Text("Create one") }
                        }
                    } else {
                        Text("Create a Card on this phone and keep studying without internet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = onCreateCard) { Text("Create a Card") }
                            TextButton(onClick = onBrowseFiles) { Text("Browse files") }
                        }
                    }
                }
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
                .background(parseCardColor(card.color)), contentAlignment = Alignment.Center) {
                Icon(if (card.isShared) Icons.Filled.Groups else Icons.Filled.AutoStories,
                    contentDescription = null, tint = Color.White, modifier = Modifier.size(27.dp))
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(5.dp))
                Text("${if (card.isShared) "Course Space" else if (card.id.startsWith("local:")) "On this phone · works offline" else "Personal Card"}${if (card.isShared) card.role?.let { " · $it" }.orEmpty() else ""}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

private fun parseCardColor(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color(0xFF6687E8))

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

private data class CardColorChoice(val label: String, val hex: String)

private val cardColorChoices = listOf(
    CardColorChoice("Blue", "#6687E8"),
    CardColorChoice("Violet", "#8B6BD6"),
    CardColorChoice("Teal", "#2A9D8F"),
    CardColorChoice("Green", "#62A86B"),
    CardColorChoice("Orange", "#E88C45"),
    CardColorChoice("Pink", "#D66B91"),
    CardColorChoice("Red", "#D65D5D")
)

@Composable
private fun CreateCardDialog(
    busy: Boolean,
    startAsCourseSpace: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var shared by remember { mutableStateOf(startAsCourseSpace) }
    var selectedColor by remember { mutableStateOf(cardColorChoices.first().hex) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(if (shared) "Create Course Space" else "Create Card") },
        text = {
            Column {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
                Text("Card colour", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    cardColorChoices.forEach { choice ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(choice.hex)))
                                    .border(
                                        width = if (selectedColor == choice.hex) 3.dp else 0.dp,
                                        color = if (selectedColor == choice.hex) MaterialTheme.colorScheme.onSurface
                                        else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = choice.hex }
                            )
                            Text(choice.label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (shared) "Share as a Course Space" else "Private Card, ready offline",
                            style = MaterialTheme.typography.bodyMedium)
                        Text(if (shared) "Invite others to study together" else "Saved on this phone first; syncs when Vision’s server is available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = shared, onCheckedChange = { shared = it })
                }
            }
        },
        confirmButton = { Button(enabled = name.isNotBlank() && !busy, onClick = {
            onCreate(name, selectedColor, shared)
        }) {
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

@Composable
private fun FeedbackDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var category by remember { mutableStateOf("Idea") }
    var details by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send feedback") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("What would you like us to know?", style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("Idea", "Problem", "Other").forEach { option ->
                        FilterChip(selected = category == option, onClick = { category = option }, label = { Text(option) })
                    }
                }
                OutlinedTextField(value = details, onValueChange = { details = it },
                    label = { Text("Your feedback") }, minLines = 4, modifier = Modifier.fillMaxWidth())
                Text("Choose an app on your phone to share your message.", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(enabled = details.isNotBlank(), onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Vision feedback: $category")
                    putExtra(Intent.EXTRA_TEXT, details.trim())
                }
                context.startActivity(Intent.createChooser(send, "Send Vision feedback"))
                onDismiss()
            }) { Text("Continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
