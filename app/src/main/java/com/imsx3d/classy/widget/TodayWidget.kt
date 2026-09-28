package com.imsx3d.classy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import com.imsx3d.classy.R
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.util.ConflictLayoutEngine
import com.imsx3d.classy.util.DateUtils
import com.imsx3d.classy.util.TimeTableUtils
import com.imsx3d.classy.util.WeekDisplayResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/**
 * 桌面 Today 小组件 — 同步 RemoteViews + Canvas (v1.0.29 起, 从 Glance 移植)。
 *
 * 之前是 GlanceAppWidgetReceiver → provideGlance 异步 SessionWorker → OPPO OplusHansManager
 * 冻结进程 → RemoteViews 从不生成 → 卡在 widget_loading 紫色布局 → 不跟随主题。
 * 现在克隆 WeekGridWidgetProvider 模式: goAsync → 加载 → 画 bitmap → awm.updateAppWidget,
 * 全程在冻结窗口前完成 → 秒刷 + 主题正确。
 *
 * v1.0.36: 内容装得下走静态 renderAndPush(与主分支一致); 装不下走 pushScrollable
 * (壳图+条带 ListView, 条带与静态渲染同源 → 顶部像素一致, 可滚动)。
 *
 * Glance 版 TodayWidget 类已删除(决策 D5-11); loadDataSync 自 Glance companion 迁入本类。
 */
/**
 * 2026-09-29（整块清理）：原先记在这里的「休眠位图推送管线」已**删除** ——
 * `pushTodayData` / `configureTodayBar` / `todayBarLayout` / `computeTodayWindow` / `footerConfigurePi`
 * 连同 TwoDay 侧的 `computeTwoDayWindows` / `footerConfigureViews` 与两个导航条布局，自 UI-4c 起零调用，
 * 经用户拍板整块清掉（需要时从 git 历史取回）。本类剩下的都是 live 路径：
 * receiver 生命周期、导航广播（handleNav）、`loadDataSync` / `loadDataForDate`。
 */
open class TodayWidgetReceiver : AppWidgetProvider() {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 小组件排版档位 — 基类默认 REGULAR(现有变体); 「今日课程 · 小」子类覆写为 SMALL */
    open val variantHint: WidgetVariant = WidgetVariant.REGULAR

    // suspend: 转发给 CourseRowPusher（内部要读课表名给顶行），调用点都在 ioScope.launch 里
    private suspend fun push(context: Context, awm: AppWidgetManager, id: Int) {
        // UI-4c（2026-09-26 用户令）：改走真实行布局（与「今日课程 · 列表」同一容器/适配器/取色）
        // —— 文字交给 TextView 排版，彻底告别位图手写排版的"1 个字 + …"。
        CourseRowPusher.push(context, awm, id, CourseRowWidgetService.CourseRowFactory.SCOPE_TODAY)
    }

    override fun onUpdate(context: Context, awm: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        ioScope.launch {
            try {
                for (id in ids) {
                    try { push(context, awm, id) }
                    catch (e: Throwable) { Log.e(TAG, "render failed $id", e) }
                }
            } finally { pending.finish() }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, awm: AppWidgetManager, id: Int, newOptions: Bundle
    ) {
        val pending = goAsync()
        ioScope.launch {
            try { push(context, awm, id) }
            catch (e: Throwable) { Log.e(TAG, "optionsChanged render failed $id", e) }
            finally { pending.finish() }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) {
            WidgetBindingStore.remove(context, id)
            TodayDateNavStore.remove(context, id)
            WidgetResizeCore.remove(id)
        }
    }

    /**
     * 导航点击派发 (issue #24 Feature2) — 三个 nav action 走 [handleNav],
     * 其余 (APPWIDGET_UPDATE / DELETED / OPTIONS_CHANGED …) 原样转发 super,
     * 否则 onUpdate 等默认分发会被截断。
     */
    override fun onReceive(context: Context, intent: Intent) {
        when {
            intent.action == WidgetVendorActions.XIAOMI_UPDATE_ACTION ->
                WidgetVendorActions.dispatchXiaomiUpdate(this, context, intent)
            intent.action in setOf(ACTION_PREV_DAY, ACTION_NEXT_DAY, ACTION_RESET_DAY) ->
                handleNav(context, intent)
            else -> super.onReceive(context, intent)
        }
    }

    /** shift / remove 持久化后重推该实例 (R2 带参 / R3 回今天 / R4 按 id 隔离)。 */
    private fun handleNav(context: Context, intent: Intent) {
        val action = intent.action
        val widgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        )
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val pending = goAsync()
        ioScope.launch {
            try {
                when (navDelta(action)) {
                    -1L -> TodayDateNavStore.shift(context, widgetId, LocalDate.now(), -1L)
                    1L -> TodayDateNavStore.shift(context, widgetId, LocalDate.now(), 1L)
                    else -> {
                        if (action == ACTION_RESET_DAY) TodayDateNavStore.remove(context, widgetId)
                    }
                }
                push(context, AppWidgetManager.getInstance(context), widgetId)
            } catch (e: Throwable) {
                Log.e(TAG, "nav failed $widgetId", e)
            } finally { pending.finish() }
        }
    }

