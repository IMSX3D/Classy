package com.imsx3d.classy.widget.notification

import java.time.LocalDate

/** Occurrences, rather than course rows, own alarm identities (including transferred classes). */
internal object ReminderWindow {
    fun dates(today: LocalDate): List<LocalDate> = (0L..6L).map(today::plusDays)
    fun key(courseId: Long, date: LocalDate): String = "$courseId/$date"
    fun courseId(key: String): Long? = key.substringBefore('/').toLongOrNull()
    fun classDate(epoch: Long, zone: java.time.ZoneId): LocalDate =
        java.time.Instant.ofEpochMilli(epoch).atZone(zone).toLocalDate()
}
