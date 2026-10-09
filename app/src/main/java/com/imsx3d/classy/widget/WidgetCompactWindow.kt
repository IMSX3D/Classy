package com.imsx3d.classy.widget

import com.imsx3d.classy.data.repository.ScheduleRepository
import com.imsx3d.classy.util.DateUtils
import java.time.LocalDate

/**
 * 最小档三天窗口数据构建 (2026-09-15 用户令) — 「本周课表（列表/周视图）· 小」专用。
 *
 * 窗口 = compactWindowDates 的三天真实日期; 每列按【该日期所在周】的周次过滤课程,
 * 因此窗口可以越出本周: 周一「今日居第二位」= 上周日(上周周次)/周一/周二(下周周次)。
 * 学期后日期课程清空(与整周口径一致); 学期前钳制到第 1 周(预习口径, currentWeek 自带)。
 */
internal object WidgetCompactWindow {

    suspend fun build(
        repo: ScheduleRepository,
        tableId: Long,
        timeJson: String,
        startDate: String,
        maxWeek: Int,
        today: LocalDate,
        todayFirst: Boolean,
        displayWeek: Int? = null,
        targetDate: LocalDate? = null,
    ): List<DayData> {
        val dates = if (targetDate != null) {
            WidgetCompactWindowCore.dates(today, targetDate, todayFirst)
        } else if (displayWeek != null) {
            (1..3).map { day -> DateUtils.dateOfWeek(startDate, displayWeek, day) }
        } else {
            WidgetCompactWindowCore.dates(today, null, todayFirst)
        }
        val courses = repo.getCourses(tableId)
        val transfers = com.imsx3d.classy.util.AppPrefs.getHolidayTransfers(com.imsx3d.classy.SleepyApp.get(), tableId)
        return dates.map { effectiveDate ->
            val dow = effectiveDate.dayOfWeek.value
            val visible = com.imsx3d.classy.util.CourseDateResolver.coursesOn(
                effectiveDate, startDate, maxWeek, courses, transfers
            )
            DayData(date = effectiveDate, dayOfWeek = dow, courses = visible, timeJson = timeJson)
        }
    }
}
