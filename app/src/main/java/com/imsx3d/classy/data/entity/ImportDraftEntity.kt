package com.imsx3d.classy.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted import preview owned by the import flow until the user applies or discards it.
 *
 * [payloadJson] is intentionally opaque to the database layer so the parser/UI contract can
 * evolve without another schema migration. [id] is supplied by the import flow, making retries
 * and process recreation idempotent for the same draft.
 */
@Entity(tableName = "import_drafts")
data class ImportDraftEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "sourceType", defaultValue = "") val sourceType: String = "",
    @ColumnInfo(name = "sourceUrl", defaultValue = "") val sourceUrl: String = "",
    @ColumnInfo(name = "payloadJson") val payloadJson: String,
    @ColumnInfo(name = "createdAt") val createdAt: Long,
    @ColumnInfo(name = "updatedAt") val updatedAt: Long,
)
