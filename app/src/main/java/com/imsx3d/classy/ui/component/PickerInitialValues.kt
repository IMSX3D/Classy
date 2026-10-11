package com.imsx3d.classy.ui.component

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Material date pickers represent calendar days at UTC midnight. */
internal object PickerInitialValues {
    val dateYears = 1900..2100
    fun dateMillis(value: String): Long? = runCatching {
        LocalDate.parse(value).also { require(it.year in dateYears) }.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    }.getOrNull()

    fun time(value: String): LocalTime =
        runCatching { LocalTime.parse(value) }.getOrDefault(LocalTime.of(8, 0))
}
