package com.imsx3d.classy.widget.notification

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.imsx3d.classy.MainActivity
import com.imsx3d.classy.R
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import com.imsx3d.classy.util.AppPrefs
import com.imsx3d.classy.util.DateUtils
import com.imsx3d.classy.util.TimeTableUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import com.imsx3d.classy.BuildConfig
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 课程通知调度器 — 支持每日提醒 + 每节课前提醒。
 *
 * 每日提醒：在用户指定时间发送今日课程摘要。
 * 课前提醒：滚动安排七天内课程，每日和数据变更时刷新。
 */
class CourseNotificationScheduler(private val context: Context) {

    companion object {
        internal val schedulingMutex = Mutex()
        private val schedulingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        internal fun alarmPrefs(context: Context) = context.getSharedPreferences("course_alarm_registry", Context.MODE_PRIVATE)
        const val CHANNEL_DAILY = "sleepy_daily"
        const val CHANNEL_BEFORE_CLASS = "sleepy_before_class"
        const val CHANNEL_FLUID = "sleepy_fluid_v2"

        // Request codes for PendingIntent discrimination
        private const val RC_DAILY = 1
        private const val RC_BEFORE_CLASS_SCHEDULER = 2
        private const val RC_TOMORROW_DAILY = 3
        private const val RC_BEFORE_CLASS_BASE = 100 // + courseId offset

        // Notification IDs
        const val NOTIFY_DAILY = 1001
        const val NOTIFY_TOMORROW_DAILY = 1002
        const val NOTIFY_BEFORE_CLASS_BASE = 2000 // + courseId offset
    }

    private val editDebouncer = ReminderDebouncer(schedulingScope) {
        try { reschedule() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { android.util.Log.e("CourseScheduler", "Deferred reschedule failed", e) }
    }

    fun requestReschedule() = editDebouncer.request()

    fun scheduleAll() = schedulingScope.launch {
        try { reschedule() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { android.util.Log.e("CourseScheduler", "Reschedule failed", e) }
    }

    /** All callers share one lock, including the midnight receiver and toggle-off path. */
    suspend fun reschedule() = schedulingMutex.withLock {
        SleepyApp.get().repository.readConsistently {
            createChannels()
            cancelAllLocked()
            val prefs = context.applicationContext
            if (AppPrefs.isReminderEnabled(prefs)) {
                if (AppPrefs.isDailyReminderEnabled(prefs)) {
                    if (AppPrefs.isTodayReminderEnabled(prefs)) scheduleDaily()
                    if (AppPrefs.isTomorrowReminderEnabled(prefs)) scheduleTomorrowReminder()
                }
                if (AppPrefs.isBeforeClassEnabled(prefs)) {
                    scheduleBeforeClassDaily()
                    ensureActiveFluidCloudLocked()
                }
            }
        }
    }

    suspend fun cancelAll() = schedulingMutex.withLock { cancelAllLocked() }

    private suspend fun cancelAllLocked() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Cancel daily
        alarmManager.cancel(buildPendingIntent(RC_DAILY, DailyNotifyReceiver::class.java))
        alarmManager.cancel(buildPendingIntent(RC_TOMORROW_DAILY, TomorrowNotifyReceiver::class.java))

        // Cancel before-class scheduler
        alarmManager.cancel(buildPendingIntent(RC_BEFORE_CLASS_SCHEDULER, BeforeClassScheduleReceiver::class.java))

        val prefs = alarmPrefs(context)
        prefs.getStringSet("occurrences", emptySet()).orEmpty().forEach { key ->
            alarmManager.cancel(occurrencePendingIntent(key))
        }
        val recorded = prefs.getStringSet("ids", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }
        // Existing rows also cancel alarms from versions predating the registry.
        val current = SleepyApp.get().repository.getAllCourses().map { it.id.toInt() }
        cancelCourseAlarmIds(alarmManager, (recorded + current).distinct())
        check(prefs.edit().putStringSet("occurrences", emptySet()).putStringSet("ids", emptySet())
            .putString("generation", java.util.UUID.randomUUID().toString()).commit())
        NotificationManagerCompat.from(context).cancel(NOTIFY_BEFORE_CLASS_BASE)
        context.stopService(Intent(context, FluidCloudService::class.java))
    }

    /**
     * 取消指定课程 id 的课前闹钟（PendingIntent 语义：extras 不参与匹配）。
     * 调用方：ScheduleRepository.deleteTable —— 删表靠外键 CASCADE 级联删课程，
     * 删除后这些课程 id 已查不到，cancelAll 的"现存课程"枚举覆盖不到，
     * 故删除前捕获 id 列表、删除后调这里显式清理孤儿闹钟。
     */
    fun cancelCourseAlarms(courseIds: List<Long>) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        cancelCourseAlarmIds(alarmManager, courseIds.map { it.toInt() })
        val ids = courseIds.toSet()
        alarmPrefs(context).getStringSet("occurrences", emptySet()).orEmpty()
            .filter { ReminderWindow.courseId(it) in ids }
            .forEach { alarmManager.cancel(occurrencePendingIntent(it)) }
    }

