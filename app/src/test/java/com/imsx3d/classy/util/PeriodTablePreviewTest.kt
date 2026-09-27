package com.imsx3d.classy.util

import com.imsx3d.classy.data.entity.CourseEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue#40 设计 §5.2/§5.3 纯函数预览契约:
 * 新旧两份 timeJson 对同一批节次编号解析出 旧时间→新时间;
 * 自定义时间(ownTime)课程不参与解释(设计 §2-7);
 * 节次编号永远不被改写(§9.1 节次绑定不重算)。
 */
class PeriodTablePreviewTest {

    private val oldJson = """
        [{"node":1,"start":"08:00","end":"08:45"},
         {"node":2,"start":"08:55","end":"09:40"},
         {"node":3,"start":"10:00","end":"10:45"},
         {"node":4,"start":"10:55","end":"11:40"}]
    """.trimIndent()

    /** 第一节后移 20 分钟, 其余不变 */
    private val newJson = """
        [{"node":1,"start":"08:20","end":"09:05"},
         {"node":2,"start":"08:55","end":"09:40"},
         {"node":3,"start":"10:00","end":"10:45"},
         {"node":4,"start":"10:55","end":"11:40"}]
    """.trimIndent()

    private fun course(
        name: String,
        startNode: Int = 1,
        step: Int = 2,
        ownTime: Boolean = false
    ) = CourseEntity(
        id = 0,
        groupId = "g-$name",
        tableId = 1,
        courseName = name,
        day = 1,
        startNode = startNode,
        step = step,
        startWeek = 1,
        endWeek = 16,
        color = "#FF6750A4",
        ownTime = ownTime,
        startTime = if (ownTime) "07:30" else "",
        endTime = if (ownTime) "09:00" else ""
    )

    @Test
    fun changed_node_shows_old_to_new_time_for_bound_courses() {
        val courses = listOf(course("高数", startNode = 1, step = 2))
        val preview = TimeTableUtils.previewPeriodTableChange(oldJson, newJson, courses)
        assertEquals(1, preview.changedCourses.size)
        val change = preview.changedCourses.first()
        assertEquals("高数", change.courseName)
        assertEquals("08:00-09:40", change.oldTime)
        assertEquals("08:20-09:40", change.newTime)
        assertEquals(0, preview.unchangedCount)
    }

    @Test
    fun course_on_untouched_nodes_counts_as_unchanged() {
        val courses = listOf(
            course("高数", startNode = 1, step = 2),
            course("英语", startNode = 3, step = 2)
        )
        val preview = TimeTableUtils.previewPeriodTableChange(oldJson, newJson, courses)
        assertEquals(1, preview.changedCourses.size)
        assertEquals("高数", preview.changedCourses.first().courseName)
        assertEquals(1, preview.unchangedCount)
    }

    @Test
    fun ownTime_courses_are_excluded_from_interpretation() {
        val courses = listOf(
            course("自定义课", startNode = 1, step = 2, ownTime = true),
            course("高数", startNode = 1, step = 1)
        )
        val preview = TimeTableUtils.previewPeriodTableChange(oldJson, newJson, courses)
        assertEquals(1, preview.changedCourses.size)
        assertTrue(preview.changedCourses.none { it.courseName == "自定义课" })
        // 自定义课不计入"无变化"——它根本不参与解释
        assertEquals(0, preview.unchangedCount)
    }

    @Test
    fun identical_timejson_yields_no_changes() {
        val courses = listOf(course("高数"), course("英语", startNode = 3, step = 1))
        val preview = TimeTableUtils.previewPeriodTableChange(oldJson, oldJson, courses)
        assertTrue(preview.changedCourses.isEmpty())
        assertEquals(2, preview.unchangedCount)
    }

    @Test
    fun missing_node_in_old_table_reports_null_old_time() {
        val sparseOld = """[{"node":1,"start":"08:00","end":"08:45"}]"""
        val courses = listOf(course("大四专选", startNode = 3, step = 1))
        val preview = TimeTableUtils.previewPeriodTableChange(sparseOld, newJson, courses)
        assertEquals(1, preview.changedCourses.size)
        assertNull(preview.changedCourses.first().oldTime)
        assertEquals("10:00-10:45", preview.changedCourses.first().newTime)
    }

