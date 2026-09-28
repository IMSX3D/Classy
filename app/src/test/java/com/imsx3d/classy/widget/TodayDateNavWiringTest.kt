package com.imsx3d.classy.widget

import com.imsx3d.classy.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate

/**
 * 每日课程小组件日期导航 — wiring 级单测。
 *
 * 仓库无 Robolectric, 这里覆盖:
 * 1) Action 常量字符串稳定契约 + navDelta 算术 (R2 PendingIntent 带参
 *    在 Android 端只靠这两个常量 + companion 方法做反 disambiguation; 在 JVM 端以
 *    纯函数形式断言);
 * 2) WidgetData.isToday 默认值 + 标题头渲染 (todayHeaderParts) 在 JVM 上的字符串分支
 *    (R1/R3/R6: 今日 vs 导航态显示差异);
 * 3) 源码级守卫 (沿用 [[WidgetBitmapLifecycleTest]] 风格): TodayWidget.kt 必须接
 * 3) 源码级守卫: TodayWidget 管线 + 布局白名单 + emptyHeader/stripHeaderless 透传; StackView 机制全清除 + 低对比三角按钮渲染守卫在 TodayDateNavHeaderWiringTest。
 */
class TodayDateNavWiringTest {

    // ---- Action 常量 + delta 映射 ----

    @Test
    fun `action constants are distinct and well-formed`() {
        val a = TodayWidgetReceiver.ACTION_PREV_DAY
        val b = TodayWidgetReceiver.ACTION_NEXT_DAY
        val c = TodayWidgetReceiver.ACTION_RESET_DAY
        assertTrue("action 须含 widget 包路径", a.startsWith("com.imsx3d.classy.widget"))
        assertTrue("action 须含 widget 包路径", b.startsWith("com.imsx3d.classy.widget"))
        assertTrue("action 须含 widget 包路径", c.startsWith("com.imsx3d.classy.widget"))
        assertFalse(a == b); assertFalse(a == c); assertFalse(b == c)
    }

    @Test
    fun `navDelta maps prev next and unknown actions`() {
        assertEquals(-1L, TodayWidgetReceiver.navDelta(TodayWidgetReceiver.ACTION_PREV_DAY))
        assertEquals(+1L, TodayWidgetReceiver.navDelta(TodayWidgetReceiver.ACTION_NEXT_DAY))
        assertNull(TodayWidgetReceiver.navDelta(TodayWidgetReceiver.ACTION_RESET_DAY))
        assertNull(TodayWidgetReceiver.navDelta(null))
        assertNull(TodayWidgetReceiver.navDelta("android.appwidget.action.APPWIDGET_UPDATE"))
    }

    // ---- R2 PendingIntent requestCode 反碰撞: (widgetId, zoneOrdinal) 全空间唯一 ----

    // ---- WidgetData.isToday 字段 (R6 标题渲染需) ----

    @Test
    fun `WidgetData isToday defaults to true and can be overridden`() {
        val d = WidgetData(date = LocalDate.of(2026, 9, 9), courses = emptyList(),
            timeJson = "", hasTable = false)
        assertTrue("默认 isToday=true; 已有调用方不加这个字段也能正确渲染今日态", d.isToday)
        val nav = d.copy(isToday = false)
        assertFalse(nav.isToday)
    }

    // ---- R3: todayHeaderParts 渲染字符串分支 ----

    @Test
    fun `todayHeaderParts today state shows literal title plus optional right date`() {
        val data = WidgetData(date = LocalDate.of(2026, 9, 9), courses = emptyList(),
            timeJson = "", hasTable = false, isToday = true)
        val withDate = WidgetBitmapRenderers.todayHeaderParts(
            data = data, dayName = "周三",
            showDate = true,
            resolve = { resId -> resNames.getValue(resId) }
        )
        assertEquals("today_today · 周三", withDate.title)
        assertEquals("9/9", withDate.rightText)
        assertFalse("today 右侧不是 action", withDate.rightIsAction)

        val withoutDate = withDate.let {
            WidgetBitmapRenderers.todayHeaderParts(
                data = data, dayName = "周三", showDate = false,
                resolve = { resId -> resNames.getValue(resId) }
            )
        }
        assertEquals("today_today · 周三", withoutDate.title)
        assertNull("showDate=false 时 today 右侧隐藏", withoutDate.rightText)
    }