    private fun cancelCourseAlarmIds(alarmManager: AlarmManager, courseIds: List<Int>) {
        for (cid in courseIds) {
            try {
                alarmManager.cancel(buildPendingIntent(RC_BEFORE_CLASS_BASE + cid, BeforeClassNotifyReceiver::class.java))
            } catch (_: Exception) {}
        }
    }

    private fun occurrencePendingIntent(key: String, payload: Intent? = null): PendingIntent {
        val intent = payload ?: Intent(context, BeforeClassNotifyReceiver::class.java)
        intent.data = android.net.Uri.parse("classy-alarm://${context.packageName}/$key")
        return PendingIntent.getBroadcast(context, RC_BEFORE_CLASS_BASE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    // ==================== Daily ====================

    private fun scheduleDaily() {
        scheduleDailyAlarm(
            timeStr = AppPrefs.getDailyReminderTime(context),
            fallbackHour = 7,
            fallbackMinute = 0,
            pending = buildPendingIntent(RC_DAILY, DailyNotifyReceiver::class.java)
        )
    }

    private fun scheduleTomorrowReminder() {
        scheduleDailyAlarm(
            timeStr = AppPrefs.getTomorrowReminderTime(context),
            fallbackHour = 22,
            fallbackMinute = 0,
            pending = buildPendingIntent(RC_TOMORROW_DAILY, TomorrowNotifyReceiver::class.java)
        )
    }

    private fun scheduleDailyAlarm(
        timeStr: String,
        fallbackHour: Int,
        fallbackMinute: Int,
        pending: PendingIntent
    ) {
        val parts = timeStr.split(":")
        // 钳制到合法范围，避免破损 pref（"07:60"、负数、空值）触发 DateTimeException 崩溃
        val hour = (parts.getOrNull(0)?.toIntOrNull() ?: fallbackHour).coerceIn(0, 23)
        val minute = (parts.getOrNull(1)?.toIntOrNull() ?: fallbackMinute).coerceIn(0, 59)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val target = LocalTime.of(hour, minute)
        var next = LocalDate.now().atTime(target)
        if (!LocalTime.now().isBefore(target)) next = next.plusDays(1)
        val epoch = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        setRepeatingAlarm(alarmManager, epoch, AlarmManager.INTERVAL_DAY, pending)
    }

    // ==================== Before-class scheduler ====================

    private suspend fun scheduleBeforeClassDaily() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = buildPendingIntent(RC_BEFORE_CLASS_SCHEDULER, BeforeClassScheduleReceiver::class.java)

        // Schedule at 00:05 every day
        val target = LocalTime.of(0, 5)
        var next = LocalDate.now().atTime(target)
        if (LocalTime.now().isAfter(target)) next = next.plusDays(1)
        val epoch = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        setRepeatingAlarm(alarmManager, epoch, AlarmManager.INTERVAL_DAY, pending)

        // Also immediately schedule for today (in case app was opened after midnight)
        scheduleTodayBeforeClassAlarmsLocked()
    }

    /**
     * Schedules a rolling seven-day occurrence window, refreshed daily and on edits.
     * Called by [BeforeClassScheduleReceiver] at midnight and by [scheduleBeforeClassDaily].
     */
    suspend fun scheduleTodayBeforeClassAlarms() = reschedule()

    private suspend fun scheduleTodayBeforeClassAlarmsLocked() {
        val app = context.applicationContext
        android.util.Log.d("CourseScheduler", "scheduleToday start enabled=${AppPrefs.isBeforeClassEnabled(app)} minutes=${AppPrefs.getBeforeClassMinutes(app)}")
        if (!AppPrefs.isBeforeClassEnabled(app)) return
        val minutes = AppPrefs.getBeforeClassMinutes(app)
        val firstDate = LocalDate.now()
        val table = resolveCurrentTable() ?: return
        val allCourses = SleepyApp.get().repository.getCourses(table.id)
        for (today in ReminderWindow.dates(firstDate)) {
            val courses = com.imsx3d.classy.widget.HolidayTransferHelper.coursesOn(
                app, table, today, allCourses
            )

            // Parse time nodes
            val nodes = TimeTableUtils.parseNodes(table.timeJson)

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val now = System.currentTimeMillis()

            courses.forEachIndexed { index, course ->
                // Get course start time
                android.util.Log.d("CourseScheduler", "course index=$index id=${course.id} name=${course.courseName} ownTime=${course.ownTime} start=${course.startTime} node=${course.startNode}")
                val startTimeStr = if (course.ownTime && course.startTime.isNotBlank()) {
                    course.startTime
                } else {
                    nodes.find { it.node == course.startNode }?.let { String.format("%02d:%02d", it.start.hour, it.start.minute) }
                } ?: run {
                    android.util.Log.w("CourseScheduler", "skip no start time course=${course.id}")
                    return@forEachIndexed
                }
                val parts = startTimeStr.split(":")
                val h = parts.getOrNull(0)?.toIntOrNull()
                val m = parts.getOrNull(1)?.toIntOrNull()
                // 钳制：ownTime/startTime 可能是破损值（h≥24/m≥60），非法则跳过本节
                if (h == null || m == null || h !in 0..23 || m !in 0..59) {
                    android.util.Log.w("CourseScheduler", "skip invalid time course=${course.id} time=$startTimeStr")
                    return@forEachIndexed
                }

                val classStart = today.atTime(h, m)
                val notifyTime = classStart.minusMinutes(minutes.toLong())
                val epoch = notifyTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

                android.util.Log.d("CourseScheduler", "course=${course.id} start=$classStart notify=$notifyTime epoch=$epoch now=$now")
                if (epoch <= now) {
                    android.util.Log.d("CourseScheduler", "skip past alarm course=${course.id}")
                    return@forEachIndexed
                }

                val registry = alarmPrefs(context)
                val key = ReminderWindow.key(course.id, today)
                val ids = registry.getStringSet("occurrences", emptySet()).orEmpty() + key
                // Persist before AlarmManager: a process death can at worst leave an extra cancel ID.
                check(registry.edit().putStringSet("occurrences", ids).commit())
                val intent = Intent(context, BeforeClassNotifyReceiver::class.java).apply {
                    putExtra("courseId", course.id)
                    putExtra("generation", registry.getString("generation", ""))
                    putExtra("courseName", course.courseName)
                    putExtra("room", course.room)
                    putExtra("teacher", course.teacher)
                    putExtra("startTime", String.format("%02d:%02d", h, m))
                    putExtra("notifyEpoch", epoch)
                    putExtra("classEpoch", classStart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                }
                val pending = occurrencePendingIntent(key, intent)

                // Use exact alarm for precision, fall back to inexact on Android 12+ without grant
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, epoch, pending)
                } else {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, epoch, pending)
                }
            }
        }
    }

    /**
     * 状态驱动的流体云兜底：只要"现在"落在任一节课的 [classStart-minutes, classStart] 窗口内，
     * 就确保 FluidCloudService 在跑、流体云在显示。不依赖"正好提前N分钟那一秒"的 alarm。
     *
     * 调用时机：app 回前台、app 启动、课程数据变更、WorkManager 周期兜底。
     * 解决"alarm 错过那一秒 / 用户在窗口内才打开 app → 流体云永远不起"的问题。
     */
    suspend fun ensureActiveFluidCloud() = schedulingMutex.withLock {
        SleepyApp.get().repository.readConsistently { ensureActiveFluidCloudLocked() }
    }

    private suspend fun ensureActiveFluidCloudLocked() {
        val app = context.applicationContext
        if (!AppPrefs.isReminderEnabled(app) || !AppPrefs.isBeforeClassEnabled(app)) return
        if (!AppPrefs.isBeforeClassFluidEnabled(app)) return
        val minutes = AppPrefs.getBeforeClassMinutes(app)
        val today = LocalDate.now()
        val table = resolveCurrentTable() ?: return
        val nodes = TimeTableUtils.parseNodes(table.timeJson)
        val now = System.currentTimeMillis()

        // 找出现在处于课前窗口内的第一节课
        val hit = com.imsx3d.classy.widget.HolidayTransferHelper.coursesOn(
            app, table, today, SleepyApp.get().repository.getCourses(table.id))
            .firstOrNull { c ->
                val st = if (c.ownTime && c.startTime.isNotBlank()) c.startTime
                    else nodes.find { it.node == c.startNode }?.let { String.format("%02d:%02d", it.start.hour, it.start.minute) }
                val p = st?.split(":")
                val h = p?.getOrNull(0)?.toIntOrNull(); val m = p?.getOrNull(1)?.toIntOrNull()
                if (h == null || m == null || h !in 0..23 || m !in 0..59) return@firstOrNull false
                val classStart = today.atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                val notifyEpoch = classStart - minutes * 60_000L
                now in notifyEpoch..classStart  // 现在在窗口内
            } ?: return

        // 计算这节课的精确窗口，启动 FluidCloudService
        val st = if (hit.ownTime && hit.startTime.isNotBlank()) hit.startTime
            else nodes.find { it.node == hit.startNode }!!.let { String.format("%02d:%02d", it.start.hour, it.start.minute) }
        val p = st.split(":")
        val classStart = today.atTime(p[0].toInt(), p[1].toInt()).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val notifyEpoch = classStart - minutes * 60_000L
        val svc = Intent(app, FluidCloudService::class.java).apply {
            putExtra("courseName", hit.courseName)
            putExtra("room", hit.room.ifBlank { app.getString(R.string.default_room) })
            putExtra("teacher", hit.teacher)
            putExtra("startTime", st)
            putExtra("notifyEpoch", notifyEpoch)
            putExtra("classEpoch", classStart)
        }
        try {
            androidx.core.content.ContextCompat.startForegroundService(app, svc)
            android.util.Log.d("CourseScheduler", "ensureActiveFluidCloud: started for ${hit.courseName} notify=$notifyEpoch class=$classStart now=$now")
        } catch (t: Throwable) {
            android.util.Log.w("CourseScheduler", "ensureActiveFluidCloud start failed", t)
        }
    }
    // ==================== Helpers ====================

    internal fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_DAILY,
            context.getString(R.string.notif_channel_daily),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = context.getString(R.string.notif_channel_daily_desc) })
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_BEFORE_CLASS,
            context.getString(R.string.notif_channel_before_class),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = context.getString(R.string.notif_channel_before_class_desc) })
        nm.createNotificationChannel(NotificationChannel(
            CHANNEL_FLUID,
            context.getString(R.string.notif_channel_fluid),
            NotificationManager.IMPORTANCE_HIGH
        ).apply { description = context.getString(R.string.notif_channel_fluid_desc) })
    }

    // buildPendingIntInfo 死函数已删（实际全部走下方 buildPendingIntent）

    @Suppress("UNCHECKED_CAST")
    private fun buildPendingIntent(rc: Int, cls: Class<out BroadcastReceiver>): PendingIntent =
        PendingIntent.getBroadcast(
            context, rc, Intent(context, cls),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun setRepeatingAlarm(am: AlarmManager, epoch: Long, interval: Long, pi: PendingIntent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, epoch, interval, pi)
        } else {
            am.setRepeating(AlarmManager.RTC_WAKEUP, epoch, interval, pi)
        }
    }

    private suspend fun resolveCurrentTable(): TimeTableEntity? {
        return com.imsx3d.classy.widget.WidgetTableResolver.resolveCurrentTable()
    }
}

