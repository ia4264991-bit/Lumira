package com.aipdfreader.app.ui.workspace

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aipdfreader.app.data.remote.dto.*

private val sections = listOf("Resources", "Notes", "Study Sets", "Quizzes", "Flashcards", "Sarah", "Updates", "People")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardWorkspaceScreen(
    cardId: String,
    cardName: String,
    isShared: Boolean,
    callerRole: String,
    onBack: () -> Unit,
    onOpenPdf: (Long, String) -> Unit,
    viewModel: CardWorkspaceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var section by remember { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var editingNote by remember { mutableStateOf<NoteDto?>(null) }
    var editingStudySet by remember { mutableStateOf<StudySetDto?>(null) }
    var activeQuiz by remember { mutableStateOf<QuizDto?>(null) }
    var activeFlashcards by remember { mutableStateOf<FlashcardSetDto?>(null) }
    var showSharing by remember { mutableStateOf(false) }
    var showAttempts by remember { mutableStateOf(false) }
    var sharedNow by remember(isShared) { mutableStateOf(isShared) }
    var activeRole by remember(callerRole) { mutableStateOf(callerRole) }
    val clipboard = LocalClipboardManager.current
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.uploadPdf(cardId, it) }
    }
    LaunchedEffect(cardId, sharedNow) { viewModel.load(cardId, sharedNow) }

    if (showSharing) {
        AlertDialog(onDismissRequest = { showSharing = false }, title = { Text("Course Space sharing") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!sharedNow) {
                    Text("Turn this Card into a shared Course Space. The Card stays in place.")
                    Button(onClick = { viewModel.enableCourseSpace(cardId) { sharedNow = true; activeRole = "OWNER" } }, enabled = !state.busy) { Text("Enable sharing") }
                } else {
                    Text("Share this Course Space with an invite link.")
                    OutlinedButton(onClick = { viewModel.loadCourseLink(cardId) }) { Text("Show current invite link") }
                    OutlinedButton(onClick = { viewModel.createOrResetCourseLink(cardId) }) { Text("Create or reset invite link") }
                    state.shareLink?.let { url ->
                        Text(url, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { clipboard.setText(AnnotatedString(url)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null); Text(" Copy link")
                        }
                    }
                    state.shareApproval?.let { required ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Require approval to join", Modifier.weight(1f))
                            Switch(checked = required, onCheckedChange = { viewModel.setApproval(cardId, it) })
                        }
                    }
                }
                state.notice?.let { Text(it, color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            } },
            confirmButton = { TextButton(onClick = { showSharing = false }) { Text("Done") } })
    }

    when (dialog) {
        "note" -> ArtifactDialog("New Note", listOf("Title", "Content"), state.busy,
            onDismiss = { dialog = null }) { values -> viewModel.createNote(cardId, values[0], values[1]); dialog = null }
        "editNote" -> editingNote?.let { note -> ArtifactDialog("Edit Note", listOf("Title", "Content"), state.busy,
            initialValues = listOf(note.title, note.content), onDismiss = { dialog = null; editingNote = null }) { values ->
                viewModel.updateNote(cardId, note.id, values[0], values[1]); dialog = null; editingNote = null
            } }
        "study" -> ArtifactDialog("New Study Set", listOf("Title", "Description"), state.busy,
            onDismiss = { dialog = null }) { values -> viewModel.createStudySet(cardId, values[0], values[1]); dialog = null }
        "editStudy" -> editingStudySet?.let { set -> ArtifactDialog("Edit Study Set", listOf("Title", "Description"), state.busy,
            initialValues = listOf(set.title, set.description), onDismiss = { dialog = null; editingStudySet = null }) { values ->
                viewModel.updateStudySet(cardId, set.id, values[0], values[1]); dialog = null; editingStudySet = null
            } }
        "flashcards" -> ArtifactDialog("New Flashcard Set", listOf("Set title", "Front", "Back"), state.busy,
            onDismiss = { dialog = null }) { values -> viewModel.createFlashcardSet(cardId, values[0], values[1], values[2]); dialog = null }
        "quiz" -> ArtifactDialog("New Quiz", listOf("Quiz title", "Question", "Correct answer", "Other answer"), state.busy,
            onDismiss = { dialog = null }) { values -> viewModel.createQuiz(cardId, values[0], values[1], values[2], values[3]); dialog = null }
    }
    activeQuiz?.let { quiz -> QuizDialog(quiz, onDismiss = { activeQuiz = null }) { answers ->
        viewModel.submitAttempt(quiz.id, answers)
    } }
    activeFlashcards?.let { set -> FlashcardsDialog(set, onDismiss = { activeFlashcards = null }) { flashcard, gotIt ->
        viewModel.reviewFlashcard(set.id, flashcard, gotIt)
    } }
    if (showAttempts) AttemptsDialog(state.attempts, onDismiss = { showAttempts = false })

    Scaffold(topBar = {
        TopAppBar(title = { Column { Text(cardName, maxLines = 1); Text(if (sharedNow) "Course Space" else "Card workspace", style = MaterialTheme.typography.labelMedium) } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            actions = {
                TextButton(onClick = { showSharing = true }) { Text(if (sharedNow) "Share" else "Make shared") }
                IconButton(onClick = { viewModel.load(cardId, sharedNow) }) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                sections.forEachIndexed { index, label ->
                    FilterChip(selected = section == index, onClick = { section = index }, label = { Text(label) })
                }
            }
            state.notice?.let {
                Text(it, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
            }
            when (section) {
                0 -> ResourceSection(state.resources, state.busy,
                    onUpload = { pdfPicker.launch(arrayOf("application/pdf")) },
                    onOpen = { resource -> viewModel.openPdf(resource) { pdfId -> onOpenPdf(pdfId, resource.id) } },
                    onGenerate = { type -> viewModel.generate(cardId, type, state.resources.map { it.id }) })
                1 -> ArtifactList("Notes", state.notes.map { it.title to it.content }, "Create note", { dialog = "note" }) {
                    state.notes.forEach { note ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text(note.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp)); Text(note.content, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { editingNote = note; dialog = "editNote" }) { Text("Edit") }
                            TextButton(onClick = { viewModel.deleteNote(note.id, cardId) }) { Text("Delete") }
                        } }
                    }
                }
                2 -> ArtifactList("Study Sets", emptyList(), "Create study set", { dialog = "study" }) {
                    state.studySets.forEach { item ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodyMedium)
                            Row {
                                TextButton(onClick = { editingStudySet = item; dialog = "editStudy" }) { Text("Edit") }
                                TextButton(onClick = { viewModel.deleteStudySet(cardId, item.id) }) { Text("Delete") }
                            }
                        } }
                    }
                }
                3 -> ArtifactList("Quizzes", emptyList(), "Create quiz", { dialog = "quiz" }) {
                    state.quizzes.forEach { quiz ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text(quiz.title, style = MaterialTheme.typography.titleMedium)
                            Text("${quiz.questions.size} questions", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { activeQuiz = quiz }, modifier = Modifier.padding(top = 8.dp)) { Text("Take quiz") }
                            TextButton(onClick = { viewModel.loadAttempts(quiz.id); showAttempts = true }) { Text("Past attempts") }
                            TextButton(onClick = { viewModel.deleteQuiz(cardId, quiz.id) }) { Text("Delete quiz") }
                        } }
                    }
                }
                4 -> ArtifactList("Flashcards", emptyList(), "Create flashcards", { dialog = "flashcards" }) {
                    state.flashcardSets.forEach { set ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text(set.title, style = MaterialTheme.typography.titleMedium)
                            Text("${set.cards.size} cards", style = MaterialTheme.typography.bodySmall)
                            Button(onClick = { activeFlashcards = set }, modifier = Modifier.padding(top = 8.dp)) { Text("Study") }
                            TextButton(onClick = { viewModel.deleteFlashcardSet(cardId, set.id) }) { Text("Delete set") }
                        } }
                    }
                }
                5 -> SarahSection(state, onAsk = { viewModel.sendSarah(cardId, it) })
                6 -> UpdatesSection(cardName, state.events)
                7 -> PeopleSection(
                    members = state.members,
                    joinRequests = state.joinRequests,
                    callerRole = activeRole,
                    onInvite = { viewModel.inviteUser(cardId, it) },
                    onWithdrawInvite = { viewModel.withdrawInvitation(cardId, it) },
                    onApprove = { viewModel.approveJoin(cardId, it) },
                    onReject = { viewModel.rejectJoin(cardId, it) },
                    onPromote = { viewModel.promoteMember(cardId, it) },
                    onDemote = { viewModel.demoteMember(cardId, it) },
                    onRemove = { viewModel.removeMember(cardId, it) },
                    onTransfer = { viewModel.transferOwnership(cardId, it) },
                    onLeave = { viewModel.leaveCourseSpace(cardId) },
                    onDissolve = { viewModel.dissolveCourseSpace(cardId) }
                )
            }
        }
    }
}

