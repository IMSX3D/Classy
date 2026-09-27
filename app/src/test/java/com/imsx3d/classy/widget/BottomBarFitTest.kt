package com.imsx3d.classy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 底部导航条定稿 (2026-09-15 用户定稿) 守卫:
 * 翻页键/回今天/「+N」胶囊从顶栏迁到底部 28dp 固定条, 顶栏只剩位图内日期标题。
 * 覆盖: 胶囊宽度判定 (纯函数) / 填满档 (footerH=0 不再二次预留) /
 * 布局契约 (底部条 + 元素顺序) / 旧顶栏档位机制退场。
 */
class BottomBarFitTest {

    private fun widgetSource(name: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/com/imsx3d/classy/widget/$name")
            if (f.exists()) return f.readText()
            dir = dir.parentFile
        }
        error("$name not found")
    }

    private fun layoutFile(name: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/res/layout/$name")
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$name not found")
    }

    // ---- 宽档布局选档判定 (纯函数; 2026-09-14 胶囊恒画改版: 判定只选布局, 不再隐藏胶囊) ----

    @Test
    fun `wide layout fits today-state at 2x2 width`() {
        // 今日态: pad20 + 胶囊36 + 间隙4 + ◀▶80 = 140 ≤ 148 (荣耀 2×2 实测宽)
        assertTrue(TodayWidgetReceiver.bottomBarWideFits(148, isToday = true, navEnabled = true))
        assertTrue(TodayWidgetReceiver.bottomBarWideFits(140, isToday = true, navEnabled = true))
        assertFalse(TodayWidgetReceiver.bottomBarWideFits(139, isToday = true, navEnabled = true))
    }

    @Test
    fun `narrow nav-state falls to compact layout, capsule never hidden`() {
        // 导航态宽档门槛: pad20 + 胶囊36 + 间隙4 + 三钮132 = 192;
        // 148 放不下 → 换紧凑档 (12+36+4+96=148), 胶囊仍恒画 — 用户定稿: 挤也画
        assertTrue(TodayWidgetReceiver.bottomBarWideFits(192, isToday = false, navEnabled = true))
        assertFalse(TodayWidgetReceiver.bottomBarWideFits(148, isToday = false, navEnabled = true))
    }

    @Test
    fun `wide layout always fits no-nav variant and unknown width`() {
        assertTrue(TodayWidgetReceiver.bottomBarWideFits(60, isToday = true, navEnabled = false))
        assertTrue(TodayWidgetReceiver.bottomBarWideFits(0, isToday = false, navEnabled = true))
        assertTrue(TodayWidgetReceiver.bottomBarWideFits(-1, isToday = true, navEnabled = true))
    }

    // ---- 填满档 (footerH=0): 装得下几行画几行, 隐藏课只点亮胶囊 ----

    private fun rows(n: Int): List<FixedWindowCore.WindowEntry> {
        val courses = (1..n).map { i ->
            val s = 480 + (i - 1) * 100
            com.imsx3d.classy.data.entity.CourseEntity(
                id = i.toLong(), groupId = "g$i", tableId = 1L, courseName = "c$i",
                day = 1, startNode = (i - 1) * 2 + 1, step = 1,
                startWeek = 1, endWeek = 20, color = "blue",
                ownTime = true, isIrregularTime = true,
                startTime = "%02d:%02d".format(s / 60, s % 60),
                endTime = "%02d:%02d".format((s + 100) / 60, (s + 100) % 60)
            )
        }
        val spans = courses.flatMap { com.imsx3d.classy.util.ConflictLayoutEngine.weekLaneRows(listOf(it)) }
        return FixedWindowCore.entriesOf(spans, null) { 38f }
    }

    @Test
    fun `fill-to-max shows two rows when two fit - no double deduction`() {
        // 用户翻车原话: 「本来有 4 节课, 能显示两节却只显示一节 + 还有三节」—
        // availH=86 容 2 行 (38+10+38), 旧档预留页脚 20 只剩 1 行。新档必须画满 2 行。
        val r = FixedWindowCore.window(rows(4), availH = 86f, mode = FixedWindowCore.Mode.HEAD, nowMin = null, gapDp = 10f, footerH = 0f)
        assertEquals("装得下 2 行必须画 2 行", 2, r.visible.size)
        assertTrue("有隐藏课 → 点亮胶囊", r.footer)
        assertEquals(2, r.hiddenAheadCourses)
    }

    @Test
    fun `fill-to-max no capsule when everything fits`() {
        val r = FixedWindowCore.window(rows(2), availH = 86f, mode = FixedWindowCore.Mode.HEAD, nowMin = null, gapDp = 10f, footerH = 0f)
        assertEquals(2, r.visible.size)
        assertFalse(r.footer)
        assertEquals(0, r.hiddenAheadCourses)
    }

    @Test
    fun `fill-to-max keeps anchor row and capsule when even one row overflows`() {
        val r = FixedWindowCore.window(rows(3), availH = 30f, mode = FixedWindowCore.Mode.HEAD, nowMin = null, gapDp = 10f, footerH = 0f)
        assertEquals("forceFirst 锚行保底", 1, r.visible.size)
        assertTrue(r.footer)
        assertEquals(2, r.hiddenAheadCourses)
    }

    @Test
    fun `twoday default footerH unchanged`() {
        // 缺省 footerH=FOOTER_H_DP: TwoDay 旧「预留页脚重算」语义逐字节不变
        val r = FixedWindowCore.window(rows(4), availH = 86f, mode = FixedWindowCore.Mode.HEAD, nowMin = null, gapDp = 10f)
        assertEquals("预留 20dp 页脚后只容 1 行 (旧语义)", 1, r.visible.size)
        assertTrue(r.footer)
    }

    // ---- 布局契约: 底部条 ----

    @Test
    fun `nav static layout declares bottom bar with capsule and buttons`() {
        val xml = layoutFile("widget_today_nav_static.xml").readText()
        assertTrue("缺底部条容器 widget_today_bar", xml.contains("widget_today_bar"))
        assertTrue("条必须贴底", xml.contains("android:layout_gravity=\"bottom\""))
        assertTrue("条高须 28dp (NAV_BAR_H_DP 口径)", xml.contains("28dp"))
        assertTrue("缺胶囊 widget_nav_more", xml.contains("widget_nav_more"))
        assertTrue("按钮须 40dp/24dp", xml.contains("40dp") && xml.contains("24dp"))
        assertEquals("须单个 weight=1 spacer (Space 无 @RemoteView 禁用)",
            1, Regex("layout_weight=\"1\"").findAll(xml).count())
        // 底部条子视图顺序: capsule < prev < today < next (用户定稿: 胶囊左下角, 三键左到右)
        val iMore = xml.indexOf("widget_nav_more")
        val iPrev = xml.indexOf("widget_today_nav_prev")
        val iToday = xml.indexOf("widget_today_nav_today")
        val iNext = xml.indexOf("widget_today_nav_next")
        assertTrue("底部条顺序须 capsule<prev<today<next",
            iMore in 1 until iPrev && iPrev < iToday && iToday < iNext)
        assertFalse("禁裸 <View> (RemoteViews 白名单)", Regex("<View\\b").containsMatchIn(xml))
    }

    @Test
    fun `compact layout keeps capsule and shrinks buttons`() {
        val xml = layoutFile("widget_today_nav_static_compact.xml").readText()
        assertTrue("缺底部条容器 widget_today_bar", xml.contains("widget_today_bar"))
        assertTrue("条必须贴底", xml.contains("android:layout_gravity=\"bottom\""))
        assertTrue("条高须 28dp (与宽档同高, 内容预算同扣)", xml.contains("28dp"))
        assertTrue("缺胶囊 widget_nav_more", xml.contains("widget_nav_more"))
        assertTrue("紧凑档按钮须 32×24dp", xml.contains("32dp") && xml.contains("24dp"))
        assertEquals("须单个 weight=1 spacer",
            1, Regex("layout_weight=\"1\"").findAll(xml).count())
        val iMore = xml.indexOf("widget_nav_more")
        val iPrev = xml.indexOf("widget_today_nav_prev")
        val iToday = xml.indexOf("widget_today_nav_today")
        val iNext = xml.indexOf("widget_today_nav_next")
        assertTrue("底部条顺序须 capsule<prev<today<next",
            iMore in 1 until iPrev && iPrev < iToday && iToday < iNext)
        assertFalse("禁裸 <View> (RemoteViews 白名单)", Regex("<View\\b").containsMatchIn(xml))
    }

    @Test
    fun `capsule is non-clickable in both bar layouts`() {
        for (name in listOf("widget_today_nav_static.xml", "widget_today_nav_static_compact.xml")) {
            val xml = layoutFile(name).readText()
            val capsule = xml.substringAfter("widget_nav_more").substringBefore("/>")
            assertFalse("$name 胶囊不得声明 clickable", capsule.contains("clickable"))
        }
    }

    @Test
    fun `old topbar machinery fully removed`() {
        val src = widgetSource("TodayWidget.kt")
        assertFalse("NavTier 档位机制必须退场 (注释提及不算)", src.contains("NavTier."))
        assertFalse("navHeaderTier 必须退场", src.contains("navHeaderTier"))
        assertFalse("configureTodayNav 必须退场", src.contains("fun configureTodayNav"))
        assertFalse("fitsNavTodayFourChar 禁回流", src.contains("fitsNavTodayFourChar"))
        assertFalse("静态壳图不再 emptyHeader 留白", src.contains("emptyHeader = true"))
        val dir = File(layoutFile("widget_today_nav_static.xml").parent)
        assertFalse("顶栏页脚布局必须已删",
            File(dir, "widget_today_nav_static_footer.xml").exists())
    }

    @Test
    fun `push wiring carries bottom bar and fill-to-max budget`() {
        val src = widgetSource("TodayWidget.kt")
        assertTrue("窗口须走填满档", src.contains("footerH = 0f"))
        assertTrue("内容预算须扣底部条高", src.contains("TodayRowGeometry.NAV_BAR_H_DP"))
        assertTrue("push 必须调 configureTodayBar", src.contains("configureTodayBar(context, views, id, receiverClass, data, hidden, wDp)"))
        assertTrue("最小档复用管线也走底部条", src.contains("configureTodayBar(context, v, id, null, data, hidden, wDp)"))
        assertTrue("两条静态路径都须走布局选档",
            src.contains("todayBarLayout(wDp, data.isToday, navEnabled = false)") &&
            src.contains("todayBarLayout(wDp, data.isToday, navEnabled = true)"))
        val bar = src.substringAfter("fun configureTodayBar(")
            .substringBefore("internal fun bottomBarWideFits")
        assertTrue("胶囊须恒画: 有隐藏课 +N, 上完/无隐藏课 +0",
            bar.contains("else \"+0\""))
        assertFalse("ALL_DONE 长状态行禁回流 (0 胶囊是唯一结束标记)",
            bar.contains("widget_status_all_done"))
        assertFalse("胶囊不可挂点击 PI (用户定稿: 纯指示不可点)",
            bar.contains("widget_nav_more, footerConfigurePi"))
        assertTrue("今日态须隐藏回今天钮 (要么能点要么不存在)",
            bar.contains("if (data.isToday) android.view.View.GONE else android.view.View.VISIBLE"))
        assertTrue("条底色须与卡片同色", bar.contains("R.id.widget_today_bar, \"setBackgroundColor\""))
    }

    @Test
    fun `scroll branch forces back to today when controls gone`() {
        // 用户定稿 ②: 滚动模式缩到最小 = 无控件 → 导航态清零强制当天; 再拖大也停今天
        val src = widgetSource("TodayWidget.kt")
        val scroll = src.substringAfter("var scrollData")
            .substringBefore("Log.d(TAG, \"pushTodayData scroll")
        assertTrue("overflow 分支必须清导航态", scroll.contains("TodayDateNavStore.remove(context, id)"))
        assertTrue("清后必须重载今天数据", scroll.contains("scrollData = loadDataSync(context, id)"))
    }

    @Test
    fun `capsule renderer is themed pill with text`() {
        val src = widgetSource("WidgetBitmapRenderers.kt")
        val body = src.substringAfter("fun renderNavCapsule")
            .substringBefore("data class TodayNavHeaderColors")
        assertTrue("胶囊底 surfaceVariant", body.contains("surfaceVariant"))
        assertTrue("胶囊字 onSurfaceVariant", body.contains("onSurfaceVariant"))
        assertTrue("胶囊画文本", body.contains("drawText"))
        assertTrue("宽度自适应下限 30dp", body.contains("NAV_CAPSULE_MIN_W_DP"))
    }
}
