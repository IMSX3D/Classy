package com.imsx3d.classy.util

import com.imsx3d.classy.data.entity.CourseEntity
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Completion belongs to a dated occurrence, not to a recurring course or weekday. */
object CourseCompletion {
    const val DIM_ALPHA = 0.45f

    fun endTime(course: CourseEntity, timeJson: String?): LocalTime? = runCatching {
        // The display parser has default slots for corrupt JSON; do not infer completion from them.
        if (!course.ownTime) {
            if (timeJson.isNullOrBlank()) return@runCatching null
            org.json.JSONArray(timeJson)
        }
        val times = TimeTableUtils.effectiveCourseTime(
            course.ownTime, course.startTime, course.endTime,
            course.startNode, course.step, timeJson.orEmpty()
        ) ?: return null
        val start = LocalTime.parse(times.first)
        val end = LocalTime.parse(times.second)
        // Timetables use same-day lessons. Invalid/reversed ranges must not dim today's class.
        end.takeIf { it > start }
    }.getOrNull()

    fun isCompleted(
        course: CourseEntity,
        occurrenceDate: LocalDate,
        timeJson: String?,
        now: LocalDateTime
    ): Boolean = when {
        occurrenceDate < now.toLocalDate() -> true
        occurrenceDate > now.toLocalDate() -> false
        else -> endTime(course, timeJson)?.let { now.toLocalTime() >= it } ?: false
    }
}