// ==================== Receivers ====================

/** Same-day schedule summary — fires at the user-chosen morning/daytime time. */
class DailyNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!hasNotifPermission(context)) return
        if (!AppPrefs.isReminderEnabled(context) ||
            !AppPrefs.isDailyReminderEnabled(context) ||
            !AppPrefs.isTodayReminderEnabled(context)
        ) return

        runAsync {
            sendScheduleSummary(
                context = context,
                targetDate = LocalDate.now(),
                isTomorrowPreview = false
            )
        }
    }
}

/** Previous-evening schedule preview — follows the same no-course behavior as the same-day summary. */
class TomorrowNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!hasNotifPermission(context)) return
        if (!AppPrefs.isReminderEnabled(context) ||
            !AppPrefs.isDailyReminderEnabled(context) ||
            !AppPrefs.isTomorrowReminderEnabled(context)
        ) return

        runAsync {
            sendScheduleSummary(
                context = context,
                targetDate = LocalDate.now().plusDays(1),
                isTomorrowPreview = true
            )
        }
    }
}

private suspend fun sendScheduleSummary(
    context: Context,
    targetDate: LocalDate,
    isTomorrowPreview: Boolean
) = SleepyApp.get().repository.readConsistently {
    val table = com.imsx3d.classy.widget.WidgetTableResolver.resolveCurrentTable()
    val dayOfMonth = targetDate.dayOfMonth
    val courses = if (table == null) emptyList() else
        com.imsx3d.classy.widget.HolidayTransferHelper.coursesOn(
            context.applicationContext, table, targetDate, SleepyApp.get().repository.getCourses(table.id)
        )

    val title: String
    val text: String
    if (courses.isEmpty()) {
        title = context.getString(
            if (isTomorrowPreview) R.string.notif_tomorrow_title_no_course else R.string.notif_daily_title_no_course,
            dayOfMonth
        )
        text = context.getString(
            if (isTomorrowPreview) R.string.notif_tomorrow_text_no_course
            else R.string.notif_daily_text_no_course
        )
    } else {
        title = context.getString(
            if (isTomorrowPreview) R.string.notif_tomorrow_title else R.string.notif_daily_title,
            dayOfMonth,
            courses.size
        )
        val first = courses.first()
        val firstTime = getCourseStartTime(first, requireNotNull(table))
        val firstRoom = first.room.ifBlank { context.getString(R.string.notif_room_unknown) }
        text = context.getString(R.string.notif_daily_text_first, first.courseName, firstTime, firstRoom)
    }

    val notif = NotificationCompat.Builder(context, CourseNotificationScheduler.CHANNEL_DAILY)
        .setSmallIcon(R.drawable.ic_notification_time)
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(openAppIntent(context))
        .setAutoCancel(true)
        .build()

    if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        NotificationManagerCompat.from(context).notify(
            if (isTomorrowPreview) {
                CourseNotificationScheduler.NOTIFY_TOMORROW_DAILY
            } else {
                CourseNotificationScheduler.NOTIFY_DAILY
            },
            notif
        )
    }
}

