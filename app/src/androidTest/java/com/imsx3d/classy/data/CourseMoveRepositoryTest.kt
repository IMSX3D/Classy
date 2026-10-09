package com.imsx3d.classy.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import com.imsx3d.classy.data.repository.CourseMoveConflictException
import com.imsx3d.classy.data.repository.ScheduleRepository
import com.imsx3d.classy.data.undo.UndoManager
import com.imsx3d.classy.util.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CourseMoveRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ScheduleRepository
    private lateinit var table: TimeTableEntity
    private lateinit var source: CourseEntity
    private var transfers = emptyList<HolidayTransferEntry>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        repo = ScheduleRepository(db)
        table = TimeTableEntity(id = 1, name = "Grid test", startDate = "2026-09-07")
        transfers = AppPrefs.getHolidayTransfers(context, 1)
        AppPrefs.setHolidayTransfers(context, 1, emptyList())
        repo.insertTable(table)
        val draft = CourseEntity(tableId = 1, groupId = "g", courseName = "Math", day = 1,
            startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#123456")
        source = draft.copy(id = repo.insertCourse(draft))
        UndoManager.clear()
    }
    @After fun cleanup() {
        AppPrefs.setHolidayTransfers(context, 1, transfers)
        db.close()
        UndoManager.clear()
    }
    private suspend fun move(force: Boolean = false) = repo.moveCourseMeeting(source, 6, 6,
        GridSelection(3, 3, 4), CourseMoveScope.THIS_WEEK, table, emptyList(), force)

    @Test fun moveSplitUndoRedoAndOtherMeetingIsolation() = runBlocking {
        val other = source.copy(id = 0, day = 5)
        val otherId = repo.insertCourse(other)
        move()
        assertEquals(other.copy(id = otherId), repo.getCourse(otherId))
        assertEquals(4, repo.getCourses(1).size)
        assertTrue(repo.restoreLastSnapshot())
        assertEquals(source, repo.getCourse(source.id))
        assertEquals(2, repo.getCourses(1).size)
        assertTrue(repo.redoLastUndo())
        assertEquals(1, repo.getCourses(1).count { it.day == 3 && it.inWeek(6) })
    }
    @Test fun rejectedConflictDoesNotMutateRowsOrUndo() = runBlocking {
        repo.insertCourse(source.copy(id = 0, groupId = "other", day = 3, startNode = 3))
        val before = repo.getCourses(1)
        val undo = UndoManager.peek()
        try { move(); fail("Expected conflict") } catch (_: CourseMoveConflictException) { }
        assertEquals(before, repo.getCourses(1))
        assertEquals(undo, UndoManager.peek())
        move(force = true)
        assertEquals(2, repo.getCourses(1).count { it.day == 3 && it.inWeek(6) })
    }
    @Test fun staleSourceCannotOverwriteConcurrentEdit() = runBlocking {
        val edited = source.copy(room = "Changed elsewhere")
        repo.updateCourse(edited)
        try { move(); fail("Expected stale source rejection") } catch (_: IllegalArgumentException) { }
        assertEquals(listOf(edited), repo.getCourses(1))
    }
}
