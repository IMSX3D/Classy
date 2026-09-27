package com.imsx3d.classy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * issue #24 导航定稿守卫 (2026-09-15 底部条改版后)。
 * 顶栏控件条已整体退场 → 日期标题画进位图, 翻页/回今天/「+N」胶囊进底部 28dp 条
 * (宽度/填满契约见 BottomBarFitTest)。本文件守: StackView 机制全清除、
 * ScrollStripService emptyHeader 透传、渲染器低对比图标风格、computeSizeDp 方向、
 * configure activity 任务栈。
 */
class TodayDateNavHeaderWiringTest {

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

    private fun findUpward(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$rel not found")
    }

    @Test
    fun `static shell draws date title inside bitmap`() {
        // 底部条定稿 (2026-09-15): 顶栏控件条已删, 日期+星期由位图头部绘制,
        // 静态壳图必须带标题 (emptyHeader 已移除; showBackToToday=false — 回今天在底部条上)
        val src = widgetSource("TodayWidget.kt").readText()
        val staticCall = src.substringAfter("Today 系静态分支")
            .substringBefore("val views = android.widget.RemoteViews(")
        assertTrue("静态壳图必须画头部 (emptyHeader 实参已移除)", !staticCall.contains("emptyHeader ="))
        assertTrue("静态壳图回今天钮必须关 (底部条承担)", staticCall.contains("showBackToToday = false"))
        assertTrue("标题数据源仍是日期 (今日→「今天 · 周X」/ 导航态→日期)",
            widgetSource("WidgetBitmapRenderers.kt").readText()
                .contains("val title = if (data.isToday)"))
    }

    @Test
    fun `stack machinery fully removed`() {
        // StackView 方案作废: 竖滑只留给内容滚动 (ListView), 不切日期
        val dir = findUpward("app/src/main/java/com/imsx3d/classy/widget")
        assertTrue("TodayStack*.kt 必须已删",
            dir.listFiles()!!.none { it.name.startsWith("TodayStack") })
        val lay = findUpward("app/src/main/res/layout")
        assertTrue("stack 布局必须已删",
            lay.listFiles()!!.none { it.name.startsWith("widget_today_stack") })
        val mf = findUpward("app/src/main/AndroidManifest.xml").readText()
        assertFalse("manifest 不得残留 TodayStackService", mf.contains("TodayStackService"))
        val src = widgetSource("TodayWidget.kt").readText()
        assertFalse("TodayWidget 不得残留 stack 管线",
            src.contains("TodayStackCore") || src.contains("TodayStackService") ||
                src.contains("setRemoteAdapter"))
        val svc = widgetSource("ScrollStripService.kt").readText()
        assertFalse("ScrollStripService 不得残留 TodayStack 引用", svc.contains("TodayStack"))
        val rdr = widgetSource("WidgetBitmapRenderers.kt").readText()
        assertFalse("渲染器不得残留 TodayStack 引用", rdr.contains("TodayStack"))
    }

    @Test
    fun `buttonless faces never draw the back-to-today text`() {
        // 用户定稿 (2026-09-13): 「回到今天」有交互语义 — 要么能点要么不存在。
        // 静态壳图 (回今天在底部条, 真实可点) 与条带 (整屏长图会每屏重复) 一律不画。
        val rdr = widgetSource("WidgetBitmapRenderers.kt").readText()
        assertTrue("todayHeaderParts 须有 showBackToToday 守卫",
            rdr.contains("showBackToToday"))
        assertTrue("守卫须挂在导航态分支 (!isToday && showBackToToday)",
            rdr.contains("!data.isToday && showBackToToday"))
        val today = widgetSource("TodayWidget.kt").readText()
        val staticShell = today.substringAfter("Today 系静态分支")
            .substringBefore("val views = android.widget.RemoteViews(")
        assertTrue("静态壳图须传 showBackToToday = false",
            staticShell.contains("showBackToToday = false"))
        val scrollBranch = today.substringAfter("var scrollData")
            .substringBefore("Log.d(TAG, \"pushTodayData scroll")
        assertTrue("overflow 条带调用不带 showBackToToday = true (缺省 false)",
            !scrollBranch.contains("showBackToToday = true"))
        val svc = widgetSource("ScrollStripService.kt").readText()
        val strip = svc.substringAfter("SCOPE_TODAY -> {")
            .substringBefore("SCOPE_TWODAY -> {")
        assertTrue("SCOPE_TODAY 条带须传 showBackToToday = false",
            strip.contains("showBackToToday = false"))
    }

    @Test
    fun `ScrollStripService plumbs emptyHeader flag through`() {
        val svc = widgetSource("ScrollStripService.kt").readText()
        assertTrue("服务端须读 EXTRA_EMPTY_HEADER",
            svc.contains("EXTRA_EMPTY_HEADER") && svc.contains("getBooleanExtra"))
        // v11: 条带 = 整张长图 (v9.1 形态回归), emptyHeader 透传给渲染器 — 条带头部随
        // 内容滚, 滚动位 0 与壳图逐像素一致。
        assertTrue("SCOPE_TODAY 条带须透传 emptyHeader 给渲染器",
            svc.contains("emptyHeader = emptyHeader"))
        val helper = widgetSource("RemoteViewsWidgetHelper.kt").readText()
        assertTrue("pushScrollable 须带 stripHeaderless 参数并 putExtra",
            helper.contains("stripHeaderless") && helper.contains("EXTRA_EMPTY_HEADER"))
        // WeekGrid 最小档 overflow 条带仍带头 - 缺省 false = 既有调用方零改动契约
        assertTrue("缺省必须 false",
            helper.contains("stripHeaderless: Boolean = false"))
    }