/**
 * Midnight scheduler — sets up individual before-class alarms for the day.
 */
class BeforeClassScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!AppPrefs.isReminderEnabled(context) || !AppPrefs.isBeforeClassEnabled(context)) return
        runAsync { SleepyApp.get().notificationScheduler.reschedule() }
    }
}

/**
 * Individual before-class notification — fires N minutes before a class.
 * Content: "下节课{courseName}于{HH}:{MM}在{room}上课"
 */
class BeforeClassNotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runAsync {
            CourseNotificationScheduler.schedulingMutex.withLock {
                SleepyApp.get().repository.readConsistently {
                    val debug = BuildConfig.DEBUG && intent.getBooleanExtra("debug_force_fluid", false)
                    if (!debug) {
                        val generation = intent.getStringExtra("generation") ?: return@readConsistently
                        if (generation != CourseNotificationScheduler.alarmPrefs(context).getString("generation", null)) return@readConsistently
                        val repo = SleepyApp.get().repository
                        val course = repo.getCourse(intent.getLongExtra("courseId", -1L)) ?: return@readConsistently
                        val table = com.imsx3d.classy.widget.WidgetTableResolver.resolveCurrentTable() ?: return@readConsistently
                        if (course.tableId != table.id) return@readConsistently
                        // A tomorrow class may notify before midnight today.
                        val expectedEpoch = intent.getLongExtra("classEpoch", -1L)
                        if (expectedEpoch <= 0) return@readConsistently
                        val today = ReminderWindow.classDate(expectedEpoch, ZoneId.systemDefault())
                        val active = com.imsx3d.classy.widget.HolidayTransferHelper.coursesOn(context, table, today, listOf(course))
                        if (active.isEmpty()) return@readConsistently
                        val start = runCatching { LocalTime.parse(getCourseStartTime(course, table)) }.getOrNull() ?: return@readConsistently
                        val epoch = today.atTime(start).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val notifyEpoch = epoch - AppPrefs.getBeforeClassMinutes(context) * 60_000L
                        if (epoch != intent.getLongExtra("classEpoch", -1L) ||
                            notifyEpoch != intent.getLongExtra("notifyEpoch", -1L) ||
                            System.currentTimeMillis() < notifyEpoch || System.currentTimeMillis() > epoch) return@readConsistently
                        intent.putExtra("courseName", course.courseName)
                        intent.putExtra("room", course.room)
                        intent.putExtra("teacher", course.teacher)
                    }
                    showNotification(context, intent)
                }
            }
        }
    }

    private fun showNotification(context: Context, intent: Intent) {
        android.util.Log.d("BeforeClassNotify", "entered extras=${intent.extras?.keySet()}")
        if (!hasNotifPermission(context)) {
            android.util.Log.w("BeforeClassNotify", "POST_NOTIFICATIONS denied")
            return
        }
        if (!AppPrefs.isReminderEnabled(context) || !AppPrefs.isBeforeClassEnabled(context)) {
            android.util.Log.w("BeforeClassNotify", "reminder toggles disabled")
            return
        }

        val courseName = intent.getStringExtra("courseName") ?: return
        val room = intent.getStringExtra("room") ?: ""
        val startTime = intent.getStringExtra("startTime") ?: ""
        val roomStr = room.ifBlank { context.getString(R.string.notif_room_unknown) }
        val teacher = intent.getStringExtra("teacher") ?: ""
        val fluid = (BuildConfig.DEBUG && intent.getBooleanExtra("debug_force_fluid", false)) || AppPrefs.isBeforeClassFluidEnabled(context)
        val banner = AppPrefs.isBeforeClassBannerEnabled(context)
        if (!banner && !fluid) return
        val fields = AppPrefs.getBeforeClassFluidFields(context)
        val fluidText = buildList {
            if ("name" in fields) add(courseName)
            if ("time" in fields) add(startTime)
            if ("room" in fields && room.isNotBlank()) add(roomStr)
            if ("teacher" in fields && teacher.isNotBlank()) add(teacher)
        }.ifEmpty { listOf(courseName) }.joinToString("  ·  ")
        // primaryText / roomTeacherText / timeTeacherText 三个死变量已删
        // (计算后从未被使用——SDK>=26 路径直接交给 FluidCloudService, fallback 用上面的 fluidText/text)

        val text = if (teacher.isBlank()) {
            context.getString(R.string.notif_before_class_text, courseName, startTime, roomStr)
        } else {
            context.getString(R.string.notif_before_class_text_with_teacher, courseName, startTime, roomStr, teacher)
        }

        // == 流体云 / Live Update ==
        // 所有 SDK>=26 统一走 FluidCloudService：service 的 Handler 每 15s 循环
        //   re-post ProgressStyle 通知推进 progress，进度条才会持续动。
        //   旧代码在 SDK>=36 单独静态 post 一次就 return，导致进度条停在 0 不更新。

        // FluidCloudService 接管流体云：前台服务每 15s 循环 re-post ProgressStyle 通知推进进度条。
        // SDK>=26（含 Android 16）统一走此路径。
        if (fluid && Build.VERSION.SDK_INT >= 26) {
            val serviceIntent = Intent(context, FluidCloudService::class.java).apply {
                putExtra("courseName", courseName)
                putExtra("room", roomStr)
                putExtra("teacher", teacher)
                putExtra("startTime", startTime)
                putExtra("notifyEpoch", intent.getLongExtra("notifyEpoch", System.currentTimeMillis()))
                putExtra("classEpoch", intent.getLongExtra("classEpoch", System.currentTimeMillis()))
            }
            try {
                ContextCompat.startForegroundService(context, serviceIntent)
                return
            } catch (e: RuntimeException) {
                android.util.Log.w("BeforeClassNotify", "Foreground service unavailable; using notification", e)
            }
        }

        // == Fallback: standard notification ==
        val notif = NotificationCompat.Builder(context, if (fluid) CourseNotificationScheduler.CHANNEL_FLUID else CourseNotificationScheduler.CHANNEL_BEFORE_CLASS)
            .setSmallIcon(R.drawable.ic_notification_time)
            .setContentTitle(if (fluid) courseName else context.getString(R.string.notif_before_class_title))
            .setContentText(if (fluid) fluidText else text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (fluid) fluidText else text))
            .setTicker(if (fluid) fluidText else text)
            .setSubText(if (fluid) fluidText else null)
            .setOngoing(fluid)
            .setOnlyAlertOnce(false)
            .setPriority(if (fluid) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()

        // Lint MissingPermission + 运行时兜底: 同 DailyNotifyReceiver,
        //   onReceive 校验后到此处之间权限可能被撤销 → 内联 checkSelfPermission 再查一次
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context)
                .notify(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE, notif)
        }
    }
}

