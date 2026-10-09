package com.aipdfreader.app.ui.workspace

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aipdfreader.app.data.remote.CardApi
import com.aipdfreader.app.data.remote.listAllCards
import com.aipdfreader.app.data.remote.DomainApi
import com.aipdfreader.app.data.remote.dto.*
import com.aipdfreader.app.data.repository.PdfRepository
import com.aipdfreader.app.data.repository.LocalCardRepository
import com.aipdfreader.app.data.repository.RemoteWorkspaceCache
import com.aipdfreader.app.data.repository.AuthRepository
import com.aipdfreader.app.data.local.entity.CachedSarahMessage
import com.aipdfreader.app.data.local.entity.WorkspaceSnapshotContent
import com.aipdfreader.app.util.FileUtils
import com.aipdfreader.app.util.BackendConfiguration
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File
import java.util.UUID
import javax.inject.Inject

data class SarahMessage(val role: String, val content: String)
data class WorkspaceState(
    val resources: List<ResourceDto> = emptyList(), val notes: List<NoteDto> = emptyList(),
    val studySets: List<StudySetDto> = emptyList(), val quizzes: List<QuizDto> = emptyList(),
    val flashcardSets: List<FlashcardSetDto> = emptyList(), val events: List<EventDto> = emptyList(),
    val members: List<MemberDto> = emptyList(), val attempts: List<QuizAttemptDto> = emptyList(),
    val joinRequests: List<JoinRequestItemDto> = emptyList(),
    val messages: List<SarahMessage> = emptyList(),
    val loading: Boolean = false, val busy: Boolean = false, val notice: String? = null,
    val error: Boolean = false, val shareLink: String? = null, val usage: String? = null,
    val usageUsed: Int? = null, val usageLimit: Int? = null,
    val shareApproval: Boolean? = null,
    val isLocalCard: Boolean = false,
    val canUseServerFeatures: Boolean = false,
    val localResourcePaths: Map<String, String> = emptyMap(),
    val resourceCardIds: Map<String, String> = emptyMap(),
    val offline: Boolean = false,
    val lastSyncedAtMillis: Long? = null,
    val hasSyncedArtifacts: Boolean = false,
    val remoteCardId: String? = null,
    val remoteResourceIdsByLocalId: Map<String, String> = emptyMap(),
    val downloadingResourceId: String? = null
)

private data class LocalWorkspaceSnapshot(
    val resources: List<ResourceDto>,
    val notes: List<NoteDto>,
    val localPaths: Map<String, String>,
    val remoteCardId: String?,
    val remoteResourceIds: Map<String, String>,
    val resourceCardIds: Map<String, String>,
    val hasSyncedArtifacts: Boolean,
    val canUseServerFeatures: Boolean,
    val studySets: List<StudySetDto>,
    val quizzes: List<QuizDto>,
    val flashcardSets: List<FlashcardSetDto>,
    val events: List<EventDto>
)

