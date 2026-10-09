package com.aipdfreader.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import com.aipdfreader.app.data.remote.CardApi
import com.aipdfreader.app.data.remote.listAllCards
import com.aipdfreader.app.data.remote.DomainApi
import com.aipdfreader.app.data.remote.dto.CardDto
import com.aipdfreader.app.data.remote.dto.CreateCardRequest
import com.aipdfreader.app.data.remote.dto.DirectInvitationDto
import com.aipdfreader.app.data.remote.dto.MemberDto
import com.aipdfreader.app.data.remote.dto.RenameCardRequest
import com.aipdfreader.app.data.local.entity.LocalCardEntity
import com.aipdfreader.app.data.repository.LocalCardRepository
import com.aipdfreader.app.data.repository.RemoteWorkspaceCache
import com.aipdfreader.app.data.repository.AuthRepository
import com.aipdfreader.app.data.repository.LearnerProfileStore
import com.aipdfreader.app.data.sync.LocalCardSyncScheduler
import com.aipdfreader.app.util.BackendConfiguration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

data class CardHomeUiState(
    val personalCards: List<CardDto> = emptyList(),
    val sharedCards: List<CardDto> = emptyList(),
    val membersByCardId: Map<String, List<MemberDto>> = emptyMap(),
    val localCards: List<LocalCardEntity> = emptyList(),
    val invitations: List<DirectInvitationDto> = emptyList(),
    val invitationsLoading: Boolean = false,
    val invitationActionMembershipId: String? = null,
    val invitationMessage: String? = null,
    val invitationError: Boolean = false,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val offline: Boolean = false,
    val lastSyncedAtMillis: Long? = null,
    val message: String? = null,
    val error: Boolean = false,
    val learnerName: String = ""
)

