package com.imsx3d.classy.util

import android.content.Context
import java.time.LocalDate

/** Immutable local calendar data, loaded with the timetable rather than after its first frame. */
data class ScheduleHolidaySnapshot(
    val holidays: Set<LocalDate> = emptySet(),
    val workdays: Set<LocalDate> = emptySet(),
    val greyHoliday: Boolean = true,
    val greyWeekend: Boolean = true,
    val ignoreWorkday: Boolean = true,
) {
    fun marker(date: LocalDate): String? = when (date) {
        in workdays -> "补"
        in holidays -> "休"
        else -> null
    }

    fun grey(date: LocalDate, transfers: List<HolidayTransferEntry>): Boolean =
        HolidayManager.decideGrey(date, holidays, workdays, greyHoliday, greyWeekend,
            ignoreWorkday, transfers.any { it.targetDate == date })

    companion object {
        /** Network refresh remains independent; only persisted/available facts enter this snapshot. */
        fun load(context: Context, startDate: String, maxWeek: Int): ScheduleHolidaySnapshot {
            val start = runCatching { LocalDate.parse(startDate) }.getOrElse { LocalDate.now() }
            val end = start.plusWeeks(maxWeek.coerceIn(1, 100).toLong())
            val entries = (start.year..end.year).flatMap { HolidayManager.cachedYearEntries(context, it) }
            val merged = HolidayRangeOps.mergeSegments(entries, AppPrefs.getHolidayRanges(context))
            val (holidays, workdays) = HolidayRangeOps.toSets(merged.active)
            return ScheduleHolidaySnapshot(holidays, workdays,
                AppPrefs.isHolidayGreyHoliday(context), AppPrefs.isHolidayGreyWeekend(context),
                AppPrefs.isHolidayIgnoreWorkday(context))
        }
    }
}
