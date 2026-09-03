package com.aipdfreader.app.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * DTOs for OUR backend's `/v1/ai/ask` endpoint (the "AI Router" component).
 *
 * This contract intentionally carries no AI-provider concepts: no model
 * name, no provider id, no system prompt, no API key. The Android app only
 * describes *what the user wants* — the document/page/passage they're
 * looking at and the question they're asking — and the backend's AI Router,
 * Document Intelligence, and prompt-engineering layers decide how to answer
 * it (which provider, which model, how to build context, whether OCR or
 * additional document retrieval is needed, etc).
 */
@Serializable
data class AskRequest(
    /** Backend-known identifier for the document, once it has been synced/
     * uploaded there. Until a document-sync endpoint exists, the client's
     * local document id is sent as a best-effort reference — see README. */
    val documentId: String? = null,
    val pageIndex: Int? = null,
    /** The exact passage the user selected, if any. This is user-provided
     * content, not a constructed prompt. */
    val selectedText: String? = null,
    val question: String,
    val conversationHistory: List<ConversationTurnDto> = emptyList()
)

@Serializable
data class ConversationTurnDto(
    val role: String, // "user" | "assistant"
    val content: String
)

@Serializable
data class AskResponse(
    val answer: String? = null,
    val conversationId: String? = null,
    val error: String? = null
)
