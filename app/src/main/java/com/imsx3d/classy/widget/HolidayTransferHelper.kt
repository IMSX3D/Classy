package com.imsx3d.classy.widget

import android.content.Context
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import com.imsx3d.classy.util.*
import java.time.LocalDate

object HolidayTransferHelper {
    fun semesterStatus(context: Context, table: TimeTableEntity, date: LocalDate): DateUtils.SemesterStatus {
        val source = CourseDateResolver.teachingDate(date, AppPrefs.getHolidayTransfers(context, table.id)) ?: date
        return DateUtils.semesterStatus(table.startDate, table.maxWeek, source)
    }

    fun effectiveDayOfWeek(context: Context, tableId: Long?, date: LocalDate): Int =
        HolidayRangeOps.HolidayTransferOps.effectiveDayOfWeek(date,
            tableId?.let { AppPrefs.getHolidayTransfers(context, it) }.orEmpty())

    fun coursesOn(context: Context, table: TimeTableEntity, date: LocalDate, courses: List<CourseEntity>): List<CourseEntity> =
        CourseDateResolver.coursesOn(date, table.startDate, table.maxWeek, courses,
            AppPrefs.getHolidayTransfers(context, table.id))
}
