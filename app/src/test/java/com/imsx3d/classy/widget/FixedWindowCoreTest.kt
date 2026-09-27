package com.imsx3d.classy.widget

import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.util.ConflictLayoutEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 纯 JVM 契约测试: [FixedWindowCore] — FIXED 窗口(默认不滚动)核心算法。
 *
 * 设计文稿 widget-redesign-2026-09 §3 不变量逐条锁死:
 * 空天 / ALL_DONE 公共闸(含 fits 少课) / 进行中锚点 / 边界分钟 / 锚行超高保留 /
 * 行原子 / HEAD 过去日 / 页脚 best-effort(0 行回退) / 节次 vs 分钟口径。
 */
class FixedWindowCoreTest {

    // ---- fixtures ----

    /** ownTime 课: effectiveCourseTime 直读 startTime/endTime, 分钟完全受控。 */
    private fun course(
        id: Long,
        day: Int = 1,
        startNode: Int = 1,
        step: Int = 1,
        start: String,
        end: String
    ) = CourseEntity(
        id = id, groupId = "g$id", tableId = 1L, courseName = "课$id",
        day = day, startNode = startNode, step = step,
        startWeek = 1, endWeek = 20, color = "blue",
        ownTime = true, isIrregularTime = true, startTime = start, endTime = end
    )

    private fun entriesOf(
        vararg rows: List<CourseEntity>,
        height: Float = 38f
    ): List<FixedWindowCore.WindowEntry> {
        val spans = rows.map { ConflictLayoutEngine.weekLaneRows(it) }.flatten()
        return FixedWindowCore.entriesOf(spans, null) { height }
    }

    private fun h(m: String): Int {
        val p = m.split(":")
        return p[0].toInt() * 60 + p[1].toInt()
    }

    // ---- 不变量 1: 空天 ----

    @Test
    fun `empty rows yields empty result and no status`() {
        val r = FixedWindowCore.window(emptyList(), 198f, FixedWindowCore.Mode.TIME_WINDOW, h("10:00"))
        assertTrue(r.visible.isEmpty())
        assertEquals(FixedWindowCore.Status.NONE, r.status)
        assertFalse(r.footer)
    }

    // ---- 不变量 2: ALL_DONE 公共闸 (评审 #4: fits 少课场景也必须触发) ----

    @Test
    fun `all done fires even when everything fits`() {
        // 2 节课全下课, availH 富余(fits 分支) — 状态行仍必须出现
        val e = entriesOf(
            listOf(course(1, start = "08:00", end = "09:40")),
            listOf(course(2, startNode = 3, start = "10:00", end = "11:40"))
        )
        val r = FixedWindowCore.window(e, 198f, FixedWindowCore.Mode.TIME_WINDOW, h("21:00"))
        assertEquals(FixedWindowCore.Status.ALL_DONE, r.status)
        assertTrue(r.visible.isEmpty())
    }

    @Test
    fun `unparseable end time blocks all done and falls back to head`() {
        // ownTime 但起止时间为空串 → 分钟不可解析 → 不得谎报已结束
        val broken = course(9, start = "", end = "")
        val rows = ConflictLayoutEngine.weekLaneRows(listOf(broken))
        val e = FixedWindowCore.entriesOf(rows, null) { 38f }
        val r = FixedWindowCore.window(e, 198f, FixedWindowCore.Mode.TIME_WINDOW, h("23:00"))
        assertEquals(FixedWindowCore.Status.NONE, r.status)
        assertEquals("不可解析 → HEAD 兜底, 首行可见", 1, r.visible.size)
    }

    // ---- 不变量 3: 进行中锚点 + 边界分钟 ----

    @Test
    fun `anchor is first row still running at now`() {
        val e = entriesOf(
            listOf(course(1, start = "08:00", end = "09:40")),
            listOf(course(2, startNode = 3, start = "10:00", end = "11:40")),
            listOf(course(3, startNode = 5, start = "14:00", end = "15:40")),
            listOf(course(4, startNode = 7, start = "16:00", end = "17:40"))
        )
        val r = FixedWindowCore.window(e, 198f, FixedWindowCore.Mode.TIME_WINDOW, h("10:30"))
        assertEquals("锚点=进行中行(10:00-11:40)", 2L, r.visible.first().row.courses.single().id)
    }

