package com.aipdfreader.app.ui.workspace

import android.content.Context
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
                val r = async { runCatching { domainApi.resources(cardId) }.getOrDefault(emptyList()) }
                val n = async { runCatching { domainApi.notes(cardId) }.getOrDefault(emptyList()) }
                val s = async { runCatching { domainApi.studySets(cardId) }.getOrDefault(emptyList()) }
                val q = async { runCatching { domainApi.quizzes(cardId) }.getOrDefault(emptyList()) }
                val f = async { runCatching { domainApi.flashcardSets(cardId) }.getOrDefault(emptyList()) }
                val e = async { runCatching { domainApi.events(cardId) }.getOrDefault(emptyList()) }
                val m = async { if (isShared) runCatching { domainApi.members(cardId) }.getOrDefault(emptyList()) else emptyList() }
                val j = async { if (isShared) runCatching { domainApi.joinRequests(cardId) }.getOrDefault(emptyList()) else emptyList() }
                listOf(r.await(), n.await(), s.await(), q.await(), f.await(), e.await(), m.await(), j.await())
            }.onSuccess { results ->
                @Suppress("UNCHECKED_CAST")
                _state.value = _state.value.copy(
                    resources = results[0] as List<ResourceDto>, notes = results[1] as List<NoteDto>,
                    studySets = results[2] as List<StudySetDto>, quizzes = results[3] as List<QuizDto>,
                    flashcardSets = results[4] as List<FlashcardSetDto>, events = results[5] as List<EventDto>,
                    members = results[6] as List<MemberDto>, joinRequests = results[7] as List<JoinRequestItemDto>,
                    loading = false, error = false, notice = null
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
    fun deleteFlashcardSet(cardId: String, id: String) = perform("Flashcard Set deleted", cardId) { domainApi.deleteFlashcardSet(id) }
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

    fun uploadPdf(cardId: String, uri: Uri) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, notice = null)
            runCatching {
                val copy = FileUtils.copyPdfToInternalStorage(context, uri) ?: error("We couldn’t read that PDF.")
                try {
                    val file = File(copy.path)
                    val part = MultipartBody.Part.createFormData("file", copy.displayName,
                        file.asRequestBody("application/pdf".toMediaType()))
                    domainApi.uploadResource(cardId, part, copy.displayName.removeSuffix(".pdf"))
                } finally { FileUtils.deleteFile(copy.path) }
            }.onSuccess {
                _state.value = _state.value.copy(busy = false, notice = "PDF uploaded and processed")
                load(cardId, workspaceIsShared)
            }.onFailure { fail(it, busy = false) }
        }
    }

    fun openPdf(resource: ResourceDto, onOpened: (Long) -> Unit) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, notice = null)
            runCatching {
                val response = domainApi.downloadResource(resource.id)
                val file = File(context.cacheDir, "${UUID.randomUUID()}.pdf")
                response.byteStream().use { input -> file.outputStream().use(input::copyTo) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                pdfRepository.importPdf(uri) ?: error("The downloaded PDF couldn’t be opened.")
            }.onSuccess { id -> _state.value = _state.value.copy(busy = false); onOpened(id) }
                .onFailure { fail(it, busy = false) }
        }
    }

    fun deleteNote(noteId: String, cardId: String) {
        viewModelScope.launch {
            runCatching { domainApi.deleteNote(noteId) }.onSuccess { _state.value = _state.value.copy(notice = "Note deleted"); load(cardId, workspaceIsShared) }
                .onFailure { fail(it) }
        }
    }

    fun reviewFlashcard(setId: String, card: FlashcardDto, gotIt: Boolean) {
        viewModelScope.launch {
            runCatching { domainApi.reviewFlashcard(setId, FlashcardProgressWriteDto(card.id, if (gotIt) "GOT_IT" else "AGAIN")) }
                .onSuccess { _state.value = _state.value.copy(notice = if (gotIt) "Marked Got it" else "Added to review") }
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

    private fun <T> perform(success: String, cardId: String? = null, block: suspend () -> T) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, notice = null)
            runCatching { block() }.onSuccess {
                _state.value = _state.value.copy(busy = false, notice = success)
                cardId?.let { load(it, workspaceIsShared) }
            }
                .onFailure { fail(it, busy = false) }
        }
    }

    private fun fail(error: Throwable, loading: Boolean? = null, busy: Boolean? = null) {
        val message = if (error is HttpException && error.code() == 401)
            "This Firebase account isn’t linked to a Vision profile yet. Ask the administrator to provision it."
        else if (error.message?.contains("failed to connect", true) == true)
            "Couldn’t connect to Lumira. Check the backend URL and network."
        else error.message?.takeIf { it.length < 180 } ?: "Something went wrong. Please try again."
        _state.value = _state.value.copy(notice = message, error = true,
            loading = loading ?: _state.value.loading, busy = busy ?: _state.value.busy)
    }
}
