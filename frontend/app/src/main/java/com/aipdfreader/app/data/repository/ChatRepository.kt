package com.aipdfreader.app.data.repository

import com.aipdfreader.app.data.local.dao.ChatMessageDao
import com.aipdfreader.app.data.local.entity.ChatMessageEntity
import com.aipdfreader.app.domain.model.ChatMessage
import com.aipdfreader.app.domain.model.ChatRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val dao: ChatMessageDao,
    private val aiRepository: AiRepository
) {
    fun observeForPdf(pdfId: Long): Flow<List<ChatMessage>> =
        dao.observeForPdf(pdfId).map { list -> list.map { it.toDomain() } }

    fun observeForHighlight(pdfId: Long, highlightId: Long): Flow<List<ChatMessage>> =
        dao.observeForHighlight(pdfId, highlightId).map { list -> list.map { it.toDomain() } }

    /**
     * Persists the user's question, forwards it to our backend's AI Router,
     * then persists the answer (or an error message flagged with
     * [ChatMessage.isError]). No prompt is built here — [pdfId]/[pageIndex]/
     * [selectedText]/[question] are sent as-is; the backend decides how to
     * answer.
     */
    suspend fun sendMessage(
        pdfId: Long,
        highlightId: Long?,
        pageIndex: Int?,
        selectedText: String?,
        question: String,
        history: List<ChatMessage>
    ) {
        dao.insert(
            ChatMessageEntity(
                pdfId = pdfId,
                highlightId = highlightId,
                role = "user",
                content = question,
                createdAtMillis = System.currentTimeMillis()
            )
        )

        when (val result = aiRepository.ask(pdfId, pageIndex, selectedText, question, history)) {
            is AiResult.Success -> dao.insert(
                ChatMessageEntity(
                    pdfId = pdfId,
                    highlightId = highlightId,
                    role = "assistant",
                    content = result.answer,
                    createdAtMillis = System.currentTimeMillis()
                )
            )
            is AiResult.Failure -> dao.insert(
                ChatMessageEntity(
                    pdfId = pdfId,
                    highlightId = highlightId,
                    role = "assistant",
                    content = "⚠️ ${result.message}",
                    createdAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun clearForPdf(pdfId: Long) = dao.clearForPdf(pdfId)
}

private fun ChatMessageEntity.toDomain() = ChatMessage(
    id = id,
    pdfId = pdfId,
    highlightId = highlightId,
    role = when (role) {
        "user" -> ChatRole.USER
        "system" -> ChatRole.SYSTEM
        else -> ChatRole.ASSISTANT
    },
    content = content,
    createdAtMillis = createdAtMillis
)
