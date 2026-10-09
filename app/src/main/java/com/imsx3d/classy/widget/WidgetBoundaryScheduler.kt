package com.imsx3d.classy.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.util.CourseCompletion
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Refresh every displayed widget at individual lesson ends and local midnight.
 * Android may defer alarms without exact-alarm permission; periodic work is a fallback.
 */
object WidgetBoundaryScheduler {
    private const val TAG = "WidgetBoundaryScheduler"
    private const val RC = 7601

    internal fun nextBoundaryMin(endMins: List<Int>, nowMin: Int): Int? =
        endMins.filter { it > nowMin }.minOrNull()

    /** Keep each course's end, including overlapping courses ending at different times. */
    internal fun rowEndMins(courses: List<CourseEntity>, timeJson: String?): List<Int> =
        courses.mapNotNull { CourseCompletion.endTime(it, timeJson)?.let { t -> t.hour * 60 + t.minute } }

    internal fun nextRefreshAt(ends: List<LocalTime>, now: LocalDateTime): LocalDateTime =
        ends.map { now.toLocalDate().atTime(it) }.filter { it > now }.minOrNull()
            ?: now.toLocalDate().plusDays(1).atStartOfDay()

    /** Blocking database access: call on IO. Serialize parallel provider updates. */
    @Synchronized
    fun armNext(context: Context) {
        try {
            val awm = AppWidgetManager.getInstance(context)
            val ids = ALL_WIDGET_VARIANTS.flatMap {
                awm.getAppWidgetIds(ComponentName(context, it.receiverClass)).toList()
            }
            val am = context.getSystemService(AlarmManager::class.java)
            val pending = buildPendingIntent(context)
            if (ids.isEmpty()) {
                am.cancel(pending)
                return
            }
            val date = LocalDateTime.now().toLocalDate()
            val ends = runBlocking {
                ids.flatMap { id ->
                    val source = WidgetWeekDataLoader.resolve(id) ?: return@flatMap emptyList()
                    source.coursesOn(date).mapNotNull { CourseCompletion.endTime(it, source.table.timeJson) }
                }
            }
            val now = LocalDateTime.now()
            val next = if (date == now.toLocalDate()) nextRefreshAt(ends, now) else now.plusSeconds(1)
            val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            am.cancel(pending)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
            Log.d(TAG, "next refresh=$next widgets=${ids.size}")
        } catch (e: Exception) {
            Log.e(TAG, "armNext failed", e)
        }
    }

    private fun buildPendingIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, RC, Intent(context, WidgetBoundaryReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
}

class WidgetBoundaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                withTimeout(9_000) { WidgetUpdater.notifyDataChanged(context.applicationContext) }
            } catch (e: Exception) {
                Log.w("WidgetBoundaryReceiver", "Widget refresh failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
