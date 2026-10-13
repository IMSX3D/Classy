package com.imsx3d.classy.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import com.imsx3d.classy.data.repository.ScheduleRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in external process-kill probe. Uses a separate persistent database, never the app database. */
class EditorProcessDeathProbe {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "editor-process-probe.db"
    private val marker get() = java.io.File(context.filesDir, "editor-process-ready")
    private fun enabled() = assumeTrue(InstrumentationRegistry.getArguments().getString("lifecycleProbe") == "true")
    private fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
    private val table = TimeTableEntity(name = "Process fixture", startDate = "2026-10-05")
    private fun row(tableId: Long) = CourseEntity(tableId = tableId, groupId = "process-group", courseName = "Original",
        day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#FF123456")

    @Test fun prepareUncommitted(): Unit = runBlocking {
        enabled(); context.deleteDatabase(name); marker.delete()
        val db = open()
        val repo = ScheduleRepository(db)
        val id = repo.insertTable(table)
        repo.insertCourse(row(id))
        repo.atomicEdit {
            db.courseDao().deleteByTableId(id)
            db.timeTableDao().update(table.copy(id = id, name = "Uncommitted"))
            marker.writeText("uncommitted")
            awaitCancellation()
        }
    }

    @Test fun verifyRollback() = runBlocking {
        enabled()
        val db = open()
        try {
            assertEquals("uncommitted", marker.readText())
            assertEquals(listOf("Process fixture"), db.timeTableDao().getAll().map { it.name })
            assertEquals(listOf("Original"), db.courseDao().getAll().map { it.courseName })
        } finally { db.close(); context.deleteDatabase(name); marker.delete() }
    }

    @Test fun prepareCommitted(): Unit = runBlocking {
        enabled(); context.deleteDatabase(name); marker.delete()
        val db = open()
        ScheduleRepository(db).saveCourseDrafts(null, listOf(row(0)), null, table, "process-creation-token") { it }
        marker.writeText("committed")
        awaitCancellation()
    }

    @Test fun verifyCommittedReplay() = runBlocking {
        enabled()
        val db = open()
        try {
            assertEquals("committed", marker.readText())
            val repo = ScheduleRepository(db)
            val id = repo.saveCourseDrafts(null, listOf(row(0)), null, table, "process-creation-token") { it }
            assertEquals(1, repo.getAllTables().size)
            assertEquals(1, repo.getCourses(id).size)
            assertEquals(id, repo.findCreatedCourseTable("process-creation-token"))
        } finally { db.close(); context.deleteDatabase(name); marker.delete() }
    }
}
