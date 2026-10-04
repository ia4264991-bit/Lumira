package com.aipdfreader.app.ui.sarah

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aipdfreader.app.data.remote.DomainApi
import com.aipdfreader.app.data.remote.dto.SarahHistoryItem
import com.aipdfreader.app.data.remote.dto.SarahResourceAskDto
import com.aipdfreader.app.data.repository.HighlightRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.util.UUID
import javax.inject.Inject

data class ContextualSarahState(
    val messages: List<Pair<String, String>> = emptyList(),
    val selectedText: String? = null,
    val pageIndex: Int? = null,
    val draft: String = "",
    val busy: Boolean = false,
    val usage: String? = null,
    val error: String? = null
)

@HiltViewModel
class ContextualSarahViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val domainApi: DomainApi,
    private val highlights: HighlightRepository
) : ViewModel() {
    private val resourceId: String = checkNotNull(savedState["resourceId"])
    private val cardId: String = checkNotNull(savedState["cardId"])
    private val highlightId: Long? = savedState.get<Long>("highlightId")?.takeIf { it >= 0 }
    private val conversationId: String = savedState.get<String>("conversationId")
        ?: UUID.randomUUID().toString().also { savedState["conversationId"] = it }
    private val _state = MutableStateFlow(ContextualSarahState())
    val state = _state.asStateFlow()

    init {
        highlightId?.let { id -> viewModelScope.launch {
            highlights.getById(id)?.let { _state.value = _state.value.copy(selectedText = it.selectedText, pageIndex = it.pageIndex) }
        } }
    }

    fun setDraft(value: String) { _state.value = _state.value.copy(draft = value, error = null) }

    fun send() {
        val question = _state.value.draft.trim()
        if (question.isBlank() || _state.value.busy) return
        val prior = _state.value.messages
        _state.value = _state.value.copy(draft = "", busy = true, error = null,
            messages = prior + ("user" to question))
        viewModelScope.launch {
            runCatching {
                domainApi.askSarahAboutResource(resourceId, SarahResourceAskDto(
                    conversationId = conversationId,
                    cardId = cardId,
                    selectedText = _state.value.selectedText,
                    question = question,
                    conversationHistory = prior.map { SarahHistoryItem(it.first, it.second) },
                    pageIndex = _state.value.pageIndex
                ))
            }.onSuccess { response ->
                _state.value = _state.value.copy(busy = false,
                    messages = _state.value.messages + ("assistant" to response.answer),
                    usage = "${response.usageUsed} / ${response.usageLimit} this month")
            }.onFailure { error ->
                _state.value = _state.value.copy(busy = false,
                    error = when (error) {
                        is HttpException -> when (error.code()) {
                            401 -> "This Firebase account isn’t linked to a Vision profile yet. Ask the administrator to provision it."
                            429 -> "You’ve reached this month’s Sarah limit. Try again next month."
                            503 -> "Sarah is temporarily unavailable. Please try again soon."
                            else -> "Sarah couldn’t answer that just now. Try again."
                        }
                        else -> "Couldn’t reach Sarah. Check your connection and try again."
                    })
            }
        }
    }
}
