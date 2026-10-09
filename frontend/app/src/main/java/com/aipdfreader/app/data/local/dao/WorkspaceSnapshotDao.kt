package com.aipdfreader.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aipdfreader.app.data.local.entity.CachedResourceFileEntity
import com.aipdfreader.app.data.local.entity.WorkspaceSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkspaceSnapshotDao {
    @Query("SELECT * FROM workspace_snapshots WHERE ownerUid = :ownerUid ORDER BY name COLLATE NOCASE")
    fun observeForUser(ownerUid: String): Flow<List<WorkspaceSnapshotEntity>>

    @Query("SELECT * FROM workspace_snapshots WHERE ownerUid = :ownerUid")
    suspend fun getForUser(ownerUid: String): List<WorkspaceSnapshotEntity>

    @Query("SELECT * FROM workspace_snapshots WHERE ownerUid = :ownerUid AND cardId = :cardId LIMIT 1")
    suspend fun get(ownerUid: String, cardId: String): WorkspaceSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(snapshot: WorkspaceSnapshotEntity)

    @Query("DELETE FROM workspace_snapshots WHERE ownerUid = :ownerUid AND cardId = :cardId")
    suspend fun delete(ownerUid: String, cardId: String)

    @Query("SELECT * FROM cached_resource_files WHERE ownerUid = :ownerUid AND cardId = :cardId AND resourceId = :resourceId LIMIT 1")
    suspend fun getFile(ownerUid: String, cardId: String, resourceId: String): CachedResourceFileEntity?

    @Query("SELECT * FROM cached_resource_files WHERE ownerUid = :ownerUid AND cardId = :cardId")
    suspend fun getFilesForCard(ownerUid: String, cardId: String): List<CachedResourceFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(file: CachedResourceFileEntity)

    @Query("DELETE FROM cached_resource_files WHERE ownerUid = :ownerUid AND cardId = :cardId AND resourceId = :resourceId")
    suspend fun deleteFile(ownerUid: String, cardId: String, resourceId: String)

    @Query("DELETE FROM cached_resource_files WHERE ownerUid = :ownerUid AND cardId = :cardId")
    suspend fun deleteFilesForCard(ownerUid: String, cardId: String)
}
