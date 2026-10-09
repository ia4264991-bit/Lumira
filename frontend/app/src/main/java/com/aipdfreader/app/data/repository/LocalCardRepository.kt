package com.aipdfreader.app.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.aipdfreader.app.data.local.AppDatabase
import com.aipdfreader.app.data.local.dao.LocalCardDao
import com.aipdfreader.app.data.local.entity.LocalCardEntity
import com.aipdfreader.app.data.local.entity.LocalCardMaterialEntity
import com.aipdfreader.app.data.local.entity.LocalCardNoteEntity
import com.aipdfreader.app.data.sync.LocalCardSyncScheduler
import com.aipdfreader.app.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCardRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    private val dao: LocalCardDao,
    private val authRepository: AuthRepository,
    private val syncScheduler: LocalCardSyncScheduler
) {
    fun observeCards(): Flow<List<LocalCardEntity>> =
        authRepository.currentUserId?.let(dao::observeCards) ?: emptyFlow()

    fun observeAllCards(): Flow<List<LocalCardEntity>> =
        authRepository.currentUserId?.let(dao::observeAllCards) ?: emptyFlow()

    suspend fun createCard(name: String, color: String): LocalCardEntity {
        val ownerUid = authRepository.currentUserId ?: error("Sign in to save a Card on this phone.")
        val card = LocalCardEntity(
            id = UUID.randomUUID().toString(),
            ownerUid = ownerUid,
            name = name.trim(),
            color = color,
            createdAtMillis = System.currentTimeMillis()
        )
        dao.insertCard(card)
        syncScheduler.enqueue()
        return card
    }

    suspend fun cardsForSync(ownerUid: String) = dao.getCardsForSync(ownerUid)
    suspend fun markCardSynced(cardId: String, ownerUid: String, remoteCardId: String) =
        dao.setRemoteCardId(cardId, ownerUid, remoteCardId)
    suspend fun recordRemoteCardIdForDeletion(cardId: String, ownerUid: String, remoteCardId: String) =
        dao.recordRemoteCardIdForDeletion(cardId, ownerUid, remoteCardId)
    suspend fun cardForSync(ownerUid: String, cardId: String) = dao.getCardForSync(ownerUid, cardId)
    suspend fun renameLocalCard(ownerUid: String, cardId: String, name: String): Boolean =
        dao.updateCardName(cardId, ownerUid, name.trim()) > 0
    suspend fun renameLocalCardByRemoteId(ownerUid: String, remoteCardId: String, name: String): Boolean =
        dao.updateCardNameByRemoteId(ownerUid, remoteCardId, name.trim()) > 0
    suspend fun markCardDeleted(ownerUid: String, cardId: String): Boolean =
        dao.markCardDeleted(cardId, ownerUid) > 0
    suspend fun isCardDeleted(ownerUid: String, cardId: String): Boolean =
        dao.getCardForSync(ownerUid, cardId)?.isDeleted == true

    suspend fun finalizeDeletedCard(ownerUid: String, cardId: String): Boolean = withContext(Dispatchers.IO) {
        val paths = database.withTransaction {
            val card = dao.getCardForSync(ownerUid, cardId) ?: return@withTransaction null
            if (!card.isDeleted) return@withTransaction null
            val materialPaths = dao.getMaterialPaths(cardId)
            dao.deleteNotesForCard(cardId)
            dao.deleteMaterialsForCard(cardId)
            dao.deleteTombstonedCard(cardId, ownerUid)
            materialPaths
        } ?: return@withContext false
        paths.forEach(FileUtils::deleteFile)
        true
    }
    suspend fun materialsForSync(cardId: String) = dao.getMaterialsForSync(cardId)
    suspend fun markMaterialSynced(cardId: String, id: Long, remoteResourceId: String) =
        dao.setRemoteResourceId(cardId, id, remoteResourceId)
    suspend fun notesForSync(cardId: String) = dao.getNotesForSync(cardId)
    suspend fun deletedNotesForSync(cardId: String) = dao.getDeletedNotesForSync(cardId)
    suspend fun markNoteSynced(cardId: String, id: String, remoteNoteId: String, syncedAtMillis: Long) =
        dao.markNoteSynced(cardId, id, remoteNoteId, syncedAtMillis)
    suspend fun removeSyncedDeletedNote(cardId: String, id: String) = dao.removeSyncedDeletedNote(cardId, id)

    suspend fun getCard(cardId: String) = dao.getCard(cardId)

    fun observeMaterials(cardId: String): Flow<List<LocalCardMaterialEntity>> = dao.observeMaterials(cardId)
    fun observeNotes(cardId: String): Flow<List<LocalCardNoteEntity>> = dao.observeNotes(cardId)

    suspend fun addMaterial(cardId: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val copied = FileUtils.copyMaterialToInternalStorage(context, uri) ?: return@withContext false
        val mimeType = FileUtils.resolveMimeType(context, uri, copied.displayName)
        saveMaterial(cardId, copied.displayName, copied.path, mimeType, copied.sizeBytes)
    }

    suspend fun addExistingFile(cardId: String, path: String, displayName: String, mimeType: String, sizeBytes: Long): Boolean =
        withContext(Dispatchers.IO) {
            val source = File(path)
            if (!source.isFile) return@withContext false
            val directory = File(context.filesDir, "materials").apply { mkdirs() }
            val copied = File(directory, "${UUID.randomUUID()}_${displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_")}")
            runCatching {
                source.copyTo(copied, overwrite = false)
                saveMaterial(cardId, displayName, copied.absolutePath, mimeType, copied.length())
            }.getOrElse {
                copied.delete()
                false
            }
        }

    private suspend fun saveMaterial(cardId: String, displayName: String, filePath: String, mimeType: String, sizeBytes: Long): Boolean =
        runCatching {
            dao.insertMaterial(LocalCardMaterialEntity(
                cardId = cardId,
                displayName = displayName,
                filePath = filePath,
                mimeType = mimeType,
                sizeBytes = sizeBytes,
                addedAtMillis = System.currentTimeMillis()
            ))
        }.onFailure { FileUtils.deleteFile(filePath) }.onSuccess { syncScheduler.enqueue() }.isSuccess

    suspend fun getMaterial(cardId: String, materialId: Long) = dao.getMaterial(cardId, materialId)

    suspend fun hasRemoteMaterial(cardId: String, remoteResourceId: String) =
        dao.getMaterialByRemoteId(cardId, remoteResourceId) != null

    suspend fun cacheDownloadedMaterial(
        cardId: String,
        remoteResourceId: String,
        displayName: String,
        mimeType: String,
        sizeBytes: Long,
        sourcePath: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (dao.getMaterialByRemoteId(cardId, remoteResourceId) != null) return@withContext true
        val source = File(sourcePath)
        if (!source.isFile) return@withContext false
        val directory = File(context.filesDir, "materials").apply { mkdirs() }
        val safeName = displayName.replace(Regex("[^A-Za-z0-9._ -]"), "_").ifBlank { "resource" }
        val target = File(directory, "${UUID.randomUUID()}_$safeName")
        runCatching {
            source.copyTo(target, overwrite = false)
            dao.insertMaterial(
                LocalCardMaterialEntity(
                    cardId = cardId,
                    displayName = displayName,
                    filePath = target.absolutePath,
                    mimeType = mimeType,
                    sizeBytes = sizeBytes.takeIf { it > 0 } ?: target.length(),
                    addedAtMillis = System.currentTimeMillis(),
                    remoteResourceId = remoteResourceId
                )
            )
        }.onFailure { FileUtils.deleteFile(target.absolutePath) }.isSuccess
    }

    suspend fun cacheRemoteNote(cardId: String, remoteNoteId: String, title: String, content: String) {
        val now = System.currentTimeMillis()
        val current = dao.getNoteByRemoteId(cardId, remoteNoteId)
        if (current == null) {
            dao.saveNote(
                LocalCardNoteEntity(
                    id = UUID.randomUUID().toString(),
                    cardId = cardId,
                    title = title,
                    content = content,
                    updatedAtMillis = now,
                    remoteNoteId = remoteNoteId,
                    lastSyncedAtMillis = now
                )
            )
        } else {
            dao.updateCleanNoteFromRemote(cardId, current.id, title, content, now, now)
        }
    }

    suspend fun saveNote(cardId: String, id: String?, title: String, content: String) {
        val noteId = id ?: UUID.randomUUID().toString()
        val previous = id?.let { dao.getNote(cardId, it) }
        dao.saveNote(LocalCardNoteEntity(
            id = noteId,
            cardId = cardId,
            title = title,
            content = content,
            updatedAtMillis = System.currentTimeMillis(),
            remoteNoteId = previous?.remoteNoteId,
            lastSyncedAtMillis = previous?.lastSyncedAtMillis,
            isDeleted = false
        ))
        syncScheduler.enqueue()
    }

    suspend fun deleteNote(cardId: String, id: String) {
        val note = dao.getNote(cardId, id) ?: return
        if (note.remoteNoteId == null) dao.deleteUnpublishedNote(cardId, id)
        else dao.markRemoteNoteDeleted(cardId, id, System.currentTimeMillis())
        syncScheduler.enqueue()
    }
}
