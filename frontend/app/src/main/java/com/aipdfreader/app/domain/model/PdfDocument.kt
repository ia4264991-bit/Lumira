package com.aipdfreader.app.domain.model

/** UI/domain-facing representation of an imported PDF. */
data class PdfDocument(
    val id: Long,
    val title: String,
    val filePath: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val importedAtMillis: Long,
    val lastOpenedAtMillis: Long,
    val lastReadPage: Int
)
