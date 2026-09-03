package com.aipdfreader.app.data.repository

import com.aipdfreader.app.data.local.dao.HighlightDao
import com.aipdfreader.app.data.local.entity.HighlightEntity
import com.aipdfreader.app.domain.model.Highlight
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HighlightRepository @Inject constructor(
    private val dao: HighlightDao
) {
    fun observeForPdf(pdfId: Long): Flow<List<Highlight>> =
        dao.observeForPdf(pdfId).map { list -> list.map { it.toDomain() } }

    fun observeForPage(pdfId: Long, pageIndex: Int): Flow<List<Highlight>> =
        dao.observeForPage(pdfId, pageIndex).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: Long): Highlight? = dao.getById(id)?.toDomain()

    suspend fun save(
        pdfId: Long,
        pageIndex: Int,
        selectedText: String,
        colorArgb: Int,
        note: String? = null
    ): Long = dao.insert(
        HighlightEntity(
            pdfId = pdfId,
            pageIndex = pageIndex,
            selectedText = selectedText,
            note = note,
            colorArgb = colorArgb,
            createdAtMillis = System.currentTimeMillis()
        )
    )

    suspend fun updateNote(highlight: Highlight, note: String?) {
        dao.update(highlight.toEntity().copy(note = note))
    }

    suspend fun delete(highlight: Highlight) = dao.delete(highlight.toEntity())
}

private fun HighlightEntity.toDomain() = Highlight(
    id = id,
    pdfId = pdfId,
    pageIndex = pageIndex,
    selectedText = selectedText,
    note = note,
    colorArgb = colorArgb,
    createdAtMillis = createdAtMillis
)

private fun Highlight.toEntity() = HighlightEntity(
    id = id,
    pdfId = pdfId,
    pageIndex = pageIndex,
    selectedText = selectedText,
    note = note,
    colorArgb = colorArgb,
    createdAtMillis = createdAtMillis
)