    @Test
    fun node_numbers_are_never_rewritten_by_preview() {
        // §9.1: 节次绑定不重算 — 预览产物里 startNode/step 必须原样
        // (第 2-4 节新旧一致, 故用整批跨节次的课让它落在变化区间里)
        val courses = listOf(course("高数", startNode = 1, step = 3))
        val preview = TimeTableUtils.previewPeriodTableChange(oldJson, newJson, courses)
        val change = preview.changedCourses.firstOrNull()
            ?: error("跨变化节次的课必须出现在预览里")
        assertEquals(1, change.startNode)
        assertEquals(3, change.step)
    }

    @Test
    fun empty_courses_give_empty_preview() {
        val preview = TimeTableUtils.previewPeriodTableChange(oldJson, newJson, emptyList())
        assertTrue(preview.changedCourses.isEmpty())
        assertEquals(0, preview.unchangedCount)
    }

    // ========== 2026-09-16 用户实测报障: 改早八(第1节)时间, 预览报 0 变化 41 不变 ==========

    /** 用户场景: 第1节结束时间改掉(拉长早八), 第一节的课必须全部列出旧→新 */
    @Test
    fun user_scenario_editing_first_period_lists_all_first_period_courses() {
        // 新表: 第1节 08:00-08:45 → 08:00-09:20 (用户拉长早八), 其余节原样
        val edited = """
            [{"node":1,"start":"08:00","end":"09:20"},
             {"node":2,"start":"08:55","end":"09:40"},
             {"node":3,"start":"10:00","end":"10:45"},
             {"node":4,"start":"10:55","end":"11:40"}]
        """.trimIndent()
        val courses = listOf(
            course("高数", startNode = 1, step = 1),
            course("英语", startNode = 1, step = 2),
            course("体育", startNode = 3, step = 2)
        )
        val preview = TimeTableUtils.previewPeriodTableChange(oldJson, edited, courses)
        assertEquals("第一节相关课必须全部报变化", 2, preview.changedCourses.size)
        val names = preview.changedCourses.map { it.courseName }.sorted()
        assertEquals(listOf("英语", "高数"), names)
        val gaoshu = preview.changedCourses.first { it.courseName == "高数" }
        assertEquals("08:00-08:45", gaoshu.oldTime)
        assertEquals("08:00-09:20", gaoshu.newTime)
        assertEquals(1, preview.unchangedCount)
    }

    /** 41 门课全量走一遍 — 真实数据规模(连堂 1-2 节为主)下第一节变化必须全部被检出 */
    @Test
    fun large_batch_first_period_change_not_swallowed() {
        val edited = oldJson.replace("08:45", "09:20")
        // 用户真实形态: 40 门全是 1-2 节连堂早八 + 1 门 3-4 节课
        val courses = (1..40).map { course("课$it", startNode = 1, step = 2) } +
            listOf(course("体育", startNode = 3, step = 2))
        val preview = TimeTableUtils.previewPeriodTableChange(oldJson, edited, courses)
        assertEquals("40 门早八连堂课必须全部报变化", 40, preview.changedCourses.size)
        assertEquals(1, preview.unchangedCount)
    }

    /** 周次类型(0/1/2/3)不影响时间解释 — 双周课的早八同样要报变化 */
    @Test
    fun week_type_does_not_block_time_interpretation() {
        val edited = oldJson.replace("08:45", "09:20")
        val base = course("大物", startNode = 1, step = 2)
        val variants = listOf(0, 1, 2, 3).map { t -> base.copy(type = t) }
        val preview = TimeTableUtils.previewPeriodTableChange(oldJson, edited, variants)
        assertEquals("四种周次类型的早八课全部要报变化", 4, preview.changedCourses.size)
    }

    /** 编辑页面 newTimeJson 由 buildTimeJsonFromRows 生成 — 往返后预览必须仍能检出 */
    @Test
    fun roundtrip_through_buildTimeJsonFromRows_preserves_change_detection() {
        val editedRows = TimeTableUtils.parseTimeSlotRows(oldJson).map {
            if (it.node == 1) it.copy(end = "09:20") else it
        }
        val editedJson = TimeTableUtils.buildTimeJsonFromRows(editedRows)
        val courses = listOf(course("高数", startNode = 1, step = 2))
        val preview = TimeTableUtils.previewPeriodTableChange(oldJson, editedJson, courses)
        assertEquals(1, preview.changedCourses.size)
        // 连堂课展示对 = 首节开始-末节结束(1-2 节 → 08:00-09:40, 末节未动)
        assertEquals("08:00-09:40", preview.changedCourses.first().newTime)
        assertEquals("变化节次必须精确指向第 1 节", listOf(1), preview.changedCourses.first().changedNodes)
    }
}
