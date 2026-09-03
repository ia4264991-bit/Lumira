package com.aipdfreader.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single message in an AI chat conversation about a PDF (or a specific
 * highlight within it). Conversations are grouped by [pdfId] and, optionally,
 * scoped to one [highlightId] so "ask AI about this selection" threads stay
 * separate from general document chat.
 */
@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = PdfDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["pdfId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pdfId"), Index("highlightId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pdfId: Long,
    val highlightId: Long? = null,
    val role: String, // "user" | "assistant" | "system"
    val content: String,
    val createdAtMillis: Long
)
