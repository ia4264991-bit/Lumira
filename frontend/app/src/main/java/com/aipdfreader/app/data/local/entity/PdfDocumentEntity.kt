package com.aipdfreader.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A PDF file that has been imported into the app's own storage.
 * We keep our own copy on disk (see FileUtils) so the file remains
 * accessible even if the original content:// Uri permission is revoked.
 */
@Entity(tableName = "pdf_documents")
data class PdfDocumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val importedAtMillis: Long,
    val lastOpenedAtMillis: Long,
    val lastReadPage: Int = 0
)
