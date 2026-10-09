package com.imsx3d.classy.widget

/**
 * Per-widget 三天窗口档位纯逻辑 (2026-09-15 用户定稿: 设置从管理页外卡挪进
 * 「· 小」变体的二级编辑页, 每个实例各管各的)。
 *
 * 与 [WidgetScrollCore] 同模式: raw prefs map 进出, 零 Android 依赖, 纯 JVM 可测。
 * 迁移语义: 本实例从未显式设置过 → 继承旧全局值 (老用户升级后行为不变);
 * 编辑页一旦拨动即写显式值, 与旧全局彻底脱钩。
 */
internal object WidgetCompactWindowCore {

    fun dates(today: java.time.LocalDate, targetDate: java.time.LocalDate?, todayFirst: Boolean): List<java.time.LocalDate> {
        val anchor = targetDate ?: today
        val first = if (todayFirst) anchor else anchor.minusDays(1)
        return (0L..2L).map(first::plusDays)
    }

    const val PREFS_NAME = "widget_compact_window_prefs"

    fun key(widgetId: Int): String = "compact_today_first_$widgetId"

    /** 显式值; null = 从未设置 (调用方按迁移语义回退旧全局)。 */
    fun read(raw: Map<String, Boolean>, widgetId: Int): Boolean? = raw[key(widgetId)]

    fun write(raw: MutableMap<String, Boolean>, widgetId: Int, todayFirst: Boolean) {
        raw[key(widgetId)] = todayFirst
    }

    fun delete(raw: MutableMap<String, Boolean>, widgetId: Int) {
        raw.remove(key(widgetId))
    }

    /** 生效值 = 显式值 ?: 旧全局 (出厂默认 true = 今日居第一位)。 */
    fun resolve(explicit: Boolean?, legacyGlobal: Boolean): Boolean = explicit ?: legacyGlobal
}
