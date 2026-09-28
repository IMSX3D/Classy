package com.imsx3d.classy.widget

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设计 §9 顺手修复的接线契约 (纯 JVM 源扫描, 仓库无 Robolectric):
 *  9.1 WeekList/WeekView SMALL 闸门按实际变体测量 (compact 脸自有口径)
 *  9.2 死代码 renderTodayCompact/renderTwoDayCompact 已删且不得复活
 *  9.3 WeekGrid 尺寸解析统一 computeSizeDp (禁内联镜像)
 *  9.4 世代闸推广 TwoDay/WeekList/WeekView (bump + 双分支 pushGen)
 *  9.5 WeekView 闸门 take(5) 口径 vs 条带全量口径分离
 */
class WidgetSection9FixesTest {

    private fun src(name: String): String =
        File("src/main/java/com/imsx3d/classy/widget/$name").readText()

    @Test
    fun `dead compact renderers stay deleted`() {
        val r = src("WidgetBitmapRenderers.kt")
        assertFalse("renderTodayCompact 死代码已删 (§9.2)", r.contains("renderTodayCompact"))
        assertFalse("renderTwoDayCompact 死代码已删 (§9.2)", r.contains("renderTwoDayCompact"))
    }

    @Test
    fun `generation gate wired in twoday weeklist weekview`() {
        // UI-4c：TwoDay 已改走真实行布局，世代闸搬进 CourseRowPusher（CourseRowWidget.kt）；
        // WeekList / WeekView 仍走位图管线，原样保留。
        for (name in listOf("CourseRowWidget.kt", "WeekListWidget.kt", "WeekViewWidget.kt")) {
            val s = src(name)
            // CourseRowPusher.push 是 `internal object` 里的 suspend fun（不是 private fun push），
            // 所以按"对象体"取块；位图系仍按 private fun push 取。
            val push = if (name == "CourseRowWidget.kt") {
                s.substringAfter("internal object CourseRowPusher").substringBefore("private companion object")
            } else {
                s.substringAfter("private fun push(").substringBefore("override fun onUpdate")
            }
            assertTrue("$name push 必须 bump 世代号 (§9.4)", push.contains("WidgetResizeCore.bump(id)"))
            if (name == "CourseRowWidget.kt") {
                // 新管线只有一个出口：updateAppWidget 前的 isStale 闸
                assertTrue("新管线 commit 前必须校验 isStale (§9.4)",
                    push.contains("WidgetResizeCore.isStale"))
            } else {
                assertTrue("$name 静态分支必须带 pushGen (§9.4)", push.contains("pushGen = gen"))
                // bump 之后恰好两处 push 出口 (renderAndPush + pushScrollable) 都受闸
                assertTrue(
                    "$name 两个推送出口都要带 pushGen (§9.4)",
                    Regex("pushGen = gen").findAll(push).count() == 2
                )
            }
        }
    }

    @Test
    fun `bitmap-family widgets parse size through the single computeSizeDp entry`() {
        // UI-4k 起 WeekGrid 也走真实布局，自己不再解析尺寸（由 launcher 量测）。
        // §9.3「尺寸解析必须走统一入口」这条口径现在落在**仍在画位图的**那些组件上。
        for (name in listOf(
            "TodayWidget.kt", "WeekListWidget.kt", "WeekViewWidget.kt",
            "ScrollStripService.kt", "CourseRowWidgetService.kt"
        )) {
            val s = src(name)
            assertTrue(
                "§9.3: $name 必须走 RemoteViewsWidgetHelper.computeSizeDp",
                s.contains("RemoteViewsWidgetHelper.computeSizeDp(")
            )
            assertFalse(
                "§9.3: $name 禁内联镜像 max-area SIZES 解析",
                s.contains("getParcelableArrayList")
            )
        }
    }

    @Test
    fun `weeklist gate measures compact face for small variant`() {
        val s = src("WeekListWidget.kt")
        // §9.1 (2026-09-15 改版): compact 脸闸门 = compactShownDays 单一口径 (三天窗口优先)
        assertTrue(s.contains("compactShownDays("))
        assertTrue(s.contains("if (!forceScroll && compactFace)"))
        assertFalse("两行纯文本闸门禁回流", s.contains("weekListCompactTexts"))
    }

    @Test
    fun `weekview gate caliber split face vs strip`() {
        val s = src("WeekViewWidget.kt")
        // §9.1 (2026-09-15 改版): compact 脸按 compactShownDays 实际列集测量 (三天窗口优先)
        assertTrue(s.contains("compactShownDays("))
        // §9.5: 静态脸闸门 take(5) 封顶; 条带全量口径 (无参=Int.MAX_VALUE) 仅 forceScroll 分支
        assertTrue(
            "§9.5: 闸门两处静态口径必须 maxCoursesPerDay = 5",
            Regex("maxCoursesPerDay = 5").findAll(s).count() == 2
        )
        val strip = src("ScrollStripService.kt")
        assertFalse(
            "§9.5: 条带保持全量口径 (不传 maxCoursesPerDay)",
            strip.substringAfter("SCOPE_WEEKVIEW -> {").substringBefore("else -> return")
                .contains("weekViewContentHeightDp(context, d, wDp.toFloat(), maxCoursesPerDay")
        )
    }

    @Test
    fun `weekview content height honors course cap`() {
        val r = src("WidgetBitmapRenderers.kt")
        val fn = r.substringAfter("fun weekViewContentHeightDp(").substringBefore("private fun renderWeekListCompact")
        assertTrue("§9.5: 测量函数必须有 maxCoursesPerDay 参数", fn.contains("maxCoursesPerDay: Int = Int.MAX_VALUE"))
        assertTrue("§9.5: 循环必须按封顶截断", fn.contains("day.courses.take(maxCoursesPerDay).forEachIndexed"))
    }
}
