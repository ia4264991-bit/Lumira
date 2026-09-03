package com.aipdfreader.app.domain.model

enum class ChatRole { USER, ASSISTANT, SYSTEM }

/** UI/domain-facing representation of a chat turn. */
data class ChatMessage(
    val id: Long = 0,
    val pdfId: Long,
    val highlightId: Long? = null,
    val role: ChatRole,
    val content: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val isPending: Boolean = false,
    val isError: Boolean = false
)
