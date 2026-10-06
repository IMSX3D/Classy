package com.imsx3d.classy.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import com.imsx3d.classy.data.repository.ScheduleRepository
import com.imsx3d.classy.data.undo.UndoManager
import com.imsx3d.classy.util.AppPrefs
import com.imsx3d.classy.util.HolidayTransferEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Real Room transactions, FK enforcement and undo/redo. Uses only an in-memory database. */
@RunWith(AndroidJUnit4::class)
class RepositoryQualityTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ScheduleRepository
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var originalTransfers = emptyList<HolidayTransferEntry>()
    private val table = TimeTableEntity(name = "Quality test", startDate = "2026-09-07")
    private fun course(tableId: Long = 0) = CourseEntity(
        tableId = tableId, groupId = "g", courseName = "Test", day = 1, startNode = 1,
        step = 2, startWeek = 1, endWeek = 20, color = "#123456"
    )

    @Before fun setup() {
        originalTransfers = AppPrefs.getHolidayTransfers(context, 1)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = ScheduleRepository(db)
        UndoManager.clear()
    }

    @After fun cleanup() {
        AppPrefs.setHolidayTransfers(context, 1, originalTransfers)
        db.close()
        UndoManager.clear()
    }

    @Test fun firstCourseAndSlotSaveUndoRedoAsOneAction() = runBlocking {
        val json = """[{"node":1,"start":"07:00","end":"08:00"}]"""
        val id = repo.saveCourseDrafts(null, listOf(course()), null, table) { json }
        assertTrue(id > 0)
        assertEquals(id, repo.getCourses(id).single().tableId)
        assertEquals(json, repo.getTable(id)?.timeJson)
        assertTrue(repo.restoreLastSnapshot())
        assertTrue(repo.getAllTables().isEmpty())
        assertTrue(repo.getAllCourses().isEmpty())
        assertTrue(repo.redoLastUndo())
        assertEquals(json, repo.getTable(id)?.timeJson)
        assertEquals(1, repo.getCourses(id).size)
    }

    @Test fun fkFailureRollsBackTableAndKeepsPreviousUndo() = runBlocking {
        repo.insertTable(table)
        val before = UndoManager.peek()
        try {
            repo.atomicEdit {
                repo.insertTable(table.copy(name = "Must roll back"))
                repo.insertCourse(course(9999))
            }
            fail("Expected FK rejection")
        } catch (_: android.database.sqlite.SQLiteConstraintException) { }
        assertEquals(1, repo.tableCount())
        assertEquals(before, UndoManager.peek())
        assertTrue(repo.getAllCourses().isEmpty())
    }

    @Test fun cancelledCompoundEditLeavesNoPartialTable() = runBlocking {
        try {
            repo.atomicEdit {
                repo.insertTable(table)
                throw CancellationException("Fault injection")
            }
        } catch (_: CancellationException) { }
        assertEquals(0, repo.tableCount())
        assertFalse(UndoManager.hasSnapshot)
    }

    @Test fun editCourseAndSlotRevertTogetherOnExistingTable() = runBlocking {
        val id = repo.insertTable(table)
        val oldId = repo.insertCourse(course(id))
        val oldCourse = requireNotNull(repo.getCourse(oldId))
        val before = requireNotNull(repo.getTable(id))
        repo.saveCourseDrafts(id, listOf(oldCourse.copy(room = "New room")), "g", table) { "[]" }
        assertEquals("New room", repo.getCourses(id).single().room)
        assertTrue(repo.restoreLastSnapshot())
        assertEquals(before.timeJson, repo.getTable(id)?.timeJson)
        assertEquals(oldCourse, repo.getCourses(id).single())
    }

    @Test fun deleteTableUndoRedoRestoresAndRemovesTransfers() = runBlocking {
        val id = repo.insertTable(table)
        val transfers = listOf(HolidayTransferEntry(LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-11"), "h"))
        AppPrefs.setHolidayTransfers(context, id, transfers)
        repo.deleteTable(id)
        assertTrue(AppPrefs.getHolidayTransfers(context, id).isEmpty())
        assertTrue(repo.restoreLastSnapshot())
        assertEquals(transfers, AppPrefs.getHolidayTransfers(context, id))
        assertTrue(repo.redoLastUndo())
        assertTrue(AppPrefs.getHolidayTransfers(context, id).isEmpty())
    }
}
