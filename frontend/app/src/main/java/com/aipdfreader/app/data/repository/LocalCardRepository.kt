package com.aipdfreader.app.data.repository

import android.content.Context
import android.net.Uri
import com.aipdfreader.app.data.local.dao.LocalCardDao
import com.aipdfreader.app.data.local.entity.LocalCardEntity
import com.aipdfreader.app.data.local.entity.LocalCardMaterialEntity
import com.aipdfreader.app.data.local.entity.LocalCardNoteEntity
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
    private val dao: LocalCardDao,
    private val authRepository: AuthRepository
) {
    fun observeCards(): Flow<List<LocalCardEntity>> =
        authRepository.currentUserId?.let(dao::observeCards) ?: emptyFlow()

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
        return card
    }

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
        }.onFailure { FileUtils.deleteFile(filePath) }.isSuccess

    suspend fun getMaterial(cardId: String, materialId: Long) = dao.getMaterial(cardId, materialId)

    suspend fun saveNote(cardId: String, id: String?, title: String, content: String) {
        dao.saveNote(LocalCardNoteEntity(
            id = id ?: UUID.randomUUID().toString(), cardId = cardId,
            title = title, content = content, updatedAtMillis = System.currentTimeMillis()
        ))
    }

    suspend fun deleteNote(cardId: String, id: String) = dao.deleteNote(cardId, id)
}