@HiltViewModel
class CardWorkspaceViewModel @Inject constructor(
    private val domainApi: DomainApi,
    private val cardApi: CardApi,
    private val pdfRepository: PdfRepository,
    private val localCardRepository: LocalCardRepository,
    private val remoteWorkspaceCache: RemoteWorkspaceCache,
    private val authRepository: AuthRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(WorkspaceState())
    val state: StateFlow<WorkspaceState> = _state.asStateFlow()
    private var conversationId = UUID.randomUUID().toString()
    private var workspaceIsShared = false
    private var workspaceCardId = ""
    private var workspaceMemberCardId = ""
    private var workspaceRole = "OWNER"

    fun load(cardId: String, isShared: Boolean = false, memberCardId: String? = null, callerRole: String = "OWNER") {
        if (cardId.startsWith("local:")) {
            val localId = cardId.removePrefix("local:")
            currentLocalCardId = localId
            workspaceIsShared = false
            viewModelScope.launch {
                _state.value = WorkspaceState(loading = true, isLocalCard = true, offline = !hasValidatedInternet())
                runCatching {
                    val localCard = localCardRepository.getCard(localId)
                        ?: error("This Card is no longer available on this phone.")
                    workspaceCardId = localCard.remoteCardId ?: "local:$localId"
                    workspaceMemberCardId = workspaceCardId
                    val localMaterials = localCardRepository.observeMaterials(localId).first()
                    val resources = localMaterials.map { material ->
                        ResourceDto(material.id.toString(), material.displayName.substringBeforeLast('.', material.displayName),
                            material.displayName, material.mimeType, "ON_THIS_PHONE", material.sizeBytes)
                    }
                    val remoteSnapshot = localCard.remoteCardId?.let { remoteId ->
                        authRepository.currentUserId?.let { uid -> remoteWorkspaceCache.snapshot(uid, remoteId) }
                    }
                    remoteSnapshot?.content?.notes.orEmpty().forEach { note ->
                        localCardRepository.cacheRemoteNote(localId, note.id, note.title, note.content)
                    }
                    val notes = localCardRepository.observeNotes(localId).first().map { note ->
                        NoteDto(note.id, note.title, note.content, note.updatedAtMillis.toString())
                    }
                    remoteSnapshot?.content?.conversationId?.takeIf(String::isNotBlank)?.let { conversationId = it }
                    val canUseServerFeatures = localCard.remoteCardId != null && BackendConfiguration.isConfigured && hasValidatedInternet()
                    val remoteStudySets = if (canUseServerFeatures)
                        async { runCatching { domainApi.studySets(localCard.remoteCardId) }.getOrDefault(remoteSnapshot?.content?.studySets.orEmpty()) }
                    else async { remoteSnapshot?.content?.studySets.orEmpty() }
                    val remoteQuizzes = if (canUseServerFeatures)
                        async { runCatching { domainApi.quizzes(localCard.remoteCardId) }.getOrDefault(remoteSnapshot?.content?.quizzes.orEmpty()) }
                    else async { remoteSnapshot?.content?.quizzes.orEmpty() }
                    val remoteFlashcards = if (canUseServerFeatures)
                        async { runCatching { domainApi.flashcardSets(localCard.remoteCardId) }.getOrDefault(remoteSnapshot?.content?.flashcardSets.orEmpty()) }
                    else async { remoteSnapshot?.content?.flashcardSets.orEmpty() }
                    val remoteEvents = if (canUseServerFeatures)
                        async { runCatching { domainApi.events(localCard.remoteCardId) }.getOrDefault(remoteSnapshot?.content?.events.orEmpty()) }
                    else async { remoteSnapshot?.content?.events.orEmpty() }
                    val syncedResourceIds = localMaterials.mapNotNull { it.remoteResourceId }.toSet()
                    val remoteResources = remoteSnapshot?.content?.resources.orEmpty().filterNot { it.id in syncedResourceIds }
                    val remotePaths = remoteResources.mapNotNull { resource ->
                        localCard.remoteCardId?.let { remoteId ->
                            authRepository.currentUserId?.let { uid -> remoteWorkspaceCache.cachedFile(uid, remoteId, resource.id) }
                                ?.let { resource.id to it.filePath }
                        }
                    }.toMap()
                    LocalWorkspaceSnapshot(
                        resources = resources + remoteResources,
                        notes = notes,
                        localPaths = localMaterials.associate { it.id.toString() to it.filePath } + remotePaths,
                        remoteCardId = localCard.remoteCardId,
                        canUseServerFeatures = canUseServerFeatures,
                        remoteResourceIds = localMaterials.mapNotNull { material ->
                            material.remoteResourceId?.let { material.id.toString() to it }
                        }.toMap(),
                        resourceCardIds = remoteResources.associate { it.id to localCard.remoteCardId.orEmpty() },
                        hasSyncedArtifacts = localCard.remoteCardId != null && (canUseServerFeatures || remoteSnapshot != null),
                        studySets = remoteStudySets.await(),
                        quizzes = remoteQuizzes.await(),
                        flashcardSets = remoteFlashcards.await(),
                        events = remoteEvents.await()
                    )
                }.onSuccess { local ->
                    _state.value = _state.value.copy(loading = false, offline = !hasValidatedInternet(),
                        error = false,
                        localResourcePaths = local.localPaths,
                        resourceCardIds = local.resourceCardIds,
                        resources = local.resources,
                        notes = local.notes,
                        studySets = local.studySets,
                        quizzes = local.quizzes,
                        flashcardSets = local.flashcardSets,
                        events = local.events,
                        remoteCardId = local.remoteCardId,
                        canUseServerFeatures = local.canUseServerFeatures,
                        hasSyncedArtifacts = local.hasSyncedArtifacts,
                        lastSyncedAtMillis = local.remoteCardId?.let { remoteId ->
                            authRepository.currentUserId?.let { uid -> remoteWorkspaceCache.snapshot(uid, remoteId)?.lastSyncedAtMillis }
                        },
                        remoteResourceIdsByLocalId = local.remoteResourceIds,
                        messages = local.remoteCardId?.let { remoteId ->
                            authRepository.currentUserId?.let { uid ->
                                remoteWorkspaceCache.snapshot(uid, remoteId)?.content?.messages
                                    ?.map { SarahMessage(it.role, it.content) }
                            }
                        }.orEmpty(),
                        notice = if (hasValidatedInternet()) "This Card and its files are saved on this phone."
                            else "You’re offline. Showing the Card and files saved on this phone.")
                }.onFailure { fail(it, loading = false) }
            }
            return
        }
        currentLocalCardId = ""
        workspaceIsShared = isShared
        workspaceCardId = cardId
        workspaceMemberCardId = memberCardId ?: cardId
        workspaceRole = callerRole
        viewModelScope.launch {
            val uid = authRepository.currentUserId
            val cached = uid?.let { remoteWorkspaceCache.snapshot(it, cardId) }
            val cachedMember = if (uid != null && workspaceMemberCardId != cardId)
                remoteWorkspaceCache.snapshot(uid, workspaceMemberCardId) else cached
            if (cached != null) renderCached(cached, cachedMember, uid!!, offline = !hasValidatedInternet())
            else _state.value = WorkspaceState(loading = true, isLocalCard = false, offline = !hasValidatedInternet())

            if (!BackendConfiguration.isConfigured || !hasValidatedInternet()) {
                _state.value = _state.value.copy(
                    loading = false,
                    offline = !hasValidatedInternet(),
                    notice = when {
                        !BackendConfiguration.isConfigured && cached != null -> "Vision’s server is not deployed yet. Showing this workspace from its last sync."
                        !BackendConfiguration.isConfigured -> "This workspace is available after Vision’s server is deployed and synced."
                        cached != null -> "You’re offline. Showing this workspace from its last sync."
                        else -> "This workspace hasn’t been saved on this phone yet. Connect to the internet to load it."
                    },
                    error = cached == null
                )
                return@launch
            }

            runCatching {
                if (isShared) {
                    val activeShared = cardApi.listAllCards("shared").map { it.id }.toSet()
                    if (cardId !in activeShared) {
                        uid?.let { remoteWorkspaceCache.reconcileSharedMembership(it, activeShared) }
                        _state.value = _state.value.copy(
                            resources = emptyList(), notes = emptyList(), studySets = emptyList(), quizzes = emptyList(),
                            flashcardSets = emptyList(), events = emptyList(), members = emptyList(), joinRequests = emptyList(),
                            localResourcePaths = emptyMap(), resourceCardIds = emptyMap(), loading = false,
                            offline = false, notice = "You no longer have access to this Course Space.", error = true
                        )
                        return@launch
                    }
                }
                val (serverCard, serverContent) = fetchSnapshot(cardId, isShared, callerRole,
                    memberCardId ?: cardId)
                val memberPair = if (workspaceMemberCardId != cardId)
                    fetchSnapshot(workspaceMemberCardId, false, "OWNER", workspaceMemberCardId)
                else null
                if (uid != null) {
                    remoteWorkspaceCache.save(serverCard, serverContent, uid)
                    memberPair?.let { remoteWorkspaceCache.save(it.first, it.second, uid) }
                }
                val nowShared = remoteWorkspaceCache.snapshot(uid ?: "", cardId)
                    ?: com.aipdfreader.app.data.repository.CachedWorkspaceSnapshot(serverCard, serverContent, System.currentTimeMillis())
                val nowMember = if (memberPair != null) {
                    remoteWorkspaceCache.snapshot(uid ?: "", workspaceMemberCardId)
                        ?: com.aipdfreader.app.data.repository.CachedWorkspaceSnapshot(memberPair.first, memberPair.second, System.currentTimeMillis())
                } else nowShared
                renderCached(nowShared, nowMember, uid.orEmpty(), offline = false, notice = null)
            }.onFailure { error ->
                if (cached != null) _state.value = _state.value.copy(
                    loading = false,
                    offline = true,
                    notice = "Couldn’t refresh. Showing the last successfully synced workspace.",
                    error = false
                ) else fail(error, loading = false)
            }
        }
    }

    private suspend fun fetchSnapshot(
        cardId: String,
        isShared: Boolean,
        role: String,
        memberCardId: String
    ): Pair<CardDto, WorkspaceSnapshotContent> = coroutineScope {
        val card = cardApi.getCard(cardId).copy(isShared = isShared, role = role, memberCardId = memberCardId)
        val resources = async { domainApi.resources(cardId) }
        val notes = async { domainApi.notes(cardId) }
        val studySets = async { domainApi.studySets(cardId) }
        val quizzes = async { domainApi.quizzes(cardId) }
        val flashcards = async { domainApi.flashcardSets(cardId) }
        val events = async { if (isShared) domainApi.events(cardId) else emptyList() }
        val members = async { if (isShared) domainApi.members(cardId) else emptyList() }
        val joinRequests = async { if (isShared && role in setOf("OWNER", "ADMIN")) domainApi.joinRequests(cardId) else emptyList() }
        card to WorkspaceSnapshotContent(
            resources = resources.await(), notes = notes.await(), studySets = studySets.await(),
            quizzes = quizzes.await(), flashcardSets = flashcards.await(), events = events.await(),
            members = members.await(), joinRequests = joinRequests.await()
        )
    }

    private suspend fun renderCached(
        shared: com.aipdfreader.app.data.repository.CachedWorkspaceSnapshot,
        member: com.aipdfreader.app.data.repository.CachedWorkspaceSnapshot?,
        uid: String,
        offline: Boolean,
        notice: String? = if (offline) "You’re offline. Showing this workspace from its last sync." else null
    ) {
        val memberContent = member?.content ?: shared.content
        val memberIsDistinct = member != null && member.card.id != shared.card.id
        val resources = (shared.content.resources + if (memberIsDistinct) memberContent.resources else emptyList())
            .distinctBy { it.id }
        val resourceOwners = buildMap {
            shared.content.resources.forEach { put(it.id, shared.card.id) }
            if (memberIsDistinct) memberContent.resources.forEach { put(it.id, member!!.card.id) }
        }
        val localPaths = resources.mapNotNull { resource ->
            val ownerCardId = resourceOwners[resource.id] ?: shared.card.id
            remoteWorkspaceCache.cachedFile(uid, ownerCardId, resource.id)?.let { resource.id to it.filePath }
        }.toMap()
        val notes = (shared.content.notes + if (memberIsDistinct) memberContent.notes else emptyList()).distinctBy { it.id }
        val studySets = (shared.content.studySets + if (memberIsDistinct) memberContent.studySets else emptyList()).distinctBy { it.id }
        val quizzes = (shared.content.quizzes + if (memberIsDistinct) memberContent.quizzes else emptyList()).distinctBy { it.id }
        val flashcards = (shared.content.flashcardSets + if (memberIsDistinct) memberContent.flashcardSets else emptyList()).distinctBy { it.id }
        conversationId = shared.content.conversationId.ifBlank { conversationId }
        _state.value = _state.value.copy(
            resources = resources,
            notes = notes,
            studySets = studySets,
            quizzes = quizzes,
            flashcardSets = flashcards,
            events = shared.content.events,
            members = shared.content.members,
            joinRequests = shared.content.joinRequests,
            messages = shared.content.messages.map { SarahMessage(it.role, it.content) },
            localResourcePaths = localPaths,
            resourceCardIds = resourceOwners,
            loading = false,
            isLocalCard = false,
            offline = offline,
            lastSyncedAtMillis = maxOf(shared.lastSyncedAtMillis, member?.lastSyncedAtMillis ?: 0L),
            shareApproval = shared.card.shareApproval,
            notice = notice,
            error = false
        )
    }

    fun createNote(cardId: String, title: String, content: String) = if (cardId.startsWith("local:")) {
        viewModelScope.launch {
            runCatching { localCardRepository.saveNote(cardId.removePrefix("local:"), null, title.trim(), content) }
                .onSuccess { load(cardId); _state.value = _state.value.copy(notice = "Note saved on this phone.") }
                .onFailure { fail(it) }
        }
    } else performOnCard("Note saved", cardId) { targetCardId -> domainApi.createNote(targetCardId, NoteWriteDto(title, content)) }
    fun updateNote(cardId: String, noteId: String, title: String, content: String) = if (cardId.startsWith("local:")) {
        viewModelScope.launch {
            runCatching { localCardRepository.saveNote(cardId.removePrefix("local:"), noteId, title.trim(), content) }
                .onSuccess { load(cardId); _state.value = _state.value.copy(notice = "Note updated.") }
                .onFailure { fail(it) }
        }
    } else perform("Note updated", cardId) { domainApi.updateNote(noteId, NoteWriteDto(title, content)) }
    fun createStudySet(cardId: String, title: String, description: String) = performOnCard("Study Set saved", cardId) { ownerCardId ->
        domainApi.createStudySet(ownerCardId, StudySetWriteDto(title, description))
    }
    fun updateStudySet(cardId: String, id: String, title: String, description: String) = perform("Study Set updated", cardId) { domainApi.updateStudySet(id, StudySetWriteDto(title, description)) }
    fun deleteStudySet(cardId: String, id: String) = perform("Study Set deleted", cardId) { domainApi.deleteStudySet(id) }
    fun deleteQuiz(cardId: String, id: String) = perform("Quiz deleted", cardId) { domainApi.deleteQuiz(id) }
    fun deleteFlashcardSet(cardId: String, id: String) = perform("Flashcard Set deleted", cardId) { domainApi.deleteFlashcardSet(id) }
    fun createFlashcardSet(cardId: String, title: String, front: String, back: String) = performOnCard("Flashcard Set saved", cardId) { ownerCardId ->
        domainApi.createFlashcardSet(ownerCardId, FlashcardSetWriteDto(title, "", listOf(FlashcardWriteDto(1, front, back))))
    }
    fun createQuiz(cardId: String, title: String, prompt: String, correct: String, other: String) = performOnCard("Quiz created", cardId) { ownerCardId ->
        domainApi.createQuiz(ownerCardId, QuizWriteDto(title, "", listOf(QuizQuestionWriteDto(1, prompt,
            listOf(QuizOptionWriteDto(1, correct, true), QuizOptionWriteDto(2, other, false))))))
    }

    fun setArtifactShared(cardId: String, artifactType: String, artifactId: String, shared: Boolean) =
        perform(if (shared) "Shared with this Course Space" else "Made private", cardId) {
            if (shared) domainApi.shareArtifact(artifactType, artifactId, ArtifactShareDto(workspaceCardId))
            else domainApi.unshareArtifact(artifactType, artifactId, workspaceCardId)
        }

    fun createCourseLink(cardId: String) {
        viewModelScope.launch {
            if (!requireOnline("You’re offline. Connect to the internet to create an invite link.")) return@launch
            _state.value = _state.value.copy(busy = true, notice = null)
            runCatching {
                cardApi.enableSharing(cardId)
                cardApi.createShareLink(cardId).url
            }.onSuccess { _state.value = _state.value.copy(busy = false, shareLink = it, notice = "Invite link ready") }
                .onFailure { fail(it, busy = false) }
        }
    }

    fun loadCourseLink(cardId: String) {
        viewModelScope.launch {
            if (!requireOnline("You’re offline. Connect to the internet to load the invite link.")) return@launch
            runCatching { cardApi.getShareLink(cardId) }
                .onSuccess { link -> _state.value = _state.value.copy(shareLink = link.url, shareApproval = link.requireApproval, notice = null) }
                .onFailure { fail(it) }
        }
    }

    fun createOrResetCourseLink(cardId: String) {
        viewModelScope.launch {
            if (!requireOnline("You’re offline. Connect to the internet to manage invite links.")) return@launch
            _state.value = _state.value.copy(busy = true)
            runCatching { cardApi.createShareLink(cardId) }
                .onSuccess { link -> _state.value = _state.value.copy(busy = false, shareLink = link.url,
                    shareApproval = link.requireApproval, notice = "Invite link created. Previous links are now invalid.") }
                .onFailure { fail(it, busy = false) }
        }
    }

    fun enableCourseSpace(cardId: String, onEnabled: () -> Unit = {}) {
        viewModelScope.launch {
            if (!requireOnline("You’re offline. Connect to the internet to create a Course Space.")) return@launch
            _state.value = _state.value.copy(busy = true)
            runCatching { cardApi.enableSharing(cardId) }
                .onSuccess {
                    _state.value = _state.value.copy(busy = false, notice = "Card is now a Course Space")
                    onEnabled()
                }
                .onFailure { fail(it, busy = false) }
        }
    }

    fun setApproval(cardId: String, required: Boolean) {
        viewModelScope.launch {
            if (!requireOnline("You’re offline. Connect to the internet to change Course Space settings.")) return@launch
            runCatching { cardApi.setJoinApproval(cardId, com.aipdfreader.app.data.remote.dto.RequireApprovalRequest(required)) }
                .onSuccess { _state.value = _state.value.copy(shareApproval = required, notice = if (required) "Join requests now need approval" else "Anyone with the current invite link can join") }
                .onFailure { fail(it) }
        }
    }

    fun uploadResource(cardId: String, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, notice = null)
            if (cardId.startsWith("local:")) {
                val saved = runCatching { localCardRepository.addMaterial(cardId.removePrefix("local:"), uri) }.getOrDefault(false)
                if (saved) {
                    load(cardId)
                    _state.value = _state.value.copy(busy = false, notice = "File saved to this Card on your phone.", error = false)
                } else {
                    _state.value = _state.value.copy(busy = false, notice = "Couldn’t save that file. Check that it is still available and try again.", error = true)
                }
                return@launch
            }
            if (!requireOnline("You’re offline. Connect to the internet to upload this resource.")) return@launch
            runCatching {
                val copy = FileUtils.copyMaterialToInternalStorage(context, uri)
                    ?: error("We couldn’t read that file.")
                try {
                    val mimeType = FileUtils.mimeTypeForFileName(copy.displayName)
                        ?: error("This file type isn’t supported yet. Use PDF, DOCX, PPTX, XLSX, CSV, TXT, PNG, JPEG, or WebP.")
                    val file = File(copy.path)
                    val part = MultipartBody.Part.createFormData("file", copy.displayName,
                        file.asRequestBody(mimeType.toMediaType()))
                    val destinationCardId = if (workspaceIsShared && workspaceCardId == cardId)
                        workspaceMemberCardId else cardId
                    domainApi.uploadResource(destinationCardId, part, copy.displayName.substringBeforeLast('.', copy.displayName))
                } finally { FileUtils.deleteFile(copy.path) }
            }.onSuccess {
                _state.value = _state.value.copy(busy = false, notice = "File uploaded and processed")
                load(cardId, workspaceIsShared, workspaceMemberCardId, workspaceRole)
            }.onFailure { fail(it, busy = false) }
        }
    }

    fun openResource(
        resource: ResourceDto,
        onOpenPdf: (Long) -> Unit,
        onOpenFile: (Uri, String) -> Unit
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, notice = null, downloadingResourceId = null)
            if (workspaceIsLocalCard()) {
                val localId = _state.value.resources.firstOrNull { it.id == resource.id }?.id?.toLongOrNull()
                if (localId != null) {
                    val material = localCardRepository.getMaterial(currentLocalCardId, localId)
                    if (material == null) {
                        _state.value = _state.value.copy(busy = false, notice = "This saved file is no longer available.", error = true)
                        return@launch
                    }
                    val file = File(material.filePath)
                    if (!file.isFile) {
                        _state.value = _state.value.copy(busy = false, notice = "This saved file is no longer available.", error = true)
                        return@launch
                    }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    if (shouldOpenInVisionPdfReader(material.mimeType, material.displayName)) {
                        val pdfId = pdfRepository.importPdf(uri)
                        if (pdfId == null) _state.value = _state.value.copy(busy = false, notice = "This PDF couldn’t be opened.", error = true)
                        else { _state.value = _state.value.copy(busy = false); onOpenPdf(pdfId) }
                    } else {
                        _state.value = _state.value.copy(busy = false)
                        onOpenFile(uri, resolvedResourceMimeType(material.mimeType, material.displayName))
                    }
                    return@launch
                }
            }
            val ownerUid = authRepository.currentUserId
            if (ownerUid == null) {
                _state.value = _state.value.copy(busy = false, notice = "Sign in to open this shared resource.", error = true)
                return@launch
            }
            val owningCardId = _state.value.resourceCardIds[resource.id] ?: workspaceCardId
            var cached = remoteWorkspaceCache.cachedFile(ownerUid, owningCardId, resource.id)
            if (cached != null && workspaceIsShared && BackendConfiguration.isConfigured && hasValidatedInternet()) {
                val stillAuthorized = runCatching {
                    domainApi.resources(owningCardId).any { it.id == resource.id }
                }.getOrElse {
                    _state.value = _state.value.copy(
                        busy = false,
                        notice = "Vision couldn’t verify your access to this shared file. Try again.",
                        error = true
                    )
                    return@launch
                }
                if (!stillAuthorized) {
                    remoteWorkspaceCache.removeCachedFile(ownerUid, owningCardId, resource.id)
                    _state.value = _state.value.copy(
                        busy = false,
                        localResourcePaths = _state.value.localResourcePaths - resource.id,
                        notice = "This shared file is no longer available to you.",
                        error = true
                    )
                    return@launch
                }
            }
            if (cached == null && !hasValidatedInternet()) {
                _state.value = _state.value.copy(
                    busy = false,
                    offline = true,
                    notice = "This file isn’t available offline. Connect to download it.",
                    error = true
                )
                return@launch
            }
            if (cached == null) _state.value = _state.value.copy(downloadingResourceId = resource.id)
            runCatching {
                val displayName = resource.originalFilename?.substringAfterLast('/')
                    ?.replace(Regex("[^A-Za-z0-9._ -]"), "_")?.takeIf(String::isNotBlank) ?: "resource"
                val mimeType = resolvedResourceMimeType(resource.mimeType, displayName)
                if (cached == null) {
                    val response = domainApi.downloadResource(resource.id)
                    val temporary = File(context.cacheDir, "${UUID.randomUUID()}_$displayName")
                    try {
                        response.byteStream().use { input -> temporary.outputStream().use(input::copyTo) }
                        cached = remoteWorkspaceCache.cacheDownloadedFile(
                            ownerUid, owningCardId, resource.id, displayName, mimeType,
                            temporary.absolutePath, resource.fileSizeBytes
                        ) ?: error("This file couldn’t be saved for offline use.")
                    } finally { FileUtils.deleteFile(temporary.absolutePath) }
                }
                val savedFile = File(cached!!.filePath)
                if (!savedFile.isFile) error("This file isn’t available offline.")
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", savedFile)
                if (shouldOpenInVisionPdfReader(mimeType, displayName)) {
                    val id = pdfRepository.importPdf(uri) ?: error("The downloaded PDF couldn’t be opened.")
                    DownloadedResource.Pdf(id)
                } else DownloadedResource.File(uri, mimeType)
            }.onSuccess { opened ->
                _state.value = _state.value.copy(
                    busy = false,
                    offline = false,
                    downloadingResourceId = null,
                    localResourcePaths = _state.value.localResourcePaths + (resource.id to cached!!.filePath),
                    notice = "Saved on this phone for offline reading.",
                    error = false
                )
                when (opened) {
                    is DownloadedResource.Pdf -> onOpenPdf(opened.id)
                    is DownloadedResource.File -> onOpenFile(opened.uri, opened.mimeType)
                }
            }.onFailure { fail(it, busy = false) }
        }
    }

    private sealed interface DownloadedResource {
        data class Pdf(val id: Long) : DownloadedResource
        data class File(val uri: Uri, val mimeType: String) : DownloadedResource
    }

    fun deleteNote(noteId: String, cardId: String) {
        viewModelScope.launch {
            if (cardId.startsWith("local:")) {
                runCatching { localCardRepository.deleteNote(cardId.removePrefix("local:"), noteId) }
                    .onSuccess { load(cardId); _state.value = _state.value.copy(notice = "Note deleted.") }
                    .onFailure { fail(it) }
                return@launch
            }
            if (!requireOnline("You’re offline. Connect to the internet to delete this Note.")) return@launch
            runCatching { domainApi.deleteNote(noteId) }.onSuccess {
                _state.value = _state.value.copy(notice = "Note deleted")
                load(cardId, workspaceIsShared, workspaceMemberCardId, workspaceRole)
            }
                .onFailure { fail(it) }
        }
    }

    fun reviewFlashcard(setId: String, card: FlashcardDto, gotIt: Boolean) {
        viewModelScope.launch {
            if (!requireOnline("You’re offline. You can review this set, but progress will update when you reconnect.")) return@launch
            runCatching { domainApi.reviewFlashcard(setId, FlashcardProgressWriteDto(card.id, if (gotIt) "GOT_IT" else "AGAIN")) }
                .onSuccess { _state.value = _state.value.copy(notice = if (gotIt) "Marked Got it" else "Added to review") }
                .onFailure { fail(it) }
        }
    }

    fun submitAttempt(quizId: String, answers: List<QuizAnswerWriteDto>) {
        viewModelScope.launch {
            if (!requireOnline("You’re offline. Connect to the internet to save this quiz attempt.")) return@launch
            _state.value = _state.value.copy(busy = true, notice = null)
            runCatching { domainApi.submitQuizAttempt(quizId, QuizAttemptWriteDto(answers)) }
                .onSuccess { attempt -> _state.value = _state.value.copy(busy = false,
                    notice = "Your attempt: ${attempt.correctCount} / ${attempt.totalQuestions} correct") }
                .onFailure { fail(it, busy = false) }
        }
    }

    fun loadAttempts(quizId: String) {
        viewModelScope.launch {
            if (!requireOnline("You’re offline. Quiz attempts need an internet connection.")) return@launch
            runCatching { domainApi.myQuizAttempts(quizId) }
                .onSuccess { _state.value = _state.value.copy(attempts = it, notice = null) }
                .onFailure { fail(it) }
        }
    }

    fun inviteUser(cardId: String, userId: String) = perform("Invitation sent", cardId) {
        domainApi.invite(cardId, InviteUserDto(userId.trim()))
    }
    fun withdrawInvitation(cardId: String, membershipId: String) = perform("Invitation withdrawn", cardId) {
        domainApi.withdrawInvitation(cardId, membershipId)
    }
    fun approveJoin(cardId: String, requestId: String) = perform("Join request approved", cardId) { domainApi.approveJoin(cardId, requestId) }
    fun rejectJoin(cardId: String, requestId: String) = perform("Join request rejected", cardId) { domainApi.rejectJoin(cardId, requestId) }
    fun promoteMember(cardId: String, userId: String) = perform("Member promoted", cardId) { domainApi.promote(cardId, userId) }
    fun demoteMember(cardId: String, userId: String) = perform("Member changed to Member", cardId) { domainApi.demote(cardId, userId) }
    fun removeMember(cardId: String, userId: String) = perform("Member removed", cardId) { domainApi.removeMember(cardId, userId) }
    fun transferOwnership(cardId: String, userId: String) = perform("Ownership transferred", cardId) {
        domainApi.transferOwnership(cardId, TransferOwnershipDto(userId.trim()))
    }
    fun leaveCourseSpace(cardId: String) = perform("You left the Course Space", cardId) { domainApi.leaveCourseSpace(cardId) }
    fun dissolveCourseSpace(cardId: String) = perform("Course Space dissolved", cardId) { domainApi.dissolveCourseSpace(cardId) }

    fun sendSarah(cardId: String, question: String) {
        viewModelScope.launch {
            if (!hasValidatedInternet()) {
                _state.value = _state.value.copy(
                    offline = true,
                    notice = "Sarah is unavailable while you’re offline.",
                    error = true
                )
                return@launch
            }
            if (!BackendConfiguration.isConfigured) {
                _state.value = _state.value.copy(
                    offline = false,
                    notice = "Sarah will be available when Vision’s server is deployed.",
                    error = true
                )
                return@launch
            }
            val remoteId = resolveRemoteCardId(cardId)
            if (remoteId == null) {
                _state.value = _state.value.copy(
                    notice = "Your files are ready offline. Sarah will be available after this Card syncs with Vision’s server.",
                    error = true
                )
                return@launch
            }
            val previous = _state.value.messages
            val pendingMessages = previous + SarahMessage("user", question)
            _state.value = _state.value.copy(busy = true, notice = null,
                messages = pendingMessages)
            authRepository.currentUserId?.let { uid ->
                remoteWorkspaceCache.saveMessages(uid, workspaceCardId, conversationId,
                    pendingMessages.map { CachedSarahMessage(it.role, it.content) })
            }
            runCatching {
                domainApi.askSarah(remoteId, SarahAskDto(conversationId, question,
                    previous.map { SarahHistoryItem(it.role, it.content) }))
            }.onSuccess { answer ->
                val completeMessages = _state.value.messages + SarahMessage("assistant", answer.answer)
                _state.value = _state.value.copy(busy = false,
                    messages = completeMessages,
                    usage = "${answer.usageUsed} / ${answer.usageLimit} this month",
                    usageUsed = answer.usageUsed, usageLimit = answer.usageLimit)
                authRepository.currentUserId?.let { uid ->
                    remoteWorkspaceCache.saveMessages(uid, workspaceCardId, answer.conversationId,
                        completeMessages.map { CachedSarahMessage(it.role, it.content) })
                }
            }.onFailure { fail(it, busy = false) }
        }
    }

    fun generate(cardId: String, type: String, resources: List<String>, instructions: String? = null) {
        viewModelScope.launch {
            if (!requireOnline("You’re offline. Connect to the internet to ask Sarah to generate study materials.")) return@launch
            val remoteId = resolveRemoteCardId(cardId)
            if (remoteId == null) {
                _state.value = _state.value.copy(
                    notice = "Your Card and files are saved offline. Sarah can generate study materials after Vision’s server is available and the Card syncs.",
                    error = true
                )
                return@launch
            }
            val remoteResourceIds = if (cardId.startsWith("local:")) {
                val mappings = _state.value.remoteResourceIdsByLocalId
                resources.map { localIdOrRemoteId -> mappings[localIdOrRemoteId] ?: localIdOrRemoteId }
            } else resources
            if (remoteResourceIds.isEmpty() || remoteResourceIds.size != resources.size) {
                _state.value = _state.value.copy(
                    notice = "These files are still waiting to sync. Sarah can use them as soon as the upload finishes.",
                    error = true
                )
                return@launch
            }
            _state.value = _state.value.copy(busy = true, notice = null)
            runCatching { domainApi.generateWithSarah(remoteId, SarahGenerateDto(type, remoteResourceIds, instructions)) }
                .onSuccess { result ->
                    _state.value = _state.value.copy(busy = false,
                        notice = "Sarah created a ${result.artifactType}. Usage: ${result.usageUsed} / ${result.usageLimit} this month")
                    load(cardId, workspaceIsShared, workspaceMemberCardId, workspaceRole)
                }.onFailure { fail(it, busy = false) }
        }
    }

    fun draftStudyNote(cardId: String, title: String, existingContent: String? = null,
                       onDraft: (String, String) -> Unit) {
        viewModelScope.launch {
            if (!requireOnline("You’re offline. Sarah needs an internet connection to draft a study note.")) return@launch
            val remoteId = resolveRemoteCardId(cardId)
            if (remoteId == null) {
                _state.value = _state.value.copy(
                    notice = "This Card and its files are saved offline. Sarah can draft a note after the Card and its files sync.",
                    error = true
                )
                return@launch
            }
            if (cardId.startsWith("local:")) {
                val localMaterials = localCardRepository.observeMaterials(cardId.removePrefix("local:")).first()
                if (localMaterials.any { it.remoteResourceId == null }) {
                    _state.value = _state.value.copy(
                        notice = "Some files are still waiting to sync. Sarah can use them once the upload finishes.",
                        error = true
                    )
                    return@launch
                }
            }
            val question = buildString {
                append("Create a clear study note summarizing the authorized learning materials in this Card. ")
                append("Use titled sections and concise explanations. Do not invent facts. Return only the note content.")
                if (!existingContent.isNullOrBlank()) {
                    append(" Regenerate and improve this existing note while keeping its useful facts: ")
                    append(existingContent.take(2000))
                }
            }
            _state.value = _state.value.copy(busy = true, notice = null, error = false)
            runCatching {
                domainApi.askSarah(remoteId, SarahAskDto(UUID.randomUUID().toString(), question, emptyList()))
            }.onSuccess { answer ->
                _state.value = _state.value.copy(
                    busy = false,
                    notice = "Sarah drafted a note. Review it before saving.",
                    error = false,
                    usage = "${answer.usageUsed} / ${answer.usageLimit} this month",
                    usageUsed = answer.usageUsed,
                    usageLimit = answer.usageLimit
                )
                onDraft(title.ifBlank { "Study note" }, answer.answer)
            }.onFailure { fail(it, busy = false) }
        }
    }

    private fun <T> perform(success: String, cardId: String? = null, block: suspend () -> T) {
        viewModelScope.launch {
            if (!requireOnline("You’re offline. Connect to the internet to continue.")) return@launch
            _state.value = _state.value.copy(busy = true, notice = null)
            runCatching { block() }.onSuccess {
                _state.value = _state.value.copy(busy = false, notice = success)
                cardId?.let { load(it, workspaceIsShared, workspaceMemberCardId, workspaceRole) }
            }
                .onFailure { fail(it, busy = false) }
        }
    }

    private fun <T> performOnCard(success: String, cardId: String, block: suspend (String) -> T) {
        viewModelScope.launch {
            if (!requireOnline("You’re offline. Connect to the internet to continue.")) return@launch
            _state.value = _state.value.copy(busy = true, notice = null)
            runCatching {
                val actionCardId = if (workspaceIsShared && workspaceCardId == cardId) workspaceMemberCardId
                    else resolveRemoteCardId(cardId)
                block(actionCardId ?: error("This Card is waiting to sync with Vision’s server."))
            }.onSuccess {
                _state.value = _state.value.copy(busy = false, notice = success, error = false)
                load(cardId, workspaceIsShared, workspaceMemberCardId, workspaceRole)
            }.onFailure { fail(it, busy = false) }
        }
    }

    private suspend fun resolveRemoteCardId(cardId: String): String? = if (cardId.startsWith("local:")) {
        localCardRepository.getCard(cardId.removePrefix("local:"))?.remoteCardId
    } else cardId

    private fun hasValidatedInternet(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun requireOnline(message: String): Boolean {
        if (!BackendConfiguration.isConfigured) {
            _state.value = _state.value.copy(busy = false, notice = "Vision’s server is not deployed yet.", error = true)
            return false
        }
        if (!hasValidatedInternet()) {
            _state.value = _state.value.copy(busy = false, offline = true, notice = message, error = true)
            return false
        }
        return true
    }

    private fun fail(error: Throwable, loading: Boolean? = null, busy: Boolean? = null) {
        val message = if (error is HttpException && error.code() == 401)
            "This Firebase account isn’t linked to a Vision profile yet. Ask the administrator to provision it."
        else if (!hasValidatedInternet())
            "You’re offline. Connect to the internet to continue."
        else if (error.message?.contains("failed to connect", true) == true)
            "Couldn’t connect to Vision. Check the backend URL and network."
        else error.message?.takeIf { it.length < 180 } ?: "Something went wrong. Please try again."
        _state.value = _state.value.copy(notice = message, error = true, downloadingResourceId = null,
            loading = loading ?: _state.value.loading, busy = busy ?: _state.value.busy)
    }

    private var currentLocalCardId: String = ""
    private fun workspaceIsLocalCard() = _state.value.isLocalCard
}

internal fun shouldOpenInVisionPdfReader(mimeType: String?, fileName: String?): Boolean =
    mimeType?.equals("application/pdf", ignoreCase = true) == true ||
        fileName?.endsWith(".pdf", ignoreCase = true) == true

internal fun resolvedResourceMimeType(mimeType: String?, fileName: String?): String {
    val declared = mimeType?.trim()?.takeIf {
        it.isNotBlank() && !it.equals("application/octet-stream", ignoreCase = true)
    }
    return declared
        ?: com.aipdfreader.app.util.FileUtils.mimeTypeForFileName(fileName.orEmpty())
        ?: mimeType?.trim()?.takeIf(String::isNotBlank)
        ?: "application/octet-stream"
}
