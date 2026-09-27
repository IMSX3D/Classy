package com.imsx3d.classy.widget

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 步骤 4 契约测试: 强制滚动(实验) 开关接线 (设计 §6, 评审 #15/#25)。
 *
 * 锁死: ①AppPrefs 出厂默认 false (FIXED 为默认); ②四个 push 站点全部读开关
 * (不得回退成硬编码 false); ③编辑页节 WeekGrid 族隐藏 + 开启前确认弹窗
 * (取消不写入=回弹); ④翻转立即全量重推。
 */
class WidgetScrollToggleTest {

    private fun src(path: String): String =
        File("src/main/java/com/imsx3d/classy/$path").readText()

    @Test
    fun `app prefs ships scroll disabled by default`() {
        val s = src("util/AppPrefs.kt")
        assertTrue("缺 key", s.contains("KEY_WIDGET_SCROLL_ENABLED = \"widget_scroll_enabled\""))
        assertTrue("默认必须 false = FIXED",
            s.contains("getBoolean(KEY_WIDGET_SCROLL_ENABLED, false)"))
    }

    @Test
    fun `all four push sites read the per-widget toggle`() {
        for (f in listOf("TodayWidget.kt", "TwoDayWidget.kt", "WeekListWidget.kt", "WeekViewWidget.kt")) {
            val s = src("widget/$f")
            assertTrue("$f 未读本实例滚动开关", s.contains("WidgetScrollStore.isScrollEnabled(context, id)"))
            assertFalse("$f 残留全局读", s.contains("AppPrefs.isWidgetScrollEnabled(context)"))
            assertFalse("$f 残留硬编码 forceScroll=false", s.contains("val forceScroll = false"))
        }
    }

    @Test
    fun `all receivers clear per-widget scroll on delete`() {
        for (f in listOf("TodayWidget.kt", "TwoDayWidget.kt", "WeekListWidget.kt", "WeekViewWidget.kt", "WeekGridWidgetProvider.kt")) {
            val s = src("widget/$f")
            assertTrue("$f onDeleted 未清滚动位", s.contains("WidgetScrollStore.remove(context, id)"))
        }
    }

    @Test
    fun `edit section hides for weekgrid family and confirms before enabling`() {
        val s = src("ui/screen/widget/WidgetEditScrollSection.kt")
        assertTrue("WeekGrid 族未隐藏", s.contains("contains(\"WeekGrid\")"))
        assertTrue("开启未走确认弹窗", s.contains("showConfirm = true"))
        assertTrue("确认才写入", s.contains("scope.onScrollEnabledChange(true)"))
        assertTrue("弹窗正文缺风险文案", s.contains("widget_scroll_dialog_body"))
        val screen = src("ui/screen/widget/WidgetEditScreen.kt")
        assertTrue("节未挂编辑页", screen.contains("WidgetEditScrollSection"))
    }

    @Test
    fun `toggle flip triggers full widget re-push`() {
        val vm = src("widget/WidgetEditViewModel.kt")
        assertTrue("翻转未写本实例位", vm.contains("WidgetScrollStore.setScrollEnabled(ctx, widgetId, v)"))
        assertTrue("状态未读本实例位", vm.contains("WidgetScrollStore.isScrollEnabled(ctx, widgetId)"))
        assertTrue("族信息未注入", vm.contains("info?.provider?.className ?: info?.configure?.className"))
        val body = vm.substringAfter("fun setScrollEnabled")
        assertTrue("setScrollEnabled 未调 notifyDataChanged",
            body.contains("WidgetUpdater.notifyDataChanged(ctx)"))
    }
}