@Composable
private fun ResourceSection(resources: List<ResourceDto>, busy: Boolean, onUpload: () -> Unit,
                            onOpen: (ResourceDto) -> Unit, onGenerate: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onUpload, enabled = !busy) { Icon(Icons.Filled.Add, null); Text(" Add PDF") }
            var showGenerate by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { showGenerate = true }, enabled = resources.isNotEmpty() && !busy) { Text("Sarah create") }
            if (showGenerate) AlertDialog(onDismissRequest = { showGenerate = false }, title = { Text("Create from resources") },
                text = { Text("Choose an artifact type for Sarah to generate from the PDFs in this Card.") },
                confirmButton = { Column {
                    TextButton(onClick = { onGenerate("flashcardset"); showGenerate = false }) { Text("Flashcard Set") }
                    TextButton(onClick = { onGenerate("quiz"); showGenerate = false }) { Text("Quiz") }
                    TextButton(onClick = { onGenerate("studyset"); showGenerate = false }) { Text("Study Set") }
                } }, dismissButton = { TextButton(onClick = { showGenerate = false }) { Text("Cancel") } })
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (resources.isEmpty()) EmptyPanel("Your PDFs will appear here after you add one.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(resources, key = { it.id }) { resource ->
                Card(onClick = { if (resource.mimeType == "application/pdf") onOpen(resource) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(resource.title, style = MaterialTheme.typography.titleMedium)
                            Text("${resource.status} · ${resource.originalFilename.orEmpty()}", style = MaterialTheme.typography.bodySmall)
                        }
                        if (resource.mimeType == "application/pdf") TextButton(onClick = { onOpen(resource) }) { Text("Open") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactList(title: String, unused: List<Pair<String, String>>, addLabel: String,
                         onAdd: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Button(onClick = onAdd) { Icon(Icons.Filled.Add, null); Text(" $addLabel") }
        }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun SimpleArtifactCard(title: String, description: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium)
    } }
}

