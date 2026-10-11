package com.imsx3d.classy.util

import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class ImportedPeriodRowsTest {
    private fun schoolPeriods(): List<Triple<Int, String, String>> {
        val text = javaClass.getResource("/zf-new/fzfu-routine.json")!!.readText()
        val array = JSONArray(text)
        return (0 until array.length()).map {
            val row = array.getJSONObject(it)
            Triple(row.getInt("jcmc"), row.getString("qssj"), row.getString("jssj"))
        }
    }

    @Test fun `eleven real periods reach confirmation without generated defaults`() {
        val rows = TimeTableUtils.importedPeriodRows(11, schoolPeriods())
        assertEquals(11, rows.size)
        assertEquals("08:25", rows.first().start)
        assertEquals("20:55", rows.last().end)
        val effective = TimeTableUtils.effectiveRowsForConfirm(rows, -1L, emptyList())
        assertTrue(effective.all { it.start.isNotBlank() && it.end.isNotBlank() })
        assertEquals(rows, TimeTableUtils.parseTimeSlotRows(TimeTableUtils.buildTimeJsonFromRows(effective)))
    }

    @Test fun `school routine is not truncated to last occupied course`() {
        assertEquals(11, TimeTableUtils.importedPeriodRows(8, schoolPeriods()).size)
    }

    @Test fun `missing course periods remain blank instead of invented`() {
        val rows = TimeTableUtils.importedPeriodRows(13, schoolPeriods())
        assertEquals(13, rows.size)
        assertEquals("", rows[11].start)
        assertEquals("", rows[12].end)
        assertEquals(8, TimeTableUtils.importedPeriodRows(8, emptyList()).size)
    }

    @Test fun `saved eleven period routine takes precedence over blank import`() {
        val imported = TimeTableUtils.importedPeriodRows(11, emptyList())
        val saved = TimeTableUtils.importedPeriodRows(11, schoolPeriods())
        val actual = TimeTableUtils.effectiveRowsForConfirm(imported, 7L,
            listOf(7L to TimeTableUtils.buildTimeJsonFromRows(saved)))
        assertEquals(saved, actual)
        assertTrue(imported.all { it.start.isEmpty() })
    }
}