    @Test
    fun `row ending exactly at now is considered ended`() {
        val e = entriesOf(
            listOf(course(1, start = "08:00", end = "10:00")),
            listOf(course(2, startNode = 3, start = "10:01", end = "11:40"))
        )
        val r = FixedWindowCore.window(e, 198f, FixedWindowCore.Mode.TIME_WINDOW, h("10:00"))
        assertEquals("endMin == now → 已结束, 锚点跳到下一行", 2L, r.visible.first().row.courses.single().id)
    }

    @Test
    fun `in progress row always lands in visible segment even if window is one row`() {
        val e = entriesOf(
            listOf(course(1, start = "08:00", end = "09:40")),
            listOf(course(2, startNode = 3, start = "10:00", end = "11:40")),
            listOf(course(3, startNode = 5, start = "14:00", end = "15:40"))
        )
        // availH 只够 1 行, 进行中在第 2 行 — 可视段必须是第 2 行本身
        val r = FixedWindowCore.window(e, 38f, FixedWindowCore.Mode.TIME_WINDOW, h("10:30"))
        assertEquals(1, r.visible.size)
        assertEquals(2L, r.visible.single().row.courses.single().id)
        assertFalse("1 行预算放不下页脚", r.footer)
    }

    @Test
    fun `anchor row taller than availH is kept not dropped`() {
        val tall = entriesOf(
            listOf(course(1, start = "08:00", end = "09:40")),
            listOf(course(2, startNode = 3, start = "10:00", end = "11:40")),
            height = 100f
        )
        val r = FixedWindowCore.window(tall, 38f, FixedWindowCore.Mode.TIME_WINDOW, h("10:30"))
        assertEquals("锚行超高 → 保留(渲染侧底裁), 不得丢行", 1, r.visible.size)
        assertEquals(2L, r.visible.single().row.courses.single().id)
    }

    // ---- 不变量 5: 行原子 (冲突行整行进整行出) ----

    @Test
    fun `conflict row is atomic unit`() {
        // 冲突行(两课并排)高=38, 其后一行 38; availH=38 → 冲突行整行保留, 第 2 行整行出局
        val conflict = listOf(
            course(1, startNode = 1, step = 2, start = "10:00", end = "11:40"),
            course(2, startNode = 2, step = 2, start = "10:00", end = "11:40")
        )
        val spans = ConflictLayoutEngine.weekLaneRows(conflict) +
            ConflictLayoutEngine.weekLaneRows(listOf(course(3, startNode = 5, start = "14:00", end = "15:40")))
        val e = FixedWindowCore.entriesOf(spans, null) { 38f }
        assertEquals("两门重叠课并一行", 2, e.size)
        val r = FixedWindowCore.window(e, 38f, FixedWindowCore.Mode.TIME_WINDOW, h("10:30"))
        assertEquals("冲突行整行保留(两课同现)", 2, r.visible.first().row.courses.size)
        assertEquals("第 2 行整行出局(不出现半行)", 1, r.visible.size)
        assertEquals("hiddenAhead 按课程数计", 1, r.hiddenAheadCourses)
    }

    // ---- 不变量 4: HEAD 过去/未来日 ----

    @Test
    fun `head mode starts from first row`() {
        val e = entriesOf(
            listOf(course(1, start = "08:00", end = "09:40")),
            listOf(course(2, startNode = 3, start = "10:00", end = "11:40"))
        )
        val r = FixedWindowCore.window(e, 38f, FixedWindowCore.Mode.HEAD, null)
        assertEquals(1L, r.visible.single().row.courses.single().id)
    }

    // ---- 不变量 8: 页脚 best-effort ----

    @Test
    fun `footer appears when rows are hidden`() {
        val e = entriesOf(
            listOf(course(1, start = "08:00", end = "09:40")),
            listOf(course(2, startNode = 3, start = "10:00", end = "11:40")),
            listOf(course(3, startNode = 5, start = "14:00", end = "15:40"))
        )
        // availH = 2 行 + 1 gap + 页脚 20 → 2 行 + 页脚, 1 课隐藏
        val r = FixedWindowCore.window(e, 38f + 10f + 38f + 20f, FixedWindowCore.Mode.HEAD, null, gapDp = 10f)
        assertEquals(2, r.visible.size)
        assertTrue(r.footer)
        assertEquals(1, r.hiddenAheadCourses)
    }

