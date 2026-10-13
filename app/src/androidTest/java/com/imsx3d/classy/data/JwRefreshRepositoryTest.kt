package com.imsx3d.classy.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.data.entity.*
import com.imsx3d.classy.data.jw.*
import com.imsx3d.classy.data.repository.ScheduleRepository
import com.imsx3d.classy.data.undo.UndoManager
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JwRefreshRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: ScheduleRepository
    private val school = JwSchoolInfo("t", "Test school", "https://example.org", "test")
    private val table = TimeTableEntity(name = "Refresh test", startDate = "2026-09-07", nodesPerDay = 8)
    private fun course(id: Long, name: String = "Math") = CourseEntity(tableId = id, groupId = "g",
        courseName = name, day = 1, startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#123456")
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java).build()
        repo = ScheduleRepository(db)
        UndoManager.clear()
    }
    @After fun cleanup() { db.close(); UndoManager.clear() }

    @Test fun replayedCreationDoesNotDuplicateTableOrCourses() = runBlocking {
        val token = "creation-fixture-token"
        val created = repo.saveCourseDrafts(null, listOf(course(0)), null, table, token) { it }
        val replayed = repo.saveCourseDrafts(null, listOf(course(0).copy(courseName = "stale restored draft")), null, table, token) { it }
        assertEquals(created, replayed)
        assertEquals(1, repo.getAllTables().size)
        assertEquals(listOf("Math"), repo.getCourses(created).map { it.courseName })
        assertEquals(created, repo.findCreatedCourseTable(token))
    }

    @Test fun savingStaleEditorPreservesConfirmedGroupColorAndCustomColors() = runBlocking {
        val id = db.timeTableDao().insert(table)
        val original = course(id)
        db.courseDao().insert(original)
        val custom = original.copy(day = 2, colorMode = CourseColorMode.CUSTOM, color = "#ABCDEF")
        db.courseDao().insert(custom)
        val drafts = repo.getCourses(id).map { it.copy(courseName = "Renamed") }
        repo.setGroupSourceColor(id, "g", "#FF112233")
        repo.saveCourseDrafts(id, drafts, "g", table) { it }
        val after = repo.getCourses(id)
        assertEquals("#FF112233", after.single { it.colorMode == CourseColorMode.GROUP }.color)
        assertEquals("#ABCDEF", after.single { it.colorMode == CourseColorMode.CUSTOM }.color)
        assertTrue(after.all { it.courseName == "Renamed" })
    }

    @Test fun missingGroupDoesNotReportColorWriteSuccess() = runBlocking {
        val id = db.timeTableDao().insert(table)
        try { repo.setGroupSourceColor(id, "missing", "#FF112233"); fail("must fail") }
        catch (_: IllegalStateException) { }
        assertTrue(repo.getCourses(id).isEmpty())
    }

    @Test fun editorDeleteUsesSourceTableAndBlankGroupsDeleteOnlyOneRow() = runBlocking {
        val first = db.timeTableDao().insert(table)
        val second = db.timeTableDao().insert(table.copy(name = "Other"))
        val grouped = course(first)
        val id = db.courseDao().insert(grouped)
        db.courseDao().insert(grouped.copy(day = 2))
        val otherId = db.courseDao().insert(course(second))
        repo.deleteCourseFromEditor(grouped.copy(id = id))
        assertTrue(db.courseDao().getByTable(first).isEmpty())
        assertNotNull(db.courseDao().getById(otherId))
        val single = course(first).copy(groupId = "")
        val singleId = db.courseDao().insert(single)
        val peer = db.courseDao().insert(single.copy(day = 3))
        repo.deleteCourseFromEditor(single.copy(id = singleId))
        assertNull(db.courseDao().getById(singleId))
        assertNotNull(db.courseDao().getById(peer))
    }

    @Test fun firstLinkPreservesLocalAndRoutineThenRepeatedRefreshIsIdempotent() = runBlocking {
        val id = db.timeTableDao().insert(table)
        db.courseDao().insert(course(id, "Local only"))
        val before = db.timeTableDao().getById(id)!!
        val plan = repo.prepareJwRefresh(id, listOf(course(id)), school)
        assertTrue(plan.firstLink)
        assertFalse(plan.removals.single().safe)
        repo.applyJwRefresh(plan, plan.additions.map { it.key }.toSet(), emptySet())
        val after = db.timeTableDao().getById(id)!!
        assertEquals(before, after.copy(jwSourceJson = ""))
        assertEquals(2, db.courseDao().getByTable(id).size)
        val repeated = repo.prepareJwRefresh(id, listOf(course(id)), school)
        assertTrue(repeated.additions.isEmpty())
        assertTrue(repeated.removals.isEmpty())
        repo.applyJwRefresh(repeated, emptySet(), emptySet())
        assertEquals(2, db.courseDao().getByTable(id).size)
    }

    @Test fun localEditsRemainProtectedAfterRepeatedRefreshAndRemoteRemoval() = runBlocking {
        val id = db.timeTableDao().insert(table)
        val first = repo.prepareJwRefresh(id, listOf(course(id)), school)
        repo.applyJwRefresh(first, first.additions.map { it.key }.toSet(), emptySet())
        val row = db.courseDao().getByTable(id).single()
        db.courseDao().update(row.copy(note = "My note"))
        val again = repo.prepareJwRefresh(id, listOf(course(id)), school)
        repo.applyJwRefresh(again, emptySet(), emptySet())
        val removed = repo.prepareJwRefresh(id, listOf(course(id, "English")), school)
        assertFalse(removed.removals.single().safe)
        repo.applyJwRefresh(removed, removed.additions.map { it.key }.toSet(), emptySet())
        assertEquals("My note", db.courseDao().getById(row.id)!!.note)
    }

    @Test fun sharedRoutineIsPreservedAndConcurrentChangesRejectRefresh() = runBlocking {
        val routineId = db.periodTableDao().insert(PeriodTableEntity(name = "Shared", nodesPerDay = 8))
        val routine = db.periodTableDao().getById(routineId)!!
        val id = db.timeTableDao().insert(table.copy(periodTableId = routineId, preBindSnapshotJson = "saved"))
        val other = db.timeTableDao().insert(table.copy(name = "Other", periodTableId = routineId))
        val before = db.timeTableDao().getById(id)!!
        val otherBefore = db.timeTableDao().getById(other)!!
        val plan = repo.prepareJwRefresh(id, listOf(course(id)), school)
        repo.applyJwRefresh(plan, plan.additions.map { it.key }.toSet(), emptySet())
        assertEquals(before, db.timeTableDao().getById(id)!!.copy(jwSourceJson = ""))
        assertEquals(otherBefore, db.timeTableDao().getById(other))
        assertEquals(routine, db.periodTableDao().getById(routineId))
        val next = repo.prepareJwRefresh(id, listOf(course(id, "New")), school)
        db.periodTableDao().update(routine.copy(nodesPerDay = 10))
        try { repo.applyJwRefresh(next, next.additions.map { it.key }.toSet(), emptySet()); fail("shared routine stale") }
        catch (_: IllegalStateException) { }
        assertEquals(listOf("Math"), db.courseDao().getByTable(id).map { it.courseName })
    }

    @Test fun staleReviewAndOutOfRangeCannotWrite() = runBlocking {
        val id = db.timeTableDao().insert(table)
        val plan = repo.prepareJwRefresh(id, listOf(course(id)), school)
        db.timeTableDao().update(table.copy(id = id, timeJson = "[]"))
        try { repo.applyJwRefresh(plan, plan.additions.map { it.key }.toSet(), emptySet()); fail("stale") }
        catch (_: IllegalStateException) { }
        assertTrue(db.courseDao().getByTable(id).isEmpty())
        try { repo.prepareJwRefresh(id, listOf(course(id).copy(startNode = 12)), school); fail("range") }
        catch (_: IllegalArgumentException) { }
        assertTrue(db.courseDao().getByTable(id).isEmpty())
    }
}