/**
 * Boot receiver — reschedules everything after reboot or app update.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED
            || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
            || intent.action == Intent.ACTION_TIME_CHANGED
            || intent.action == Intent.ACTION_TIMEZONE_CHANGED
            || intent.action == Intent.ACTION_DATE_CHANGED) {
            val appContext = context.applicationContext
            runAsync {
                SleepyApp.get().notificationScheduler.reschedule()
                com.imsx3d.classy.widget.WidgetUpdater.notifyDataChanged(appContext)
            }
        }
    }
}

// ==================== Shared helpers ====================

private fun hasNotifPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

private fun openAppIntent(context: Context): PendingIntent =
    PendingIntent.getActivity(
        context, 0,
        // 与组件同源：点通知进来先落到**课表页**（这个 extra 由 MainActivity.handleDeepLinkIntent 消费），
        // 而不是停在"上次打开的那个 tab"。extras 不参与 PendingIntent 的身份匹配，
        // 但这里带 FLAG_UPDATE_CURRENT → 系统里已缓存的那个也会被补上新 extras。
        Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_SCHEDULE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

private fun getCourseStartTime(course: CourseEntity, table: TimeTableEntity): String {
    if (course.ownTime && course.startTime.isNotBlank()) return course.startTime
    val nodes = TimeTableUtils.parseNodes(table.timeJson)
    val node = nodes.find { it.node == course.startNode } ?: return ""
    return String.format("%02d:%02d", node.start.hour, node.start.minute)
}

/** Keep the process alive while handling a short broadcast, always release on failure. */
private fun BroadcastReceiver.runAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
        try { withTimeout(9_000) { block() } }
        catch (e: Exception) { android.util.Log.e("CourseReceiver", "Broadcast work failed", e) }
        finally { pending.finish() }
    }
}
