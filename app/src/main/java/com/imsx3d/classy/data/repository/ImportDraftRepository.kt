package com.imsx3d.classy.data.repository

import com.imsx3d.classy.data.AppDatabase
import com.imsx3d.classy.data.entity.ImportDraftEntity
import com.imsx3d.classy.data.jw.JwImportDraftCodec
import com.imsx3d.classy.data.jw.JwImportDraftSnapshot
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** Persistence boundary for resumable academic imports. */
class ImportDraftRepository(private val db: AppDatabase) {
    private val dao = db.importDraftDao()

    fun observeAll(): Flow<List<ImportDraftEntity>> = dao.observeAll()

    suspend fun get(id: String): JwImportDraftSnapshot? =
        dao.getById(id)?.let { JwImportDraftCodec.fromJson(it.payloadJson) }

    suspend fun save(snapshot: JwImportDraftSnapshot, sourceType: String = "", sourceUrl: String = ""): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        dao.insert(
            ImportDraftEntity(
                id = id,
                sourceType = sourceType,
                sourceUrl = sourceUrl,
                payloadJson = JwImportDraftCodec.toJson(snapshot),
                createdAt = now,
                updatedAt = now,
            )
        )
        return id
    }

    suspend fun update(id: String, snapshot: JwImportDraftSnapshot) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                payloadJson = JwImportDraftCodec.toJson(snapshot),
                updatedAt = System.currentTimeMillis(),
            )
        )
    }

    suspend fun delete(id: String) = dao.deleteById(id)
}