    @Test
    fun `computeSizeDp passes orientation hint from min width and height`() {
        // 2026-09-10 方向契约: OPTION_APPWIDGET_SIZES 在横竖双向 widget 上返回两份,
        // 纯宽度优先会取横份 — 竖放 (常态) 时 shell 按横份画 → fitXY 强拉变形。
        // computeSizeDp 必须把 MIN_WIDTH/MIN_HEIGHT (当前 cell 口径) 作为 hint 传入。
        val src = widgetSource("RemoteViewsWidgetHelper.kt").readText()
        val body = src.substringAfter("fun computeSizeDp(")
            .substringBefore("fun <T> renderAndPush(")
        assertTrue(
            "computeSizeDp 须读 OPTION_APPWIDGET_MIN_WIDTH 作方向 hint",
            body.contains("OPTION_APPWIDGET_MIN_WIDTH")
        )
        assertTrue(
            "computeSizeDp 须读 OPTION_APPWIDGET_MIN_HEIGHT 作方向 hint",
            body.contains("OPTION_APPWIDGET_MIN_HEIGHT")
        )
        assertTrue(
            "pickSizeDp 调用须带 hint 实参",
            Regex("pickSizeDp\\([^)]*hint").containsMatchIn(body)
        )
    }

    @Test
    fun `renderer nav triangle is low-contrast and glyph-free`() {
        val src = widgetSource("WidgetBitmapRenderers.kt").readText()
        val body = src.substringAfter("fun renderNavTriangle")
            .substringBefore("fun renderNavRefresh")
        assertTrue("低对比: surfaceVariant 圆角矩形底", body.contains("surfaceVariant"))
        assertTrue("低对比: onSurfaceVariant 三角图标", body.contains("onSurfaceVariant"))
        assertFalse("按钮禁圆形 (用户要三角形)", body.contains("drawCircle"))
        assertFalse("按钮禁文字 glyph (用户要三角形图标)", body.contains("drawText"))
        assertTrue("renderToday 须有 emptyHeader 参数",
            src.contains("emptyHeader: Boolean = false"))
    }

    @Test
    fun `renderer nav refresh matches triangle style and pushes as image bitmap`() {
        // 刷新按钮与三角同风格 (surfaceVariant 圆角底 + onSurfaceVariant 图标),
        // configureTodayBar 须 setImageViewBitmap 推送 (nav_today 是 ImageView)。
        val src = widgetSource("WidgetBitmapRenderers.kt").readText()
        val body = src.substringAfter("fun renderNavRefresh")
            .substringBefore("NAV_CAPSULE_H_DP")
        assertTrue("低对比: surfaceVariant 圆角矩形底", body.contains("surfaceVariant"))
        assertTrue("低对比: onSurfaceVariant 刷新图标", body.contains("onSurfaceVariant"))
        assertFalse("按钮禁文字 glyph", body.contains("drawText"))
        assertTrue("尺寸须同 NAV_BUTTON 口径",
            body.contains("NAV_BUTTON_W_DP") && body.contains("NAV_BUTTON_H_DP"))
        val today = widgetSource("TodayWidget.kt").readText()
        val barBody = today.substringAfter("fun configureTodayBar(")
            .substringBefore("internal fun bottomBarWideFits")
        assertTrue("configureTodayBar 须 setImageViewBitmap 推送刷新按钮",
            barBody.contains("setImageViewBitmap") && barBody.contains("renderNavRefresh"))
        assertFalse("nav_today 不走文字 (文字判定作废)",
            barBody.contains("today_nav_today_short"))
    }

    @Test
    fun `configure activity declares empty taskAffinity and delayed auto-finish`() {
        // 2026-09-10 真机+模拟器实证: 拖放添加时 launcher 经 ProxyActivityStarter 启动 configure,
        // 缺省 affinity 下 ActivityRecord 被丢弃 → CanceledException → add 回滚 → "小组件无法添加"。
        // 守卫: manifest 须 taskAffinity="" 且 first-add auto-finish 须延迟 (≥300ms), 禁回退一帧 post。
        val mf = findUpward("app/src/main/AndroidManifest.xml").readText()
        val block = mf.substringAfter("WidgetConfigureActivity")
            .substringBefore("/>")
        assertTrue("configure 须 android:taskAffinity=\"\"", block.contains("android:taskAffinity=\"\""))
        val cfg = widgetSource("WidgetConfigureActivity.kt").readText()
        assertTrue("first-add finish 须 postDelayed ≥300ms (一帧 post 在 launcher result 回调前送达)",
            Regex("postDelayed\\(\\s*\\{[^}]*finishWithResult", RegexOption.DOT_MATCHES_ALL)
                .containsMatchIn(cfg))
        assertFalse("禁回退到一帧 decorView.post 直 finish (竞态根因)",
            cfg.contains("decorView.post {"))
    }
}