    @Test
    fun `todayHeaderParts navigated state shows date plus dow and back-to-today affordance`() {
        val data = WidgetData(date = LocalDate.of(2026, 9, 10), courses = emptyList(),
            timeJson = "", hasTable = false, isToday = false)
        val header = WidgetBitmapRenderers.todayHeaderParts(
            data = data, dayName = "周四", showDate = true,
            resolve = { resId -> resNames.getValue(resId) }
        )
        assertEquals("9/10 · 周四", header.title)
        assertEquals("today_nav_back_to_today", header.rightText)
        assertTrue("导航态右侧必为可点击的 action 文本", header.rightIsAction)

        // showDate 不会关掉导航态右侧 (可发现性: 即便用户关了日期显示, 仍须能看到「回到今天」)
        val headerNoDate = WidgetBitmapRenderers.todayHeaderParts(
            data = data, dayName = "周四", showDate = false,
            resolve = { resId -> resNames.getValue(resId) }
        )
        assertEquals("today_nav_back_to_today", headerNoDate.rightText)
    }

    @Test
    fun `navigated state never renders date twice when back affordance is hidden`() {
        val data = WidgetData(date = LocalDate.of(2026, 9, 14), courses = emptyList(),
            timeJson = "", hasTable = false, isToday = false)
        val header = WidgetBitmapRenderers.todayHeaderParts(
            data = data, dayName = "周一", showDate = true,
            resolve = { resId -> resNames.getValue(resId) },
            showBackToToday = false
        )
        assertEquals("9/14 · 周一", header.title)
        assertNull("导航态日期已在标题中, 不得再画第二份 9/14", header.rightText)
    }

    private val resNames = mapOf(
        R.string.today_today to "today_today",
        R.string.today_nav_back_to_today to "today_nav_back_to_today"
    )

    // ---- 源码级守卫: TodayWidget.kt 确实接上了导航管线 ----

    private fun widgetSource(name: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, "app/src/main/java/com/imsx3d/classy/widget/$name")
            if (f.exists()) return f
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

    @Test
    fun `TodayWidget source loadDataSync resolves nav target`() {
        val src = widgetSource("TodayWidget.kt").readText()
        assertTrue("loadDataSync 必须解析 nav 状态为当前 target",
            src.contains("TodayDateNavStore.target"))
        assertTrue("loadDataSync 必须把 target 当作数据日期",
            src.contains("WidgetData(date = target") || src.contains("date = target"))
    }

    @Test
    fun `RemoteViewsWidgetHelper renderAndPush and pushScrollable expose configureViews hook`() {
        val src = widgetSource("RemoteViewsWidgetHelper.kt").readText()
        assertTrue("renderAndPush 必须有 configureViews 钩子接 Today 导航 zone",
            src.contains("configureViews"))
        // 两个推送路径 (static + scrollable) 都必须支持
        assertTrue("pushScrollable 也必须有 configureViews 钩子 (scrollable 今日态也需导航)",
            src.substringAfter("fun pushScrollable").contains("configureViews"))
    }

    @Test
    fun `WidgetBindingStore-onDeleted cleanup has matching nav store cleanup`() {
        // 镜像 WidgetBindingStore 的清理纪律: onDeleted 收尾必须包括今日导航 store.remove
        val src = widgetSource("TodayWidget.kt").readText()
        val onDeletedBlock = src.substringAfter("override fun onDeleted")
        assertTrue("onDeleted 必须调 WidgetBindingStore.remove (兼容旧契约)",
            onDeletedBlock.contains("WidgetBindingStore.remove"))
        assertTrue("onDeleted 必须同时清今日导航状态",
            onDeletedBlock.contains("TodayDateNavStore.remove"))
    }

