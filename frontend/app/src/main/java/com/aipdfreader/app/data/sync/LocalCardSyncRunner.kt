package com.aipdfreader.app.data.sync

import com.aipdfreader.app.data.remote.CardApi
import com.aipdfreader.app.data.remote.listAllCards
import com.aipdfreader.app.data.remote.DomainApi
import com.aipdfreader.app.data.remote.dto.CardDto
import com.aipdfreader.app.data.remote.dto.CreateCardRequest
import com.aipdfreader.app.data.remote.dto.NoteWriteDto
import com.aipdfreader.app.data.repository.AuthRepository
import com.aipdfreader.app.data.repository.LocalCardRepository
import com.aipdfreader.app.data.repository.RemoteWorkspaceCache
import com.aipdfreader.app.data.local.entity.WorkspaceSnapshotContent
import com.aipdfreader.app.util.BackendConfiguration
import com.aipdfreader.app.util.FileUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalCardSyncRunner @Inject constructor(
    private val authRepository: AuthRepository,
    private val localCardRepository: LocalCardRepository,
    private val workspaceCache: RemoteWorkspaceCache,
    private val cardApi: CardApi,
    private val domainApi: DomainApi
) {
    /** Returns true when JobScheduler should retry after its backoff delay. */
    suspend fun run(): Boolean {
        if (!BackendConfiguration.isConfigured) return false
        val uid = authRepository.currentUserId ?: return false
        return try {
            for (card in localCardRepository.cardsForSync(uid)) {
                if (card.isDeleted) {
                    finishDeletedCard(uid, card.id, card.remoteCardId)
                    continue
                }
                var remoteCardId = card.remoteCardId
                if (remoteCardId == null) {
                    remoteCardId = cardApi.createCard(CreateCardRequest(card.name, card.color)).id
                    if (localCardRepository.markCardSynced(card.id, uid, remoteCardId) == 0) {
                        localCardRepository.recordRemoteCardIdForDeletion(card.id, uid, remoteCardId)
                        finishDeletedCard(uid, card.id, remoteCardId)
                        continue
                    }
                }

                for (material in localCardRepository.materialsForSync(card.id)) {
                    if (finishDeletedCard(uid, card.id, remoteCardId)) break
                    val file = File(material.filePath)
                    if (!file.isFile) throw IOException("A saved file is no longer available")
                    val mimeType = FileUtils.mimeTypeForFileName(material.displayName)
                        ?: material.mimeType.takeIf(String::isNotBlank)
                        ?: "application/octet-stream"
                    val part = withContext(Dispatchers.IO) {
                        MultipartBody.Part.createFormData(
                            "file",
                            material.displayName,
                            file.asRequestBody(mimeType.toMediaTypeOrNull())
                        )
                    }
                    val resource = domainApi.uploadResource(
                        remoteCardId,
                        part,
                        material.displayName.substringBeforeLast('.', material.displayName)
                    )
                    localCardRepository.markMaterialSynced(card.id, material.id, resource.id)
                }
                if (localCardRepository.isCardDeleted(uid, card.id)) {
                    finishDeletedCard(uid, card.id, remoteCardId)
                    continue
                }

                for (note in localCardRepository.notesForSync(card.id)) {
                    if (finishDeletedCard(uid, card.id, remoteCardId)) break
                    val body = NoteWriteDto(note.title, note.content)
                    val remoteNote = note.remoteNoteId?.let {
                        domainApi.updateNote(it, body)
                    } ?: domainApi.createNote(remoteCardId, body)
                    localCardRepository.markNoteSynced(
                        card.id,
                        note.id,
                        remoteNote.id,
                        note.updatedAtMillis
                    )
                }
                if (localCardRepository.isCardDeleted(uid, card.id)) {
                    finishDeletedCard(uid, card.id, remoteCardId)
                    continue
                }

                for (note in localCardRepository.deletedNotesForSync(card.id)) {
                    if (finishDeletedCard(uid, card.id, remoteCardId)) break
                    val response = domainApi.deleteNote(note.remoteNoteId!!)
                    if (!response.isSuccessful && response.code() != 404) throw HttpException(response)
                    localCardRepository.removeSyncedDeletedNote(card.id, note.id)
                }

                // A server file's metadata can be cached without silently downloading its bytes.
                // Explicit downloads from the workspace are stored persistently by RemoteWorkspaceCache.
            }
            syncAuthorizedWorkspaces(uid)
            false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: HttpException) {
            error.code() == 408 || error.code() == 429 || error.code() >= 500
        } catch (_: IOException) {
            true
        } catch (_: Exception) {
            true
        }
    }

    private suspend fun finishDeletedCard(uid: String, localCardId: String, remoteCardId: String?): Boolean {
        if (!localCardRepository.isCardDeleted(uid, localCardId)) return false
        if (remoteCardId != null) {
            val response = cardApi.deletePrivateCard(remoteCardId)
            if (!response.isSuccessful && response.code() != 404) throw HttpException(response)
            workspaceCache.removeDeletedCard(uid, remoteCardId)
        }
        localCardRepository.finalizeDeletedCard(uid, localCardId)
        return true
    }

    private suspend fun syncAuthorizedWorkspaces(uid: String) {
        val personalCards = cardApi.listAllCards()
        val sharedCards = cardApi.listAllCards("shared")
        val sharedById = sharedCards.associateBy(CardDto::id)
        val cards = (personalCards + sharedCards).associateBy { it.id }
            .values.map { card ->
                val membership = sharedById[card.id]
                card.copy(
                    role = membership?.role ?: card.role ?: "OWNER",
                    memberCardId = membership?.memberCardId ?: card.id
                )
            }

        val snapshots = cards.map { card ->
            val remote = cardApi.getCard(card.id)
            val resources = domainApi.resources(card.id)
            val notes = domainApi.notes(card.id)
            val studySets = domainApi.studySets(card.id)
            val quizzes = domainApi.quizzes(card.id)
            val flashcardSets = domainApi.flashcardSets(card.id)
            val events = if (card.isShared) domainApi.events(card.id) else emptyList()
            val members = if (card.isShared) domainApi.members(card.id) else emptyList()
            val canManage = card.role == "OWNER" || card.role == "ADMIN"
            val joinRequests = if (card.isShared && canManage) domainApi.joinRequests(card.id) else emptyList()
            card.copy(
                ownerId = remote.ownerId,
                name = remote.name,
                color = remote.color,
                isShared = remote.isShared
            ) to WorkspaceSnapshotContent(
                resources = resources,
                notes = notes,
                studySets = studySets,
                quizzes = quizzes,
                flashcardSets = flashcardSets,
                events = events,
                members = members,
                joinRequests = joinRequests
            )
        }
        snapshots.forEach { (card, content) -> workspaceCache.save(card, content, uid) }
        workspaceCache.reconcileSharedMembership(uid, sharedCards.map { it.id }.toSet())
    }
}
