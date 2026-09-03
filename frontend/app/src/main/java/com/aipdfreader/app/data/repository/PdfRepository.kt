package com.aipdfreader.app.data.repository

import android.content.Context
import android.net.Uri
import com.aipdfreader.app.data.local.dao.PdfDocumentDao
import com.aipdfreader.app.data.local.entity.PdfDocumentEntity
import com.aipdfreader.app.domain.model.PdfDocument
import com.aipdfreader.app.pdf.PdfTextExtractor
import com.aipdfreader.app.util.FileUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PdfDocumentDao,
    private val textExtractor: PdfTextExtractor
) {

    fun observeLibrary(): Flow<List<PdfDocument>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeDocument(id: Long): Flow<PdfDocument?> =
        dao.observeById(id).map { it?.toDomain() }

    suspend fun getDocument(id: Long): PdfDocument? = dao.getById(id)?.toDomain()

    /**
     * Copies the picked PDF into app-private storage and records it in Room.
     * Returns the new document id, or null if the import failed (e.g.
     * corrupt/unreadable file).
     */
    suspend fun importPdf(uri: Uri): Long? {
        val copied = FileUtils.copyPdfToInternalStorage(context, uri) ?: return null
        val pageCount = runCatching { textExtractor.getPageCount(copied.path) }.getOrElse {
            FileUtils.deleteFile(copied.path)
            return null
        }

        val now = System.currentTimeMillis()
        val entity = PdfDocumentEntity(
            title = copied.displayName.removeSuffix(".pdf"),
            filePath = copied.path,
            pageCount = pageCount,
            sizeBytes = copied.sizeBytes,
            importedAtMillis = now,
            lastOpenedAtMillis = now
        )
        return dao.insert(entity)
    }

    suspend fun deleteDocument(document: PdfDocument) {
        FileUtils.deleteFile(document.filePath)
        dao.delete(document.toEntity())
    }

    suspend fun markOpened(id: Long) = dao.touchLastOpened(id, System.currentTimeMillis())

    suspend fun saveLastReadPage(id: Long, page: Int) = dao.updateLastReadPage(id, page)

    suspend fun extractPageText(filePath: String, pageIndex: Int): String =
        textExtractor.extractPageText(filePath, pageIndex)
}

private fun PdfDocumentEntity.toDomain() = PdfDocument(
    id = id,
    title = title,
    filePath = filePath,
    pageCount = pageCount,
    sizeBytes = sizeBytes,
    importedAtMillis = importedAtMillis,
    lastOpenedAtMillis = lastOpenedAtMillis,
    lastReadPage = lastReadPage
)

private fun PdfDocument.toEntity() = PdfDocumentEntity(
    id = id,
    title = title,
    filePath = filePath,
    pageCount = pageCount,
    sizeBytes = sizeBytes,
    importedAtMillis = importedAtMillis,
    lastOpenedAtMillis = lastOpenedAtMillis,
    lastReadPage = lastReadPage
)