    companion object {
        private const val TAG = "TodayWidgetRV"

        // ── issue #24 Feature2 日期导航 ──
        /** 前一天 / 后一天 / 回今天 的广播 action (nav PendingIntent 用)。 */
        const val ACTION_PREV_DAY = "com.imsx3d.classy.widget.TODAY_NAV_PREV"
        const val ACTION_NEXT_DAY = "com.imsx3d.classy.widget.TODAY_NAV_NEXT"
        const val ACTION_RESET_DAY = "com.imsx3d.classy.widget.TODAY_NAV_RESET"

        /** action → 导航增量; RESET / 未知 / null → null (RESET 由 handleNav 单独分支)。 */
        fun navDelta(action: String?): Long? = when (action) {
            ACTION_PREV_DAY -> -1L
            ACTION_NEXT_DAY -> 1L
            else -> null
        }









        /**
         * 同步版数据加载 (runBlocking DB 读) — 供 RemoteViews Receiver 使用。
         *
         * [appWidgetId] is plumbed through so a per-widget binding can override
         * the app-wide default table; receivers fall back to
         * [WidgetTableResolver.resolveCurrentTable] when no binding exists.
         *
         * now 只取一次贯穿全程 (nav target 解析 + isToday 同口径): 两次独立 now()
         * 在跨午夜渲染时会把 nav target 算在昨天、isToday 判在今天 = 单次渲染口径分裂。
         */
        fun loadDataSync(context: Context, appWidgetId: Int): WidgetData {
            val now = LocalDate.now()
            val manualNavigation = TodayDateNavStore.hasNavigation(context, appWidgetId)
            return loadDataForDate(
                context, appWidgetId,
                TodayDateNavStore.target(context, appWidgetId, now), now,
                autoNearestBusyDay = !manualNavigation
            )
        }

        /**
         * 指定日期版数据加载 — [target] 由调用方给出 (loadDataSync 传导航锚定日,
         * 翻页卡工厂传卡面日期); [today] 必须同源自调用方 (禁内部再取第二次 now)。
         */
        fun loadDataForDate(
            context: Context,
            appWidgetId: Int,
            target: LocalDate,
            today: LocalDate,
            autoNearestBusyDay: Boolean = false
        ): WidgetData {
            val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDark = com.imsx3d.classy.util.AppPrefs.isDarkMode(context, isSystemDark)
            val themeKey = com.imsx3d.classy.util.AppPrefs.getThemeKey(context)
            val themeMode = com.imsx3d.classy.util.AppPrefs.getThemeMode(context)
            Log.d("TodayWidget", "DIAG: isDark=$isDark isSystemDark=$isSystemDark themeMode=$themeMode themeKey=$themeKey")
            return try {
                runBlocking {
                    val source = WidgetWeekDataLoader.resolve(appWidgetId)
                    if (source == null) {
                        WidgetData(date = target, courses = emptyList(), timeJson = TimeTableUtils.DEFAULT_TIME_JSON, hasTable = false, isDark = isDark, themeKey = themeKey, isToday = target == today)
                    } else {
                        val table = source.table
                        val effectiveTarget = if (autoNearestBusyDay &&
                            source.display.status == com.imsx3d.classy.util.WeekDisplayStatus.NEAREST_BUSY_DAY
                        ) source.display.targetDate else target
                        val effectiveDayOfWeek = HolidayTransferHelper.effectiveDayOfWeek(context, table.id, effectiveTarget)
                        val week = DateUtils.currentWeek(table.startDate, effectiveTarget)
                        val status = DateUtils.semesterStatus(table.startDate, table.maxWeek, effectiveTarget)
                        // 学期外(前/后)不展示课程 — App 今日页同语义, 避免学期前显示"第1周"的课
                        val visible = if (status != DateUtils.SemesterStatus.IN_RANGE) emptyList() else
                            source.coursesFor(effectiveDayOfWeek, week)
                        WidgetData(
                            date = effectiveTarget,
                            courses = visible,
                            timeJson = table.timeJson,
                            hasTable = true,
                            isDark = isDark,
                            themeKey = themeKey,
                            semesterStatus = status,
                            isToday = effectiveTarget == today,
                            weekDisplayStatus = WeekDisplayResolver.statusForSelectedWeek(
                                source.display, week
                            )
                        )
                    }
                }
            } catch (_: Throwable) {
                WidgetData(date = target, courses = emptyList(), timeJson = TimeTableUtils.DEFAULT_TIME_JSON, hasTable = false, isDark = isDark, themeKey = themeKey, isToday = target == today)
            }
        }
    }
}
