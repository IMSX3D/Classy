package com.imsx3d.classy.ui.theme

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.SaverScope
import com.imsx3d.classy.ui.component.PeriodRowsSaver
import com.imsx3d.classy.util.TimeTableUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class PeriodDraftSaversTest {
    private val scope = SaverScope { true }

    @Test fun partialInputAndEdgeNodesSurviveRestoration() {
        val rows = mutableStateListOf(
            TimeTableUtils.TimeSlotRow(0, "07:", "", TimeTableUtils.EdgeClass.Before),
            TimeTableUtils.TimeSlotRow(1, "08:00", "08:45"),
            TimeTableUtils.TimeSlotRow(13, "21:00", "21:50", TimeTableUtils.EdgeClass.After)
        )
        val saved = with(PeriodRowsSaver) { scope.save(rows) }!!
        assertEquals(rows.toList(), PeriodRowsSaver.restore(saved)!!.toList())
    }

    @Test fun emptyDraftDoesNotRegenerateDefaultRows() {
        val rows = mutableStateListOf<TimeTableUtils.TimeSlotRow>()
        val saved = with(PeriodRowsSaver) { scope.save(rows) }!!
        assertEquals(emptyList<TimeTableUtils.TimeSlotRow>(), PeriodRowsSaver.restore(saved)!!.toList())
    }
}