    @Test
    fun `footer dropped rather than reducing to zero rows`() {
        val e = entriesOf(
            listOf(course(1, start = "08:00", end = "09:40")),
            listOf(course(2, startNode = 3, start = "10:00", end = "11:40"))
        )
        // availH 恰好 1 行: 预留 20dp 页脚后 0 行 → 必须丢页脚保 1 行 (S 档真实场景)
        val r = FixedWindowCore.window(e, 38f, FixedWindowCore.Mode.HEAD, null)
        assertEquals("永不出现纯页脚 0 行", 1, r.visible.size)
        assertFalse(r.footer)
    }

    @Test
    fun `no footer when everything fits`() {
        val e = entriesOf(
            listOf(course(1, start = "08:00", end = "09:40")),
            listOf(course(2, startNode = 3, start = "10:00", end = "11:40"))
        )
        val r = FixedWindowCore.window(e, 198f, FixedWindowCore.Mode.HEAD, null, gapDp = 10f)
        assertEquals(2, r.visible.size)
        assertFalse("全装下 → 无页脚", r.footer)
        assertEquals(0, r.hiddenAheadCourses)
    }

    @Test
    fun `gap counted in budget`() {
        val e = entriesOf(
            listOf(course(1, start = "08:00", end = "09:40")),
            listOf(course(2, startNode = 3, start = "10:00", end = "11:40"))
        )
        // 72dp: 38+38=76 > 72 → 只 1 行; 76dp → 2 行
        assertEquals(1, FixedWindowCore.window(e, 72f, FixedWindowCore.Mode.HEAD, null).visible.size)
        assertEquals(2, FixedWindowCore.window(e, 76f, FixedWindowCore.Mode.HEAD, null).visible.size)
    }

    // ---- 评审 #5: 节次 vs 分钟口径 (调休 ownTime 与节次序倒挂) ----

    @Test
    fun `anchor uses real minutes not node order`() {
        // 调休: 节次 1 的课挪到 14:00(ownTime), 节次 3 的课照常 08:00 —
        // 行序(节次) = [14:00 课, 08:00 课]; now=08:30 进行中=08:00 课 → 锚点必须是它, 不是行首
        val moved = course(1, startNode = 1, start = "14:00", end = "15:40")
        val normal = course(2, startNode = 3, start = "08:00", end = "09:40")
        val spans = ConflictLayoutEngine.weekLaneRows(listOf(moved, normal))
        val e = FixedWindowCore.entriesOf(spans, null) { 38f }
        assertEquals("行序按节次: 挪课在前", 1L, e.first().row.courses.single().id)
        val r = FixedWindowCore.window(e, 198f, FixedWindowCore.Mode.TIME_WINDOW, h("08:30"))
        assertEquals("锚点按真实分钟", 2L, r.visible.first().row.courses.single().id)
    }

    @Test
    fun `no in-progress picks nearest upcoming by real minutes`() {
        val moved = course(1, startNode = 1, start = "14:00", end = "15:40")
        val soon = course(2, startNode = 3, start = "09:00", end = "09:40")
        val spans = ConflictLayoutEngine.weekLaneRows(listOf(moved, soon))
        val e = FixedWindowCore.entriesOf(spans, null) { 38f }
        val r = FixedWindowCore.window(e, 198f, FixedWindowCore.Mode.TIME_WINDOW, h("08:30"))
        assertEquals("无进行中 → 最近将来(09:00)而非行首(14:00)", 2L, r.visible.first().row.courses.single().id)
    }

    // ---- 行时间派生 ----

    @Test
    fun `row minutes are min start and max end over courses`() {
        val pair = entriesOf(
            listOf(
                course(1, startNode = 1, step = 2, start = "10:00", end = "11:40"),
                course(2, startNode = 2, step = 2, start = "10:00", end = "12:30")
            )
        ).single()
        assertEquals(h("10:00"), pair.startMin)
        assertEquals(h("12:30"), pair.endMin)
    }
}
