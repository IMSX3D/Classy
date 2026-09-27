package com.imsx3d.classy.widget

import android.content.Context
import com.imsx3d.classy.util.AppPrefs

/**
 * Android facade — [WidgetScrollCore] 的 SharedPreferences 读写壳。
 * 与 [WidgetBindingStore] 同模式 (raw prefs map 进出, 业务逻辑归 Core)。
 *
 * 2026-09-14: 滚动从全局一档改为 per-widget — 四个 push 闸门与编辑页开关
 * 全部经本 store; 旧 AppPrefs 全局 key 只作未设置实例的迁移默认, 不再写入。
 */
object WidgetScrollStore {

    /** 本实例生效值 = 显式设置 ?: 旧全局值 (升级老用户行为不变)。 */
    fun isScrollEnabled(context: Context, widgetId: Int): Boolean =
        WidgetScrollCore.resolve(read(context, widgetId), AppPrefs.isWidgetScrollEnabled(context))

    fun setScrollEnabled(context: Context, widgetId: Int, enabled: Boolean) {
        synchronized(writeLock) {
            val raw = loadAll(context).toMutableMap()
            WidgetScrollCore.write(raw, widgetId, enabled)
            saveAll(context, raw)
        }
    }

    /** onDeleted 清位 — 回收的 widget id 不得继承前任的滚动偏好。 */
    fun remove(context: Context, widgetId: Int) {
        synchronized(writeLock) {
            val raw = loadAll(context).toMutableMap()
            WidgetScrollCore.delete(raw, widgetId)
            saveAll(context, raw)
        }
    }

    private val writeLock = Any()

    private fun read(context: Context, widgetId: Int): Boolean? =
        WidgetScrollCore.read(loadAll(context), widgetId)

    private fun loadAll(context: Context): Map<String, Boolean> {
        val sp = context.getSharedPreferences(WidgetScrollCore.PREFS_NAME, Context.MODE_PRIVATE)
        return sp.all.mapNotNull { (k, v) -> if (v is Boolean) k to v else null }.toMap()
    }

    private fun saveAll(context: Context, data: Map<String, Boolean>) {
        val sp = context.getSharedPreferences(WidgetScrollCore.PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sp.edit().clear()
        data.forEach { (k, v) -> editor.putBoolean(k, v) }
        editor.apply()
    }
}
