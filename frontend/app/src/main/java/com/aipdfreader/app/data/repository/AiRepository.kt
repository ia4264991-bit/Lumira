package com.aipdfreader.app.data.repository

import com.aipdfreader.app.data.remote.AiRouterApi
import com.aipdfreader.app.data.remote.dto.AskRequest
import com.aipdfreader.app.data.remote.dto.ConversationTurnDto
import com.aipdfreader.app.domain.model.ChatMessage
import com.aipdfreader.app.domain.model.ChatRole
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

sealed class AiResult {
    data class Success(val answer: String) : AiResult()
    data class Failure(val message: String) : AiResult()
}

/**
 * Client for OUR backend's AI Router.
 *
 * Deliberately thin: this class does not choose a provider, does not choose
 * a model, does not build a system prompt, and does not hold any API key.
 * It packages what the user is looking at and what they asked, sends it to
 * the backend, and relays the answer (or a user-facing error) back. All
 * "intelligence" about how to answer — provider routing, prompt engineering,
 * OCR fallback, document context retrieval — happens server-side.
 */
@Singleton
class AiRepository @Inject constructor(
    private val aiRouterApi: AiRouterApi
) {

    suspend fun ask(
        documentId: Long,
        pageIndex: Int?,
        selectedText: String?,
        question: String,
        history: List<ChatMessage>
    ): AiResult {
        val request = AskRequest(
            documentId = documentId.toString(),
            pageIndex = pageIndex,
            selectedText = selectedText,
            question = question,
            conversationHistory = history.map {
                ConversationTurnDto(
                    role = if (it.role == ChatRole.USER) "user" else "assistant",
                    content = it.content
                )
            }
        )

        return try {
            val response = aiRouterApi.ask(request)
            val answer = response.answer?.trim()
            if (!answer.isNullOrBlank()) {
                AiResult.Success(answer)
            } else {
                AiResult.Failure(response.error ?: "The server returned an empty response.")
            }
        } catch (e: HttpException) {
            AiResult.Failure(httpErrorMessage(e))
        } catch (e: Exception) {
            AiResult.Failure(e.message ?: "Network error while contacting the server.")
        }
    }

    private fun httpErrorMessage(e: HttpException): String = when (e.code()) {
        401 -> "Your session has expired. Please sign in again."
        429 -> "Too many requests right now. Please wait a moment and try again."
        in 500..599 -> "The AI service is temporarily unavailable. Please try again shortly."
        else -> "Something went wrong (code ${e.code()})."
    }
}
