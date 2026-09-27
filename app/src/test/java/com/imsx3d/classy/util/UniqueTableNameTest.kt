package com.imsx3d.classy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v1.0.56 全局唯一名(T2):课表∪作息表全域查重,排己契约。
 *
 * - isTableNameTaken: 名称在两表任一存在即 true;excludeId 排除自己(编辑改名不撞自己)。
 * - suggestUniqueName: 撞名自动顺延 2/3/4…(导入自动建表用),不撞原样返回。
 */
class UniqueTableNameTest {

    private val tableNames = listOf("春季课表", "考试周", "作息表2")
    private val periodNames = listOf("默认作息", "作息表")

    @Test
    fun taken_when_name_in_course_tables() {
        assertTrue(TimeTableUtils.isTableNameTaken("春季课表", tableNames, periodNames))
    }

    @Test
    fun taken_when_name_in_period_tables() {
        assertTrue(TimeTableUtils.isTableNameTaken("默认作息", tableNames, periodNames))
    }

    @Test
    fun not_taken_when_name_absent_from_both() {
        assertFalse(TimeTableUtils.isTableNameTaken("全新名字", tableNames, periodNames))
    }

    @Test
    fun exclude_self_row_by_id() {
        // 自己占用自己名字:排除后不算撞(编辑页改回来时必经路径)
        assertFalse(
            TimeTableUtils.isTableNameTaken(
                "春季课表", tableNames, periodNames,
                excludeCourseTableId = 7L, courseTableNamesById = mapOf(7L to "春季课表")
            )
        )
    }

    @Test
    fun exclude_self_period_table_by_id() {
        assertFalse(
            TimeTableUtils.isTableNameTaken(
                "作息表", tableNames, periodNames,
                excludePeriodTableId = 3L, periodTableNamesById = mapOf(3L to "作息表")
            )
        )
    }

    @Test
    fun exclusion_only_skips_named_row_not_others() {
        // 排除 id=7 后,同名不同 id 的其他行仍算撞
        assertTrue(
            TimeTableUtils.isTableNameTaken(
                "春季课表", tableNames, periodNames,
                excludeCourseTableId = 99L, courseTableNamesById = mapOf(99L to "别的表")
            )
        )
    }

    @Test
    fun suggest_returns_original_when_free() {
        assertEquals("新表", TimeTableUtils.suggestUniqueName("新表", tableNames, periodNames))
    }

    @Test
    fun suggest_appends_2_when_base_taken() {
        // '作息表' 被作息表占; '作息表2' 恰好也被某课表占 → 跳到 '作息表3'
        assertEquals("作息表3", TimeTableUtils.suggestUniqueName("作息表", tableNames, periodNames))
    }

    @Test
    fun suggest_appends_2_when_only_base_taken() {
        assertEquals("默认作息2", TimeTableUtils.suggestUniqueName("默认作息", tableNames, periodNames))
    }

    @Test
    fun suggest_increments_until_free() {
        // 作息表 与 作息表2 都被占 → 作息表3
        assertEquals("作息表3", TimeTableUtils.suggestUniqueName("作息表", tableNames, periodNames))
    }

    @Test
    fun suggest_blank_falls_back_to_default() {
        assertEquals("作息表2", TimeTableUtils.suggestUniqueName("", listOf("作息表"), periodNames, defaultName = "作息表"))
    }

    @Test
    fun suggest_avoids_both_paren_and_plain_variants() {
        // 兼容导入路径历史生成的 "名字(2)" 形态 — 顺延时两种都要避开
        val t = listOf("作息表", "作息表2", "作息表(2)")
        assertEquals("作息表3", TimeTableUtils.suggestUniqueName("作息表", t, emptyList()))
    }
}
