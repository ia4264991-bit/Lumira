package com.aipdfreader.app.domain.model

/** UI/domain-facing representation of a saved highlight/selection. */
data class Highlight(
    val id: Long,
    val pdfId: Long,
    val pageIndex: Int,
    val selectedText: String,
    val note: String?,
    val colorArgb: Int,
    val createdAtMillis: Long
)
