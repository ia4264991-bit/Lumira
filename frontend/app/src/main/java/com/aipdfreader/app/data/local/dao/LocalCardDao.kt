package com.aipdfreader.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aipdfreader.app.data.local.entity.LocalCardEntity
import com.aipdfreader.app.data.local.entity.LocalCardMaterialEntity
import com.aipdfreader.app.data.local.entity.LocalCardNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalCardDao {
    @Query("SELECT * FROM local_cards WHERE ownerUid = :ownerUid ORDER BY createdAtMillis DESC")
    fun observeCards(ownerUid: String): Flow<List<LocalCardEntity>>

    @Query("SELECT * FROM local_cards WHERE id = :cardId LIMIT 1")
    suspend fun getCard(cardId: String): LocalCardEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCard(card: LocalCardEntity)

    @Query("SELECT * FROM local_card_materials WHERE cardId = :cardId ORDER BY addedAtMillis DESC")
    fun observeMaterials(cardId: String): Flow<List<LocalCardMaterialEntity>>

    @Query("SELECT * FROM local_card_materials WHERE id = :id AND cardId = :cardId LIMIT 1")
    suspend fun getMaterial(cardId: String, id: Long): LocalCardMaterialEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMaterial(material: LocalCardMaterialEntity): Long

    @Query("DELETE FROM local_card_materials WHERE id = :id AND cardId = :cardId")
    suspend fun deleteMaterial(cardId: String, id: Long)

    @Query("SELECT * FROM local_card_notes WHERE cardId = :cardId ORDER BY updatedAtMillis DESC")
    fun observeNotes(cardId: String): Flow<List<LocalCardNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNote(note: LocalCardNoteEntity)

    @Query("DELETE FROM local_card_notes WHERE id = :id AND cardId = :cardId")
    suspend fun deleteNote(cardId: String, id: String)
}
