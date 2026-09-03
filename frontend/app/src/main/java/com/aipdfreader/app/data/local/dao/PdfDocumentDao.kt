package com.aipdfreader.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aipdfreader.app.data.local.entity.PdfDocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDocumentDao {

    @Query("SELECT * FROM pdf_documents ORDER BY lastOpenedAtMillis DESC")
    fun observeAll(): Flow<List<PdfDocumentEntity>>

    @Query("SELECT * FROM pdf_documents WHERE id = :id")
    suspend fun getById(id: Long): PdfDocumentEntity?

    @Query("SELECT * FROM pdf_documents WHERE id = :id")
    fun observeById(id: Long): Flow<PdfDocumentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: PdfDocumentEntity): Long

    @Update
    suspend fun update(document: PdfDocumentEntity)

    @Delete
    suspend fun delete(document: PdfDocumentEntity)

    @Query("UPDATE pdf_documents SET lastOpenedAtMillis = :timestamp WHERE id = :id")
    suspend fun touchLastOpened(id: Long, timestamp: Long)

    @Query("UPDATE pdf_documents SET lastReadPage = :page WHERE id = :id")
    suspend fun updateLastReadPage(id: Long, page: Int)
}
