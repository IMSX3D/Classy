package com.imsx3d.classy.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.imsx3d.classy.R
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/**
 * 桌面 TwoDay 小组件 — 同步 RemoteViews + Canvas (v1.0.29 起, 从 Glance 移植)。
 * 原因见 [TodayWidgetReceiver] 注释。
 *
 * v1.0.36: 内容装得下走静态 renderAndPush; 超出走 pushScrollable(壳图+条带)。
 *
 * Glance 版 TwoDayWidget 类已删除(决策 D5-11); loadDataSync 自 Glance companion 迁入本类。
 */
/**
 * 2026-09-29（整块清理）：`computeTwoDayWindows` / `footerConfigureViews` 两个休眠的位图推送包装已删除
 * （UI-4c 起零调用；用户拍板整块清掉，需要时从 git 历史取回）。本类剩下的都是 live 路径：
 * receiver 生命周期与 `loadDataSync`。
 */
open class TwoDayWidgetReceiver : AppWidgetProvider() {
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 小组件排版档位 — 基类默认 REGULAR(现有变体); 「最近两天 · 小」子类覆写为 SMALL */
    open val variantHint: WidgetVariant = WidgetVariant.REGULAR

    // suspend: 转发给 CourseRowPusher（内部要读课表名给顶行），调用点都在 ioScope.launch 里
    private suspend fun push(context: Context, awm: AppWidgetManager, id: Int) {
        // UI-4c: 同上，改走真实行布局（今天 / 明天 分组行）。
        CourseRowPusher.push(context, awm, id, CourseRowWidgetService.CourseRowFactory.SCOPE_TWODAY)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WidgetVendorActions.XIAOMI_UPDATE_ACTION) {
            WidgetVendorActions.dispatchXiaomiUpdate(this, context, intent)
        } else {
            super.onReceive(context, intent)
        }
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
        for (id in appWidgetIds) { WidgetBindingStore.remove(context, id) }
    }

    companion object {
        private const val TAG = "TwoDayRV"



        /**
         * 同步版数据加载 — 今天 + 明天课程。
         *
         * [appWidgetId] is plumbed through so a per-widget binding can override
         * the app-wide default table; receivers fall back to
         * [WidgetTableResolver.resolveCurrentTable] when no binding exists.
         */
        fun loadDataSync(context: Context, appWidgetId: Int): TwoDayData {
            val today = LocalDate.now()
            val tomorrow = today.plusDays(1)
            val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDark = com.imsx3d.classy.util.AppPrefs.isDarkMode(context, isSystemDark)
            val themeKey = com.imsx3d.classy.util.AppPrefs.getThemeKey(context)
            return try {
                runBlocking {
                    val source = WidgetWeekDataLoader.resolve(appWidgetId)
                    if (source == null) {
                        TwoDayData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
                    } else {
                        val table = source.table
                        val dates = if (source.display.status == com.imsx3d.classy.util.WeekDisplayStatus.NEAREST_BUSY_DAY) {
                            listOf(source.display.targetDate, source.display.targetDate.plusDays(1))
                        } else listOf(today, tomorrow)
                        val status = DateUtils.semesterStatus(table.startDate, table.maxWeek, dates.first())
                        val days = dates.map { date ->
                            val week = DateUtils.currentWeek(table.startDate, date)
                            val dow = HolidayTransferHelper.effectiveDayOfWeek(context, table.id, date)
                            val courses = if (status != DateUtils.SemesterStatus.IN_RANGE) emptyList()
                                else source.coursesFor(dow, week)
                            DayData(date = date, dayOfWeek = dow, courses = courses, timeJson = table.timeJson)
                        }
                        TwoDayData(
                            days = days,
                            hasTable = true,
                            isDark = isDark,
                            themeKey = themeKey,
                            semesterStatus = status,
                            weekDisplayStatus = source.display.status
                        )
                    }
                }
            } catch (_: Throwable) {
                TwoDayData(days = emptyList(), hasTable = false, isDark = isDark, themeKey = themeKey)
            }
        }
    }
}
