package com.imsx3d.classy.ui.component

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.imsx3d.classy.data.entity.SmartPeriodConfig
import com.imsx3d.classy.util.TimeTableUtils
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Saves unfinished input verbatim; restoration never writes to the database. */
val PeriodRowsSaver = Saver<SnapshotStateList<TimeTableUtils.TimeSlotRow>, String>(
    save = { TimeTableUtils.buildTimeJsonFromRows(it.toList()) },
    restore = { json -> mutableStateListOf<TimeTableUtils.TimeSlotRow>().apply {
        addAll(TimeTableUtils.parseTimeSlotRows(json))
    } }
)

val SmartPeriodDraftSaver = Saver<SmartPeriodConfig, String>(
    save = { Json.encodeToString(it) },
    restore = { decodeSmartPeriodConfig(it) }
)
