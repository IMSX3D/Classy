package com.imsx3d.classy.util

import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import java.time.LocalDate

/** A dated occurrence retains its source row; equal names never imply equal course identity. */
data class SemesterOccurrence(val date: LocalDate, val course: CourseEntity, val time: String?)
data class SemesterCourse(val rows: List<CourseEntity>, val occurrences: List<SemesterOccurrence>) {
    val id: Long get() = rows.first().id
    val name: String get() = rows.first().courseName
    val teacher: String get() = rows.first().teacher
}

/** Read-only projection. Pass the hydrated table, never its stale legacy period snapshot.
 * The regular semester plus explicit transfer destinations covers classes moved outside its
 * boundary. Grey/completed styling is not cancellation and never removes an occurrence.
 * Call off the UI thread; no database writes or changes to stored course colors/times.
 */
object SemesterSchedule {
    fun build(table: TimeTableEntity, courses: List<CourseEntity>, transfers: List<HolidayTransferEntry>): List<SemesterCourse> {
        require(table.maxWeek in 1..104) { "Invalid semester length" }
        val first = DateUtils.dateOfWeek(table.startDate, 1, 1)
        val dates = sortedSetOf<LocalDate>()
        repeat(table.maxWeek * 7) { dates.add(first.plusDays(it.toLong())) }
        transfers.forEach { dates.add(it.targetDate) }
        val dated = dates.flatMap { date ->
            CourseDateResolver.coursesOn(date, table.startDate, table.maxWeek, courses, transfers).map { course ->
                SemesterOccurrence(date, course, TimeTableUtils.courseTimeString(course.startNode,
                    course.step, table.timeJson, course.ownTime, course.startTime, course.endTime))
            }
        }.groupBy { it.course.id }
        return courses.sortedBy { it.id }.groupBy {
            Triple(it.groupId.ifBlank { "row:${it.id}" }, it.courseName, it.teacher.trim())
        }.values.map { rows ->
            SemesterCourse(rows, rows.flatMap { dated[it.id].orEmpty() }
                .sortedWith(compareBy<SemesterOccurrence> { it.date }.thenBy { it.time.orEmpty() }.thenBy { it.course.startNode }))
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
}
