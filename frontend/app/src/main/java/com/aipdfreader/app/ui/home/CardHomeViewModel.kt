package com.aipdfreader.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aipdfreader.app.data.remote.CardApi
import com.aipdfreader.app.data.remote.DomainApi
import com.aipdfreader.app.data.remote.dto.CardDto
import com.aipdfreader.app.data.remote.dto.CreateCardRequest
import com.aipdfreader.app.data.remote.dto.DirectInvitationDto
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject
import dagger.hilt.android.lifecycle.HiltViewModel

data class CardHomeUiState(
    val personalCards: List<CardDto> = emptyList(),
    val sharedCards: List<CardDto> = emptyList(),
    val invitations: List<DirectInvitationDto> = emptyList(),
    val invitationsLoading: Boolean = false,
    val invitationActionMembershipId: String? = null,
    val invitationMessage: String? = null,
    val invitationError: Boolean = false,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val error: Boolean = false
)

@HiltViewModel
class CardHomeViewModel @Inject constructor(private val cardApi: CardApi, private val domainApi: DomainApi) : ViewModel() {
    private val _state = MutableStateFlow(CardHomeUiState())
    val state: StateFlow<CardHomeUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        refreshCards()
        refreshInvitations()
    }

    private fun refreshCards() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, message = null, error = false)
            runCatching {
                val personal = async { cardApi.listCards() }
                val shared = async { cardApi.listCards("shared") }
                personal.await() to shared.await()
            }.onSuccess { (personal, shared) ->
                _state.value = _state.value.copy(
                    personalCards = personal,
                    sharedCards = shared,
                    loading = false,
                    message = null,
                    error = false
                )
            }.onFailure { failure ->
                _state.value = _state.value.copy(
                    loading = false,
                    message = failure.toUserMessage(),
                    error = true
                )
            }
        }
    }

    private fun refreshInvitations() {
        viewModelScope.launch {
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
            runCatching {
                val card = cardApi.createCard(CreateCardRequest(name.trim(), color))
                if (asCourseSpace) {
                    cardApi.enableSharing(card.id)
                    cardApi.createShareLink(card.id)
                }
            }.onSuccess {
                _state.value = _state.value.copy(busy = false)
                refresh()
            }.onFailure { failure ->
                _state.value = _state.value.copy(busy = false, message = failure.toUserMessage(), error = true)
            }
        }
    }

    fun joinCourseSpace(linkOrToken: String) {
        val token = linkOrToken.trim().trimEnd('/').substringAfterLast('/')
        if (token.isBlank()) {
            _state.value = _state.value.copy(message = "Enter a Course Space invite link or token.", error = true)
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
            409 -> "That action conflicts with the current Course Space state. Refresh and try again."
            410 -> "This invite link has expired or was reset. Ask for a new link."
            in 500..599 -> "Lumira is having trouble right now. Please try again shortly."
            else -> "We couldn’t complete that request (HTTP ${code()})."
        }
        else -> "Couldn’t reach Lumira. Check the connection and try again."
    }
}
