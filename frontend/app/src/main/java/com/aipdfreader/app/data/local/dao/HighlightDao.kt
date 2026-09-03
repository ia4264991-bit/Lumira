package com.aipdfreader.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.aipdfreader.app.data.local.entity.HighlightEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HighlightDao {

    @Query("SELECT * FROM highlights WHERE pdfId = :pdfId ORDER BY pageIndex ASC, createdAtMillis ASC")
    fun observeForPdf(pdfId: Long): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE pdfId = :pdfId AND pageIndex = :pageIndex ORDER BY createdAtMillis ASC")
    fun observeForPage(pdfId: Long, pageIndex: Int): Flow<List<HighlightEntity>>

    @Query("SELECT * FROM highlights WHERE id = :id")
    suspend fun getById(id: Long): HighlightEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(highlight: HighlightEntity): Long

    @Update
    suspend fun update(highlight: HighlightEntity)

    @Delete
    suspend fun delete(highlight: HighlightEntity)
}
