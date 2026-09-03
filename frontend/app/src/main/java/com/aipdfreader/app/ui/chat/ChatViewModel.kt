package com.aipdfreader.app.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aipdfreader.app.data.repository.ChatRepository
import com.aipdfreader.app.data.repository.HighlightRepository
import com.aipdfreader.app.domain.model.ChatMessage
import com.aipdfreader.app.domain.model.Highlight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val highlight: Highlight? = null,
    val draft: String = "",
    val isSending: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val chatRepository: ChatRepository,
    private val highlightRepository: HighlightRepository
) : ViewModel() {

    private val pdfId: Long = checkNotNull(savedStateHandle["pdfId"])
    private val highlightId: Long? = (savedStateHandle.get<Long>("highlightId"))?.takeIf { it >= 0 }

    private val draft = MutableStateFlow("")
    private val isSending = MutableStateFlow(false)
    private val highlight = MutableStateFlow<Highlight?>(null)

    private val messagesFlow = if (highlightId != null) {
        chatRepository.observeForHighlight(pdfId, highlightId)
    } else {
        chatRepository.observeForPdf(pdfId)
    }

    val uiState: StateFlow<ChatUiState> = combine(
        messagesFlow, highlight, draft, isSending
    ) { messages, hl, draftText, sending ->
        ChatUiState(messages = messages, highlight = hl, draft = draftText, isSending = sending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ChatUiState())

    init {
        highlightId?.let { id ->
            viewModelScope.launch {
                highlight.value = highlightRepository.getById(id)
            }
        }
    }

    fun onDraftChanged(text: String) {
        draft.value = text
    }

    fun sendMessage() {
        val question = draft.value.trim()
        if (question.isBlank() || isSending.value) return

        draft.value = ""
        isSending.value = true

        viewModelScope.launch {
            chatRepository.sendMessage(
                pdfId = pdfId,
                highlightId = highlightId,
                pageIndex = highlight.value?.pageIndex,
                selectedText = highlight.value?.selectedText,
                question = question,
                history = uiState.value.messages
            )
            isSending.value = false
        }
    }

    /** Lets the user ask a quick follow-up without typing, e.g. "Summarize this". */
    fun sendQuickPrompt(prompt: String) {
        draft.value = prompt
        sendMessage()
    }
}