@Composable
private fun EmptyPanel(text: String) {
    Box(Modifier.fillMaxWidth().padding(36.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ArtifactDialog(title: String, labels: List<String>, busy: Boolean, onDismiss: () -> Unit,
                           initialValues: List<String> = emptyList(),
                           onSubmit: (List<String>) -> Unit) {
    val values = remember(title) { labels.mapIndexed { index, _ -> mutableStateOf(initialValues.getOrElse(index) { "" }) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, label -> OutlinedTextField(value = values[i].value,
                onValueChange = { values[i].value = it }, label = { Text(label) },
                minLines = if (label in listOf("Content", "Description", "Question", "Front", "Back")) 2 else 1,
                modifier = Modifier.fillMaxWidth()) }
        } },
        confirmButton = { Button(onClick = { onSubmit(values.map { it.value.trim() }) },
            enabled = !busy && values.all { it.value.isNotBlank() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun QuizDialog(quiz: QuizDto, onDismiss: () -> Unit, onSubmit: (List<QuizAnswerWriteDto>) -> Unit) {
    val selected = remember(quiz.id) { mutableStateMapOf<String, String>() }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(quiz.title) },
        text = { if (quiz.questions.isEmpty()) Text("This quiz has no questions yet.") else Column {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                quiz.questions.forEachIndexed { index, question ->
                    Text("${index + 1}. ${question.prompt}", style = MaterialTheme.typography.titleMedium)
                    question.options.forEach { option -> Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = selected[question.id] == option.id, onClick = { selected[question.id] = option.id })
                        Text(option.text)
                    } }
                }
            }
        } },
        confirmButton = { Button(enabled = quiz.questions.isNotEmpty() && quiz.questions.all { selected[it.id] != null }, onClick = {
            onSubmit(quiz.questions.map { QuizAnswerWriteDto(it.id, selected.getValue(it.id)) }); onDismiss()
        }) { Text("Submit answers") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun FlashcardsDialog(set: FlashcardSetDto, onDismiss: () -> Unit,
                             onReview: (FlashcardDto, Boolean) -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var showingBack by remember { mutableStateOf(false) }
    val card = set.cards.getOrNull(index)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(set.title) },
        text = { if (card == null) Text("This set has no cards.") else Column {
            Text("${index + 1} of ${set.cards.size}", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(12.dp))
            Text(if (showingBack) card.back else card.front, style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { showingBack = !showingBack }) { Text(if (showingBack) "Show front" else "Show answer") }
        } },
        confirmButton = { if (card != null && showingBack) Row {
            TextButton(onClick = { onReview(card, false); index = (index + 1) % set.cards.size; showingBack = false }) { Text("Again") }
            Button(onClick = { onReview(card, true); index = (index + 1) % set.cards.size; showingBack = false }) { Text("Got it") }
        } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}

@Composable
private fun AttemptsDialog(attempts: List<QuizAttemptDto>, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Your quiz attempts") },
        text = { if (attempts.isEmpty()) Text("No attempts yet.") else Column {
            attempts.forEachIndexed { index, attempt ->
                Text("Attempt ${index + 1}: ${attempt.correctCount} / ${attempt.totalQuestions} correct",
                    modifier = Modifier.padding(vertical = 4.dp))
            }
        } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
private fun SarahSection(state: WorkspaceState, onAsk: (String) -> Unit) {
    var question by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Sarah", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Ask about this Card and its shared study materials.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.usage?.let { Text("Sarah usage: $it", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp)) }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.messages) { message ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor =
                    if (message.role == "user") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Text(message.content, Modifier.padding(14.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = question, onValueChange = { question = it }, label = { Text("Ask Sarah") },
                modifier = Modifier.weight(1f), maxLines = 3)
            IconButton(enabled = question.isNotBlank() && !state.busy, onClick = { onAsk(question.trim()); question = "" }) {
                Text("Send")
            }
        }
    }
}

@Composable
private fun UpdatesSection(cardName: String, events: List<EventDto>) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Updates", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 10.dp))
        if (events.isEmpty()) EmptyPanel("Updates for $cardName will show here.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(events, key = { it.id }) { event -> SimpleArtifactCard(event.type.replace('_', ' '), event.createdAt) }
        }
    }
}

@Composable
private fun PeopleSection(
    members: List<MemberDto>,
    joinRequests: List<JoinRequestItemDto>,
    callerRole: String,
    onInvite: (String) -> Unit,
    onWithdrawInvite: (String) -> Unit,
    onApprove: (String) -> Unit,
    onReject: (String) -> Unit,
    onPromote: (String) -> Unit,
    onDemote: (String) -> Unit,
    onRemove: (String) -> Unit,
    onTransfer: (String) -> Unit,
    onLeave: () -> Unit,
    onDissolve: () -> Unit
) {
    val canManage = callerRole == "OWNER" || callerRole == "ADMIN"
    val isOwner = callerRole == "OWNER"
    var userId by remember { mutableStateOf("") }
    var confirmTransfer by remember { mutableStateOf<String?>(null) }
    var confirmDissolve by remember { mutableStateOf(false) }
    var confirmWithdraw by remember { mutableStateOf<MemberDto?>(null) }
    if (confirmTransfer != null) AlertDialog(onDismissRequest = { confirmTransfer = null },
        title = { Text("Transfer ownership?") },
        text = { Text("You will become an Admin. This change takes effect immediately.") },
        confirmButton = { Button(onClick = { confirmTransfer?.let(onTransfer); confirmTransfer = null }) { Text("Transfer") } },
        dismissButton = { TextButton(onClick = { confirmTransfer = null }) { Text("Cancel") } })
    if (confirmDissolve) AlertDialog(onDismissRequest = { confirmDissolve = false },
        title = { Text("Dissolve Course Space?") },
        text = { Text("The Card and its materials remain, but shared access and invite links will be removed.") },
        confirmButton = { Button(onClick = { onDissolve(); confirmDissolve = false }) { Text("Dissolve") } },
        dismissButton = { TextButton(onClick = { confirmDissolve = false }) { Text("Cancel") } })

    if (confirmWithdraw != null) AlertDialog(onDismissRequest = { confirmWithdraw = null },
        title = { Text("Withdraw invitation?") },
        text = { Text("The invitee will no longer be able to accept this Course Space invitation.") },
        confirmButton = { Button(onClick = { confirmWithdraw?.membershipId?.let(onWithdrawInvite); confirmWithdraw = null }) { Text("Withdraw") } },
        dismissButton = { TextButton(onClick = { confirmWithdraw = null }) { Text("Cancel") } })
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Members", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 10.dp))
        }
        if (canManage) item {
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text("Invite an existing Vision user", style = MaterialTheme.typography.titleSmall)
                Text("Use their Vision user ID. Email-based account linking is not supported.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = userId, onValueChange = { userId = it }, label = { Text("Vision user ID") },
                        modifier = Modifier.weight(1f), singleLine = true)
                    TextButton(enabled = userId.isNotBlank(), onClick = { onInvite(userId.trim()); userId = "" }) { Text("Invite") }
                }
            } }
        }
        if (canManage && joinRequests.isNotEmpty()) item {
            Text("Join requests", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        }
        if (canManage) items(joinRequests, key = { it.joinRequestId }) { request ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text("User ${request.requestingUserId} wants to join", style = MaterialTheme.typography.bodyMedium)
                Row {
                    TextButton(onClick = { onApprove(request.joinRequestId) }) { Text("Approve") }
                    TextButton(onClick = { onReject(request.joinRequestId) }) { Text("Reject") }
                }
            } }
        }
        if (members.isEmpty()) item { EmptyPanel("Active Course Space members will appear here.") }
        else items(members, key = { it.membershipId.ifBlank { "${it.userId}:${it.status}:${it.joinedAt.orEmpty()}" } }) { member ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) {
                Text(member.userId, style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${member.role} · ${member.status}", modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (isOwner && member.status == "INVITED" && member.membershipId.isNotBlank()) {
                        TextButton(onClick = { confirmWithdraw = member }) { Text("Withdraw invite") }
                    }
                    if (isOwner && member.role != "OWNER" && member.status == "ACTIVE") {
                        TextButton(onClick = { if (member.role == "ADMIN") onDemote(member.userId) else onPromote(member.userId) }) {
                            Text(if (member.role == "ADMIN") "Demote" else "Promote")
                        }
                        TextButton(onClick = { onRemove(member.userId) }) { Text("Remove") }
                        TextButton(onClick = { confirmTransfer = member.userId }) { Text("Transfer owner") }
                    }
                }
            } }
        }
        if (isOwner) item { TextButton(onClick = { confirmDissolve = true }) { Text("Dissolve Course Space") } }
        else if (callerRole == "ADMIN" || callerRole == "MEMBER") item { TextButton(onClick = onLeave) { Text("Leave Course Space") } }
    }
}
