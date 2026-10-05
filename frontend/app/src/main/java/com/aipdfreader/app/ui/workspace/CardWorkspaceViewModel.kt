package com.aipdfreader.app.ui.workspace

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aipdfreader.app.data.remote.CardApi
import com.aipdfreader.app.data.remote.DomainApi
import com.aipdfreader.app.data.remote.dto.*
import com.aipdfreader.app.data.repository.PdfRepository
import com.aipdfreader.app.util.FileUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import javax.inject.Inject

data class SarahMessage(val role: String, val content: String)
data class WorkspaceState(
    val resources: List<ResourceDto> = emptyList(), val notes: List<NoteDto> = emptyList(),
    val studySets: List<StudySetDto> = emptyList(), val quizzes: List<QuizDto> = emptyList(),
    val flashcardSets: List<FlashcardSetDto> = emptyList(), val events: List<EventDto> = emptyList(),
    val members: List<MemberDto> = emptyList(), val attempts: List<QuizAttemptDto> = emptyList(),
    val flashcardProgress: Map<String, List<FlashcardProgressDto>> = emptyMap(),
    val progressLoading: String? = null, val progressError: String? = null,
    val joinRequests: List<JoinRequestItemDto> = emptyList(),
    val messages: List<SarahMessage> = emptyList(),
    val loading: Boolean = false, val busy: Boolean = false, val notice: String? = null,
    val error: Boolean = false, val shareLink: String? = null, val usage: String? = null,
    val usageUsed: Int? = null, val usageLimit: Int? = null,
    val shareApproval: Boolean? = null
)

