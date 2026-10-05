package com.aipdfreader.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "local_materials")
data class LocalMaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val displayName: String,
    val filePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val importedAtMillis: Long
)
