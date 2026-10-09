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
    /** Data-free static sample layout; must match the provider XML previewLayout. */
    val previewLayoutRes: Int
)

val ALL_WIDGET_VARIANTS: List<WidgetVariantInfo> = listOf(
    // Preset sizes for launchers without resize handles; existing receiver identities retained.
    WidgetVariantInfo(TodaySmallWidgetReceiver::class.java, R.string.widget_today_small_label,
        R.layout.widget_preview_today_small),
    WidgetVariantInfo(TodayWidgetReceiver::class.java,  R.string.widget_today_label,
        R.layout.widget_preview_today),
    WidgetVariantInfo(TwoDayWidgetReceiver::class.java, R.string.widget_twoday_label,
        R.layout.widget_preview_twoday),
    WidgetVariantInfo(WeekGridWidgetProvider::class.java, R.string.widget_week_grid_label,
        R.layout.widget_preview_weekgrid)
)
