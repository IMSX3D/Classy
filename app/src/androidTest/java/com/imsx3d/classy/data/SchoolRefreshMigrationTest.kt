package com.imsx3d.classy.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SchoolRefreshMigrationTest {
    @Test fun populatedVersionNineOpensWithoutLosingAnyExistingColumn() = verifyMigration(9)

    /** Historical fixtures reconstructed from the additive migrations, not archived user DBs.
     * Exercises every registered starting version against Room's complete current schema.
     */
    @Test fun allEarlierRegisteredVersionsRetainTheirRowsAndRoutineBinding() {
        for (version in 3..8) verifyMigration(version)
    }

    private fun verifyMigration(version: Int) = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "refresh-migration-$version-test.db"
        context.deleteDatabase(name)
        val schema = JSONObject(instrumentation.context.assets.open(
            "com.imsx3d.classy.data.AppDatabase/9.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val expected = linkedMapOf<String, Map<String, String>>()
        try {
            val file = context.getDatabasePath(name)
            file.parentFile!!.mkdirs()
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    if (table == "period_tables" && version < 8 || table == "import_drafts" && version < 7) continue
                    val absent = buildSet {
                        if (table == "courses") {
                            if (version < 4) add("colorMode")
                            if (version < 5) addAll(listOf("isIrregularNode", "isIrregularTime"))
                            if (version < 6) add("alias")
                        }
                        if (table == "time_tables") {
                            if (version < 8) add("periodTableId")
                            if (version < 9) add("preBindSnapshotJson")
                        }
                    }
                    var createSql = entity.getString("createSql").replace("\${TABLE_NAME}", table)
                    absent.forEach { column -> createSql = createSql.replace(Regex(", `$column` [^,)]*"), "") }
                    old.execSQL(createSql)
                    val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                    for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j)
                        .getString("createSql").replace("\${TABLE_NAME}", table))
                    val values = ContentValues()
                    val fields = entity.getJSONArray("fields")
                    for (j in 0 until fields.length()) {
                        val field = fields.getJSONObject(j)
                        val column = field.getString("columnName")
                        if (column in absent) continue
                        when (field.getString("affinity")) {
                            "TEXT" -> values.put(column, "saved-$table-$column")
                            "REAL" -> values.put(column, 1.5)
                            else -> values.put(column, 1L)
                        }
                    }
                    old.insertOrThrow(table, null, values)
                    old.rawQuery("SELECT * FROM `$table`", null).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        expected[table] = cursor.columnNames.associateWith { cursor.getString(cursor.getColumnIndexOrThrow(it)) }
                    }
                }
                old.version = version
            }
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(*ALL_MIGRATIONS).build()
            try {
                    val migrated = db.openHelper.writableDatabase // Room validates the complete v10 schema.
                    assertEquals(10, migrated.version)
                    expected.forEach { (table, columns) ->
                        migrated.query("SELECT * FROM `$table`").use { cursor ->
                            assertTrue(cursor.moveToFirst())
                            columns.forEach { (column, value) ->
                                assertEquals("$table.$column", value, cursor.getString(cursor.getColumnIndexOrThrow(column)))
                            }
                            if (table == "time_tables") assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("jwSourceJson")))
                            assertFalse(cursor.moveToNext())
                        }
                    }
                    if (version < 8) {
                        migrated.query("SELECT t.periodTableId, p.id, t.timeJson, p.timeJson FROM time_tables t JOIN period_tables p ON t.periodTableId = p.id").use {
                            assertTrue("v$version routine backfill missing", it.moveToFirst())
                            assertEquals(it.getLong(0), it.getLong(1))
                            assertEquals(it.getString(2), it.getString(3))
                        }
                    }
                    if (version < 5) migrated.query("SELECT isIrregularTime FROM courses").use {
                        assertTrue(it.moveToFirst())
                        assertEquals("Legacy ownTime must become irregular time", 1, it.getInt(0))
                    }
                } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