@HiltViewModel
class CardHomeViewModel @Inject constructor(
    private val cardApi: CardApi,
    private val domainApi: DomainApi,
    private val localCardRepository: LocalCardRepository,
    private val remoteWorkspaceCache: RemoteWorkspaceCache,
    private val authRepository: AuthRepository,
    private val syncScheduler: LocalCardSyncScheduler,
    learnerProfileStore: LearnerProfileStore,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _state = MutableStateFlow(CardHomeUiState(learnerName = learnerProfileStore.current()?.name.orEmpty()))
    val state: StateFlow<CardHomeUiState> = _state.asStateFlow()
    private var hasLiveCards = false

    init {
        syncScheduler.enqueue()
        refresh()
        viewModelScope.launch {
            localCardRepository.observeAllCards().collect { cards ->
                _state.value = _state.value.copy(localCards = cards)
            }
        }
        authRepository.currentUserId?.let { uid ->
            viewModelScope.launch {
                remoteWorkspaceCache.observeForUser(uid).collect { snapshots ->
                    val cachedShared = snapshots.map { it.card }.filter { it.isShared }
                    val cachedMembers = snapshots.asSequence()
                        .filter { it.card.isShared }
                        .associate { snapshot ->
                            snapshot.card.id to snapshot.content.members.filter { it.status == "ACTIVE" }
                        }
                    val linkedCardIds = cachedShared.mapNotNull { it.memberCardId }.toSet()
                    val cachedPersonal = snapshots.map { it.card }
                        .filterNot { it.isShared || it.id in linkedCardIds }
                    val current = _state.value
                    _state.value = current.copy(
                        personalCards = if (hasLiveCards) current.personalCards else cachedPersonal,
                        sharedCards = if (hasLiveCards) current.sharedCards else cachedShared,
                        membersByCardId = if (hasLiveCards) current.membersByCardId else cachedMembers,
                        lastSyncedAtMillis = snapshots.maxOfOrNull { it.lastSyncedAtMillis }
                    )
                }
            }
        }
    }

    fun refresh() {
        refreshCards()
        refreshInvitations()
    }

    private fun refreshCards() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null, error = false)
            if (!BackendConfiguration.isConfigured) {
                _state.value = _state.value.copy(
                    loading = false,
                    message = "Your Cards and files stay on this phone. Online Cards and AI study tools will be available when Vision’s server is deployed."
                )
                return@launch
            }
            if (!hasValidatedInternet()) {
                _state.value = _state.value.copy(
                    loading = false,
                    offline = true,
                    message = "You’re offline. Showing Cards and Course Spaces from the last sync."
                )
                return@launch
            }
            val result = try {
                withTimeoutOrNull(8_000) {
                    supervisorScope {
                        val personalRequest = async { cardApi.listAllCards() }
                        val sharedRequest = async { cardApi.listAllCards("shared") }
                        personalRequest.await() to sharedRequest.await()
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    message = "Couldn’t load online Cards. Cards saved on this phone are still ready to study.",
                    error = false
                )
                return@launch
            }
            if (result == null) {
                _state.value = _state.value.copy(
                    loading = false,
                    message = "Online Cards are unavailable right now. Cards saved on this phone are still ready to study.",
                    error = false
                )
                return@launch
            }
            try {
                val (personal, shared) = result
                val cachedMemberships = _state.value.membersByCardId
                val memberResults = supervisorScope {
                    val memberRequestLimit = Semaphore(4)
                    shared.map { card ->
                        async {
                            card.id to memberRequestLimit.withPermit {
                                runCatching { domainApi.members(card.id) }
                                    .getOrNull()
                                    ?.filter { it.status == "ACTIVE" }
                            }
                        }
                    }.awaitAll()
                }
                hasLiveCards = true
                _state.value = _state.value.copy(
                    personalCards = personal,
                    sharedCards = shared,
                    membersByCardId = memberResults.mapNotNull { (cardId, members) ->
                        (members ?: cachedMemberships[cardId])?.let { cardId to it }
                    }.toMap(),
                    loading = false,
                    offline = false,
                    message = null,
                    error = false
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    message = "Couldn’t load online Cards. Cards saved on this phone are still ready to study.",
                    error = false
                )
            }
        }
    }

    private fun refreshInvitations() {
        viewModelScope.launch {
            if (!BackendConfiguration.isConfigured) {
                _state.value = _state.value.copy(invitationsLoading = false, invitationError = false)
                return@launch
            }
            if (!hasValidatedInternet()) {
                _state.value = _state.value.copy(invitationsLoading = false, invitationError = false)
                return@launch
            }
            _state.value = _state.value.copy(invitationsLoading = true, invitationMessage = null, invitationError = false)
            runCatching { domainApi.myInvitations() }
                .onSuccess { invitations ->
                    _state.value = _state.value.copy(
                        invitations = invitations,
                        invitationsLoading = false,
                        invitationMessage = null,
                        invitationError = false
                    )
                }
                .onFailure { failure ->
                    _state.value = _state.value.copy(
                        invitationsLoading = false,
                        invitationMessage = failure.toUserMessage(),
                        invitationError = true
                    )
                }
        }
    }
    fun createCard(name: String, color: String, asCourseSpace: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            if (!asCourseSpace) {
                saveLocalCard(
                    name,
                    color,
                    "Card saved on this phone. It stays ready offline and will sync when Vision’s server is available."
                )
                return@launch
            }
            if (!BackendConfiguration.isConfigured || !hasValidatedInternet()) {
                _state.value = _state.value.copy(
                    busy = false,
                    message = "Course Spaces need Vision’s server and an internet connection. You can create a private Card now and use it offline.",
                    error = true
                )
                return@launch
            }
            val onlineCreated = try {
                withTimeoutOrNull(8_000) {
                    val card = cardApi.createCard(CreateCardRequest(name.trim(), color))
                    cardApi.enableSharing(card.id)
                    cardApi.createShareLink(card.id)
                    true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                false
            }
            if (onlineCreated == true) {
                _state.value = _state.value.copy(busy = false)
                refresh()
            } else {
                _state.value = _state.value.copy(
                    busy = false,
                    message = "Vision’s server did not respond. You can create a private Card now and use it offline.",
                    error = true
                )
            }
        }
    }

    fun renameCard(card: CardDto, requestedName: String) {
        val name = requestedName.trim()
        if (name.isBlank()) {
            _state.value = _state.value.copy(message = "Enter a name for this Card.", error = true)
            return
        }
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null, error = false)
            runCatching {
                val uid = authRepository.currentUserId ?: error("Sign in to manage your Cards.")
                if (card.id.startsWith("local:")) {
                    val localId = card.id.removePrefix("local:")
                    val local = localCardRepository.getCard(localId) ?: error("This Card is no longer available.")
                    if (local.remoteCardId == null) {
                        check(localCardRepository.renameLocalCard(uid, localId, name)) { "This Card could not be renamed." }
                        syncScheduler.enqueue()
                    } else {
                        requireOnlineCardManagement()
                        cardApi.renameCard(local.remoteCardId, RenameCardRequest(name))
                        localCardRepository.renameLocalCard(uid, localId, name)
                    }
                } else {
                    requireOnlineCardManagement()
                    cardApi.renameCard(card.id, RenameCardRequest(name))
                    localCardRepository.renameLocalCardByRemoteId(uid, card.id, name)
                }
            }.onSuccess {
                _state.value = _state.value.copy(busy = false, message = "Card renamed.", error = false)
                refreshCards()
            }.onFailure { failure ->
                _state.value = _state.value.copy(busy = false, message = failure.toUserMessage(), error = true)
            }
        }
    }

    fun deletePrivateCard(card: CardDto) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null, error = false)
            runCatching {
                val uid = authRepository.currentUserId ?: error("Sign in to manage your Cards.")
                if (card.id.startsWith("local:")) {
                    val localId = card.id.removePrefix("local:")
                    val local = localCardRepository.getCard(localId) ?: error("This Card is no longer available.")
                    if (local.remoteCardId == null) {
                        check(localCardRepository.markCardDeleted(uid, localId)) { "This Card could not be deleted." }
                        if (!BackendConfiguration.isConfigured) {
                            localCardRepository.finalizeDeletedCard(uid, localId)
                        } else syncScheduler.enqueue()
                    } else {
                        requireOnlineCardManagement()
                        val response = cardApi.deletePrivateCard(local.remoteCardId)
                        if (!response.isSuccessful && response.code() != 404) throw HttpException(response)
                        remoteWorkspaceCache.removeDeletedCard(uid, local.remoteCardId)
                        check(localCardRepository.markCardDeleted(uid, localId)) { "This Card could not be deleted." }
                        localCardRepository.finalizeDeletedCard(uid, localId)
                    }
                } else {
                    requireOnlineCardManagement()
                    val response = cardApi.deletePrivateCard(card.id)
                    if (!response.isSuccessful && response.code() != 404) throw HttpException(response)
                    remoteWorkspaceCache.removeDeletedCard(uid, card.id)
                }
            }.onSuccess {
                _state.value = _state.value.copy(busy = false, message = "Card deleted.", error = false)
                refreshCards()
            }.onFailure { failure ->
                _state.value = _state.value.copy(busy = false, message = failure.toUserMessage(), error = true)
            }
        }
    }

    fun leaveCourseSpace(card: CardDto) = performCourseSpaceAction("You left the Course Space.") {
        domainApi.leaveCourseSpace(card.id)
    }

    fun dissolveCourseSpace(card: CardDto) = performCourseSpaceAction("Course Space dissolved.") {
        domainApi.dissolveCourseSpace(card.id)
    }

    private fun performCourseSpaceAction(success: String, action: suspend () -> Any) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null, error = false)
            runCatching {
                requireOnlineCardManagement()
                action()
            }.onSuccess {
                _state.value = _state.value.copy(busy = false, message = success, error = false)
                syncScheduler.enqueue()
                refreshCards()
            }.onFailure { failure ->
                _state.value = _state.value.copy(busy = false, message = failure.toUserMessage(), error = true)
            }
        }
    }

    private fun requireOnlineCardManagement() {
        if (!BackendConfiguration.isConfigured) error("Card management will be available when Vision’s server is deployed.")
        if (!hasValidatedInternet()) error("Connect to the internet to manage this Card.")
    }

    private suspend fun saveLocalCard(name: String, color: String, successMessage: String) {
        try {
            localCardRepository.createCard(name, color)
            _state.value = _state.value.copy(busy = false, message = successMessage, error = false)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            _state.value = _state.value.copy(busy = false, message = failure.message ?: "Couldn’t save this Card on your phone.", error = true)
        }
    }

    private fun hasValidatedInternet(): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun joinCourseSpace(linkOrToken: String) {
        val token = linkOrToken.trim().trimEnd('/').substringAfterLast('/')
        if (token.isBlank()) {
            _state.value = _state.value.copy(message = "Enter a Course Space invite link or token.", error = true)
            return
        }
        if (!BackendConfiguration.isConfigured) {
            _state.value = _state.value.copy(
                message = "Joining a Course Space will be available when Vision’s server is deployed.",
                error = true
            )
            return
        }
        if (!hasValidatedInternet()) {
            _state.value = _state.value.copy(
                message = "You’re offline. Connect to the internet to join a Course Space.",
                error = true
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            runCatching {
                val response = cardApi.joinByLink(token)
                if (!response.isSuccessful) throw HttpException(response)
                response.code()
            }
                .onSuccess { code ->
                    _state.value = _state.value.copy(
                        busy = false,
                        message = if (code == 202) "Request sent. An Admin or Owner must approve it." else "You joined the Course Space.",
                        error = false
                    )
                    syncScheduler.enqueue()
                    refresh()
                }
                .onFailure { failure ->
                    _state.value = _state.value.copy(busy = false, message = failure.toUserMessage(), error = true)
                }
        }
    }

    fun acceptInvitation(membershipId: String) = respondToInvitation(membershipId, accept = true)

    fun declineInvitation(membershipId: String) = respondToInvitation(membershipId, accept = false)

    private fun respondToInvitation(membershipId: String, accept: Boolean) {
        if (_state.value.invitationActionMembershipId != null) return
        if (!hasValidatedInternet()) {
            _state.value = _state.value.copy(
                invitationMessage = "You’re offline. Connect to the internet to update this invitation.",
                invitationError = true
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(
                invitationActionMembershipId = membershipId,
                invitationMessage = null,
                invitationError = false
            )
            runCatching {
                if (accept) domainApi.acceptInvitation(membershipId)
                else domainApi.declineInvitation(membershipId)
            }.onSuccess {
                _state.value = _state.value.copy(
                    invitations = _state.value.invitations.filterNot { it.membershipId == membershipId },
                    invitationActionMembershipId = null,
                    invitationMessage = if (accept) "Invitation accepted. Course Space added to your list." else "Invitation declined.",
                    invitationError = false
                )
                if (accept) refreshCards()
                if (accept) syncScheduler.enqueue()
            }.onFailure { failure ->
                _state.value = _state.value.copy(
                    invitationActionMembershipId = null,
                    invitationMessage = failure.toUserMessage(),
                    invitationError = true
                )
            }
        }
    }
    private fun Throwable.toUserMessage(): String = when (this) {
        is HttpException -> when (code()) {
            401 -> "You’re signed in with Firebase, but this account isn’t linked to a Vision profile yet. Ask the project administrator to provision it."
            403 -> "You don’t have permission to do that."
            404 -> "That Card or Course Space isn’t available."
            409 -> "This Card is still a Course Space or is linked to Course Space history. Dissolve it or preserve the membership history before deleting it."
            410 -> "This invite link has expired or was reset. Ask for a new link."
            in 500..599 -> "Vision is having trouble right now. Please try again shortly."
            else -> "We couldn’t complete that request (HTTP ${code()})."
        }
        else -> "Couldn’t reach Vision. Check the connection and try again."
    }
}
