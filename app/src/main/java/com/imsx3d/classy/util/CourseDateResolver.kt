package com.imsx3d.classy.util

import com.imsx3d.classy.data.entity.CourseEntity
import java.time.LocalDate

/** The target replaces that day's regular timetable; the moved source day is empty.
 * Week parity and semester membership belong to the source date, not the target date.
 */
object CourseDateResolver {
    fun teachingDate(date: LocalDate, transfers: List<HolidayTransferEntry>): LocalDate? =
        HolidayRangeOps.HolidayTransferOps.sourceDateFor(date, transfers)

    fun coursesOn(
        date: LocalDate,
        startDate: String,
        maxWeek: Int,
        courses: List<CourseEntity>,
        transfers: List<HolidayTransferEntry> = emptyList()
    ): List<CourseEntity> {
        val source = teachingDate(date, transfers) ?: return emptyList()
        if (DateUtils.semesterStatus(startDate, maxWeek, source) != DateUtils.SemesterStatus.IN_RANGE) return emptyList()
        val week = DateUtils.currentWeek(startDate, source)
        return courses.filter { it.day == source.dayOfWeek.value && it.inWeek(week) }.sortedBy { it.startNode }
    }

    fun displayWeek(
        week: Int,
        startDate: String,
        maxWeek: Int,
        courses: List<CourseEntity>,
        transfers: List<HolidayTransferEntry>
    ): List<CourseEntity> = (1..7).flatMap { day ->
        coursesOn(DateUtils.dateOfWeek(startDate, week, day), startDate, maxWeek, courses, transfers)
            .map { it.copy(day = day) }
    }
}
