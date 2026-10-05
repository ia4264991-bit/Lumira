package com.aipdfreader.app.data.repository

import android.content.Context
import android.net.Uri
import com.aipdfreader.app.data.local.dao.LocalMaterialDao
import com.aipdfreader.app.data.local.entity.LocalMaterialEntity
import com.aipdfreader.app.domain.model.LocalMaterial
import com.aipdfreader.app.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalMaterialRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: LocalMaterialDao
) {
    fun observeMaterials(): Flow<List<LocalMaterial>> =
        dao.observeAll().map { rows -> rows.map(LocalMaterialEntity::toDomain) }

    suspend fun import(uri: Uri, mimeType: String): Boolean = withContext(Dispatchers.IO) {
        val copied = FileUtils.copyMaterialToInternalStorage(context, uri) ?: return@withContext false
        runCatching {
            dao.insert(
                LocalMaterialEntity(
                    displayName = copied.displayName,
                    filePath = copied.path,
                    mimeType = mimeType,
                    sizeBytes = copied.sizeBytes,
                    importedAtMillis = System.currentTimeMillis()
                )
            )
        }.onFailure { FileUtils.deleteFile(copied.path) }.isSuccess
    }

    suspend fun delete(material: LocalMaterial) {
        FileUtils.deleteFile(material.filePath)
        dao.delete(material.toEntity())
    }
}

private fun LocalMaterialEntity.toDomain() = LocalMaterial(
    id = id,
    displayName = displayName,
    filePath = filePath,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    importedAtMillis = importedAtMillis
)

private fun LocalMaterial.toEntity() = LocalMaterialEntity(
    id = id,
    displayName = displayName,
    filePath = filePath,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    importedAtMillis = importedAtMillis
)
