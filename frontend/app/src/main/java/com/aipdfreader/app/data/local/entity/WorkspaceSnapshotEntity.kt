package com.aipdfreader.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import kotlinx.serialization.Serializable
import com.aipdfreader.app.data.remote.dto.*

/** Caller-authorized read snapshot. It is a cache, never an authorization source. */
@Entity(
    tableName = "workspace_snapshots",
    primaryKeys = ["ownerUid", "cardId"],
    indices = [Index("ownerUid"), Index("isShared")]
)
data class WorkspaceSnapshotEntity(
    val ownerUid: String,
    val cardId: String,
    val cardOwnerId: String,
    val name: String,
    val color: String,
    val isShared: Boolean,
    val role: String,
    val memberCardId: String?,
    val shareApproval: Boolean?,
    val lastSyncedAtMillis: Long,
    val contentJson: String
)

/** Typed API read models grouped as one atomic local snapshot. */
@Serializable
data class WorkspaceSnapshotContent(
    val resources: List<ResourceDto> = emptyList(),
    val notes: List<NoteDto> = emptyList(),
    val studySets: List<StudySetDto> = emptyList(),
    val quizzes: List<QuizDto> = emptyList(),
    val flashcardSets: List<FlashcardSetDto> = emptyList(),
    val events: List<EventDto> = emptyList(),
    val members: List<MemberDto> = emptyList(),
    val joinRequests: List<JoinRequestItemDto> = emptyList(),
    val messages: List<CachedSarahMessage> = emptyList(),
    val conversationId: String = ""
)

@Serializable
data class CachedSarahMessage(val role: String, val content: String)

@Entity(
    tableName = "cached_resource_files",
    primaryKeys = ["ownerUid", "cardId", "resourceId"],
    indices = [Index("ownerUid"), Index("cardId")]
)
data class CachedResourceFileEntity(
    val ownerUid: String,
    val cardId: String,
    val resourceId: String,
    val displayName: String,
    val filePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val downloadedAtMillis: Long
)
