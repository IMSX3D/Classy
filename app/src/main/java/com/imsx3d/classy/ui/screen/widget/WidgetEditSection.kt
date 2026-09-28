package com.imsx3d.classy.ui.screen.widget

import androidx.compose.runtime.Composable
import com.imsx3d.classy.data.entity.TimeTableEntity

/**
 * Per-widget edit state, passed to every [WidgetEditSection].
 *
 * [onSelectTable] receives `null` to clear the binding (revert to the
 * app-wide default). The ViewModel is responsible for writing the change
 * to [com.imsx3d.classy.widget.WidgetBindingStore] and asking
 * [com.imsx3d.classy.widget.WidgetUpdater] to refresh.
 */
data class WidgetEditScope(
    val widgetId: Int,
    val currentBinding: Long?,
    val availableTables: List<TimeTableEntity>,
    val onSelectTable: (Long?) -> Unit,
    /** issue#26: 全部小组件共享一档 — widget 场景 课程名显示 原名/别名 */
    val useAlias: Boolean = false,
    val onUseAliasChange: (Boolean) -> Unit = {},
    /**
     * 说明（2026-09-29）：`receiverSimpleName` / `scrollEnabled` / `onCompactTodayFirstChange`
     * 三个字段随「滚动方式」「最小档三天窗口」两节一起删除 —— 那两节所服务的位图管线
     * （固定窗口 / 强制滚动条带）已被真实布局取代，组件编辑页现在只剩"绑定哪张课表"与
     * "课程名显示原名/别名"两件对用户真的有意义的事。
     */
)

/**
 * A single "section" inside [com.imsx3d.classy.ui.screen.widget.WidgetEditScreen].
 *
 * Modeled as a sealed interface so adding a new section (e.g. theme, time
 * format) is one new file + one entry in the screen's `sections` list —
 * no edits to the screen, ViewModel, or storage layer.
 *
 * Sections must be stateless; the screen owns the ViewModel-derived
 * [WidgetEditScope] and threads it through.
 */
sealed interface WidgetEditSection {
    /** Title resource shown above the section body. */
    val titleRes: Int

    /** Render the section body. */
    @Composable
    fun Content(scope: WidgetEditScope)
}
