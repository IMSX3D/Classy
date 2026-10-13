package com.imsx3d.classy.widget

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.data.entity.TimeTableEntity
import com.imsx3d.classy.util.AppPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

/**
 * Read model for a single widget instance's edit screen.
 *
 * - [currentBinding]: `null` means "follow the app-wide default table"
 *   (i.e. [WidgetTableResolver.resolveCurrentTable]); a Long means the
 *   user has explicitly picked this schedule for this widget.
 * - [availableTables]: schedules with at least one course (binding to an
 *   empty table would render the "请先创建课表" state).
 */
data class WidgetEditUiState(
    val currentBinding: Long? = null,
    val availableTables: List<TimeTableEntity> = emptyList(),
    /** issue#26: widget 场景 课程名显示 原名/别名(全局一档, 全部小组件共享) */
    val useAlias: Boolean = false,
    val loading: Boolean = true,
    val loadFailed: Boolean = false,
)

/**
 * Per-widget edit ViewModel. All non-trivial logic is delegated to
 * [WidgetEditCore] (pure-JVM, unit-tested in [WidgetEditCoreTest]).
 *
 * Owned by the screen host's ViewModelStore, keyed by widget ID. Loading is explicit so a pending
 * query cannot briefly show a false default binding or empty table list.
 */
class WidgetEditViewModel(
    val widgetId: Int,
    private val loadAvailableTables: suspend () -> List<TimeTableEntity> = {
        val repo = SleepyApp.get().repository
        val all = repo.getAllTables()
        val counts = all.associate { it.id to repo.countCourses(it.id) }
        WidgetEditCore.filterAvailableTables(all) { counts[it] ?: 0 }
    }
) : ViewModel() {

    private val ctx: Context get() = SleepyApp.get()

    private val _state = MutableStateFlow(WidgetEditUiState())
    val state: StateFlow<WidgetEditUiState> = _state.asStateFlow()

    private var reloadJob: Job? = null

    init { reload() }

    /** Re-read binding + non-empty tables from store and repo. */
    fun reload() {
        reloadJob?.cancel()
        _state.value = _state.value.copy(loading = _state.value.loading || _state.value.loadFailed, loadFailed = false)
        reloadJob = viewModelScope.launch {
            try {
                val available = loadAvailableTables()
                // Sentinel (0L) means "follow default" — translated to null so the
                // UI shows the "Default" row as selected (same as no binding).
                val raw = WidgetBindingStore.get(ctx, widgetId)
                _state.value = WidgetEditUiState(
                    currentBinding = WidgetEditCore.displayBinding(raw),
                    availableTables = available,
                    useAlias = AppPrefs.isWidgetUseAlias(ctx),
                    loading = false,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = _state.value.copy(loading = false, loadFailed = true)
            }
        }
    }

    /**
     * 设计 §6: 解析本实例的 receiver simpleName。
     * 首选 AppWidgetInfo.provider (绑定即有, 就是 receiver 本体);
     * configure 仅作兜底 (本 app 未声明 android:configure, 恒 null — 2026-09-15 修正,
     * 此前只读 configure 导致族判断永远走"未知"分支)。
     * 实例已删除/厂商异常 → null (编辑页仍可用, 仅按族显隐的节按未知=隐藏)。
     */
    /**
     * issue#26: 切换 widget 场景 课程名显示(原名/别名)。全局一档(所有小组件共享),
     * 写 AppPrefs 后 reload + 全量刷 widget — 与 setBinding 同管线。
     */
    fun setUseAlias(v: Boolean) {
        AppPrefs.setWidgetUseAlias(ctx, v)
        _state.value = _state.value.copy(useAlias = v)
        reload()
        viewModelScope.launch {
            runCatching { WidgetUpdater.notifyDataChanged(ctx) }
        }
    }

    /**
     * Persist a binding change. `null` clears the binding so the widget
     * falls back to [WidgetTableResolver.resolveCurrentTable]. After
     * writing, ask [WidgetUpdater] to refresh every widget receiver so the
     * change is reflected on the home screen immediately.
     */
    fun setBinding(tableId: Long?) {
        if (tableId == null) WidgetBindingStore.remove(ctx, widgetId)
        else WidgetBindingStore.put(ctx, widgetId, tableId)
        _state.value = _state.value.copy(currentBinding = tableId)
        reload()
        viewModelScope.launch {
            runCatching { WidgetUpdater.notifyDataChanged(ctx) }
        }
    }
}
