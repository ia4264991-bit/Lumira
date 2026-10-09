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
    fun observeAllCards(ownerUid: String): Flow<List<LocalCardEntity>>

    @Query("SELECT * FROM local_cards WHERE ownerUid = :ownerUid AND isDeleted = 0 ORDER BY createdAtMillis DESC")
    fun observeCards(ownerUid: String): Flow<List<LocalCardEntity>>

    @Query("SELECT * FROM local_cards WHERE id = :cardId AND isDeleted = 0 LIMIT 1")
    suspend fun getCard(cardId: String): LocalCardEntity?

    @Query("SELECT * FROM local_cards WHERE ownerUid = :ownerUid ORDER BY createdAtMillis ASC")
    suspend fun getCardsForSync(ownerUid: String): List<LocalCardEntity>

    @Query("SELECT * FROM local_cards WHERE id = :cardId AND ownerUid = :ownerUid LIMIT 1")
    suspend fun getCardForSync(ownerUid: String, cardId: String): LocalCardEntity?

    @Query("UPDATE local_cards SET remoteCardId = :remoteCardId WHERE id = :cardId AND ownerUid = :ownerUid AND remoteCardId IS NULL AND isDeleted = 0")
    suspend fun setRemoteCardId(cardId: String, ownerUid: String, remoteCardId: String): Int

    @Query("UPDATE local_cards SET remoteCardId = :remoteCardId WHERE id = :cardId AND ownerUid = :ownerUid AND remoteCardId IS NULL")
    suspend fun recordRemoteCardIdForDeletion(cardId: String, ownerUid: String, remoteCardId: String): Int

    @Query("UPDATE local_cards SET name = :name WHERE id = :cardId AND ownerUid = :ownerUid AND isDeleted = 0")
    suspend fun updateCardName(cardId: String, ownerUid: String, name: String): Int

    @Query("UPDATE local_cards SET name = :name WHERE ownerUid = :ownerUid AND remoteCardId = :remoteCardId AND isDeleted = 0")
    suspend fun updateCardNameByRemoteId(ownerUid: String, remoteCardId: String, name: String): Int

    @Query("UPDATE local_cards SET isDeleted = 1 WHERE id = :cardId AND ownerUid = :ownerUid AND isDeleted = 0")
    suspend fun markCardDeleted(cardId: String, ownerUid: String): Int

    @Query("SELECT filePath FROM local_card_materials WHERE cardId = :cardId")
    suspend fun getMaterialPaths(cardId: String): List<String>

    @Query("DELETE FROM local_card_materials WHERE cardId = :cardId")
    suspend fun deleteMaterialsForCard(cardId: String)

    @Query("DELETE FROM local_card_notes WHERE cardId = :cardId")
    suspend fun deleteNotesForCard(cardId: String)

    @Query("DELETE FROM local_cards WHERE id = :cardId AND ownerUid = :ownerUid AND isDeleted = 1")
    suspend fun deleteTombstonedCard(cardId: String, ownerUid: String): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCard(card: LocalCardEntity)

    @Query("SELECT * FROM local_card_materials WHERE cardId = :cardId ORDER BY addedAtMillis DESC")
    fun observeMaterials(cardId: String): Flow<List<LocalCardMaterialEntity>>

    @Query("SELECT * FROM local_card_materials WHERE id = :id AND cardId = :cardId LIMIT 1")
    suspend fun getMaterial(cardId: String, id: Long): LocalCardMaterialEntity?

    @Query("SELECT * FROM local_card_materials WHERE cardId = :cardId AND remoteResourceId = :remoteResourceId LIMIT 1")
    suspend fun getMaterialByRemoteId(cardId: String, remoteResourceId: String): LocalCardMaterialEntity?

    @Query("SELECT * FROM local_card_materials WHERE cardId = :cardId AND remoteResourceId IS NULL ORDER BY addedAtMillis ASC")
    suspend fun getMaterialsForSync(cardId: String): List<LocalCardMaterialEntity>

    @Query("UPDATE local_card_materials SET remoteResourceId = :remoteResourceId WHERE id = :id AND cardId = :cardId AND remoteResourceId IS NULL")
    suspend fun setRemoteResourceId(cardId: String, id: Long, remoteResourceId: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMaterial(material: LocalCardMaterialEntity): Long

    @Query("DELETE FROM local_card_materials WHERE id = :id AND cardId = :cardId")
    suspend fun deleteMaterial(cardId: String, id: Long)

    @Query("SELECT * FROM local_card_notes WHERE cardId = :cardId AND isDeleted = 0 ORDER BY updatedAtMillis DESC")
    fun observeNotes(cardId: String): Flow<List<LocalCardNoteEntity>>

    @Query("SELECT * FROM local_card_notes WHERE cardId = :cardId AND isDeleted = 0 AND (remoteNoteId IS NULL OR updatedAtMillis > COALESCE(lastSyncedAtMillis, 0)) ORDER BY updatedAtMillis ASC")
    suspend fun getNotesForSync(cardId: String): List<LocalCardNoteEntity>

    @Query("SELECT * FROM local_card_notes WHERE cardId = :cardId AND isDeleted = 1 AND remoteNoteId IS NOT NULL")
    suspend fun getDeletedNotesForSync(cardId: String): List<LocalCardNoteEntity>

    @Query("SELECT * FROM local_card_notes WHERE id = :id AND cardId = :cardId LIMIT 1")
    suspend fun getNote(cardId: String, id: String): LocalCardNoteEntity?

    @Query("SELECT * FROM local_card_notes WHERE cardId = :cardId AND remoteNoteId = :remoteNoteId LIMIT 1")
    suspend fun getNoteByRemoteId(cardId: String, remoteNoteId: String): LocalCardNoteEntity?

    @Query("UPDATE local_card_notes SET title = :title, content = :content, updatedAtMillis = :updatedAtMillis, lastSyncedAtMillis = :syncedAtMillis WHERE id = :id AND cardId = :cardId AND isDeleted = 0 AND updatedAtMillis <= COALESCE(lastSyncedAtMillis, 0)")
    suspend fun updateCleanNoteFromRemote(cardId: String, id: String, title: String, content: String, updatedAtMillis: Long, syncedAtMillis: Long)

    @Query("UPDATE local_card_notes SET remoteNoteId = :remoteNoteId, lastSyncedAtMillis = :syncedAtMillis WHERE id = :id AND cardId = :cardId")
    suspend fun markNoteSynced(cardId: String, id: String, remoteNoteId: String, syncedAtMillis: Long)

    @Query("UPDATE local_card_notes SET isDeleted = 1, updatedAtMillis = :deletedAtMillis WHERE id = :id AND cardId = :cardId AND remoteNoteId IS NOT NULL")
    suspend fun markRemoteNoteDeleted(cardId: String, id: String, deletedAtMillis: Long)

    @Query("DELETE FROM local_card_notes WHERE id = :id AND cardId = :cardId AND remoteNoteId IS NULL")
    suspend fun deleteUnpublishedNote(cardId: String, id: String)

    @Query("DELETE FROM local_card_notes WHERE id = :id AND cardId = :cardId")
    suspend fun removeSyncedDeletedNote(cardId: String, id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNote(note: LocalCardNoteEntity)

}
