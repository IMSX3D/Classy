package com.imsx3d.classy.widget

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.imsx3d.classy.R
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.util.DateUtils

/**
 * 组件实验室（debug 工具，exported 便于 adb 启动）—— 「有课状态下组件长什么样」的离线预览。
 *
 * 为什么需要：放假 / 学期外 / 当天没课时，今日·近日组件只会显示"今天没有课"，
 * 无法核对**有课**时的版式（用户报障原话："我无法确定在有课情况下组件具体是什么效果"）。
 * 本页用**样例课程**，走与真机组件**完全相同的布局与取色**（`widget_course_list` 容器 +
 * `widget_course_row` 行 + `resolveSchemePublic` 主题色），把行直接塞进容器渲染。
 * 不写数据库、不动真实课表 —— 只是"预览用的假数据"。
 *
 * 用法:
 *   adb shell am start -n com.imsx3d.classy/.widget.WidgetLabActivity --es kind today
 *   adb shell am start -n com.imsx3d.classy/.widget.WidgetLabActivity --es kind twoday
 *   adb shell am start -n com.imsx3d.classy/.widget.WidgetLabActivity --es kind weekgrid
 *   （加 --ei w 320 --ei h 300 可指定尺寸 dp；加 --ez light true 强制浅色 → 出图脚本用）
 *
 * 出图（选择器预览素材）：`python tools/gen_widget_previews.py` 按变体调度本页、
 * 截图并裁出卡片 → `res/drawable-nodpi/widget_preview_*.png`（UI-7）。
 *
 * 注意（真机踩过的坑）: 同一个 Activity 再次 am start **不会重建**（走 onNewIntent，
 * 本类未处理）→ 截图会是上一份内容。换 kind 前先 `adb shell am force-stop com.imsx3d.classy`。
 */
class WidgetLabActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val kind = intent.getStringExtra("kind") ?: "today"
        val wDp = intent.getIntExtra("w", 320).coerceAtLeast(160)
        // h ≤ 0 = 按内容自适应高度（wrap_content）：出图时用它，卡片高度 = 内容高度，
        // 不会出现"最后一行被切一刀"（手填 dp 时反复踩：行高随课名行数在 62~78dp 之间变）。
        val wrapHeight = intent.getIntExtra("h", 300) <= 0
        val hDp = intent.getIntExtra("h", 300).coerceAtLeast(120)
        // UI-7（2026-09-28）: 出图用强制浅色 —— 选择器预览图是**随包发布的静态素材**，
        // 不能跟着测试机当时的深浅色走（否则同一次生成会因手机处于深色而产出深色图）。
        val forceLight = intent.getBooleanExtra("light", false)
        if (kind == "weekgrid") {
            showWeekGridSample(wDp, hDp, forceLight)
            return
        }
        if (kind == "headersweep") {
            // 头栏宽度自检：同一个头栏在 5 种组件宽度下的样子（专治"窄列串列"这类问题）
            showHeaderSweep(hDp)
            return
        }

        // 与真机组件同一份取色
        val isSystemDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val isDark = !forceLight && com.imsx3d.classy.util.AppPrefs.isDarkMode(this, isSystemDark)
        val themeKey = com.imsx3d.classy.util.AppPrefs.getThemeKey(this)
        val scheme = resolveSchemePublic(this, themeKey, isDark)

        val bg = FrameLayout(this).apply { setBackgroundColor(0xFF1A1A2E.toInt()) }
        val density = resources.displayMetrics.density
        val cardW = (wDp * density).toInt()
        val cardH = if (wrapHeight) ViewGroup.LayoutParams.WRAP_CONTENT else (hDp * density).toInt()
        val card = LayoutInflater.from(this).inflate(R.layout.widget_course_list, bg, false)
        card.layoutParams = FrameLayout.LayoutParams(cardW, cardH).apply { gravity = Gravity.CENTER }
        bg.addView(card)
        setContentView(bg)

        val header = card.findViewById<TextView>(R.id.widget_list_header)
        val headerDate = card.findViewById<TextView>(R.id.widget_list_header_date)
        val empty = card.findViewById<TextView>(R.id.widget_list_empty)

        // UI-7（2026-09-28 用户报障「组件视图太简陋」）: 顶行按**生产口径**排
        // （CourseRowPusher: 左「课表名」+ 右「周X M/D」）。原来这里是
        // "周六 9/26 · 4 门课" 这种实验室自造文案 —— 与本类"走真机同一布局"的
        // 初衷相悖，也让选出去的预览图跟真机对不上。
        val rows = if (kind == "twoday") SAMPLE_TWODAY else SAMPLE_TODAY
        header.setText(PREVIEW_TABLE_NAME)
        headerDate.setText(previewHeaderDate())
        // 文字色同生产（CourseRowPusher.push）：左表名 onSurfaceVariant，右日期 onSurface
        header.setTextColor(scheme.onSurfaceVariant.toArgb())
        headerDate.setTextColor(scheme.onSurface.toArgb())
        empty.setTextColor(scheme.onSurfaceVariant.toArgb())
        card.findViewById<View>(R.id.widget_list_root)
            .setBackgroundColor(scheme.bg.toArgb())

        // ListView 换成等价的竖向 LinearLayout：本页只做静态预览，不接适配器
        val list = card.findViewById<View>(R.id.widget_course_list)
        val parent = list.parent as ViewGroup
        val index = parent.indexOfChild(list)
        val lp = list.layoutParams
        parent.removeViewAt(index)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = lp
        }
        parent.addView(column, index)

        val inflater = LayoutInflater.from(this)
        for (r in rows) {
            val row = inflater.inflate(R.layout.widget_course_row, column, false)
            row.findViewById<TextView>(R.id.widget_row_name)
                .apply { text = r.name; setTextColor(scheme.onSurface.toArgb()) }
            row.findViewById<TextView>(R.id.widget_row_meta)
                .apply { text = r.meta; setTextColor(scheme.onSurfaceVariant.toArgb()) }
            row.findViewById<TextView>(R.id.widget_row_time)
                .apply { text = r.time; setTextColor(scheme.onSurfaceVariant.toArgb()) }
            row.findViewById<View>(R.id.widget_row_bar).setBackgroundColor(r.bar)
            row.findViewById<View>(R.id.widget_row_root)
                .setBackgroundColor(scheme.surfaceContainer.toArgb())
            column.addView(row)
        }
        setTitle("组件实验室 · $kind")
    }

    /**
     * 网格组件预览：用**样例周数据**渲染位图（放假期间也能看有课状态）。
     * 走的是真机组件同一个渲染器 `WeekGridWidgetProvider.renderBitmap`，
     * 尺寸可指定 → 能按桌面上的真实尺寸核对版式。
     */
    private fun showWeekGridSample(wDp: Int, hDp: Int, forceLight: Boolean = false) {
        val isSystemDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val isDark = !forceLight && com.imsx3d.classy.util.AppPrefs.isDarkMode(this, isSystemDark)
        val themeKey = com.imsx3d.classy.util.AppPrefs.getThemeKey(this)

        val monday = java.time.LocalDate.now()
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val days = (1..7).map { d ->
            DayData(
                date = monday.plusDays((d - 1).toLong()),
                dayOfWeek = d,
                courses = samplesForDay(d),
                timeJson = com.imsx3d.classy.util.TimeTableUtils.DEFAULT_TIME_JSON
            )
        }
        val data = WeekData(
            days = days,
            hasTable = true,
            isDark = isDark,
            themeKey = themeKey,
            showDate = true,
            visibleDays = (1..7).toSet()
        )
        val density = resources.displayMetrics.density
        val bmp = WeekGridWidgetProvider.renderBitmap(
            this, data, (wDp * density).toInt(), (hDp * density).toInt()
        )
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF1A1A2E.toInt()) }
        val img = android.widget.ImageView(this).apply {
            // UI-7: FIT_CENTER **会放大**（位图 900x750 落进 1080 宽的空视图 → 1.2× 拉伸），
            // 出图脚本按"渲染尺寸 = 落盘尺寸"裁卡片，这里必须 1:1 显示。
            scaleType = android.widget.ImageView.ScaleType.CENTER
            contentDescription = "组件实验室 · weekgrid"
            setImageBitmap(bmp)
        }
        root.addView(
            img,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)
        setTitle("组件实验室 · weekgrid")
    }

    /**
     * 头栏宽度自检：把网格头栏在多种组件宽度下渲染出来堆在一屏，
     * 一眼就能看出某档宽度下会不会"日期压到隔壁星期"（列宽 ~28dp 时曾连续踩坑三轮）。
     */
    private fun showHeaderSweep(hDp: Int) {
        val isSystemDark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val isDark = com.imsx3d.classy.util.AppPrefs.isDarkMode(this, isSystemDark)
        val themeKey = com.imsx3d.classy.util.AppPrefs.getThemeKey(this)
        val monday = java.time.LocalDate.now()
            .with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        val days = (1..7).map { d ->
            DayData(
                date = monday.plusDays((d - 1).toLong()),
                dayOfWeek = d,
                courses = if (d == 1) samplesForDay(1) else emptyList(),
                timeJson = com.imsx3d.classy.util.TimeTableUtils.DEFAULT_TIME_JSON
            )
        }
        val data = WeekData(
            days = days, hasTable = true, isDark = isDark, themeKey = themeKey,
            showDate = true, visibleDays = (1..7).toSet()
        )
        val density = resources.displayMetrics.density
        // 头栏只有 ~34dp 高，这里给 90dp：够看头栏 + 一行底色，堆 5 档也不超屏
        val rowH = (hDp.coerceIn(70, 120))
        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(0xFF1A1A2E.toInt())
            setPadding(0, 40, 0, 40)
        }
        for (w in listOf(240, 272, 320, 380, 420)) {
            val bmp = WeekGridWidgetProvider.renderBitmap(
                this, data, (w * density).toInt(), (rowH * density).toInt()
            )
            val img = android.widget.ImageView(this).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                setImageBitmap(bmp)
                contentDescription = "header sweep w=${w}dp"
            }
            root.addView(
                img,
                android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    (rowH * density).toInt()
                )
            )
        }
        val scroll = android.widget.ScrollView(this).apply { addView(root) }
        setContentView(scroll)
        setTitle("头栏自检 240/272/320/380/420dp")
    }

    /** 样例周的课程（形状参照真实课表：1-2 节早课、5-6/7-8 节下午课、9-11 节晚课 + 长课名）。 */
    private fun samplesForDay(day: Int): List<com.imsx3d.classy.data.entity.CourseEntity> {
        fun c(name: String, room: String, teacher: String, node: Int, step: Int) =
            com.imsx3d.classy.data.entity.CourseEntity(
                groupId = name + room,
                tableId = 0L,
                courseName = name,
                teacher = teacher,
                room = room,
                day = day,
                startNode = node,
                step = step,
                startWeek = 1,
                endWeek = 20,
                color = ""
            )
        return when (day) {
            1 -> listOf(
                c("管理学原理", "教4502", "张艺煜", 1, 2),
                c("欧洲古典音乐欣赏（公选）", "人文3-202", "徐若煌", 9, 3)
            )
            2 -> listOf(
                c("中级财务会计II", "教2604", "", 1, 2),
                c("大学生思想政治理论课社会实践1", "教4205", "张艺煜", 9, 3)
            )
            3 -> listOf(
                c("习近平新时代中国特色社会主义思想概论", "教4104", "", 5, 2),
                c("微观经济学", "实3204", "", 7, 2)
            )
            4 -> listOf(
                c("线性代数B", "人文3-103", "", 1, 2),
                c("中级财务会计II", "教4502", "", 3, 2)
            )
            5 -> listOf(c("大学体育3（定向运动）", "田径场", "", 5, 2))
            else -> emptyList()
        }
    }

    private data class Sample(
        val name: String, val meta: String, val time: String, val bar: Int
    )

    private companion object {
        /**
         * 预览顶行左槽（课表名）的样例文本 —— 与真机顶行同格式，但**不读真实数据库**：
         * 预览/出图不能把用户课表名泄露进随包资源。
         */
        const val PREVIEW_TABLE_NAME = "示例课表"

        /**
         * 预览顶行右槽（"周X M/D"）—— 与 CourseRowPusher 的 headerDate 同口径
         * （今天档 = dayName + dateLabel，近日档 = dayName + shortDateSlash，两者同形）。
         */
        fun previewHeaderDate(): String {
            val today = java.time.LocalDate.now()
            return "${DateUtils.localizedDay(today.dayOfWeek.value, SleepyApp.get())} " +
                DateUtils.shortDateSlash(today)
        }

        // 样例色取自 App 网格的课程配色观感（仅预览用）
        val SAMPLE_TODAY = listOf(
            Sample("管理学原理", "教4502 · 张艺煜", "第1-2节 · 08:25-10:00", 0xFFB98CE0.toInt()),
            Sample("中级财务会计II", "教2604", "第3-4节 · 10:15-11:50", 0xFFF27B7B.toInt()),
            Sample("大学体育3（定向运动）", "田径场", "第5-6节 · 13:55-15:30", 0xFFF28F8F.toInt()),
            Sample("欧洲古典音乐欣赏（公选）", "人文3-202 · 徐若煌", "第9-11节 · 18:30-21:05", 0xFF8FD6C0.toInt())
        )
        val SAMPLE_TWODAY = listOf(
            Sample("管理学原理", "今天 · 教4502", "第1-2节 · 08:25-10:00", 0xFFB98CE0.toInt()),
            Sample("中级财务会计II", "今天 · 教2604", "第3-4节 · 10:15-11:50", 0xFFF27B7B.toInt()),
            Sample("微观经济学", "明天 · 实3204", "第7-8节 · 15:45-17:20", 0xFFF27B7B.toInt()),
            Sample("线性代数B", "明天 · 人文3-103", "第3-4节 · 10:15-11:50", 0xFFF27B7B.toInt()),
            Sample("大学体育3（定向运动）", "明天 · 田径场", "第5-6节 · 13:55-15:30", 0xFFF28F8F.toInt())
        )
    }
}
