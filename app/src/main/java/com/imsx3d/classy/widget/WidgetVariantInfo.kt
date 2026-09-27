package com.imsx3d.classy.widget

import android.appwidget.AppWidgetProvider
import com.imsx3d.classy.R

/**
 * Metadata describing one widget variant (base or small).
 *
 * The list [ALL_WIDGET_VARIANTS] is the single source of truth for both the
 * refresh broadcast dispatched by [WidgetUpdater] and the management screen UI.
 * Adding a new widget must add exactly one entry here; the refresh broadcast
 * is derived automatically.
 */
data class WidgetVariantInfo(
    val receiverClass: Class<out AppWidgetProvider>,
    val displayNameRes: Int,
    /**
     * 静态样例预览布局（RemoteViews）—— API 35+ 的选择器预览内容。
     *
     * UI-7（2026-09-28 用户报障「组件视图展示太简陋」）：[WidgetPreviewRegistrar]
     * 原先给**所有**变体挂 `widget_bitmap_container`（一张空 ImageView）→ 位图类
     * 组件在选择器里是空白卡；XML 侧 previewImage 又被上游遗留的骨架图顶掉，
     * 结果用户看到的只有"白卡 + 灰块"。现在按变体挂真实样例布局。
     */
    val previewLayoutRes: Int
)

val ALL_WIDGET_VARIANTS: List<WidgetVariantInfo> = listOf(
    // UI-4c（2026-09-26 用户令「重新做组件」）: 组件收敛到 3 个 ——
    // 「今日课程」「最近两天」改走**真实行布局**（RemoteViews 真实视图 + ListView 原生滚动，
    // 与 wakeup 同路子：文字交给系统排版，不再有手写截断导致的形变）；
    // 「本周课表（网格）」保留网格形态，按可读行高渲染 + 可滑动。
    // 尺寸档（小/中）整体退场：真实布局自适应任意尺寸，不需要按尺寸各来一套。
    // 被摘掉的类（TodaySmall/Wide、TwoDaySmall/Wide、WeekGridSmall、CourseRow*）都还在，
    // 要恢复只需把 <receiver> 加回 AndroidManifest.xml 并在此登记。
    WidgetVariantInfo(TodayWidgetReceiver::class.java,  R.string.widget_today_label,
        R.layout.widget_preview_today),
    WidgetVariantInfo(TwoDayWidgetReceiver::class.java, R.string.widget_twoday_label,
        R.layout.widget_preview_twoday),
    WidgetVariantInfo(WeekGridWidgetProvider::class.java, R.string.widget_week_grid_label,
        R.layout.widget_preview_weekgrid)
)
