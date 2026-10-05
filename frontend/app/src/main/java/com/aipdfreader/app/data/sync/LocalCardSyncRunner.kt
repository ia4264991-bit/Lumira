package com.aipdfreader.app.data.sync

import com.aipdfreader.app.data.remote.CardApi
import com.aipdfreader.app.data.remote.DomainApi
import com.aipdfreader.app.data.remote.dto.CreateCardRequest
import com.aipdfreader.app.data.remote.dto.NoteWriteDto
import com.aipdfreader.app.data.repository.AuthRepository
import com.aipdfreader.app.data.repository.LocalCardRepository
import com.aipdfreader.app.util.BackendConfiguration
import com.aipdfreader.app.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
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
    private val cardApi: CardApi,
    private val domainApi: DomainApi,
    @ApplicationContext private val context: Context
) {
    /** Returns true when JobScheduler should retry after its backoff delay. */
    suspend fun run(): Boolean {
        if (!BackendConfiguration.isConfigured) return false
        val uid = authRepository.currentUserId ?: return false
        return try {
            for (card in localCardRepository.cardsForSync(uid)) {
                var remoteCardId = card.remoteCardId
                if (remoteCardId == null) {
                    remoteCardId = cardApi.createCard(CreateCardRequest(card.name, card.color)).id
                    localCardRepository.markCardSynced(card.id, uid, remoteCardId)
                }

                for (material in localCardRepository.materialsForSync(card.id)) {
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

                for (note in localCardRepository.notesForSync(card.id)) {
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

                for (note in localCardRepository.deletedNotesForSync(card.id)) {
                    val response = domainApi.deleteNote(note.remoteNoteId!!)
                    if (!response.isSuccessful && response.code() != 404) throw HttpException(response)
                    localCardRepository.removeSyncedDeletedNote(card.id, note.id)
                }

                val remoteResources = domainApi.resources(remoteCardId)
                for (resource in remoteResources) {
                    if (resource.status != "READY" || localCardRepository.hasRemoteMaterial(card.id, resource.id)) continue
                    val displayName = resource.originalFilename?.substringAfterLast('/')
                        ?.takeIf(String::isNotBlank) ?: resource.title
                    val file = File(context.cacheDir, "vision-sync-${java.util.UUID.randomUUID()}")
                    try {
                        val response = domainApi.downloadResource(resource.id)
                        withContext(Dispatchers.IO) {
                            response.byteStream().use { input -> file.outputStream().use(input::copyTo) }
                        }
                        val cached = localCardRepository.cacheDownloadedMaterial(
                            card.id,
                            resource.id,
                            displayName,
                            resource.mimeType ?: FileUtils.resolveMimeType(context, android.net.Uri.fromFile(file), displayName),
                            resource.fileSizeBytes,
                            file.absolutePath
                        )
                        if (!cached) throw IOException("A server file could not be saved on this phone")
                    } finally {
                        FileUtils.deleteFile(file.absolutePath)
                    }
                }

                for (note in domainApi.notes(remoteCardId)) {
                    localCardRepository.cacheRemoteNote(card.id, note.id, note.title, note.content)
                }
            }
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
}
