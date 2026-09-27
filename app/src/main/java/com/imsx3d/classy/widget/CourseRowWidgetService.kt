package com.imsx3d.classy.widget

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.graphics.toArgb
import com.imsx3d.classy.R
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.util.CourseColorUtil
import com.imsx3d.classy.util.TimeTableUtils

/**
 * 可滚动课程列表组件 · 数据工厂（M1 新实现，2026-09-22）。
 *
 * 与既有 [ScrollStripService] 的根本区别：
 *   ScrollStripService = 把整块内容画成**一张长位图**塞进单行 ListView，行高靠
 *     setViewLayoutHeight 钉死（API31+ 独占，作者注释里记录 OPPO 真机翻车三次）。
 *     内容仍是位图 → 尺寸一变就整体缩放（用户实测「变形严重」），小尺寸下只能
 *     把课名压成一个字 + 省略号（「中…」「习…」）。
 *   本服务 = **每一行是真实布局**（widget_course_row.xml），行高由内容自然决定：
 *     · 不缩放 → 不变形（行宽跟随组件，文字自己换行）
 *     · 原生滚动 → 内容多往下滑，不压缩、不需要「还有 N 门课」兜底
 *     · 课名最多两行 → 不再退化成单字截断
 *     不依赖 setViewLayoutHeight，所以 API26+ 与各厂商 launcher 行为一致。
 *
 * 数据源复用应用既有同步读取入口（TodayWidgetReceiver/TwoDayWidgetReceiver
 * 的 loadDataSync），不新开数据库路径。
 */
class CourseRowWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        CourseRowFactory(applicationContext, intent)

    class CourseRowFactory(
        private val context: Context,
        intent: Intent
    ) : RemoteViewsFactory {

        companion object {
            private const val TAG = "CourseRowWidget"
            const val EXTRA_WIDGET_ID = "widget_id"
            const val EXTRA_SCOPE = "scope"
            const val SCOPE_TODAY = "today"
            const val SCOPE_TWODAY = "twoday"
        }

        private val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)
        private val scope = intent.getStringExtra(EXTRA_SCOPE) ?: SCOPE_TODAY

        /** 预解析行 — 文案与颜色都在 onDataSetChanged 算完，getViewAt 只做赋值。 */
        private data class Row(
            val name: String,
            val meta: String,
            val time: String,
            val barColor: Int,
            val nameColor: Int,
            val metaColor: Int,
            val cardColor: Int
        )

        /** 一天的数据切片（今日档只有一片，近日档两片）。 */
        private data class Slice(
            val label: String?,
            val courses: List<CourseEntity>,
            val timeJson: String
        )

        @Volatile
        private var rows: List<Row> = emptyList()

        /**
         * 行尾**填充行**数量（UI-6c）：课程行装不满组件时，ListView 下方那片空白会吃掉触摸
         * （点它打不开 app，用户报障"近日组件点击无法打开 app"）。用透明填充行把剩余高度铺满，
         * 每行都带 fill-in intent → 点哪都能进应用。
         * 真实行 0 条时**不加填充**：空态视图是 ListView 的兄弟节点，由容器点击兜住（已验证）。
         */
        @Volatile
        private var fillerRows: Int = 0

        /** 一行真实内容的高度估算（真实行是 wrap_content，实测 ≈64dp）。 */
        private val rowHeightDp = 64
        /** 表头 + 上下内边距的高度估算（widget_course_list.xml：padding 8+4、表头两行 ≈30dp）。 */
        private val chromeHeightDp = 42

        override fun onCreate() {}

        override fun onDestroy() {}

        override fun onDataSetChanged() {
            // binder 线程边界：本方法抛异常 = 直接杀进程，必须全捕获。
            try {
                val built = buildRows()
                rows = built
                fillerRows = if (built.isEmpty()) 0 else {
                    val hDp = runCatching {
                        val opts = android.appwidget.AppWidgetManager.getInstance(context)
                            .getAppWidgetOptions(widgetId)
                        RemoteViewsWidgetHelper.computeSizeDp(opts).second
                    }.getOrDefault(0)
                    val contentDp = chromeHeightDp + built.size * rowHeightDp
                    // 余量除行高 + 1：多补一行确保盖满（多出的那行只是把可滚动区微微拉长，无副作用）
                    ((hDp - contentDp).coerceAtLeast(0) / rowHeightDp) + 1
                }
            } catch (t: Throwable) {
                Log.e(TAG, "onDataSetChanged failed id=$widgetId scope=$scope", t)
                rows = emptyList()
            }
        }

        private fun buildRows(): List<Row> {
            val scheme: WidgetScheme
            val slices: List<Slice>
            if (scope == SCOPE_TWODAY) {
                val d = TwoDayWidgetReceiver.loadDataSync(context, widgetId)
                scheme = resolveSchemePublic(context, d.themeKey, d.isDark)
                slices = d.days.map { day ->
                    val label = when {
                        day.isToday -> "今天"
                        day.isTomorrow -> "明天"
                        else -> day.dayLabel
                    }
                    Slice(label, day.courses, day.timeJson)
                }
            } else {
                val d = TodayWidgetReceiver.loadDataSync(context, widgetId)
                scheme = resolveSchemePublic(context, d.themeKey, d.isDark)
                slices = listOf(Slice(null, d.courses, d.timeJson))
            }
            return slices.flatMap { slice ->
                slice.courses.sortedBy { it.startNode }
                    .map { rowFor(slice.label, it, slice.timeJson, scheme) }
            }
        }

        private fun rowFor(
            dayLabel: String?,
            course: CourseEntity,
            timeJson: String,
            scheme: WidgetScheme
        ): Row {
            val bg = CourseColorUtil.pickCourseColorCompose(
                course, scheme.isDark, scheme.surfaceVariant
            )
            val name = if (course.alias.isNotBlank()) course.alias else course.courseName
            val place = listOf(course.room, course.teacher).filter { it.isNotBlank() }
            val meta = buildString {
                if (dayLabel != null) {
                    append(dayLabel)
                    if (place.isNotEmpty()) append(" · ")
                }
                append(place.joinToString(" · "))
            }
            val nodes = "第${course.startNode}-${course.startNode + course.step - 1}节"
            val clock = TimeTableUtils.courseTimeString(
                course.startNode, course.step, timeJson,
                course.ownTime, course.startTime, course.endTime
            )
            return Row(
                name = name,
                // 极端情况（导入数据缺地点/教师）也给一行可见文案，不留空行
                meta = meta.ifBlank { "—" },
                time = if (clock.isNullOrBlank()) nodes else "$nodes · $clock",
                barColor = bg.toArgb(),
                nameColor = scheme.onSurface.toArgb(),
                metaColor = scheme.onSurfaceVariant.toArgb(),
                cardColor = scheme.surfaceContainer.toArgb()
            )
        }

        override fun getCount(): Int = rows.size + fillerRows

        override fun getViewAt(position: Int): RemoteViews {
            // onDataSetChanged 与 getViewAt 是不同 binder 池线程 → 先取局部快照，越界回退空白
            val snapshot = rows
            // 填充行：透明 + 带 fill-in intent（点它同样进应用）
            if (position >= snapshot.size || position < 0) {
                val filler = RemoteViews(context.packageName, R.layout.widget_course_row_filler)
                filler.setOnClickFillInIntent(R.id.widget_row_filler_root, Intent())
                return filler
            }
            val views = RemoteViews(context.packageName, R.layout.widget_course_row)
            val r = snapshot[position]
            views.apply {
                setTextViewText(R.id.widget_row_name, r.name)
                setTextViewText(R.id.widget_row_meta, r.meta)
                setTextViewText(R.id.widget_row_time, r.time)
                setInt(R.id.widget_row_root, "setBackgroundColor", r.cardColor)
                setInt(R.id.widget_row_bar, "setBackgroundColor", r.barColor)
                setTextColor(R.id.widget_row_name, r.nameColor)
                setTextColor(R.id.widget_row_meta, r.metaColor)
                setTextColor(R.id.widget_row_time, r.metaColor)
                // 空 fill-in intent → 合并进 ListView 的 PendingIntentTemplate（点行进应用）
                setOnClickFillInIntent(R.id.widget_row_root, Intent())
            }
            return views
        }

        override fun getLoadingView(): RemoteViews? = null

        override fun getViewTypeCount(): Int = 1

        override fun getItemId(position: Int): Long = position.toLong()

        override fun hasStableIds(): Boolean = false
    }
}
