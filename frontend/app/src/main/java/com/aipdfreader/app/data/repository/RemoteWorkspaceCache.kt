package com.aipdfreader.app.data.repository

import android.content.Context
import com.aipdfreader.app.data.local.AppDatabase
import com.aipdfreader.app.data.local.entity.CachedResourceFileEntity
import com.aipdfreader.app.data.local.entity.CachedSarahMessage
import com.aipdfreader.app.data.local.entity.WorkspaceSnapshotContent
import com.aipdfreader.app.data.local.entity.WorkspaceSnapshotEntity
import com.aipdfreader.app.data.remote.dto.CardDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class CachedWorkspaceSnapshot(
    val card: CardDto,
    val content: WorkspaceSnapshotContent,
    val lastSyncedAtMillis: Long
)

/** Room-backed read cache for server Cards/Course Spaces and explicit file downloads. */
@Singleton
class RemoteWorkspaceCache @Inject constructor(
    private val db: AppDatabase,
    private val json: Json,
    @ApplicationContext private val context: Context
) {
    private val dao get() = db.workspaceSnapshotDao()

    fun observeForUser(ownerUid: String): Flow<List<CachedWorkspaceSnapshot>> =
        dao.observeForUser(ownerUid).map { rows -> rows.mapNotNull(::decode) }

    suspend fun snapshot(ownerUid: String, cardId: String): CachedWorkspaceSnapshot? =
        dao.get(ownerUid, cardId)?.let(::decode)

    suspend fun save(card: CardDto, content: WorkspaceSnapshotContent, ownerUid: String) {
        val previous = dao.get(ownerUid, card.id)?.let(::decode)
        val retainedContent = content.copy(
            messages = content.messages.ifEmpty { previous?.content?.messages.orEmpty() },
            conversationId = content.conversationId.ifBlank { previous?.content?.conversationId.orEmpty() }
        )
        dao.upsert(WorkspaceSnapshotEntity(
            ownerUid = ownerUid,
            cardId = card.id,
            cardOwnerId = card.ownerId,
            name = card.name,
            color = card.color,
            isShared = card.isShared,
            role = card.role ?: "OWNER",
            memberCardId = card.memberCardId ?: card.id,
            shareApproval = card.shareApproval,
            lastSyncedAtMillis = System.currentTimeMillis(),
            contentJson = json.encodeToString(retainedContent)
        ))
    }

    suspend fun saveMessages(
        ownerUid: String,
        cardId: String,
        conversationId: String,
        messages: List<CachedSarahMessage>
    ) {
        val existing = dao.get(ownerUid, cardId)?.let(::decode) ?: return
        dao.upsert(WorkspaceSnapshotEntity(
            ownerUid = ownerUid,
            cardId = existing.card.id,
            cardOwnerId = existing.card.ownerId,
            name = existing.card.name,
            color = existing.card.color,
            isShared = existing.card.isShared,
            role = existing.card.role ?: "OWNER",
            memberCardId = existing.card.memberCardId ?: existing.card.id,
            shareApproval = existing.card.shareApproval,
            lastSyncedAtMillis = existing.lastSyncedAtMillis,
            contentJson = json.encodeToString(existing.content.copy(
                messages = messages,
                conversationId = conversationId
            ))
        ))
    }

    suspend fun reconcileSharedMembership(ownerUid: String, activeSharedCardIds: Set<String>) {
        val revoked = dao.getForUser(ownerUid).filter { it.isShared && it.cardId !in activeSharedCardIds }
        revoked.forEach { row ->
            dao.getFilesForCard(ownerUid, row.cardId).forEach { File(it.filePath).delete() }
            dao.deleteFilesForCard(ownerUid, row.cardId)
            dao.delete(ownerUid, row.cardId)
        }
    }

    suspend fun removeDeletedCard(ownerUid: String, cardId: String) {
        dao.getFilesForCard(ownerUid, cardId).forEach { File(it.filePath).delete() }
        dao.deleteFilesForCard(ownerUid, cardId)
        dao.delete(ownerUid, cardId)
    }

    suspend fun cachedFile(ownerUid: String, cardId: String, resourceId: String): CachedResourceFileEntity? {
        val cached = dao.getFile(ownerUid, cardId, resourceId) ?: return null
        if (File(cached.filePath).isFile) return cached
        dao.deleteFile(ownerUid, cardId, resourceId)
        return null
    }

    suspend fun removeCachedFile(ownerUid: String, cardId: String, resourceId: String) {
        dao.getFile(ownerUid, cardId, resourceId)?.let { File(it.filePath).delete() }
        dao.deleteFile(ownerUid, cardId, resourceId)
    }

    suspend fun cacheDownloadedFile(
        ownerUid: String,
        cardId: String,
        resourceId: String,
        displayName: String,
        mimeType: String,
        sourcePath: String,
        sizeBytes: Long
    ): CachedResourceFileEntity? = withContext(Dispatchers.IO) {
        val source = File(sourcePath)
        if (!source.isFile) return@withContext null
        val old = dao.getFile(ownerUid, cardId, resourceId)
        val ownerFolder = UUID.nameUUIDFromBytes(ownerUid.toByteArray()).toString()
        val directory = File(context.filesDir, "remote-resources/$ownerFolder/$cardId").apply { mkdirs() }
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "resource" }
        val target = File(directory, "${UUID.randomUUID()}_$safeName")
        runCatching {
            source.copyTo(target, overwrite = false)
            val entry = CachedResourceFileEntity(
                ownerUid = ownerUid,
                cardId = cardId,
                resourceId = resourceId,
                displayName = displayName,
                filePath = target.absolutePath,
                mimeType = mimeType,
                sizeBytes = sizeBytes.takeIf { it > 0 } ?: target.length(),
                downloadedAtMillis = System.currentTimeMillis()
            )
            dao.upsertFile(entry)
            old?.takeIf { it.filePath != entry.filePath }?.let { File(it.filePath).delete() }
            entry
        }.getOrElse {
            target.delete()
            null
        }
    }

    private fun decode(row: WorkspaceSnapshotEntity): CachedWorkspaceSnapshot? = runCatching {
        val card = CardDto(
            id = row.cardId,
            ownerId = row.cardOwnerId,
            name = row.name,
            color = row.color,
            isShared = row.isShared,
            role = row.role,
            memberCardId = row.memberCardId,
            shareApproval = row.shareApproval
        )
        CachedWorkspaceSnapshot(card, json.decodeFromString(row.contentJson), row.lastSyncedAtMillis)
    }.getOrNull()
}
