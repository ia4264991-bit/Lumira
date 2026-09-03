package com.aipdfreader.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aipdfreader.app.data.local.entity.ChatMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {

    @Query("SELECT * FROM chat_messages WHERE pdfId = :pdfId ORDER BY createdAtMillis ASC")
    fun observeForPdf(pdfId: Long): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE pdfId = :pdfId AND highlightId = :highlightId ORDER BY createdAtMillis ASC")
    fun observeForHighlight(pdfId: Long, highlightId: Long): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessageEntity): Long

    @Delete
    suspend fun delete(message: ChatMessageEntity)

    @Query("DELETE FROM chat_messages WHERE pdfId = :pdfId")
    suspend fun clearForPdf(pdfId: Long)
}
