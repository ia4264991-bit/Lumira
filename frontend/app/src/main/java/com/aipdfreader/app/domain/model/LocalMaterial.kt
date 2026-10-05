package com.aipdfreader.app.domain.model

data class LocalMaterial(
    val id: Long,
    val displayName: String,
    val filePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val importedAtMillis: Long
)
