package com.aipdfreader.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.aipdfreader.app.data.local.entity.LocalMaterialEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMaterialDao {
    @Query("SELECT * FROM local_materials ORDER BY importedAtMillis DESC")
    fun observeAll(): Flow<List<LocalMaterialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(material: LocalMaterialEntity): Long

    @Delete
    suspend fun delete(material: LocalMaterialEntity)
}
