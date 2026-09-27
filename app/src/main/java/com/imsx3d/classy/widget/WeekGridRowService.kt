package com.imsx3d.classy.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.content.res.ColorStateList
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import android.widget.RemoteViewsService.RemoteViewsFactory
import com.imsx3d.classy.R
import com.imsx3d.classy.util.CourseColorUtil
import com.imsx3d.classy.util.CourseDisplayUtil
import com.imsx3d.classy.util.TimeTableUtils
import androidx.compose.ui.graphics.toArgb

/**
 * 网格组件（真实布局版）· 条目工厂 —— **整个星期 = 一个 ListView 条目**。
 *
 * ## 为什么是"一个条目"（UI-4t，2026-09-26 用户报障"课程块里有不明显的横线截断"）
 * 原结构是"一行 = 一个节次"，跨节次的课靠相邻行同色拼成一块。但桌面把组件按自身密度缩放，
 * 而 ListView 的**每一行是独立渲染层**（各自光栅化后按比例摆放）→ 相邻两行的色块在接缝处
 * 各自抗锯齿，漏出 1px 卡片底色 —— 用户看到的就是"淡横线"，跨节次的块会有多条。
 * 实测排除的画法：drawable 负 inset 外扩、`clipChildren=false`、真·视图外扩、纯矩形填充、
 * XML 与运行时的负 `dividerHeight` —— 接缝全在（被行边界裁掉或被桌面忽略）。
 * ⇒ 改成**单条目**：整个网格在一个条目里，纵向相邻的格子是同一次绘制中的兄弟视图，
 *   没有层间过滤 → 天然无缝。附带好处：每列严格按节次对齐（空节次也占位），
 *   时间轴不再因"跳过整周没课的节次"而跳动。
 *
 * 取值与 App 网格同源：`CourseColorUtil.pickCourseColorCompose` + `textColorOn`，
 * 主题色走 `resolveSchemePublic`（与其它组件同一份）。
 */
class WeekGridRowService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        WeekGridRowFactory(applicationContext, intent)
}

