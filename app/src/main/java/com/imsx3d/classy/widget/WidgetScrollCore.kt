package com.imsx3d.classy.widget

/**
 * Per-widget 滚动开关纯逻辑 (2026-09-14 用户定稿: 滚动不再全局一档,
 * 每个小组件在编辑页控制自己的滚动)。
 *
 * 与 [WidgetBindingCore] 同模式: raw prefs map 进出, 零 Android 依赖, 纯 JVM 可测。
 * 迁移语义: 本实例从未显式设置过 → 继承旧全局值 (老用户升级后行为不变);
 * 编辑页一旦拨动即写显式值, 与旧全局彻底脱钩。
 */
internal object WidgetScrollCore {

    const val PREFS_NAME = "widget_scroll_prefs"

    fun key(widgetId: Int): String = "scroll_$widgetId"

    /** 显式值; null = 从未设置 (调用方按迁移语义回退旧全局)。 */
    fun read(raw: Map<String, Boolean>, widgetId: Int): Boolean? = raw[key(widgetId)]

    fun write(raw: MutableMap<String, Boolean>, widgetId: Int, enabled: Boolean) {
        raw[key(widgetId)] = enabled
    }

    fun delete(raw: MutableMap<String, Boolean>, widgetId: Int) {
        raw.remove(key(widgetId))
    }

    /** 生效值 = 显式值 ?: 旧全局 (出厂默认 false = FIXED)。 */
    fun resolve(explicit: Boolean?, legacyGlobal: Boolean): Boolean = explicit ?: legacyGlobal
}
