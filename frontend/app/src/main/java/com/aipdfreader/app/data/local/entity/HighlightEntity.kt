package com.aipdfreader.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user-selected passage of text from a PDF, optionally with a personal note
 * attached. Highlights are the bridge between "selected text" and "Ask AI".
 */
@Entity(
    tableName = "highlights",
    foreignKeys = [
        ForeignKey(
            entity = PdfDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["pdfId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pdfId")]
)
data class HighlightEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pdfId: Long,
    val pageIndex: Int,
    val selectedText: String,
    val note: String? = null,
    val colorArgb: Int,
    val createdAtMillis: Long
)
