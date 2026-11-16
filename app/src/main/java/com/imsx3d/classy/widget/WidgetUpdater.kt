package com.imsx3d.classy.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Refresh requests for registered widget variants (ALL_WIDGET_VARIANTS is the authority).
 * Receivers build RemoteViews asynchronously; sending the broadcast is not proof that an
 * OEM launcher displayed it. WorkManager periodic/midnight refresh is best-effort and can
 * be delayed by Doze or vendor background restrictions. Binding, sizing and rendered output
 * require separate device acceptance. Vendor notes: docs/widget-vendor-specs/INDEX.md.
 */
object WidgetUpdater {

    private const val TAG = "WidgetUpdater"
    private const val WORK_NAME = "sleepy_widget_update"
    private const val MIDNIGHT_WORK_NAME = "sleepy_widget_midnight_update"
    private const val FIRST_ADD_WORK_NAME = "sleepy_widget_first_add_refresh"
    /** 系统在组件完成 add 后发给 provider 的广播（AppWidgetManager 未暴露该常量）。 */
    internal const val ACTION_APPWIDGET_ADDED = "android.appwidget.action.APPWIDGET_ADDED"
    private const val REPEAT_MINUTES = 15L

    /** 首次上桌后的补渲染延迟：等桌面完成 host attach/apply 再重发一次 UPDATE。 */
    internal const val FIRST_ADD_RETRY_DELAY_MS = 2_500L

    /** All registered providers receiving an asynchronous refresh request. */
    internal val remoteViewsReceiverClasses: List<Class<out AppWidgetProvider>> =
        ALL_WIDGET_VARIANTS.map { it.receiverClass }

    /**
     * 首次添加小组件后补一次渲染。
     *
     * 2026-11-16 真机反馈：新加组件先是全透明（停在 initialLayout），手动「刷新所有小组件」
     * 后才出现内容。首次添加发生在 App 进程冷启动 + OEM 桌面 add/bind 流程中，桌面此时可能
     * 还没 attach host 就丢弃了 provider 的 `updateAppWidget`；后续没有第二个 UPDATE，
     * 于是透明状态一直保留到用户手动刷新。
     *
     * 这里在 ACTION_APPWIDGET_ADDED 时排一个短延迟的一次性 WorkManager 任务再刷一遍：
     * 不依赖 provider 进程存活（WorkManager 持久化），也和 15min 周期兜底/午夜刷新同一条链路。
     * 唯一任务名 + REPLACE：连续添加多个组件只保留最后一次刷新。
     */
    internal fun armFirstAddRefresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setConstraints(Constraints.Builder().build())
            .setInitialDelay(FIRST_ADD_RETRY_DELAY_MS, TimeUnit.MILLISECONDS)
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                FIRST_ADD_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }.onFailure { Log.w(TAG, "armFirstAddRefresh failed", it) }
    }

    /**
     * 各 widget provider 的 onReceive 入口统一调用：ACTION_APPWIDGET_ADDED 时排补渲染。
     * @return true 表示这是首次添加广播（调用方仍可继续走自身分发，ADDED 对
     *         AppWidgetProvider 是 no-op）。
     */
    internal fun handleFirstAddBroadcast(context: Context, intent: Intent): Boolean {
        if (intent.action != ACTION_APPWIDGET_ADDED) return false
        armFirstAddRefresh(context)
        return true
    }

    /** 距下一个本地午夜的毫秒数 — 恒 >0 (午夜整点 = 整 24h), 纯函数可 JVM 单测。 */
    internal fun nextMidnightDelayMillis(now: LocalDateTime): Long {
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        return Duration.between(now, nextMidnight).toMillis()
    }

    /**
     * 排一个到下个午夜的刷新单次任务 (幂等): 唯一名 + REPLACE → 任意刷新路径重复
     * 调用只保留最新对齐结果, 链不重复不分叉。
     */
    private fun armMidnightRefresh(context: Context) {
        val delay = nextMidnightDelayMillis(LocalDateTime.now())
        val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
            .setConstraints(Constraints.Builder().build())
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            MIDNIGHT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** 注册定期刷新（幂等） */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(
            REPEAT_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(Constraints.Builder().build())
            .setInitialDelay(3, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * 请求刷新所有已放置的小组件，并重排课程边界与午夜兜底任务。
     * suspend 只等待请求和排程完成，不等待桌面实际显示；广播接收器后台生成
     * RemoteViews。WorkManager 周期任务和闹钟都仍受系统省电/厂商后台策略约束。
     */
    suspend fun notifyDataChanged(context: Context) {
        withContext(Dispatchers.IO) {
            val awm = AppWidgetManager.getInstance(context)

            // Enumerate actual bindings; do not wake providers with no placed instances.
            val remoteViewsReceivers = remoteViewsReceiverClasses
            for (receiver in remoteViewsReceivers) {
                try {
                    val component = ComponentName(context, receiver)
                    val ids = awm.getAppWidgetIds(component)
                    if (ids.isNotEmpty()) {
                        Log.d(TAG, "${receiver.simpleName} ids=${ids.toList()}, broadcasting UPDATE")
                        val intent = Intent("android.appwidget.action.APPWIDGET_UPDATE").apply {
                            this.component = component
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                        }
                        context.sendBroadcast(intent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "${receiver.simpleName} broadcast failed", e)
                }
            }

            // 课程边界闹钟重排 (设计 §5): 数据变了边界就变, 每次全量刷新后重对齐。
            // 放 IO 块内 — armNext 阻塞读库, 禁在 Main。
            WidgetBoundaryScheduler.armNext(context)
        }
        // Requests have been sent, not necessarily rendered. Cancellation of the IO block
        // prevents this step; the periodic worker remains a best-effort recovery path.
        runCatching { armMidnightRefresh(context) }
    }
}
