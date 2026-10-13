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
    private const val REPEAT_MINUTES = 15L

    /** All registered providers receiving an asynchronous refresh request. */
    internal val remoteViewsReceiverClasses: List<Class<out AppWidgetProvider>> =
        ALL_WIDGET_VARIANTS.map { it.receiverClass }

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
