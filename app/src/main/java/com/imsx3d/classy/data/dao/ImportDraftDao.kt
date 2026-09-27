package com.imsx3d.classy.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.imsx3d.classy.data.entity.ImportDraftEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ImportDraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(draft: ImportDraftEntity)

    @Update
    suspend fun update(draft: ImportDraftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: ImportDraftEntity)

    @Query("SELECT * FROM import_drafts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ImportDraftEntity?

    @Query("SELECT * FROM import_drafts WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ImportDraftEntity?>

    @Query("SELECT * FROM import_drafts ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ImportDraftEntity>>

    @Query("SELECT * FROM import_drafts ORDER BY updatedAt DESC")
    suspend fun getAll(): List<ImportDraftEntity>

    @Query("DELETE FROM import_drafts WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM import_drafts")
    suspend fun deleteAll()
}
