package com.aipdfreader.app.ui.workspace

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aipdfreader.app.data.remote.dto.*
import com.aipdfreader.app.ui.components.MaterialThumbnail
import com.aipdfreader.app.ui.reader.InAppResourceViewer
import com.aipdfreader.app.ui.theme.AmberHighlight

private val sharedSections = listOf("Resources", "Notes", "Study Sets", "Quizzes", "Flashcards", "Sarah", "Updates", "Members")
private data class QuizGenerationPrompt(val sourceResourceIds: List<String>, val regenerate: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardWorkspaceScreen(
    cardId: String,
    cardName: String,
    isShared: Boolean,
    callerRole: String,
    memberCardId: String? = null,
    onBack: () -> Unit,
    onOpenPdf: (Long, String) -> Unit,
    viewModel: CardWorkspaceViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var section by rememberSaveable(cardId) { mutableIntStateOf(0) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var editingNote by remember { mutableStateOf<NoteDto?>(null) }
    var editingStudySet by remember { mutableStateOf<StudySetDto?>(null) }
    var activeQuiz by remember { mutableStateOf<QuizDto?>(null) }
    var activeFlashcards by remember { mutableStateOf<FlashcardSetDto?>(null) }
    var inAppResource by remember { mutableStateOf<Triple<Uri, String, String>?>(null) }
    var showSharing by remember { mutableStateOf(false) }
    var showCardMenu by remember { mutableStateOf(false) }
    var showAttempts by remember { mutableStateOf(false) }
    var quizGeneration by remember { mutableStateOf<QuizGenerationPrompt?>(null) }
    var generatedNoteInitialValues by remember { mutableStateOf(emptyList<String>()) }
    var generatedNoteTarget by remember { mutableStateOf<NoteDto?>(null) }
    var sharedNow by remember(isShared) { mutableStateOf(isShared) }
    var activeRole by remember(callerRole) { mutableStateOf(callerRole) }
    var activeCardId by rememberSaveable(cardId) { mutableStateOf(cardId) }
    val privateSections = sharedSections.take(6)
    // Keep the full workspace available for local Cards too. Server-backed
    // actions report their offline requirement when selected; hiding their
    // sections made an offline Card look like a different product.
    val sections = if (sharedNow) sharedSections else privateSections
    val clipboard = LocalClipboardManager.current
    val currentOwnerCardId = if (sharedNow) memberCardId ?: activeCardId else activeCardId
    fun draftNote(target: NoteDto? = null) {
        val title = target?.title ?: "$cardName summary"
        viewModel.draftStudyNote(activeCardId, title, target?.content) { draftTitle, content ->
            generatedNoteTarget = target
            generatedNoteInitialValues = listOf(draftTitle, content)
            dialog = "generatedNote"
        }
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.uploadResource(activeCardId, it) }
    }
    LaunchedEffect(activeCardId, sharedNow, memberCardId, callerRole) {
        viewModel.load(activeCardId, sharedNow, memberCardId ?: if (sharedNow) activeCardId else null, callerRole)
    }
    LaunchedEffect(sections.size) { if (section >= sections.size) section = 0 }

    inAppResource?.let { (uri, mimeType, displayName) ->
        InAppResourceViewer(uri, displayName, mimeType) { inAppResource = null }
    }

    if (showSharing) {
        AlertDialog(onDismissRequest = { showSharing = false }, title = { Text("Course Space sharing") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!sharedNow) {
                    Text("Turn this Card into a shared Course Space. The Card stays in place.")
                    if (state.isLocalCard && state.remoteCardId == null) {
                        Text("This Card is saved on this phone. Connect and sync it with Vision before creating a Course Space.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Button(onClick = {
                            val serverCardId = state.remoteCardId ?: activeCardId
                            viewModel.enableCourseSpace(serverCardId) {
                                activeCardId = serverCardId
                                sharedNow = true
                                activeRole = "OWNER"
                            }
                        }, enabled = !state.busy) { Text("Create Course Space") }
                    }
                } else {
                    Text("Share this Course Space with an invite link.")
                    OutlinedButton(onClick = { viewModel.loadCourseLink(activeCardId) }) { Text("Show current invite link") }
                    OutlinedButton(onClick = { viewModel.createOrResetCourseLink(activeCardId) }) { Text("Create or reset invite link") }
                    state.shareLink?.let { url ->
                        Text(url, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { clipboard.setText(AnnotatedString(url)) }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null); Text(" Copy link")
                        }
                    }
                    state.shareApproval?.let { required ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Require approval to join", Modifier.weight(1f))
                            Switch(checked = required, onCheckedChange = { viewModel.setApproval(activeCardId, it) })
                        }
                    }
                }
                state.notice?.let { Text(it, color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            } },
            confirmButton = { TextButton(onClick = { showSharing = false }) { Text("Done") } })
    }

    when (dialog) {
        "note" -> ArtifactDialog("New Note", listOf("Title", "Content"), state.busy,
            onDismiss = { dialog = null }) { values -> viewModel.createNote(activeCardId, values[0], values[1]); dialog = null }
        "generatedNote" -> ArtifactDialog("Review Sarah’s study note", listOf("Title", "Content"), state.busy,
            initialValues = generatedNoteInitialValues, onDismiss = { dialog = null; generatedNoteTarget = null }) { values ->
                generatedNoteTarget?.let { viewModel.updateNote(activeCardId, it.id, values[0], values[1]) }
                    ?: viewModel.createNote(activeCardId, values[0], values[1])
                dialog = null
                generatedNoteTarget = null
            }
        "editNote" -> editingNote?.let { note -> ArtifactDialog("Edit Note", listOf("Title", "Content"), state.busy,
            initialValues = listOf(note.title, note.content), onDismiss = { dialog = null; editingNote = null }) { values ->
                viewModel.updateNote(activeCardId, note.id, values[0], values[1]); dialog = null; editingNote = null
            } }
        "study" -> ArtifactDialog("New Study Set", listOf("Title", "Description"), state.busy,
            onDismiss = { dialog = null }) { values -> viewModel.createStudySet(activeCardId, values[0], values[1]); dialog = null }
        "editStudy" -> editingStudySet?.let { set -> ArtifactDialog("Edit Study Set", listOf("Title", "Description"), state.busy,
            initialValues = listOf(set.title, set.description), onDismiss = { dialog = null; editingStudySet = null }) { values ->
                viewModel.updateStudySet(activeCardId, set.id, values[0], values[1]); dialog = null; editingStudySet = null
            } }
        "flashcards" -> ArtifactDialog("New Flashcard Set", listOf("Set title", "Front", "Back"), state.busy,
            onDismiss = { dialog = null }) { values -> viewModel.createFlashcardSet(activeCardId, values[0], values[1], values[2]); dialog = null }
        "quiz" -> ArtifactDialog("New Quiz", listOf("Quiz title", "Question", "Correct answer", "Other answer"), state.busy,
            onDismiss = { dialog = null }) { values -> viewModel.createQuiz(activeCardId, values[0], values[1], values[2], values[3]); dialog = null }
    }
    quizGeneration?.let { request ->
        QuizGenerationDialog(
            regenerate = request.regenerate,
            busy = state.busy,
            onDismiss = { quizGeneration = null },
            onGenerate = { instructions ->
                viewModel.generate(activeCardId, "quiz", request.sourceResourceIds, instructions)
                quizGeneration = null
            }
        )
    }
    activeQuiz?.let { quiz -> QuizDialog(quiz, onDismiss = { activeQuiz = null }) { answers ->
        viewModel.submitAttempt(quiz.id, answers)
    } }
    activeFlashcards?.let { set -> FlashcardsDialog(set, onDismiss = { activeFlashcards = null }) { flashcard, gotIt ->
        viewModel.reviewFlashcard(set.id, flashcard, gotIt)
    } }
    if (showAttempts) AttemptsDialog(state.attempts, onDismiss = { showAttempts = false })

    Scaffold(topBar = {
        TopAppBar(title = { Column {
            Text(cardName, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (sharedNow) "Course Space · ${state.members.count { it.status.equals("ACTIVE", true) }} members"
                    else if (state.isLocalCard) "Private Card · saved on this phone" else "Private Card",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1
                )
                if (sharedNow) Surface(
                    modifier = Modifier.padding(start = 6.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(activeRole.lowercase().replaceFirstChar(Char::uppercase),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        } },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            actions = {
                if (sharedNow && activeRole.uppercase() in setOf("OWNER", "ADMIN")) {
                    TextButton(onClick = { showSharing = true }) { Text("Share") }
                }
                Box {
                    IconButton(onClick = { showCardMenu = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Card options")
                    }
                    DropdownMenu(expanded = showCardMenu, onDismissRequest = { showCardMenu = false }) {
                        when (section) {
                            1 -> {
                                val notesYouOwn = state.notes.filter {
                                    state.isLocalCard || it.ownerCardId == currentOwnerCardId
                                }
                                val latestNote = notesYouOwn.maxByOrNull { it.updatedAt.orEmpty() }
                                DropdownMenuItem(
                                    text = { Text(if (latestNote == null) "Generate Summary Note" else "Regenerate Latest Note") },
                                    onClick = { showCardMenu = false; draftNote(latestNote) }
                                )
                            }
                            2 -> DropdownMenuItem(
                                text = { Text(if (state.studySets.isEmpty()) "Generate Study Set" else "Generate Another Study Set") },
                                onClick = {
                                    showCardMenu = false
                                    viewModel.generate(activeCardId, "studyset", state.resources.map { it.id })
                                }
                            )
                            3 -> DropdownMenuItem(
                                text = { Text(if (state.quizzes.isEmpty()) "Generate Quiz" else "Regenerate Quiz") },
                                onClick = {
                                    showCardMenu = false
                                    quizGeneration = QuizGenerationPrompt(
                                        state.resources.map { it.id }, regenerate = state.quizzes.isNotEmpty()
                                    )
                                }
                            )
                            4 -> DropdownMenuItem(
                                text = {
                                    Text(if (state.flashcardSets.isEmpty()) "Generate Flashcards" else "Regenerate Flashcards · new set")
                                },
                                onClick = {
                                    showCardMenu = false
                                    viewModel.generate(activeCardId, "flashcardset", state.resources.map { it.id })
                                }
                            )
                        }
                        if (!sharedNow) {
                            DropdownMenuItem(
                                text = { Text("Create Course Space") },
                                onClick = { showCardMenu = false; showSharing = true }
                            )
                        } else if (activeRole.uppercase() in setOf("OWNER", "ADMIN")) {
                            DropdownMenuItem(
                                text = { Text("Course Space sharing") },
                                onClick = { showCardMenu = false; showSharing = true }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Refresh") },
                            onClick = {
                                showCardMenu = false
                                viewModel.load(activeCardId, sharedNow, memberCardId, activeRole)
                            }
                        )
                    }
                }
            })
    }, floatingActionButton = {
        val sarahIndex = sections.indexOf("Sarah")
        if (sarahIndex >= 0) {
            FloatingActionButton(
                onClick = { section = sarahIndex },
                containerColor = AmberHighlight,
                contentColor = Color(0xFF352A00)
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = "Ask Sarah about this Card")
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!sharedNow) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Start
                ) {
                    Button(
                        onClick = { showSharing = true },
                        enabled = !state.busy
                    ) {
                        Text("Create Course Space")
                    }
                }
            }
            WorkspaceTabs(sections, section, onSelect = { section = it })
            if (state.offline) {
                val synced = state.lastSyncedAtMillis?.let {
                    android.text.format.DateUtils.getRelativeTimeSpanString(it).toString()
                }
                Text(
                    synced?.let { "Offline · Last synced $it" } ?: "Offline · Showing saved content",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            state.notice?.let {
                Text(it, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    color = if (state.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
            }
            when (section) {
                0 -> ResourceSection(state.resources, state.busy, state.localResourcePaths, state.offline,
                    state.downloadingResourceId,
                    onUpload = { pdfPicker.launch(arrayOf("*/*")) },
                    onOpen = { resource ->
                        viewModel.openResource(
                            resource,
                            onOpenPdf = { pdfId -> onOpenPdf(pdfId, resource.id) },
                        onOpenFile = { uri, mimeType ->
                            inAppResource = Triple(uri, mimeType, resource.originalFilename ?: resource.title)
                        }
                        )
                    },
                    onGenerate = { type ->
                        if (type == "quiz") quizGeneration = QuizGenerationPrompt(
                            state.resources.map { it.id }, regenerate = false
                        ) else viewModel.generate(activeCardId, type, state.resources.map { it.id })
                    })
                1 -> ArtifactList("Notes", state.notes.isEmpty(), "Your notes will appear here.", "Create note", { dialog = "note" },
                    footerAction = {
                        SarahGenerationButton("Generate Summary", state.resources.isNotEmpty(), state.busy) {
                            draftNote()
                        }
                    }) {
                    state.notes.forEach { note ->
                        val isNoteOwner = state.isLocalCard || note.ownerCardId == currentOwnerCardId
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text(note.title, style = MaterialTheme.typography.titleMedium)
                            Text(if (note.sharedWithThisCourseSpace) "Shared with this Course Space" else "Private to you",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(4.dp)); Text(note.content, style = MaterialTheme.typography.bodyMedium)
                            if (sharedNow && note.ownerCardId == (memberCardId ?: activeCardId)) TextButton(
                                enabled = !state.busy,
                                onClick = { viewModel.setArtifactShared(activeCardId, "note", note.id, !note.sharedWithThisCourseSpace) }
                            ) { Text(if (note.sharedWithThisCourseSpace) "Make private" else "Share with Course Space") }
                            if (isNoteOwner) {
                                Row {
                                    TextButton(onClick = { editingNote = note; dialog = "editNote" }) { Text("Edit") }
                                    TextButton(onClick = { draftNote(note) }, enabled = state.resources.isNotEmpty() && !state.busy) { Text("Regenerate") }
                                    TextButton(onClick = { viewModel.deleteNote(note.id, activeCardId) }, enabled = !state.busy) { Text("Delete") }
                                }
                            }
                        } }
                    }
                }
                2 -> ArtifactList("Study Sets", state.studySets.isEmpty(), "No study sets yet — private to you unless you share it.", "Create study set", { dialog = "study" },
                    footerAction = {
                        SarahGenerationButton("Generate Study Set", state.resources.isNotEmpty(), state.busy) {
                            viewModel.generate(activeCardId, "studyset", state.resources.map { it.id })
                        }
                    }, emptyIcon = "📚") {
                    state.studySets.forEach { item ->
                        val isArtifactOwner = state.isLocalCard || item.ownerCardId == currentOwnerCardId
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(if (item.sharedWithThisCourseSpace) "Shared with this Course Space" else "Private to you",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodyMedium)
                            if (sharedNow && isArtifactOwner) TextButton(
                                enabled = !state.busy,
                                onClick = { viewModel.setArtifactShared(activeCardId, "studyset", item.id, !item.sharedWithThisCourseSpace) }
                            ) { Text(if (item.sharedWithThisCourseSpace) "Make private" else "Share with Course Space") }
                            if (isArtifactOwner) Row {
                                TextButton(onClick = { editingStudySet = item; dialog = "editStudy" }) { Text("Edit") }
                                TextButton(onClick = { viewModel.deleteStudySet(activeCardId, item.id) }, enabled = !state.busy) { Text("Delete") }
                            }
                        } }
                    }
                }
                3 -> ArtifactList("Quizzes", state.quizzes.isEmpty(), "No quiz yet — private to you unless you share it.", "Create quiz", { dialog = "quiz" },
                    footerAction = {
                        SarahGenerationButton("Generate Quiz", state.resources.isNotEmpty(), state.busy) {
                            quizGeneration = QuizGenerationPrompt(state.resources.map { it.id }, regenerate = false)
                        }
                    }, emptyIcon = "❔") {
                    state.quizzes.forEach { quiz ->
                        val isArtifactOwner = state.isLocalCard || quiz.ownerCardId == currentOwnerCardId
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text(quiz.title, style = MaterialTheme.typography.titleMedium)
                            Text(if (quiz.sharedWithThisCourseSpace) "Shared with this Course Space" else "Private to you",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text("${quiz.questions.size} questions", style = MaterialTheme.typography.bodySmall)
                            if (sharedNow && isArtifactOwner) TextButton(
                                enabled = !state.busy,
                                onClick = { viewModel.setArtifactShared(activeCardId, "quiz", quiz.id, !quiz.sharedWithThisCourseSpace) }
                            ) { Text(if (quiz.sharedWithThisCourseSpace) "Make private" else "Share with Course Space") }
                            Button(onClick = { activeQuiz = quiz }, modifier = Modifier.padding(top = 8.dp)) { Text("Take quiz") }
                            TextButton(onClick = { viewModel.loadAttempts(quiz.id); showAttempts = true }) { Text("Past attempts") }
                            if (isArtifactOwner) Row {
                                TextButton(enabled = state.resources.isNotEmpty() && !state.busy,
                                    onClick = { quizGeneration = QuizGenerationPrompt(state.resources.map { it.id }, regenerate = true) }) {
                                    Text("Regenerate")
                                }
                                TextButton(onClick = { viewModel.deleteQuiz(activeCardId, quiz.id) }, enabled = !state.busy) { Text("Delete quiz") }
                            }
                        } }
                    }
                }
                4 -> ArtifactList("Flashcards", state.flashcardSets.isEmpty(), "No flashcards yet — private to you unless you share it.", "Create flashcards", { dialog = "flashcards" },
                    footerAction = {
                        SarahGenerationButton("Generate Flashcards", state.resources.isNotEmpty(), state.busy) {
                            viewModel.generate(activeCardId, "flashcardset", state.resources.map { it.id })
                        }
                    }, emptyIcon = "📁") {
                    state.flashcardSets.forEach { set ->
                        val isArtifactOwner = state.isLocalCard || set.ownerCardId == currentOwnerCardId
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                            Text(set.title, style = MaterialTheme.typography.titleMedium)
                            Text(if (set.sharedWithThisCourseSpace) "Shared with this Course Space" else "Private to you",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text("${set.cards.size} cards", style = MaterialTheme.typography.bodySmall)
                            if (sharedNow && isArtifactOwner) TextButton(
                                enabled = !state.busy,
                                onClick = { viewModel.setArtifactShared(activeCardId, "flashcard_set", set.id, !set.sharedWithThisCourseSpace) }
                            ) { Text(if (set.sharedWithThisCourseSpace) "Make private" else "Share with Course Space") }
                            Button(onClick = { activeFlashcards = set }, modifier = Modifier.padding(top = 8.dp)) { Text("Study") }
                            if (isArtifactOwner) Row {
                                TextButton(enabled = state.resources.isNotEmpty() && !state.busy,
                                    onClick = { viewModel.generate(activeCardId, "flashcardset", state.resources.map { it.id }) }) { Text("Generate another") }
                                TextButton(onClick = { viewModel.deleteFlashcardSet(activeCardId, set.id) }, enabled = !state.busy) { Text("Delete set") }
                            }
                        } }
                    }
                }
                5 -> SarahSection(state, onAsk = { viewModel.sendSarah(activeCardId, it) })
                6 -> UpdatesSection(cardName, state.events)
                7 -> PeopleSection(
                    members = state.members,
                    joinRequests = state.joinRequests,
                    callerRole = activeRole,
                    onInvite = { viewModel.inviteUser(activeCardId, it) },
                    onWithdrawInvite = { viewModel.withdrawInvitation(activeCardId, it) },
                    onApprove = { viewModel.approveJoin(activeCardId, it) },
                    onReject = { viewModel.rejectJoin(activeCardId, it) },
                    onPromote = { viewModel.promoteMember(activeCardId, it) },
                    onDemote = { viewModel.demoteMember(activeCardId, it) },
                    onRemove = { viewModel.removeMember(activeCardId, it) },
                    onTransfer = { viewModel.transferOwnership(activeCardId, it) },
                    onLeave = { viewModel.leaveCourseSpace(activeCardId) },
                    onDissolve = { viewModel.dissolveCourseSpace(activeCardId) }
                )
            }
        }
    }
}