class WeekGridRowFactory(
    private val context: Context,
    intent: Intent
) : RemoteViewsFactory {

    companion object {
        const val EXTRA_WIDGET_ID = "widget_id"
    }

    private val widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, -1)

    /** 一次渲染的全部输入（onDataSetChanged 里算好，getViewAt 只读）。 */
    private class Snapshot(
        val nodeCount: Int,                        // 渲染的节次总数（1..nodeCount 全出，空节次占位）
        val courses: List<com.imsx3d.classy.data.entity.CourseEntity>,
        val starts: List<String>,                  // 每节的开始时间（下标 0 = 第 1 节）
        val scheme: WidgetScheme,
        val visibleDays: List<Int>
    )

    @Volatile
    private var snap: Snapshot? = null

    override fun onCreate() {}

    override fun onDestroy() {}

    override fun onDataSetChanged() {
        try {
            val data = WeekGridWidgetProvider.loadWeekData(context, widgetId)
            val courses = data.days.flatMap { it.courses }
            val slots = WeekGridWidgetProvider.timeSlotStarts(
                data.days.firstOrNull()?.timeJson ?: ""
            )
            // 渲染到"有课的最大节次"为止；这一节下面的节次没人上课就不占高度（避免长空白）
            val maxNode = courses.maxOfOrNull { it.startNode + it.step - 1 } ?: 0
            snap = Snapshot(
                nodeCount = maxNode,
                courses = courses,
                starts = slots,
                scheme = resolveSchemePublic(context, data.themeKey, data.isDark),
                visibleDays = data.visibleDays.sorted()
            )
            android.util.Log.d("WeekGridRow", "id=$widgetId nodes=$maxNode courses=${courses.size}")
        } catch (e: Throwable) {
            android.util.Log.e("WeekGridRow", "onDataSetChanged failed id=$widgetId", e)
            snap = null
        }
    }

    /** 整周没课时给 0 → 容器上的空态视图生效（见 provider 的 setEmptyView）。 */
    override fun getCount(): Int = if ((snap?.nodeCount ?: 0) > 0) 1 else 0

    private val colIds = intArrayOf(
        R.id.wg_col1, R.id.wg_col2, R.id.wg_col3, R.id.wg_col4,
        R.id.wg_col5, R.id.wg_col6, R.id.wg_col7
    )

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_weekgrid_grid)
        val s = snap ?: return views
        if (position != 0 || s.nodeCount <= 0) return views

        // UI-5d（2026-09-27 用户报障"同一周的课程多次出现，往下滑又看到早上的课"）：
        // 桌面在**布局 id 不变**时会走 `reapply` —— 在**既有视图树**上重放动作。
        // `addView` 的语义是"追加"，于是每刷新一次，各列就多长出一整份网格：
        // 播到第 11 节之后再往下滑，又会看到第 1 节（实测复现）。
        // 解法：每次下发前先 **removeAllViews** 清空这 8 个容器，让本条目的构造变成幂等的。
        // （同一类坑见 UI-4g 的壳图 reapply —— RemoteViews 的通用规矩：reapply 会保留上一次的状态，
        //   凡是 set 类动作必须"每次都给值"，凡是 add 类动作必须"先清再加"。）
        for (id in intArrayOf(R.id.wg_timecol, *colIds)) {
            views.removeAllViews(id)
        }

        val useAlias = com.imsx3d.classy.util.AppPrefs.isWidgetUseAlias(context)
        val nameColor = s.scheme.onSurface.toArgb()
        val metaColor = s.scheme.onSurfaceVariant.toArgb()

        // ① 左侧时间列：1..nodeCount 每节一格（空节次也占位 → 各列天然对齐）
        for (node in 1..s.nodeCount) {
            val cell = RemoteViews(context.packageName, R.layout.widget_weekgrid_timecell)
            cell.setTextViewText(R.id.wg_t_node, node.toString())
            cell.setTextViewText(R.id.wg_t_start, s.starts.getOrNull(node - 1) ?: "")
            cell.setTextColor(R.id.wg_t_node, nameColor)
            cell.setTextColor(R.id.wg_t_start, metaColor)
            views.addView(R.id.wg_timecol, cell)
        }

        // ② 每天的列：一门课 = 一个块（跨节次也是同一个块 → 块内无接缝），
        //    无课的节次用 44dp 透明占位格补齐 → 各列与左侧时间轴严格对齐。
        for ((idx, dow) in s.visibleDays.withIndex()) {
            if (idx >= colIds.size) break
            val colId = colIds[idx]
            // 该天按起始节次排序，逐个"块"铺下去
            val dayCourses = s.courses.filter { it.day == dow }.sortedBy { it.startNode }
            var node = 1
            for (course in dayCourses) {
                if (course.startNode > s.nodeCount) continue
                // 先补上"这门课之前"的空白节次
                while (node < course.startNode) {
                    views.addView(colId, RemoteViews(context.packageName, R.layout.widget_weekgrid_spacer))
                    node++
                }
                val span = course.step.coerceAtLeast(1)
                    .coerceAtMost(s.nodeCount - course.startNode + 1)
                val block = RemoteViews(context.packageName, R.layout.widget_weekgrid_block)
                // 高度来源：块内塞 span 个 44dp 占位格
                repeat(span) {
                    block.addView(R.id.wg_blk_sizer, RemoteViews(context.packageName, R.layout.widget_weekgrid_spacer))
                }
                val bg = CourseColorUtil.pickCourseColorCompose(
                    course, s.scheme.isDark, s.scheme.surfaceVariant
                )
                val fg = CourseColorUtil.textColorOn(bg, s.scheme.isDark, s.scheme.onSurface)
                val bgInt = bg.toArgb()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // 圆角 shape + 动态 tint（不能用 setBackgroundColor，那会变成方形色块）
                    block.setColorStateList(
                        R.id.wg_blk_bg, "setBackgroundTintList", ColorStateList.valueOf(bgInt)
                    )
                } else {
                    block.setInt(R.id.wg_blk_bg, "setBackgroundColor", bgInt)
                }
                block.setTextViewText(
                    R.id.wg_blk_name, CourseDisplayUtil.displayName(course, useAlias)
                )
                block.setTextColor(R.id.wg_blk_name, fg.toArgb())
                block.setTextViewText(R.id.wg_blk_room, course.room.filter { it != '\n' })
                block.setTextColor(R.id.wg_blk_room, fg.copy(alpha = 0.72f).toArgb())
                views.addView(colId, block)
                node = course.startNode + span
            }
            // 收尾：这门课之后到 nodeCount 的空白节次
            while (node <= s.nodeCount) {
                views.addView(colId, RemoteViews(context.packageName, R.layout.widget_weekgrid_spacer))
                node++
            }
        }

        // 空 fill-in intent → 合并进 ListView 的 PendingIntentTemplate（点组件进应用）。
        // 注意必须挂到**条目布局里真实存在**的视图上（此处是整个网格容器）。
        views.setOnClickFillInIntent(R.id.wg_grid, Intent())
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = 0L

    override fun hasStableIds(): Boolean = false
}
