package com.imsx3d.classy.ui

import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.data.AppDatabase
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import com.imsx3d.classy.data.repository.ScheduleRepository
import com.imsx3d.classy.ui.screen.schedule.ScheduleViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ScheduleInitialSnapshotTest {
    @Test fun firstReadySnapshotContainsTargetWeekAndAllCourses() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repo = ScheduleRepository(db)
        val store = ViewModelStore()
        try {
            val start = LocalDate.now().with(java.time.DayOfWeek.MONDAY).minusWeeks(6).toString()
            val id = db.timeTableDao().insert(TimeTableEntity(name = "Cold fixture", startDate = start, isDefault = true))
            repeat(12) { index ->
                db.courseDao().insert(CourseEntity(tableId = id, groupId = "cold-$index", courseName = "Course $index",
                    day = index % 6 + 1, startNode = index / 6 * 2 + 1, step = 2,
                    startWeek = 1, endWeek = 16, color = "#123456"))
            }
            val vm = withContext(Dispatchers.Main) {
                ScheduleViewModel(repo).also {
                    store.put("cold", it)
                    assertTrue("Initial state is loading, not an empty timetable", it.state.value.loading)
                }
            }
            val first = withTimeout(5000) { vm.state.first { !it.loading } }
            assertEquals(id, first.selectedTableId)
            assertEquals(7, first.selectedWeek)
            assertEquals(12, first.courses.size)
            assertTrue(first.initialWeekSettled)
            withContext(Dispatchers.Main) { vm.changeWeek(4) }
            db.courseDao().insert(CourseEntity(tableId = id, groupId = "extra", courseName = "Extra", day = 7,
                startNode = 1, step = 2, startWeek = 1, endWeek = 16, color = "#123456"))
            val updated = withTimeout(5000) { vm.state.first { it.courses.size == 13 } }
            assertFalse(updated.loading)
            assertEquals("Live edits must retain the user's week", 4, updated.selectedWeek)
            repo.deleteTable(id)
            val empty = withTimeout(5000) { vm.state.first { !it.loading && it.tables.isEmpty() } }
            assertTrue(empty.courses.isEmpty())
            assertNull(empty.effectivePeriodTable)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            db.close()
        }
    }

    @Test fun noTablesBecomesEmptyOnlyAfterDatabaseEmits() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val store = ViewModelStore()
        try {
            val vm = withContext(Dispatchers.Main) {
                ScheduleViewModel(ScheduleRepository(db)).also { store.put("empty", it); assertTrue(it.state.value.loading) }
            }
            val ready = withTimeout(5000) { vm.state.first { !it.loading } }
            assertTrue(ready.tables.isEmpty())
            assertTrue(ready.courses.isEmpty())
        } finally { withContext(Dispatchers.Main) { store.clear() }; db.close() }
    }
}