@Composable
private fun WorkspaceTabs(sections: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        sections.forEachIndexed { index, title ->
            val active = selected == index
            Surface(
                modifier = Modifier.clickable { onSelect(index) },
                shape = RoundedCornerShape(50),
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceVariant,
                contentColor = if (active) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(title, modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
                    maxLines = 1)
            }
        }
    }
}

@Composable
private fun ResourceAvailabilityChip(label: String) {
    val (background, foreground) = when (label) {
        "Available offline" -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        "Downloading" -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        "Not available offline" -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = background, contentColor = foreground) {
        Text(label, modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun ResourceSection(resources: List<ResourceDto>, busy: Boolean, localPaths: Map<String, String>, offline: Boolean,
                            downloadingResourceId: String?, onUpload: () -> Unit,
                            onOpen: (ResourceDto) -> Unit, onGenerate: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onUpload, enabled = !busy) { Icon(Icons.Filled.Add, null); Text(" Add file") }
            var showGenerate by remember { mutableStateOf(false) }
            OutlinedButton(onClick = { showGenerate = true }, enabled = resources.isNotEmpty() && !busy) { Text("Sarah create") }
            if (showGenerate) AlertDialog(onDismissRequest = { showGenerate = false }, title = { Text("Create from resources") },
                text = { Text("Choose an artifact type for Sarah to generate from the files in this Card.") },
                confirmButton = { Column {
                    TextButton(onClick = { onGenerate("flashcardset"); showGenerate = false }) { Text("Flashcard Set") }
                    TextButton(onClick = { onGenerate("quiz"); showGenerate = false }) { Text("Quiz") }
                    TextButton(onClick = { onGenerate("studyset"); showGenerate = false }) { Text("Study Set") }
                } }, dismissButton = { TextButton(onClick = { showGenerate = false }) { Text("Cancel") } })
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (resources.isEmpty()) EmptyPanel("Your files will appear here after you add one.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(resources, key = { it.id }) { resource ->
                Card(onClick = { onOpen(resource) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        MaterialThumbnail(resource.originalFilename ?: resource.title, resource.mimeType ?: "application/octet-stream",
                            localPaths[resource.id], Modifier.size(width = 72.dp, height = 92.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(resource.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Text(resource.originalFilename.orEmpty(), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            val availableOffline = resource.status == "ON_THIS_PHONE" || resource.id in localPaths
                            val availability = when {
                                downloadingResourceId == resource.id -> "Downloading"
                                availableOffline -> "Available offline"
                                resource.status != "READY" -> resource.status.lowercase().replaceFirstChar(Char::uppercase)
                                offline -> "Not available offline"
                                else -> "Not downloaded · tap to download"
                            }
                            ResourceAvailabilityChip(availability)
                            TextButton(onClick = { onOpen(resource) }) { Text("Open") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArtifactList(title: String, isEmpty: Boolean, emptyMessage: String, addLabel: String,
                         onAdd: () -> Unit,
                         secondaryAction: @Composable RowScope.() -> Unit = {},
                         footerAction: @Composable ColumnScope.() -> Unit = {},
                         emptyIcon: String? = null,
                         content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) { secondaryAction() }
            Button(onClick = onAdd) { Icon(Icons.Filled.Add, null); Text(" $addLabel") }
        }
        if (isEmpty && emptyIcon != null) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(emptyIcon, style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(12.dp))
                Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                footerAction()
            }
        } else if (isEmpty) {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { EmptyPanel(emptyMessage) }
                footerAction()
            }
        } else {
            Column(
                Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp), content = content
            )
            footerAction()
        }
    }
}

@Composable
private fun SarahGenerationButton(label: String, hasResources: Boolean, busy: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            enabled = hasResources && !busy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
        if (!hasResources) {
            Text("Add a resource first to generate study material.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuizGenerationDialog(
    regenerate: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onGenerate: (String) -> Unit
) {
    val languages = listOf("English", "Arabic", "French", "Hausa", "Spanish", "Swahili")
    val counts = listOf("Few" to "1–15", "Standard" to "20–40", "Many" to "50+")
    val difficulties = listOf("Easy", "Medium", "Hard")
    var language by rememberSaveable(regenerate) { mutableStateOf("English") }
    var count by rememberSaveable(regenerate) { mutableStateOf("Standard") }
    var difficulty by rememberSaveable(regenerate) { mutableStateOf("Medium") }
    var instructions by rememberSaveable(regenerate) { mutableStateOf("") }
    var showLanguages by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (regenerate) "Regenerate Quiz" else "Create Quiz") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Choose how Sarah should prepare your questions.", style = MaterialTheme.typography.bodyMedium)
                Box {
                    OutlinedButton(onClick = { showLanguages = true }, enabled = !busy,
                        modifier = Modifier.fillMaxWidth()) {
                        Text("Study Set Language · $language", modifier = Modifier.weight(1f))
                        Text("⌄")
                    }
                    DropdownMenu(expanded = showLanguages, onDismissRequest = { showLanguages = false }) {
                        languages.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = {
                                language = option
                                showLanguages = false
                            })
                        }
                    }
                }
                Text("Number of Questions", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    counts.forEach { (label, range) ->
                        FilterChip(
                            selected = count == label,
                            onClick = { count = label },
                            enabled = !busy,
                            label = { Column {
                                Text(label)
                                Text(range, style = MaterialTheme.typography.labelSmall)
                            } },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                Text("Difficulty Level", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    difficulties.forEach { option ->
                        FilterChip(
                            selected = difficulty == option,
                            onClick = { difficulty = option },
                            enabled = !busy,
                            label = { Text(option) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it.take(3000) },
                    enabled = !busy,
                    label = { Text("Custom Instructions (optional)") },
                    placeholder = { Text("Focus on a topic or chapter, or ask for more examples…") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                if (regenerate) Text(
                    "Sarah will create another quiz. Your current quiz will stay available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                val questionCount = counts.first { it.first == count }.second
                val prompt = buildString {
                    append("Create the quiz in $language. Target $questionCount questions. ")
                    append("Use $difficulty difficulty. ")
                    if (instructions.isNotBlank()) append("Additional instructions: ${instructions.trim()}")
                }
                onGenerate(prompt)
            }) { Text(if (busy) "Creating…" else if (regenerate) "Regenerate" else "Generate Quiz") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } }
    )
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
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium)
        }
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
            if (state.messages.isEmpty()) item {
                EmptyPanel("Your Sarah conversation for this Card will appear here.")
            }
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
