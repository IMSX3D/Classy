package com.imsx3d.classy.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 首次添加小组件后的补渲染契约。
 *
 * 2026-11-16 真机反馈：新加组件先是全透明（停在 initialLayout），手动「刷新所有小组件」
 * 后才出现内容。首次添加发生在 App 冷启动 + OEM 桌面 add/bind 流程中，桌面可能丢弃
 * provider 的 updateAppWidget，之后不再有第二个 UPDATE。
 *
 * 修复：所有 widget provider 在 ACTION_APPWIDGET_ADDED 时排一个短延迟的一次性
 * WorkManager 刷新（持久化、不依赖进程存活），桌面 attach 完成后再刷一次。
 */
class WidgetFirstAddRefreshContractTest {

    private fun source(relative: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, relative)
            if (f.isFile) return f.readText()
            dir = dir.parentFile
        }
        error("source not found: $relative")
    }

    private val widgetDir = "app/src/main/java/com/imsx3d/classy/widget"

    @Test
    fun `updater arms a delayed one-time refresh on first add`() {
        val updater = source("$widgetDir/WidgetUpdater.kt")
        assertTrue("必须有首次添加补渲染的唯一任务名", updater.contains("sleepy_widget_first_add_refresh"))
        assertTrue("必须通过 WorkManager 排一次性任务", updater.contains("OneTimeWorkRequestBuilder<WidgetUpdateWorker>"))
        assertTrue("必须监听 ACTION_APPWIDGET_ADDED", updater.contains("\"android.appwidget.action.APPWIDGET_ADDED\""))
        assertTrue("补渲染必须延迟到桌面 attach 之后", updater.contains("FIRST_ADD_RETRY_DELAY_MS"))
    }

    @Test
    fun `every widget provider routes first-add broadcast to the retry`() {
        // 基类 receiver 覆盖子变体（TodaySmall/Today/TwoDay/WeekGrid + 旧 WeekList/WeekView）
        val providers = listOf(
            "TodayWidget.kt",
            "TwoDayWidget.kt",
            "WeekGridWidgetProvider.kt",
            "WeekListWidget.kt",
            "WeekViewWidget.kt",
        )
        for (name in providers) {
            val src = source("$widgetDir/$name")
            assertTrue(
                "$name 的 onReceive 必须调用 handleFirstAddBroadcast",
                src.contains("WidgetUpdater.handleFirstAddBroadcast(context, intent)"),
            )
        }
    }
}
