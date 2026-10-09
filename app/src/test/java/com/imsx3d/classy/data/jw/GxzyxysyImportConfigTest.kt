package com.imsx3d.classy.data.jw

import com.imsx3d.classy.ui.screen.imports.gxzyxysyFetchJs
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test

class GxzyxysyImportConfigTest {
    @Test fun `tester timetable is carried to import preview with thirteen exact periods`() {
        val js = gxzyxysyFetchJs(10, 13)
        val json = js.substringAfter("startDate:startDate,periods:").substringBefore("});")
        val periods = JSONArray(json)
        assertEquals(13, periods.length())
        assertEquals("08:30", periods.getJSONObject(0).getString("start"))
        assertEquals("12:15", periods.getJSONObject(4).getString("end"))
        assertEquals("14:20", periods.getJSONObject(5).getString("start"))
        assertEquals(10, periods.getJSONObject(9).getInt("node"))
        assertEquals("18:30", periods.getJSONObject(9).getString("start"))
        assertEquals("21:25", periods.getJSONObject(12).getString("end"))
        assertFalse(js.contains("__EVENING_"))
    }
    @Test fun `invalid mapping is rejected before JS injection`() {
        assertThrows(IllegalArgumentException::class.java) { gxzyxysyFetchJs(0, 13) }
        assertThrows(IllegalArgumentException::class.java) { gxzyxysyFetchJs(13, 10) }
    }
}