    @Test
    fun `push commit points guard against stale generations`() {
        // resize 稳定性: 渲染在后台协程, resize 拖拽期间系统连发 OPTIONS_CHANGED。
        // 旧尺寸任务若后完成会覆盖新内容且无人纠正 → commit 前必须校验世代号。
        val helper = widgetSource("RemoteViewsWidgetHelper.kt").readText()
        assertTrue("pushScrollable commit 前须校验世代号 (WidgetResizeCore.isStale)",
            helper.substringAfter("fun pushScrollable").contains("WidgetResizeCore.isStale"))
        assertTrue("renderAndPush commit 前也须校验 (WeekGrid 最小档 overflow 同样受益)",
            helper.substringAfter("fun renderAndPush").contains("WidgetResizeCore.isStale"))
        // UI-4c：Today 自己不再直接 bump —— 三个触发点统一走 push() → CourseRowPusher.push，
        // 世代闸随之搬进 CourseRowPusher（渲染前 bump + commit 前 isStale，成对保留）。
        val today = widgetSource("TodayWidget.kt").readText()
        val pusher = widgetSource("CourseRowWidget.kt").readText()
        listOf("onUpdate", "onAppWidgetOptionsChanged", "handleNav").forEach { trigger ->
            val body = today.substringAfter("fun $trigger").take(1400)
            assertTrue("Today 的 $trigger 必须走 push()", body.contains("push(context"))
        }
        assertTrue("Today 的 push 必须委托 CourseRowPusher(SCOPE_TODAY)",
            today.contains("CourseRowPusher.push(") &&
                today.contains("CourseRowWidgetService.CourseRowFactory.SCOPE_TODAY"))
        assertTrue("世代闸必须在 CourseRowPusher 里：渲染前 bump",
            pusher.contains("WidgetResizeCore.bump(id)"))
        assertTrue("世代闸必须在 CourseRowPusher 里：commit 前 isStale",
            pusher.contains("WidgetResizeCore.isStale"))
        val svc = widgetSource("ScrollStripService.kt").readText()
        assertTrue("条带工厂 onDataSetChanged 也须按世代丢弃过期重算",
            svc.contains("WidgetResizeCore"))
    }

    @Test
    fun `onDeleted clears resize generation for widget id reuse`() {
        val today = widgetSource("TodayWidget.kt").readText()
        assertTrue("onDeleted 必须清世代号 (widget id 被系统复用后旧世代不得干扰新实例)",
            today.contains("WidgetResizeCore.remove"))
    }

    @Test
    fun `WeekGrid goes real-layout and never touches nav zones (scope guard)`() {
        // issue #24 范围铁律: 日期导航只在每日小组件。UI-4k 起 WeekGrid 也走真实布局，
        // 但依旧不得引用导航布局/点击区 — 守卫 WeekGrid 侧零沾染。
        val grid = widgetSource("WeekGridWidgetProvider.kt").readText()
        assertFalse("WeekGrid 不得引用 widget_today_nav_static",
            grid.contains("widget_today_nav_static"))
        assertFalse("WeekGrid 不得引用 widget_scroll_today_nav",
            grid.contains("widget_scroll_today_nav"))
        assertFalse("WeekGrid 不得引用 configureTodayNav (旧顶栏已退场)",
            grid.contains("configureTodayNav"))
        // UI-4k 起 WeekGrid 也走真实布局（不再复用 pushTodayData 位图管线），
        // 所以正向断言改成"必须走真实布局卡片 + 集合适配器"，三条负向（导航零沾染）保持不变。
        assertTrue("WeekGrid 必须走真实布局卡片 (widget_weekgrid_card)",
            grid.contains("R.layout.widget_weekgrid_card"))
        assertTrue("WeekGrid 行由集合适配器提供 (setRemoteAdapter + wg_rows)",
            grid.contains("setRemoteAdapter(R.id.wg_rows"))
    }

    private fun findUpward(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("\$rel not found")
    }

}
