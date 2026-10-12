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
    @Test fun populatedVersionNineOpensWithoutLosingAnyExistingColumn() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "refresh-migration-test.db"
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
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                    val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                    for (j in 0 until indices.length()) old.execSQL(indices.getJSONObject(j)
                        .getString("createSql").replace("\${TABLE_NAME}", table))
                    val values = ContentValues()
                    val fields = entity.getJSONArray("fields")
                    for (j in 0 until fields.length()) {
                        val field = fields.getJSONObject(j)
                        val column = field.getString("columnName")
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
                old.version = 9
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
                } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
}