@HiltViewModel
class CardWorkspaceViewModel @Inject constructor(
    private val domainApi: DomainApi,
    private val cardApi: CardApi,
    private val pdfRepository: PdfRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(WorkspaceState())
    val state: StateFlow<WorkspaceState> = _state.asStateFlow()
    private val conversationId = UUID.randomUUID().toString()
    private var workspaceIsShared = false

    fun load(cardId: String, isShared: Boolean = false) {
        workspaceIsShared = isShared
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = false)
            runCatching {
                cardApi.getCard(cardId)
                val resources = async { runCatching { domainApi.resources(cardId) } }
                val notes = async { runCatching { domainApi.notes(cardId) } }
                val studySets = async { runCatching { domainApi.studySets(cardId) } }
                val quizzes = async { runCatching { domainApi.quizzes(cardId) } }
                val flashcardSets = async { runCatching { domainApi.flashcardSets(cardId) } }
                val events = async { runCatching { domainApi.events(cardId) } }
                val members = async { runCatching { if (isShared) domainApi.members(cardId) else emptyList() } }
                val joinRequests = async { runCatching { if (isShared) domainApi.joinRequests(cardId) else emptyList() } }
                listOf(
                    resources.await(), notes.await(), studySets.await(), quizzes.await(),
                    flashcardSets.await(), events.await(), members.await(), joinRequests.await()
                )
            }.onSuccess { results ->
                @Suppress("UNCHECKED_CAST")
                val resourceResult = results[0] as Result<List<ResourceDto>>
                @Suppress("UNCHECKED_CAST")
                val noteResult = results[1] as Result<List<NoteDto>>
                @Suppress("UNCHECKED_CAST")
                val studySetResult = results[2] as Result<List<StudySetDto>>
                @Suppress("UNCHECKED_CAST")
                val quizResult = results[3] as Result<List<QuizDto>>
                @Suppress("UNCHECKED_CAST")
                val flashcardResult = results[4] as Result<List<FlashcardSetDto>>
                @Suppress("UNCHECKED_CAST")
                val eventResult = results[5] as Result<List<EventDto>>
                @Suppress("UNCHECKED_CAST")
                val memberResult = results[6] as Result<List<MemberDto>>
                @Suppress("UNCHECKED_CAST")
                val joinRequestResult = results[7] as Result<List<JoinRequestItemDto>>
                val sectionErrors = listOf(
                    resourceResult.exceptionOrNull(), noteResult.exceptionOrNull(),
                    studySetResult.exceptionOrNull(), quizResult.exceptionOrNull(),
                    flashcardResult.exceptionOrNull(), eventResult.exceptionOrNull(),
                    memberResult.exceptionOrNull(), joinRequestResult.exceptionOrNull()
                ).filterNotNull()
                _state.value = _state.value.copy(
                    resources = resourceResult.getOrElse { _state.value.resources },
                    notes = noteResult.getOrElse { _state.value.notes },
                    studySets = studySetResult.getOrElse { _state.value.studySets },
                    quizzes = quizResult.getOrElse { _state.value.quizzes },
                    flashcardSets = flashcardResult.getOrElse { _state.value.flashcardSets },
                    events = eventResult.getOrElse { _state.value.events },
                    members = memberResult.getOrElse { _state.value.members },
                    joinRequests = joinRequestResult.getOrElse { _state.value.joinRequests },
                    loading = false,
                    error = sectionErrors.isNotEmpty(),
                    notice = if (sectionErrors.isEmpty()) null else
                        "Some workspace sections couldn't load. Tap Refresh to try again."
                )
            }.onFailure { fail(it, loading = false) }
        }
    }
    fun createNote(cardId: String, title: String, content: String) = perform("Note saved", cardId) { domainApi.createNote(cardId, NoteWriteDto(title, content)) }
    fun updateNote(cardId: String, noteId: String, title: String, content: String) = perform("Note updated", cardId) { domainApi.updateNote(noteId, NoteWriteDto(title, content)) }
    fun createStudySet(cardId: String, title: String, description: String) = perform("Study Set saved", cardId) { domainApi.createStudySet(cardId, StudySetWriteDto(title, description)) }
    fun updateStudySet(cardId: String, id: String, title: String, description: String) = perform("Study Set updated", cardId) { domainApi.updateStudySet(id, StudySetWriteDto(title, description)) }
    fun deleteStudySet(cardId: String, id: String) = perform("Study Set deleted", cardId) { domainApi.deleteStudySet(id) }
    fun deleteQuiz(cardId: String, id: String) = perform("Quiz deleted", cardId) { domainApi.deleteQuiz(id) }
    fun updateQuiz(cardId: String, id: String, body: QuizPatchDto, onSaved: () -> Unit = {}) = perform("Quiz updated", cardId, onSuccess = onSaved) { domainApi.updateQuiz(id, body) }
    fun deleteFlashcardSet(cardId: String, id: String) = perform("Flashcard Set deleted", cardId) { domainApi.deleteFlashcardSet(id) }
    fun createFlashcardSet(cardId: String, body: FlashcardSetWriteDto, onSaved: () -> Unit = {}) = perform("Flashcard Set saved", cardId, onSuccess = onSaved) { domainApi.createFlashcardSet(cardId, body) }
    fun updateFlashcardSet(cardId: String, id: String, body: FlashcardSetPatchDto, onSaved: () -> Unit = {}) = perform("Flashcard Set updated", cardId, onSuccess = onSaved) { domainApi.updateFlashcardSet(id, body) }
    fun createQuiz(cardId: String, body: QuizWriteDto, onSaved: () -> Unit = {}) = perform("Quiz created", cardId, onSuccess = onSaved) { domainApi.createQuiz(cardId, body) }
    fun createFlashcardSet(cardId: String, title: String, front: String, back: String) = perform("Flashcard Set saved", cardId) {
        domainApi.createFlashcardSet(cardId, FlashcardSetWriteDto(title, "", listOf(FlashcardWriteDto(1, front, back))))
    }
    fun createQuiz(cardId: String, title: String, prompt: String, correct: String, other: String) = perform("Quiz created", cardId) {
        domainApi.createQuiz(cardId, QuizWriteDto(title, "", listOf(QuizQuestionWriteDto(1, prompt,
            listOf(QuizOptionWriteDto(1, correct, true), QuizOptionWriteDto(2, other, false)))))
    }

    fun createCourseLink(cardId: String) {
        viewModelScope.launch {
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
            runCatching { cardApi.getShareLink(cardId) }
                .onSuccess { link -> _state.value = _state.value.copy(shareLink = link.url, shareApproval = link.requireApproval, notice = null) }
                .onFailure { fail(it) }
        }
    }

    fun createOrResetCourseLink(cardId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true)
            runCatching { cardApi.createShareLink(cardId) }
                .onSuccess { link -> _state.value = _state.value.copy(busy = false, shareLink = link.url,
                    shareApproval = link.requireApproval, notice = "Invite link created. Previous links are now invalid.") }
                .onFailure { fail(it, busy = false) }
        }
    }

    fun enableCourseSpace(cardId: String, onEnabled: () -> Unit = {}) {
        viewModelScope.launch {
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
            runCatching { cardApi.setJoinApproval(cardId, com.aipdfreader.app.data.remote.dto.RequireApprovalRequest(required)) }
                .onSuccess { _state.value = _state.value.copy(shareApproval = required, notice = if (required) "Join requests now need approval" else "Anyone with the current invite link can join") }
                .onFailure { fail(it) }
        }
    }

    fun uploadPdf(cardId: String, uri: Uri) = uploadResource(cardId, uri)

    fun uploadResource(cardId: String, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, notice = null, error = false)
            runCatching {
                val copy = FileUtils.copyResourceToCache(context, uri)
                    ?: error("Choose a supported file under 20 MB: PDF, DOCX, PPTX, XLSX, CSV, TXT, PNG, JPEG, or WEBP.")
                try {
                    val file = File(copy.path)
                    val mimeType = copy.mimeType ?: error("This resource type is not supported.")
                    val part = MultipartBody.Part.createFormData(
                        "file", copy.displayName, file.asRequestBody(mimeType.toMediaType())
                    )
                    domainApi.uploadResource(
                        cardId,
                        part,
                        copy.displayName.substringBeforeLast('.', copy.displayName)
                    )
                } finally {
                    FileUtils.deleteFile(copy.path)
                }
            }.onSuccess {
                _state.value = _state.value.copy(busy = false, notice = "Resource uploaded and processed")
                load(cardId, workspaceIsShared)
            }.onFailure { fail(it, busy = false) }
        }
    }

    fun openResource(resource: ResourceDto, onOpenedPdf: (Long) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, notice = null, error = false)
            var downloadedFile: File? = null
            runCatching {
                val extension = FileUtils.resourceExtension(resource.originalFilename)
                    ?: error("This resource file type is not supported on this device.")
                val mimeType = FileUtils.resourceMimeType(resource.originalFilename)
                    ?: error("This resource file type is not supported on this device.")
                if (resource.mimeType != null && resource.mimeType != mimeType) {
                    error("The resource's file type does not match its filename.")
                }

                domainApi.downloadResource(resource.id).use { response ->
                    val file = File(context.cacheDir, "lumira-resource-" + UUID.randomUUID() + "." + extension)
                    downloadedFile = file
                    val declaredLength = response.contentLength()
                    if (declaredLength > 20L * 1024 * 1024) error("This resource is larger than the supported 20 MB limit.")
                    var totalBytes = 0L
                    response.byteStream().use { input ->
                        file.outputStream().buffered().use { output ->
                            val buffer = ByteArray(8 * 1024)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                totalBytes += count
                                if (totalBytes > 20L * 1024 * 1024) error("This resource is larger than the supported 20 MB limit.")
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    if (totalBytes == 0L) error("The downloaded resource was empty.")

                    val fileUri = FileProvider.getUriForFile(
                        context, context.packageName + ".fileprovider", file
                    )
                    if (mimeType == "application/pdf") {
                        pdfRepository.importPdf(fileUri) ?: error("The downloaded PDF couldn’t be opened.")
                    } else {
                        val viewIntent = Intent(Intent.ACTION_VIEW)
                            .setDataAndType(fileUri, mimeType)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                        try {
                            context.startActivity(
                                Intent.createChooser(viewIntent, "Open resource")
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        } catch (_: ActivityNotFoundException) {
                            error("No compatible app is installed to open this file.")
                        }
                        null
                    }
                }
            }.onSuccess { pdfId ->
                _state.value = _state.value.copy(
                    busy = false,
                    notice = if (pdfId == null) "Resource opened in another app." else null,
                    error = false
                )
                if (pdfId != null) {
                    downloadedFile?.let { FileUtils.deleteFile(it.absolutePath) }
                    onOpenedPdf(pdfId)
                }
            }.onFailure { error ->
                downloadedFile?.let { FileUtils.deleteFile(it.absolutePath) }
                fail(error, busy = false)
            }
        }
    }
    fun deleteNote(noteId: String, cardId: String) {
        viewModelScope.launch {
            runCatching { domainApi.deleteNote(noteId) }.onSuccess { _state.value = _state.value.copy(notice = "Note deleted"); load(cardId, workspaceIsShared) }
                .onFailure { fail(it) }
        }
    }

    fun loadFlashcardProgress(setId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(progressLoading = setId, progressError = null)
            runCatching { domainApi.myFlashcardProgress(setId) }
                .onSuccess { progress -> _state.value = _state.value.copy(
                    flashcardProgress = _state.value.flashcardProgress + (setId to progress),
                    progressLoading = null, progressError = null
                )
                }
                .onFailure { error -> _state.value = _state.value.copy(progressLoading = null, progressError = error.message ?: "Could not load review history.") }
        }
    }

    fun reviewFlashcard(setId: String, card: FlashcardDto, gotIt: Boolean) {
        viewModelScope.launch {
            runCatching { domainApi.reviewFlashcard(setId, FlashcardProgressWriteDto(card.id, if (gotIt) "GOT_IT" else "AGAIN")) }
                .onSuccess { progress ->
                    val previous = _state.value.flashcardProgress[setId].orEmpty()
                    _state.value = _state.value.copy(
                        flashcardProgress = _state.value.flashcardProgress + (setId to (previous + progress)),
                        notice = if (gotIt) "Marked Got it" else "Added to review"
                    )
                }
                .onFailure { fail(it) }
        }
    }

    fun submitAttempt(quizId: String, answers: List<QuizAnswerWriteDto>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, notice = null)
            runCatching { domainApi.submitQuizAttempt(quizId, QuizAttemptWriteDto(answers)) }
                .onSuccess { attempt -> _state.value = _state.value.copy(busy = false,
                    notice = "Your attempt: ${attempt.correctCount} / ${attempt.totalQuestions} correct") }
                .onFailure { fail(it, busy = false) }
        }
    }

    fun loadAttempts(quizId: String) {
        viewModelScope.launch {
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
        val previous = _state.value.messages
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, notice = null,
                messages = previous + SarahMessage("user", question))
            runCatching {
                domainApi.askSarah(cardId, SarahAskDto(conversationId, question,
                    previous.map { SarahHistoryItem(it.role, it.content) }))
            }.onSuccess { answer ->
                _state.value = _state.value.copy(busy = false,
                    messages = _state.value.messages + SarahMessage("assistant", answer.answer),
                    usage = "${answer.usageUsed} / ${answer.usageLimit} this month",
                    usageUsed = answer.usageUsed, usageLimit = answer.usageLimit)
            }.onFailure { fail(it, busy = false) }
        }
    }

    fun generate(cardId: String, type: String, resources: List<String>) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, notice = null)
            runCatching { domainApi.generateWithSarah(cardId, SarahGenerateDto(type, resources)) }
                .onSuccess { result ->
                    _state.value = _state.value.copy(busy = false,
                        notice = "Sarah created a ${result.artifactType}. Usage: ${result.usageUsed} / ${result.usageLimit} this month")
                    load(cardId, workspaceIsShared)
                }.onFailure { fail(it, busy = false) }
        }
    }

    private fun <T> perform(success: String, cardId: String? = null, onSuccess: () -> Unit = {}, block: suspend () -> T) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, notice = null)
            runCatching { block() }.onSuccess {
                _state.value = _state.value.copy(busy = false, notice = success)
                onSuccess()
                cardId?.let { load(it, workspaceIsShared) }
            }
                .onFailure { fail(it, busy = false) }
        }
    }

    private fun fail(error: Throwable, loading: Boolean? = null, busy: Boolean? = null) {
        val httpError = error as? HttpException
        val message = when {
            httpError?.code() == 401 ->
                "This Firebase account isn’t linked to a Lumira profile yet. Ask the administrator to provision it."
            httpError?.code() == 403 ->
                "You don’t have permission to make that change."
            httpError?.code() == 404 ->
                "This item is no longer available. Refresh the workspace."
            httpError?.code() == 409 ->
                "This item changed elsewhere. Refresh and try again."
            httpError?.code() == 413 ->
                "That file is too large to upload."
            httpError?.code() == 429 ->
                "Too many requests. Wait a moment and try again."
            httpError != null ->
                serverErrorMessage(httpError) ?: "Lumira couldn’t complete that request. Try again."
            error is UnknownHostException ->
                "Couldn’t find the Lumira server. Check the backend URL and your connection."
            error is SocketTimeoutException ->
                "The request took too long. Check your connection and try again."
            error is IOException ->
                "Couldn’t reach Lumira. Check your connection and try again."
            error.message?.length ?: 0 < 180 ->
                error.message ?: "Something went wrong. Please try again."
            else -> "Something went wrong. Please try again."
        }
        _state.value = _state.value.copy(notice = message, error = true,
            loading = loading ?: _state.value.loading, busy = busy ?: _state.value.busy)
    }

    private fun serverErrorMessage(error: HttpException): String? = runCatching {
        val body = error.response()?.errorBody()?.string().orEmpty()
        JSONObject(body).optJSONObject("error")?.optString("message")
            ?.takeIf { it.isNotBlank() && it != "null" && it.length < 180 }
    }.getOrNull()
}
