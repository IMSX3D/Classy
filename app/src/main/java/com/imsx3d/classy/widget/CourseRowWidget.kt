package com.imsx3d.classy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.imsx3d.classy.R
import com.imsx3d.classy.util.DateUtils
import com.imsx3d.classy.util.HolidayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 可滚动课程列表组件 · Provider（M1 新实现，2026-09-22）。
 *
 * 推送方式与既有组件一致（goAsync 续命 + 世代号防乱序），但**不画位图**：
 * 只挂 RemoteViews 集合（setRemoteAdapter → [CourseRowWidgetService]）+ 标题/空态文案，
 * 行内容由工厂按真实布局逐行提供。因此尺寸变化时只需重推一次（行会自行重新布局），
 * 不存在"按旧尺寸渲染的位图被拉伸"的问题。
 *
 * 基类持有 scope；子类决定是「今日」还是「近日」：
 *   CourseRowWidgetReceiver      → 今日课程（可滚动）
 *   CourseRowTwoDayReceiver      → 今明两天（可滚动）
 */
open class CourseRowWidgetReceiver : AppWidgetProvider() {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 数据范围 — 子类覆写。 */
    open val scope: String = CourseRowWidgetService.CourseRowFactory.SCOPE_TODAY

    override fun onUpdate(context: Context, awm: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        ioScope.launch {
            try {
                for (id in ids) {
                    try {
                        push(context, awm, id)
                    } catch (e: Throwable) {
                        Log.e(TAG, "push failed id=$id scope=$scope", e)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    /** 尺寸/摆放变化 → 重推壳（行由 launcher 自适应，无需重新渲染内容）。 */
    override fun onAppWidgetOptionsChanged(
        context: Context, awm: AppWidgetManager, id: Int, newOptions: Bundle
    ) {
        val pending = goAsync()
        ioScope.launch {
            try {
                push(context, awm, id)
            } catch (e: Throwable) {
                Log.e(TAG, "optionsChanged push failed id=$id", e)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) WidgetResizeCore.remove(id)
    }

    // suspend: 推送里要读一次课表名（给顶行用），两个调用点都在 ioScope.launch 里
    private suspend fun push(context: Context, awm: AppWidgetManager, id: Int) =
        CourseRowPusher.push(context, awm, id, scope)

    private companion object {
        const val TAG = "CourseRowWidget"
    }
}

/** 今日课程 · 可滚动（真实行布局，不再走位图）。 */
class CourseRowTodayWidgetReceiver : CourseRowWidgetReceiver() {
    override val scope: String = CourseRowWidgetService.CourseRowFactory.SCOPE_TODAY
}

/** 近日课程（今天 + 明天）· 可滚动。 */
class CourseRowTwoDayWidgetReceiver : CourseRowWidgetReceiver() {
    override val scope: String = CourseRowWidgetService.CourseRowFactory.SCOPE_TWODAY
}

/**
 * 真实行布局组件的推送实现（今日 / 近日 / 列表 共用一条）。
 *
 * UI-4c（2026-09-26 用户令「重新做组件、对着 wakeup 复刻」）：
 * 「今日课程」「最近两天」原先走 Canvas 位图（文字手写排版 → 形变），现改为与本类
 * 同一个容器 + 同一个适配器 + 同一份 scheme —— 文字交给 TextView 排版，
 * 行高按内容定，超出由 launcher 原生滚动，不再有任何"手写截断"。
 */
internal object CourseRowPusher {

    private const val TAG = "CourseRowPusher"

    suspend fun push(context: Context, awm: AppWidgetManager, id: Int, scope: String) {
        // 世代号：连发更新时旧任务晚到不能覆盖新结果（与既有组件同机制）
        val gen = WidgetResizeCore.bump(id)
        if (scope == CourseRowWidgetService.CourseRowFactory.SCOPE_TWODAY) {
            TwoDayColumnsPusher.push(context, awm, id, gen)
            return
        }

        // UI-6a：顶行 = 课表名（左）+ 日期·周几（右）—— 对齐 wakeup 的顶行信息结构。
        // 原来的 "N 门课" 从顶行去掉：课程行本身就一眼看得出有几门，顶行留给"这是哪张表 + 今天几号周几"。
        val headerDate: String
        val emptyText: String
        val isDark: Boolean
        val themeKey: String
        if (scope == CourseRowWidgetService.CourseRowFactory.SCOPE_TWODAY) {
            val d = TwoDayWidgetReceiver.loadDataSync(context, id)
            val today = d.days.firstOrNull()
            headerDate = if (today != null) "${today.dayName} ${DateUtils.shortDateSlash(today.date)}" else ""
            emptyText = "今天和明天都没有课"
            isDark = d.isDark
            themeKey = d.themeKey
        } else {
            val d = TodayWidgetReceiver.loadDataSync(context, id)
            // 导航态（用户点过 ◀▶ 翻到别的日子）时 dateLabel/dayName 本来就跟着变，正好
            headerDate = "${d.dayName} ${d.dateLabel}"
            emptyText = "今天没有课"
            isDark = d.isDark
            themeKey = d.themeKey
        }
        val headerName = runCatching { (WidgetTableResolver.resolveBoundTable(id) ?: WidgetTableResolver.resolveCurrentTable())?.name }
            .getOrNull().orEmpty()
        // UI-6b：顶行日期后面补当天节日名（"周四 10/1 · 国庆节"），与网格组件表头同一套信息
        //（那边是把"周X"换成节日名，这边列表要保留星期几，所以追加）。
        val headerDateFinal = runCatching {
            val day = if (scope == CourseRowWidgetService.CourseRowFactory.SCOPE_TWODAY) {
                TwoDayWidgetReceiver.loadDataSync(context, id).days.firstOrNull()?.date
            } else {
                TodayWidgetReceiver.loadDataSync(context, id).date
            }
            val name = day?.let { HolidayManager.dayHolidayName(context, it) }
            if (name.isNullOrBlank()) headerDate else "$headerDate · $name"
        }.getOrDefault(headerDate)

        val views = RemoteViews(context.packageName, R.layout.widget_course_list)
        views.setTextViewText(R.id.widget_list_header, headerName)
        views.setTextViewText(R.id.widget_list_header_date, headerDateFinal)
        views.setTextViewText(R.id.widget_list_empty, emptyText)
        // UI-4: 卡片底 + 标题/空态文字色原先写在布局 XML 里（硬编码浅色）→
        // 深色模式下白底黑字落在深色桌面上、且不跟随 App 内主题。改为按 scheme 下发
        // （与位图组件同一份 resolveSchemePublic 取色）。外层 FrameLayout 的
        // clipToOutline=true 会把这块不透明底裁成 12dp 圆角，所以这里给纯色即可。
        val scheme = resolveSchemePublic(context, themeKey, isDark)
        views.setInt(R.id.widget_list_root, "setBackgroundColor", scheme.bg.toArgb())
        views.setTextColor(R.id.widget_list_header, scheme.onSurfaceVariant.toArgb())
        views.setTextColor(R.id.widget_list_header_date, scheme.onSurface.toArgb())
        views.setTextColor(R.id.widget_list_empty, scheme.onSurfaceVariant.toArgb())

        val svcIntent = Intent(context, CourseRowWidgetService::class.java).apply {
            putExtra(CourseRowWidgetService.CourseRowFactory.EXTRA_WIDGET_ID, id)
            putExtra(CourseRowWidgetService.CourseRowFactory.EXTRA_SCOPE, scope)
            // 不同 widget 实例的 adapter intent 必须不同，否则 launcher 复用同一工厂实例
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.widget_course_list, svcIntent)
        // 行数为 0 时显示空态（空态视图与 ListView 同父，ListView.setEmptyView 的硬要求）
        views.setEmptyView(R.id.widget_course_list, R.id.widget_list_empty)

        val template = PendingIntent.getActivity(
            context, WidgetRoutes.tapRequestCode(id), WidgetRoutes.tapIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setPendingIntentTemplate(R.id.widget_course_list, template)
        // UI-4o: 表头 / 空态 / 行外空白也要能点开 App。
        // 此前只有「课程行」挂了 fill-in intent（配合 ListView 的 template），没课的日子
        // 整列是空的 —— 组件里只剩表头和"今天没有课"，**没有任何可点区域** → 用户点了没反应。
        // 挂在 match_parent 的 widget_list_root（覆盖整个组件）上兜底；课程行的 fill-in
        // 照旧优先（行是子视图，先拿到触摸）。
        views.setOnClickPendingIntent(R.id.widget_list_root, template)
        // UI-6c 失败尝试记录：曾在此给 **ListView 自己** 挂 setOnClickPendingIntent 想兜住
        // 行下方的空白区 —— 结果桌面直接把集合视图渲染成"空白卡 + 转圈"（框架不允许在
        // 集合视图上挂普通点击）。已撤回，改用"补足填充行"的方案（见 CourseRowWidgetService）。

        if (gen > 0 && WidgetResizeCore.isStale(id, gen)) {
            Log.d(TAG, "skip stale id=$id gen=$gen")
            return
        }
        awm.updateAppWidget(id, views)
        // updateAppWidget 与 notify 是两次 binder 调用，紧挨着下发把错配窗口压到最小
        awm.notifyAppWidgetViewDataChanged(id, R.id.widget_course_list)
        Log.d(TAG, "push id=$id scope=$scope name=$headerName date=$headerDate")
    }
}
