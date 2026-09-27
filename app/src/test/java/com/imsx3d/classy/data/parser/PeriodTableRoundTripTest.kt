package com.imsx3d.classy.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue#40 §6/§10 导入导出往返契约:
 * - 新格式(带 P 区块): 解析恢复独立时间表(sourceId/name/nodesPerDay/timeJson 无损)
 * - 旧格式(无 P 区块): periodTable=null → 导入流程为每张课表各建一张(不误共享)
 * - 兼容列 timeJson 永远保留(旧版本至少读到每张课表内的作息)
 * - chk 校验在 P 区块存在时仍然成立
 */
class PeriodTableRoundTripTest {

    private val timeJson = """
        [{"node":1,"start":"08:20","end":"09:05"},
         {"node":2,"start":"09:15","end":"10:00"}]
    """.trimIndent()

    private fun exported(withPeriodTable: Boolean): String {
        val pt = if (withPeriodTable) SleepyNativeExporter.PeriodTableExport(
            id = 5L, name = "春季作息", nodesPerDay = 2, timeJson = timeJson
        ) else null
        return SleepyNativeExporter.exportFile(
            tableName = "2026 春季课表",
            startDate = "2026-02-23",
            maxWeek = 18,
            nodesPerDay = 2,
            timeJson = timeJson,
            courses = emptyList(),
            periodTable = pt
        )
    }

    @Test
    fun export_with_period_table_parses_back_losslessly() {
        val exported = exported(withPeriodTable = true)
        val result = ScheduleParser.parse(exported, defaultTableId = 999L)
        assertTrue("Parse should succeed, got: ${result.exceptionOrNull()}", result.isSuccess)
        val parsed = result.getOrThrow()

        val pt = parsed.periodTable
        assertNotNull("P 区块必须解析出来", pt)
        assertEquals(5L, pt!!.sourceId)
        assertEquals("春季作息", pt.name)
        assertEquals(2, pt.nodesPerDay)
        // timeJson 往返无损(逐节时间一致)
        val rows = com.imsx3d.classy.util.TimeTableUtils.parseTimeSlotRows(pt.timeJson)
        assertEquals(2, rows.size)
        assertEquals("08:20", rows[0].start)
        assertEquals("09:05", rows[0].end)
        assertEquals("09:15", rows[1].start)
        assertEquals("10:00", rows[1].end)
    }

    @Test
    fun compat_timejson_always_present_for_old_versions() {
        // §6: 旧版本打开新导出文件, 至少读到每张课表内的兼容 timeJson —
        // 导出端 P 区块之外必须照常输出 N 行
        val exported = exported(withPeriodTable = true)
        val result = ScheduleParser.parse(exported, defaultTableId = 999L)
        val parsed = result.getOrThrow()
        val rows = com.imsx3d.classy.util.TimeTableUtils.parseTimeSlotRows(parsed.timeJson)
        assertEquals("兼容列 N 行不得因 P 区块缺失", 2, rows.size)
        assertEquals("08:20", rows[0].start)
    }

    @Test
    fun export_without_period_table_yields_null_period_table() {
        // 旧格式(每张课表各自 timeJson): 导入端 periodTable=null → 各建一张, 不误共享
        val exported = exported(withPeriodTable = false)
        val result = ScheduleParser.parse(exported, defaultTableId = 999L)
        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow().periodTable)
    }

    @Test
    fun crc_check_passes_with_period_table_block() {
        val exported = exported(withPeriodTable = true)
        val result = ScheduleParser.parse(exported, defaultTableId = 999L)
        val parsed = result.getOrThrow()
        assertTrue(
            "P 区块不得破坏 crc 校验: warnings=${parsed.warnings}",
            parsed.warnings.none { it.contains("校验") }
        )
    }

    @Test
    fun share_text_form_round_trips_period_table() {
        val exported = SleepyNativeExporter.exportShareText(
            tableName = "分享课表",
            startDate = "2026-02-23",
            maxWeek = 16,
            nodesPerDay = 2,
            timeJson = timeJson,
            courses = emptyList(),
            periodTable = SleepyNativeExporter.PeriodTableExport(
                id = 9L, name = "考试周作息", nodesPerDay = 2, timeJson = timeJson
            )
        )
        val result = ScheduleParser.parse(exported, defaultTableId = 999L)
        assertTrue(result.isSuccess)
        val pt = result.getOrThrow().periodTable
        assertNotNull(pt)
        assertEquals(9L, pt!!.sourceId)
        assertEquals("考试周作息", pt.name)
    }

    @Test
    fun nd_preset_period_table_exports_as_pd_and_expands_back() {
        // P 区块时间等于冻结预设时写 Pd(紧凑形), 导入端展开回 12 节
        val exported = SleepyNativeExporter.exportFile(
            tableName = "ND表",
            startDate = "2026-02-23",
            maxWeek = 16,
            nodesPerDay = 12,
            timeJson = com.imsx3d.classy.util.TimeTableUtils.DEFAULT_TIME_JSON,
            courses = emptyList(),
            periodTable = SleepyNativeExporter.PeriodTableExport(
                id = 3L, name = "默认作息", nodesPerDay = 12,
                timeJson = com.imsx3d.classy.util.TimeTableUtils.DEFAULT_TIME_JSON
            )
        )
        assertTrue("Pd 行必须出现", exported.contains("Pd"))
        val result = ScheduleParser.parse(exported, defaultTableId = 999L)
        assertTrue(result.isSuccess)
        val pt = result.getOrThrow().periodTable
        assertNotNull(pt)
        val rows = com.imsx3d.classy.util.TimeTableUtils.parseTimeSlotRows(pt!!.timeJson)
        assertEquals(12, rows.size)
        assertEquals("08:00", rows[0].start)
        assertEquals("22:30", rows[11].end)
    }
}
