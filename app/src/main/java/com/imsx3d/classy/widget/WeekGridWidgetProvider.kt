package com.imsx3d.classy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import android.view.View
import android.widget.RemoteViews
import com.imsx3d.classy.R
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.util.AppPrefs
import com.imsx3d.classy.util.CourseColorUtil
import com.imsx3d.classy.util.HolidayManager
import com.imsx3d.classy.util.CourseDisplayUtil
import com.imsx3d.classy.util.DateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * v19: WeekGrid widget — RemoteViews + Bitmap + Canvas
 *
 * 为什么不用 Glance: Glance 1.1.0 转 RemoteViews 时 LinearLayout 丢 Period 11+ child
 * Canvas 在 Bitmap 上画, 不受 LinearLayout child 数量限制, Period 1~9999 全显示
 *
 * 视觉复刻 CourseTableView: 圆角卡片 + gap + today 高亮 + 课程名居中
 */
open class WeekGridWidgetProvider : AppWidgetProvider() {

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 小组件排版档位 — 基类默认 REGULAR(现有变体); 「本周课表（网格）· 小」子类覆写为 SMALL */
    open val variantHint: WidgetVariant = WidgetVariant.REGULAR

    /**
     * ANR 修复: onUpdate/onAppWidgetOptionsChanged 在主线程回调,
     * 原实现 renderWidget 内含 runBlocking(DB) + Canvas 重活 → 主线程阻塞 → ANR。
     * 改用 goAsync() 获取 PendingResult, 在后台线程做完 DB 加载 + Bitmap 渲染后 finish。
     * 系统广播 ANR 阈值(前台~10s/后台~60s)由 goAsync 续命, 实际工作在 Dispatchers.Default。
     */
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
                    try { renderWidget(context, awm, id) }
                    catch (e: Throwable) { Log.e(TAG, "render failed $id", e) }
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context, awm: AppWidgetManager, id: Int, newOptions: android.os.Bundle
    ) {
        val pending = goAsync()
        ioScope.launch {
            try { renderWidget(context, awm, id) }
            catch (e: Throwable) { Log.e(TAG, "optionsChanged render failed $id", e) }
            finally { pending.finish() }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (id in appWidgetIds) { WidgetBindingStore.remove(context, id) }
    }

    /**
     * 推送网格组件（真实布局版，UI-4k）。
     *
     * 不再画 Canvas 位图：表头 + 节次行 ListView + 行内 7 个真实格子，全部走 RemoteViews。
     * 位图版留在本文件里（`renderBitmap`）供工具页/回滚使用，生产路径已不经过它。
     */
    // suspend: 表头要查当天节日名（UI-6b）；调用点在 ioScope.launch 里
    private suspend fun renderWidget(context: Context, awm: AppWidgetManager, widgetId: Int) {
        val gen = WidgetResizeCore.bump(widgetId)
        val data = loadWeekData(context, widgetId)
        val scheme = resolveSchemePublic(context, data.themeKey, data.isDark)
        val views = RemoteViews(context.packageName, R.layout.widget_weekgrid_card)

        // 卡片底跟随 App 主题（不是系统日夜）—— 外层 FrameLayout 的圆角裁剪负责形状
        views.setInt(R.id.wg_card, "setBackgroundColor", scheme.bg.toArgb())

        // 表头：一行星期 + 一行日期；今天整列用主色胶囊包住（用户点名的"今日选中提示"）
        val visible = data.visibleDays.sorted()
        val dayIds = intArrayOf(
            R.id.wg_hdr1_day, R.id.wg_hdr2_day, R.id.wg_hdr3_day, R.id.wg_hdr4_day,
            R.id.wg_hdr5_day, R.id.wg_hdr6_day, R.id.wg_hdr7_day
        )
        val dateIds = intArrayOf(
            R.id.wg_hdr1_date, R.id.wg_hdr2_date, R.id.wg_hdr3_date, R.id.wg_hdr4_date,
            R.id.wg_hdr5_date, R.id.wg_hdr6_date, R.id.wg_hdr7_date
        )
        val pillIds = intArrayOf(
            R.id.wg_hdr1_pill, R.id.wg_hdr2_pill, R.id.wg_hdr3_pill, R.id.wg_hdr4_pill,
            R.id.wg_hdr5_pill, R.id.wg_hdr6_pill, R.id.wg_hdr7_pill
        )
        val primary = scheme.primary.toArgb()
        // WidgetScheme 没有 onPrimary 槽位；浅色各预设主题的 onPrimary 均为白。
        // UI-4r（用户报障"深色下胶囊刺眼、字与底糊"）：与 App 网格表头同一套规则 ——
        // 深色下主色是浅色调（亮青），实心铺 + 白字 = 糊且刺眼 → 改主色半透明暗底 + 主色当字色。
        // 数值集中在 util/TodayHighlight.kt，改一处两端一起变。
        val darkTheme = scheme.isDark
        val pillFill = if (darkTheme) {
            com.imsx3d.classy.util.TodayHighlight.darkContainerArgb(primary)
        } else primary
        val pillInk = if (darkTheme) primary else android.graphics.Color.WHITE
        val onSurface = scheme.onSurface.toArgb()
        val onVariant = scheme.onSurfaceVariant.toArgb()
        for (i in 0 until 7) {
            val dow = visible.getOrNull(i)
            if (dow == null) {
                views.setTextViewText(dayIds[i], "")
                views.setTextViewText(dateIds[i], "")
                views.setViewVisibility(pillIds[i], android.view.View.INVISIBLE)
                continue
            }
            val day = data.days.firstOrNull { it.dayOfWeek == dow }
            val isToday = day != null && DateUtils.isDateToday(day.date)
            // UI-6b（用户令"照抄 wakeup 的节日名"）：wakeup 的组件把节日名直接写在日名那一行
            //（9/30「烈士纪念日」、10/1「国庆节」），比只看"周X"信息量大。
            // 有节日名 → 用节日名替掉"周X"（列序本身就隐含星期几，日期在下一行）；
            // 休/补 标记拼在日期后面（"1休"），一眼知道那天放不放假。
            val holidayName = if (day != null) {
                runCatching { HolidayManager.dayHolidayName(context, day.date) }.getOrNull()
            } else null
            val marker = if (day != null) {
                runCatching { HolidayManager.dayMarker(context, day.date) }.getOrNull()
            } else null
            views.setTextViewText(
                dayIds[i],
                holidayName ?: DateUtils.localizedDay(dow, SleepyApp.get())
            )
            views.setTextViewText(
                dateIds[i],
                if (day == null) "" else "${day.date.dayOfMonth}${marker ?: ""}"
            )
            if (isToday) {
                views.setViewVisibility(pillIds[i], android.view.View.VISIBLE)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    views.setColorStateList(
                        pillIds[i], "setBackgroundTintList",
                        android.content.res.ColorStateList.valueOf(pillFill)
                    )
                } else {
                    views.setInt(pillIds[i], "setBackgroundColor", pillFill)
                }
            } else {
                views.setViewVisibility(pillIds[i], android.view.View.INVISIBLE)
            }
            views.setTextColor(dayIds[i], if (isToday) pillInk else onSurface)
            views.setTextColor(dateIds[i], if (isToday) pillInk else onVariant)
        }

        // 角落：学期状态（学期前/后给用户一个说明；学期中留空）
        val cornerText = when (data.semesterStatus) {
            DateUtils.SemesterStatus.BEFORE_START -> SleepyApp.get().getString(R.string.semester_not_started)
            DateUtils.SemesterStatus.AFTER_END -> SleepyApp.get().getString(R.string.semester_ended)
            else -> ""
        }
        views.setTextViewText(R.id.wg_corner, cornerText)
        views.setTextColor(R.id.wg_corner, onVariant)

        // 空态 + 节次行列表
        val hasAny = data.days.any { it.courses.isNotEmpty() }
        views.setTextViewText(
            R.id.wg_empty,
            if (data.hasTable) SleepyApp.get().getString(R.string.semester_ended) else
                SleepyApp.get().getString(R.string.widget_create_schedule)
        )
        views.setTextColor(R.id.wg_empty, onVariant)
        views.setEmptyView(R.id.wg_rows, R.id.wg_empty)

        val svcIntent = Intent(context, WeekGridRowService::class.java).apply {
            putExtra(WeekGridRowFactory.EXTRA_WIDGET_ID, widgetId)
            this.data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        views.setRemoteAdapter(R.id.wg_rows, svcIntent)
        val template = PendingIntent.getActivity(
            context, WidgetRoutes.tapRequestCode(widgetId), WidgetRoutes.tapIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setPendingIntentTemplate(R.id.wg_rows, template)
        // 表头 / 空白区（非行区域）也要能点开 app
        views.setOnClickPendingIntent(R.id.wg_card, template)

        if (gen > 0 && WidgetResizeCore.isStale(widgetId, gen)) {
            Log.d(TAG, "skip stale id=$widgetId gen=$gen")
            return
        }
        awm.updateAppWidget(widgetId, views)
        awm.notifyAppWidgetViewDataChanged(widgetId, R.id.wg_rows)
        Log.d(TAG, "renderWidget real-layout id=$widgetId hasAny=$hasAny visible=$visible")
    }


    companion object {
        private const val TAG = "WeekGridV19"

        /**
         * §4.4 降级阶梯几何 — bodyH(px)/slotH(px) 推导 (渲染器与契约测试单一事实来源)。
         * UI-4d（2026-09-26 用户报障"星期栏是古早样式、竖向占空间极大"）: headH 56dp → 34dp,
         * 与 App 的紧凑星期头（UI-2c）同值 —— 组件星期栏不再是大圆角胶囊，省下的 22dp 全给课程区。
         * 与旧内联算式同式（仅 headH 变）: outerPad 6dp×2 + headH 34dp, bodyH 地板 20dp,
         * 节间隙 1.5dp×(n+1), slotH 地板 3dp (整除口径保持 Int / Int)。
         */
        internal fun weekGridBodyGeomPx(hPx: Int, density: Float, maxNode: Int): Pair<Int, Float> {
            fun dp(v: Float) = (v * density).roundToInt()
            val bodyH = (hPx - dp(6f) * 2 - dp(HEAD_H_DP)).coerceAtLeast(dp(20f))
            val totalGapH = dp(1.5f) * (maxNode + 1)
            val slotH = ((bodyH - totalGapH) / maxNode).toFloat().coerceAtLeast(dp(3f).toFloat())
            return bodyH to slotH
        }

        /** §4.4 降级阶梯末档: 单节 slotH < 9dp → 文字行排不下, 切色带模式 (无文字非空白) */
        internal fun weekGridColorBand(slotHPx: Float, density: Float): Boolean =
            slotHPx < (9f * density).roundToInt()

        /**
         * 可读单节最小高度（dp）—— UI-4b（2026-09-26 用户报障"字体形变严重"）。
         *
         * 旧行为：无论容器多矮都把 maxNode 个节次压进容器高度（slotH 地板 3dp，<9dp 直接
         * 退化成无文字色带）→ 12 节次的周网格在 4×4 组件里每行只有 ~11dp，
         * 课名只能画"1 个字 + …"（用户截图），这就是"形变"的根因。
         * 新行为：低于该高度就不压了 —— 按可读高度渲染长图 + 交给可滚动条带（WakeUp 的做法）。
         * 真机实测（380dp 宽 / 7 列，每列 ~34dp）：40dp 卡片只放得下 2 行课名，
         * "中级财务会计II" 这种 7 字课名必然截断 → 调到 60dp（≈4 行课名 + 教室小字 + 内边距），
         * 与 wakeup 的周组件同量级；代价是纵向更长、可滑动（与其"不省略、可滑"的口径一致）。
         */
        internal const val MIN_READABLE_SLOT_DP = 60f

        /** 星期头条高（dp）—— 与 App 紧凑头同值（UI-4d：原 56dp 的大胶囊行改成一行式）。 */
        internal const val HEAD_H_DP = 34f

        /** 可读排版所需总高（px）: outerPad×2 + headH + maxNode×(slot+gap) + gap。 */
        internal fun weekGridRequiredHPx(density: Float, maxNode: Int): Int {
            fun dp(v: Float) = (v * density).roundToInt()
            return dp(6f) * 2 + dp(HEAD_H_DP) +
                maxNode * (dp(MIN_READABLE_SLOT_DP) + dp(1.5f)) + dp(1.5f)
        }

        /**
         * 网格需要渲染的最大节次数（= 课表末节次与课程末节次的较大者）。
         * 渲染与"可读高度"推导共用，避免两处口径漂移。
         */
        /** 每节的开始时间（下标 0 = 第 1 节）—— 真实布局版的时间栏用。 */
        internal fun timeSlotStarts(timeJson: String): List<String> = parseTimeSlots(timeJson)

        internal fun weekGridMaxNode(data: WeekData): Int {
            val slots = parseTimeSlots(data.days.firstOrNull()?.timeJson ?: "")
            return (data.days.flatMap { it.courses }
                .maxOfOrNull { it.startNode + it.step - 1 } ?: slots.size)
                .coerceAtLeast(1)
        }

        /**
         * 教室角标几何钳制 — 标签字号不得超底部预留带, 基线不得越过带外。
         * 极矮卡上旧代码 roomSize 下限 5dp 可大于预留带, 标签压进竖排课名区 = 挤占。
         * 返回 (clampedSize, baseline): size ≤ 预留带高, 基线锚定卡底--pad-size×0.3。
         * 纯函数 — 渲染与单测单一事实来源。
         */
        internal fun weekGridRoomLabelLayout(
            cardBottom: Float,
            unifiedPad: Float,
            roomReserveH: Float,
            requestedSize: Float
        ): Pair<Float, Float> {
            val size = requestedSize.coerceAtMost((roomReserveH / 1.1f).coerceAtLeast(0f))
            val baseline = cardBottom - unifiedPad - size * 0.3f
            return size to baseline
        }

        fun renderBitmap(context: Context, data: WeekData, wPx: Int, hPx: Int): Bitmap {
            val density = context.resources.displayMetrics.density
            val isDark = data.isDark

            // ── 颜色 (跟随主题: resolveSchemePublic 支持 system=动态取色) ──
            // 之前硬编码紫色十六进制 → 小组件永远紫色, 不跟随 app / 系统壁纸取色
            val scheme = resolveSchemePublic(context, data.themeKey, isDark)
            fun androidx.compose.ui.graphics.Color.toIntArgb(): Int =
                (0xFF shl 24) or ((this.red * 255).toInt() shl 16) or
                    ((this.green * 255).toInt() shl 8) or (this.blue * 255).toInt()
            val bgSurface       = scheme.surface.toIntArgb()
            val bgContainer     = scheme.surfaceContainer.toIntArgb()
            val fgPrimary       = scheme.primary.toIntArgb()
            val fgOnSurface     = scheme.onSurface.toIntArgb()
            val fgOnSurfaceVar  = scheme.onSurfaceVariant.toIntArgb()
            val gridLine        = scheme.surfaceVariant.toIntArgb()
            val colorless       = AppPrefs.isWidgetColorless(context)

            // v23: 课程颜色完全对齐 CourseTableView — 黄金角 HSL 分配
            // hue = groupId.hashCode() * 137.508° → 相邻课色差最大化, 同门课永远同色
            // 亮色 S=0.55 L=0.82 (粉彩), 暗色 S=0.40 L=0.28 (沉稳)
            // 用户自定义 color 优先 (#FF6750A4 视为未设置)
            // (本地 hslToColorInt/pickCourseColor 副本已收敛至 util/CourseColorUtil.kt, 决策 D3)

            // ── 数据 ──
            val timeJson = data.days.firstOrNull()?.timeJson ?: ""
            val allSlots = parseTimeSlots(timeJson)
            // UI-4b: maxNode 推导抽到 weekGridMaxNode（渲染与"可读高度"共用同一口径）
            val maxNode = weekGridMaxNode(data)
            // issue#22: 同名课程多地点 — 跨天汇总 course 全集,传给 pickCourseColorIntWithGroupRows
            val allCourses = data.days.flatMap { it.courses }
            val slots = allSlots.take(maxNode)
            val sortedDays = data.visibleDays.sorted()
            val dayCount = sortedDays.size.coerceIn(1, 7)
            val todayDow = LocalDate.now().dayOfWeek.value

            // ── 布局 (dp → px, 跟 CourseTableView 同参数) ──
            val dp = { v: Float -> (v * density).roundToInt() }
            // v19c 字号参数 (用户原话: "你这个字号明显是不合格的")
            // 之前 headH*0.30 cap dp(15f) 太大, day header 文字溢出 cell 边界全挤在一起
            // 改成: cap 降到 dp(13f), min 升到 dp(10f), 文字宽度永远 < dayW - padding
            val outerPad = dp(6f)
            val headH = dp(HEAD_H_DP)
            val timeW = dp(40f)
            val gapH = dp(1.5f)
            val gapW = dp(2.5f)

            val bodyW = wPx - outerPad * 2
            // §4.4 降级阶梯几何单一事实来源 (与 weekGridBodyGeomPx 契约测试同源)
            val (bodyH, slotH) = weekGridBodyGeomPx(hPx, density, maxNode)
            val totalGapW = gapW * (dayCount + 1)
            val dayW = ((bodyW - timeW - totalGapW) / dayCount)
                .toFloat().coerceAtLeast(dp(20f).toFloat())  // 下限: 防 launcher 返极小宽度致负数

            Log.d(TAG, "w=${wPx}x${hPx} maxNode=$maxNode dayCount=$dayCount " +
                "slotH=${slotH}px dayW=${dayW}px headH=${headH}px")

            // ── Canvas ──
            val bmp = Bitmap.createBitmap(wPx, hPx, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            // UI-4g 真机实证: 位图里会残留"上一次绘制"的内容（把表头临时改成红色后，
            // 红色新字下面压着黑色旧字 = 同一张位图被画了两遍）。开画前显式清屏兜底，
            // 不依赖 createBitmap 的零填充语义。
            c.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)

            // 背景: 圆角容器 — UI-4（2026-09-26）: 18dp → 12dp，与 App 的 Glasense 卡片同值
            p.color = bgContainer
            val containerRect = RectF(0f, 0f, wPx.toFloat(), hPx.toFloat())
            c.drawRoundRect(containerRect, dp(12f).toFloat(), dp(12f).toFloat(), p)

            // 空状态: 无课表时显示占位提示, 不渲染空白网格
            // 学期后课程被清空 → 落到这分支; 学期状态文案优先于"去创建课表"
            if (!data.hasTable || data.days.isEmpty() || data.days.all { it.courses.isEmpty() }) {
                val ctx = SleepyApp.get()
                p.textAlign = Paint.Align.CENTER
                p.color = fgOnSurface
                p.textSize = dp(15f).toFloat()
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                if (data.semesterStatus != DateUtils.SemesterStatus.IN_RANGE) {
                    val statusRes = if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START)
                        R.string.semester_not_started else R.string.semester_ended
                    c.drawText(ctx.getString(statusRes), wPx / 2f, hPx / 2f - dp(8f), p)
                    p.textSize = dp(11f).toFloat()
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    p.color = fgOnSurfaceVar
                    c.drawText(ctx.getString(R.string.today_semester_out_hint),
                        wPx / 2f, hPx / 2f + dp(12f), p)
                } else {
                    c.drawText(ctx.getString(R.string.widget_create_schedule),
                        wPx / 2f, hPx / 2f - dp(8f), p)
                    p.textSize = dp(11f).toFloat()
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    p.color = fgOnSurfaceVar
                    c.drawText(ctx.getString(R.string.widget_open_sleepy),
                        wPx / 2f, hPx / 2f + dp(12f), p)
                }
                return bmp
            }

            // ── Header (Day labels) ──
            var x = outerPad.toFloat()
            var y = outerPad.toFloat()

            // v19b: 字号完全根据 widget 宽高自适应 (用户原话: "能不能自动根据这个宽度, 高度调整")
            // 1dp 永远 = 1dp, 但用 widget 尺寸作为 scale 单位
            // dayW = (bodyW-timeW) / 7, cardH = slotH * step
            // 单节 course 卡片: cardH = slotH (小卡), 多节: cardH = slotH*N (大卡)

            // ── 紧凑星期头（UI-4d）──
            // 一行 = 星期文字 + 日期（今天用主色、加粗）。旧版每格画 56dp 高的大圆角胶囊
            // （角落还画一个空白胶囊），纵向吃掉 22dp 且与 App 形态割裂 —— 用户报障点。
            p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            if (data.semesterStatus == DateUtils.SemesterStatus.BEFORE_START) {
                // 学期前(课照常显示供预习): 角落给一行小字，用户知道现在学期没开始
                p.color = fgOnSurfaceVar
                p.textSize = (headH * 0.30f).coerceAtMost(dp(10f).toFloat()).coerceAtLeast(dp(7f).toFloat())
                p.textAlign = Paint.Align.CENTER
                c.drawText(
                    SleepyApp.get().getString(R.string.semester_not_started),
                    x + timeW / 2f, y + headH * 0.62f, p
                )
            }
            for ((idx, dow) in sortedDays.withIndex()) {
                val cellX = x + timeW + gapW + idx * (dayW + gapW)
                val dayData = data.days.firstOrNull { it.dayOfWeek == dow }
                val isToday = dayData != null && DateUtils.isDateToday(dayData.date)
                val cx = cellX + dayW / 2f
                val dayName = DateUtils.localizedDay(dow, SleepyApp.get())
                val dateStr = dayData?.date?.dayOfMonth?.toString()

                // UI-4j（用户第三次报障头栏）: **不再横排"周一 21"**。
                // 真机实测：组件 272dp / 7 列 → 单列仅 ~28dp（85px），
                // 而"周一 21"横排要 ~110px → 日期必然压到隔壁列的星期上，
                // 于是整行看着就是"周一 周二 周三…周日27"（日期只在最后一列露出来）。
                // 改成**两行**：上面星期、下面日期，各自居中在本列内 → 结构上不可能串列。
                // 字号由列宽反推（宁可小一点也不越界），并把上下两条基线固定在头栏内。
                val usableW = (dayW - dp(4f)).coerceAtLeast(dp(8f).toFloat())
                val nameByWidth = usableW / 2.2f          // 「周一」两个字 + 余量
                val nameSize = minOf(headH * 0.38f, dp(12.5f).toFloat(), nameByWidth)
                    .coerceAtLeast(dp(8f).toFloat())
                val dateByWidth = usableW / 2.4f          // 两位日期
                val dateSize = minOf(headH * 0.30f, dp(10f).toFloat(), dateByWidth)
                    .coerceAtLeast(dp(7f).toFloat())

                p.textAlign = Paint.Align.CENTER
                p.typeface = Typeface.create(
                    Typeface.DEFAULT, if (isToday) Typeface.BOLD else Typeface.NORMAL
                )
                p.color = if (isToday) fgPrimary else fgOnSurface
                p.textSize = nameSize
                val nameBaseline = if (dateStr == null) y + headH * 0.64f else y + headH * 0.44f
                c.drawText(dayName, cx, nameBaseline, p)
                if (dateStr != null) {
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    p.color = if (isToday) fgPrimary else fgOnSurfaceVar
                    p.textSize = dateSize
                    c.drawText(dateStr, cx, y + headH * 0.88f, p)
                }
            }

            // ── Body ──
            y = (outerPad + headH).toFloat()
            val bodyTop = y

            // §4.4 降级阶梯末档: slotH 小到文字行排不下 (单节卡高 <9dp) → 色带模式:
            // 日头保留, 主体只画课程色条 (冲突课按 lane 分宽), 无任何文字。任意高度非空白。
            if (weekGridColorBand(slotH, density)) {
                for ((idx, dow) in sortedDays.withIndex()) {
                    val colX = x + timeW + gapW + idx * (dayW + gapW)
                    val dayData = data.days.firstOrNull { it.dayOfWeek == dow } ?: continue
                    // UI-4d: 色带模式下同样不再铺"今天"列底
                    for (laneRect in com.imsx3d.classy.util.ConflictLayoutEngine
                            .gridDayLanes(dayData.courses, dayData.timeJson)) {
                        val course = laneRect.course
                        val startIdx = (course.startNode - 1).coerceAtLeast(0)
                        val step = course.step.coerceAtLeast(1).coerceAtMost(maxNode - startIdx)
                        val top = bodyTop + gapH + startIdx * (slotH + gapH)
                        val barH = (slotH * step + gapH * (step - 1)).coerceAtLeast(1f)
                        val laneX = colX + dayW * laneRect.laneStartFraction
                        val laneW = dayW * laneRect.laneWidthFraction
                        p.color = CourseColorUtil.pickCourseColorIntWithGroupRows(
                            course, allCourses.filter { it.groupId == course.groupId },
                            isDark, gridLine, colorless
                        )
                        p.alpha = 200
                        val r = minOf(dp(4f).toFloat(), barH / 2f)
                        c.drawRoundRect(RectF(laneX, top, laneX + laneW, top + barH), r, r, p)
                        p.alpha = 255
                    }
                }
                return bmp
            }

            // time column labels
            p.textAlign = Paint.Align.CENTER
            for (i in 1..maxNode) {
                val rowY = bodyTop + gapH + (i - 1) * (slotH + gapH)
                val slot = slots.getOrNull(i - 1)

                // period number 字号 = slotH * 0.40 (降比例)
                p.color = fgOnSurface
                p.textSize = (slotH * 0.40f)
                    .coerceAtMost(dp(13f).toFloat())
                    .coerceAtLeast(dp(8f).toFloat())
                p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val cy = rowY + slotH / 2f + p.textSize * 0.35f
                c.drawText("$i", x + timeW / 2f, cy, p)

                // time label 字号 = slotH * 0.20 (降比例)
                if (slot != null && slotH > dp(18f)) {
                    p.color = fgOnSurfaceVar
                    p.textSize = (slotH * 0.20f)
                        .coerceAtMost(dp(7f).toFloat())
                        .coerceAtLeast(dp(4f).toFloat())
                    p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    c.drawText(slot, x + timeW / 2f, cy + p.textSize * 1.6f, p)
                }
            }

            // v21: 竖排(直书) — token 化 + 拉丁组旋转 + 标点优化
            val useVertForms = AppPrefs.isVertPunctReplace(context)  // 方案B开关(默认false=方案A'旋转)
            // issue#26: widget 场景别名 — 字号预算与绘制必须用同一个名字, 否则截断不一致
            val useAlias = AppPrefs.isWidgetUseAlias(context)

            // v20b: 字号统一到「全表最小理想值」— 自适应算法 + 统一字号
            // 每卡按 cardH/unitHeight 算理想字号(v21: 用 token 单位高度替代旧字数)
            // → 全表取最小 → 所有卡用同一个字号(整齐)
            // 下限 11dp 保可读; 上限对齐表头"周一/周二"字号(用户原话: 课名字号最大不能超过周一周二)
            // UI-4d 修正: 原实现把"课名字号上限"挂在表头高度上（headH*0.24, 上限 dp13）——
            // 表头 56dp→34dp 之后 (headH*0.24)=24.5px < 下限 dp(11)=33px → coerceIn 空区间直接崩
            // （真机上表现就是组件"载入窗口小部件时出现问题"）。课名字号本就该由**卡片高度**决定，
            // 与表头无关：上限固定 dp(13)（与 App 网格的课名字号同档），再受卡宽约束。
            val unifiedPad = dp(3f).toFloat()
            val nameMinDp = dp(11f).toFloat()   // 可读下限
            val nameMaxDp = dp(13f).toFloat()   // 可读上限（与 App 网格课名同档）
            val dayAvailW = (dayW - unifiedPad * 2).coerceAtLeast(dp(8f).toFloat())
            val nameMaxPxByW = dayAvailW * 0.92f
            val nameCeil = minOf(nameMaxDp, nameMaxPxByW)
            val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG)

            // 遍历所有课程算每卡理想字号, 取全表最小 → unifiedCharSize
            var minIdeal = nameCeil  // 初始=上限, 任何卡都会更小
            for (dow in sortedDays) {
                val dd = data.days.firstOrNull { it.dayOfWeek == dow } ?: continue
                for (course in dd.courses) {
                    val step = course.step.coerceIn(1, maxNode)
                    val cardH = slotH * step + gapH * (step - 1)
                    val hasRoom = course.room.isNotBlank()
                    // v22: 真实可用高度(不夹下限 → 矮卡算真实空间) + 自适应 room 预留
                    val availCardHPre = (cardH - unifiedPad * 2).coerceAtLeast(0f)
                    val roomReservePre = if (hasRoom) (nameMinDp * 0.7f).coerceAtMost(availCardHPre * 0.35f) else 0f
                    val nameAvailH = (availCardHPre - roomReservePre).coerceAtLeast(0f)
                    // v21: token 单位高度(拉丁组旋转省空间 → unit<字数 → 统一号可能更大)
                    val tokens = tokenizeName(CourseDisplayUtil.displayName(course, useAlias), useVertForms)
                    val unitH = measureUnitHeight(tokens, measurePaint).coerceAtLeast(1f)
                    val hi = nameCeil.coerceAtLeast(nameMinDp)   // 防"空区间"崩溃
                    val ideal = (nameAvailH / unitH).coerceIn(nameMinDp, hi)
                    if (ideal < minIdeal) minIdeal = ideal
                }
            }
            val unifiedCharSize = minIdeal
            Log.d(TAG, "v21 unifiedCharSize=${unifiedCharSize.toInt()}px vertForms=$useVertForms (全表最小理想字号, token化) nameMin=${nameMinDp.toInt()}px nameMax=${nameCeil.toInt()}px slotH=${slotH}px dayW=${dayW}px")

            // day columns
            for ((idx, dow) in sortedDays.withIndex()) {
                val colX = x + timeW + gapW + idx * (dayW + gapW)
                val dayData = data.days.firstOrNull { it.dayOfWeek == dow } ?: continue
                val isToday = DateUtils.isDateToday(dayData.date)

                // today 背景列
                // UI-4d: 不再给"今天"整列铺淡色底（用户点名的那条竖条）——
                // 今天由表头主色文字表达（与 App 网格一致），整列铺色既冗余又压课块。

                // 课程卡片
                // v7.10.8: 冲突课分栏 — 与 App 周视图同一引擎(ConflictLayoutEngine.gridDayLanes),
                // 冲突区域内的课并排各占 1/N 列宽, 无冲突课整列宽。旧实现所有课画满整列宽,
                // 同节次课互相覆盖(后画盖先画), 小组件上冲突课信息丢失。
                val laneRects = com.imsx3d.classy.util.ConflictLayoutEngine.gridDayLanes(dayData.courses, dayData.timeJson)
                for (laneRect in laneRects) {
                    val course = laneRect.course
                    val startIdx = (course.startNode - 1).coerceAtLeast(0)
                    val step = course.step.coerceAtLeast(1)
                        .coerceAtMost(maxNode - startIdx)
                    val cardTop = bodyTop + gapH + startIdx * (slotH + gapH)
                    val cardH = slotH * step + gapH * (step - 1)
                    // 分栏: 横向按引擎给的起点/宽度比例收缩列宽
                    val laneX = colX + dayW * laneRect.laneStartFraction
                    val laneW = dayW * laneRect.laneWidthFraction
                    val cardRect = RectF(laneX, cardTop, laneX + laneW, cardTop + cardH)

                    // 卡片背景色 (v19e: 对齐 CourseTableView palette) — 统一入口 CourseColorUtil (决策 D3)
                    // colorless 灰底传 gridLine(即 surfaceVariant 的 Int), 与原实现一致
                    val baseColor = CourseColorUtil.pickCourseColorIntWithGroupRows(
                        course, allCourses.filter { it.groupId == course.groupId },
                        isDark, gridLine, colorless
                    )
                    p.color = baseColor
                    // UI-4（2026-09-26）: 原 alpha 200（半透明）会让色块与浅灰面板混成一片、看着发灰；
                    // App 网格里的课程块是实色 → 统一为实色（12dp 圆角与 App 卡片同值，原 10dp）
                    p.alpha = 255
                    c.drawRoundRect(cardRect, dp(4f).toFloat(), dp(4f).toFloat(), p)
                    p.alpha = 255

                    // border
                    p.style = Paint.Style.STROKE
                    p.strokeWidth = dp(0.5f).toFloat()
                    p.color = baseColor
                    p.alpha = 80
                    c.drawRoundRect(cardRect, dp(4f).toFloat(), dp(4f).toFloat(), p)
                    p.style = Paint.Style.FILL
                    p.alpha = 255

                    // v19k: 课名居中独占主体, 教室做底部小字角标
                    // 卡片窄(~40dp), 双列并排挤死 → 改成: 课名竖排居中 + 教室缩到 0.6× 字号横排在底部
                    val textColor = if (isDarkOn(baseColor)) Color.WHITE else 0xFF1D1B20.toInt()
                    p.color = textColor
                    p.textAlign = Paint.Align.CENTER

                    // nameChars 死变量已删 (v21 起 token 化走 tokenizeName, 不再用字符列表)
                    val roomChars = course.room.takeIf { it.isNotBlank() }
                        ?.filter { it != '\n' && it != ' ' }?.toList() ?: emptyList()

                    // v20b: 用全表统一字号(unifiedCharSize), 截断逻辑保留
                    val availCardH = cardRect.height() - unifiedPad * 2
                    val hasRoom = roomChars.isNotEmpty()
                    val roomReserveH = if (hasRoom) (nameMinDp * 0.7f).coerceAtMost(availCardH * 0.35f) else 0f
                    val nameAvailH = (availCardH - roomReserveH).coerceAtLeast(0f)
                    val charSize = unifiedCharSize

                    // ── 课名 / 教室：交给系统排版（StaticLayout = TextView 同一个引擎）──
                    // UI-4c（2026-09-26 用户报障"字体形变严重"）：旧实现按字符贪心累加、
                    // token 各自旋转/堆叠，空间不够就"截到 1 个字 + …"。现在：
                    //   · 中文按字自动换行 + 水平居中（StaticLayout，与 TextView 同一排版）
                    //   · 只在 maxLines 内真的放不下时才画省略号
                    //   · Latin 整组旋转 90° 那套特殊处理整体退场（真排版不需要）
                    val nameText = CourseDisplayUtil.displayName(course, useAlias)
                    val innerW = (cardRect.width() - unifiedPad * 2).coerceAtLeast(1f).toInt()
                    // UI-4f 真机实测: StaticLayout 的行高 ≈ 1.25×字号（不是 1×），
                    // 按 nameAvailH/charSize 算行数会让末行压到教室小字上（真机截图实测重叠）。
                    val nameLines = (nameAvailH / (charSize * 1.25f)).toInt().coerceIn(1, 5)
                    val nameLayout = cardTextLayout(
                        text = nameText, size = charSize, color = textColor,
                        bold = true, widthPx = innerW, maxLines = nameLines, alpha = 255
                    )
                    val nameTop = cardRect.top + unifiedPad +
                        ((nameAvailH - nameLayout.height).coerceAtLeast(0f)) / 2f
                    c.save()
                    c.translate(cardRect.left + unifiedPad, nameTop)
                    nameLayout.draw(c)
                    c.restore()

                    // 教室：卡底小字一行（真放不下才省略号）
                    if (roomChars.isNotEmpty()) {
                        val roomStr = course.room.filter { it != '\n' && it != ' ' }
                        // UI-4f 真机实测: 0.62×/dp8 在 34dp 列宽下会把"教4502"截成"教4…"
                        // → 0.52×/dp7.5（约 6.8dp 字），完整显示教室/教师
                        val roomRequested = (charSize * 0.52f)
                            .coerceAtMost(dp(7.5f).toFloat()).coerceAtLeast(dp(5f).toFloat())
                        val roomSize = roomRequested
                            .coerceAtMost((roomReserveH / 1.1f).coerceAtLeast(1f))
                        val roomLayout = cardTextLayout(
                            text = roomStr, size = roomSize, color = textColor,
                            bold = false, widthPx = innerW, maxLines = 1, alpha = 160
                        )
                        val roomTop = cardRect.bottom - unifiedPad - roomLayout.height
                        c.save()
                        c.translate(cardRect.left + unifiedPad, roomTop)
                        roomLayout.draw(c)
                        c.restore()
                    }
                    // 循环内 Log.d 渲染调试日志已删（每张课程卡都求值字符串模板, Release 也无法被 R8 消除）
                }
            }

            return bmp
        }

        /**
         * 组件卡片里的一段文本 → StaticLayout（TextView 同一排版引擎）。
         * 中文按字换行、水平居中、maxLines 内放不下才画省略号 —— 不再手数字符/手画省略号。
         * UI-4c（2026-09-26）：替换原先"逐字符累加 + token 旋转"的手写排版。
         */
        private fun cardTextLayout(
            text: String, size: Float, color: Int, bold: Boolean,
            widthPx: Int, maxLines: Int, alpha: Int
        ): android.text.StaticLayout {
            val tp = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            tp.textSize = size
            tp.color = color
            tp.alpha = alpha
            tp.typeface = Typeface.create(
                Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL
            )
            return android.text.StaticLayout.Builder
                .obtain(text, 0, text.length, tp, widthPx.coerceAtLeast(1))
                .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .setMaxLines(maxLines.coerceAtLeast(1))
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()
        }

        internal fun parseTimeSlots(timeJson: String): List<String> {
            return try {
                val arr = org.json.JSONArray(timeJson)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    o.getString("start")
                }
            } catch (e: Exception) {
                // 默认 12 节
                listOf("08:00","08:55","10:00","10:55","14:00","14:55",
                    "16:00","16:55","19:00","19:55","20:50","21:45")
            }
        }

        // ===== v21 竖排(直书) token 化 =====
        // 把课名切成有序 token: CJK run(直立) / Latin run≥2(整组旋转90°) / Latin=1(直立) / 标点
        // 标点处理由 useVertForms 决定: true→替换为 Vertical Forms 直立; false→逐个旋转90°

        /** token 类型 */
        private enum class TT { CJK, LATIN, PUNCT }

        /** 一个 token: 类型 + 文本(已按方案处理过标点替换) */
        private data class NameToken(val type: TT, val text: String)

        /** 标点字符集 — 横排符号, 需特殊处理(旋转或替换) */
        private val PUNCT_CHARS = setOf(
            '(', ')', '（', '）', '〔', '〕', '【', '】', '《', '》', '〈', '〉',
            '「', '」', '『', '』', '[', ']', '{', '}', '〈', '〉',
            '—', '–', '～', '~', '…', '·', '・', '、', '，', '。', '：', '；',
            '！', '？', '”', '“', '’', '‘', '"', '\'', '/', '／', '｜', '|'
        )

        /** 方案B: 横排符号 → Unicode Vertical Forms (U+FE19–FE44) */
        private val VERT_FORM_MAP = mapOf(
            '(' to '︵', '（' to '︵',   // U+FE35
            ')' to '︶', '）' to '︶',   // U+FE36
            '〔' to '︹',                  // U+FE39
            '〕' to '︺',                  // U+FE3A
            '【' to '︻',                  // U+FE3B
            '】' to '︼',                  // U+FE3C
            '《' to '︽',                  // U+FE3D
            '》' to '︾',                  // U+FE3E
            '〈' to '︿',                  // U+FE3F
            '〉' to '﹀',                  // U+FE40
            '「' to '﹁',                  // U+FE41
            '」' to '﹂',                  // U+FE42
            '『' to '﹃',                  // U+FE43
            '』' to '﹄',                  // U+FE44
            '[' to '︻',                  // 复用
            ']' to '︼',                  // 复用
            '{' to '︷',                  // U+FE37
            '}' to '︸',                  // U+FE38
            '—' to '︱',                  // U+FE31
            '…' to '︙'                   // U+FE19
        )

        private fun isLatin(ch: Char): Boolean =
            (ch in 'A'..'Z' || ch in 'a'..'z' || ch in '0'..'9')

        private fun isCJK(ch: Char): Boolean =
            (ch in '一'..'鿿' || ch in '㐀'..'䶿' || ch in '豈'..'﫿')

        /**
         * 课名 → token 列表。先去空白, 再扫描连续 run。
         * useVertForms=true(方案B): 标点替换为 Vertical Forms(变 CJK 直立)
         * useVertForms=false(方案A'): 标点保持原样(绘制时逐个旋转)
         */
        private fun tokenizeName(name: String, useVertForms: Boolean): List<NameToken> {
            val s = name.filter { it != '\n' && it != ' ' }
            if (s.isEmpty()) return emptyList()
            val tokens = ArrayList<NameToken>()
            val sb = StringBuilder()
            var runType: TT? = null

            fun flush() {
                if (sb.isNotEmpty() && runType != null) {
                    tokens.add(NameToken(runType!!, sb.toString()))
                    sb.clear()
                }
                runType = null
            }

            for (ch in s) {
                // 方案B: 标点先替换为 Vertical Forms → 归为 CJK 直立
                val c = if (useVertForms && ch in VERT_FORM_MAP) VERT_FORM_MAP[ch]!! else ch
                val t = when {
                    isCJK(c) -> TT.CJK
                    c in PUNCT_CHARS -> TT.PUNCT
                    isLatin(c) -> TT.LATIN
                    else -> TT.CJK  // 其他字符(含替换后的竖排符号)按 CJK 直立
                }
                if (t != runType) { flush(); runType = t }
                sb.append(c)
            }
            flush()

            // 后处理: LATIN run 长度=1 → 按 spec 保持直立(改判为 CJK 处理即直立)
            return tokens.map { tok ->
                if (tok.type == TT.LATIN && tok.text.length == 1) NameToken(TT.CJK, tok.text) else tok
            }
        }

        /**
         * token 单位高度(与 charSize 无关的比值):
         *   CJK/单字Latin: 每字 1.0
         *   LATIN run≥2(旋转): measureText/textSize (旋转后占高=组宽)
         *   PUNCT(旋转 方案A'): measureText(每字)/textSize
         *   PUNCT 已替换为 VertForms → 走 CJK 路径(每字≈1.0)
         * 用临时 paint 在任意 textSize(如1.0)下测, 比值与绝对字号无关。
         */
        private fun measureUnitHeight(tokens: List<NameToken>, paint: Paint): Float {
            var h = 0f
            for (tok in tokens) {
                when (tok.type) {
                    TT.CJK -> h += tok.text.length * 1f
                    TT.LATIN -> {
                        paint.textSize = 1f
                        h += paint.measureText(tok.text)  // 旋转组占高=组宽
                    }
                    TT.PUNCT -> {
                        paint.textSize = 1f
                        for (ch in tok.text) h += paint.measureText(ch.toString())
                    }
                }
            }
            return h
        }

        private fun isDarkOn(color: Int): Boolean {
            val r = Color.red(color); val g = Color.green(color); val b = Color.blue(color)
            return (0.299 * r + 0.587 * g + 0.114 * b) / 255.0 < 0.55
        }

        fun loadWeekData(context: Context, appWidgetId: Int): WeekData {
            val today = LocalDate.now()
            val isSystemDark = (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDark = AppPrefs.isDarkMode(context, isSystemDark)
            val themeKey = AppPrefs.getThemeKey(context)
            val showDate = AppPrefs.isShowDate(context)
            val visibleDays = AppPrefs.getVisibleDays(context)
            return try {
                // Triple<Table?, Status, List<Pair<dow, courses>>>
                val loaded = kotlinx.coroutines.runBlocking {
                    val app = SleepyApp.get()
                    val repo = app.repository
                    // 选表逻辑：先按 widgetId 取绑定表，未绑定则走 WidgetTableResolver（默认表优先），避免与 App 选中表不同步
                    val t = WidgetTableResolver.resolveBoundTable(appWidgetId)
                        ?: WidgetTableResolver.resolveCurrentTable()
                    val status = if (t != null)
                        DateUtils.semesterStatus(t.startDate, t.maxWeek, today)
                    else DateUtils.SemesterStatus.IN_RANGE
                    val map = if (t != null) {
                        val week = DateUtils.currentWeek(t.startDate, today)
                        (1..7).map { dow ->
                            val date = DateUtils.dateOfWeekDay(today, dow)
                            val courses = HolidayTransferHelper.coursesOn(context, t, date, repo.getCourses(t.id))
                            dow to courses
                        }
                    } else emptyList()
                    Triple(t, status, map)
                }
                val (t, status, daysPerCourse) = loaded
                if (t == null) {
                    WeekData(days = emptyList(), hasTable = false, isDark = isDark,
                        themeKey = themeKey,
                        showDate = showDate, visibleDays = visibleDays)
                } else {
                    val days = daysPerCourse.map { (dow, courses) ->
                        val date = DateUtils.dateOfWeekDay(today, dow)
                        DayData(date = date, dayOfWeek = dow, courses = courses, timeJson = t.timeJson)
                    }
                    WeekData(days = days, hasTable = true, isDark = isDark,
                        themeKey = themeKey,
                        showDate = showDate, visibleDays = visibleDays,
                        semesterStatus = status)
                }
            } catch (e: Throwable) {
                Log.e(TAG, "loadWeekData failed", e)
                WeekData(days = emptyList(), hasTable = false, isDark = isDark,
                    themeKey = themeKey,
                    showDate = showDate, visibleDays = visibleDays)
            }
        }
    }
}
