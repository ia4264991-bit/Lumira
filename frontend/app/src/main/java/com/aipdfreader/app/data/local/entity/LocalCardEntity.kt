package com.aipdfreader.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A private, device-only Card that remains available without a network connection. */
@Entity(tableName = "local_cards")
data class LocalCardEntity(
    @PrimaryKey val id: String,
    val ownerUid: String,
    val name: String,
    val color: String,
    val createdAtMillis: Long
)

@Entity(tableName = "local_card_materials", indices = [Index("cardId")])
data class LocalCardMaterialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cardId: String,
    val displayName: String,
    val filePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val addedAtMillis: Long
)

@Entity(tableName = "local_card_notes", indices = [Index("cardId")])
data class LocalCardNoteEntity(
    @PrimaryKey val id: String,
    val cardId: String,
    val title: String,
    val content: String,
    val updatedAtMillis: Long
)
