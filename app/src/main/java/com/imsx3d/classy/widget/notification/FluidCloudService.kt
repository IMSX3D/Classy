package com.imsx3d.classy.widget.notification

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.imsx3d.classy.MainActivity
import com.imsx3d.classy.R
import com.imsx3d.classy.util.AppPrefs

/**
 * Keeps the promoted course notification's progress synchronized with the
 * user's before-class reminder window. The capsule text remains static.
 */
class FluidCloudService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var courseName = ""
    private var room = ""
    private var teacher = ""
    private var startTime = ""
    private var notifyEpoch = 0L
    private var classEpoch = 0L
    private var updateSequence = 0

    /**
     * 每 15s 推进一次进度条，下课即收工。
     *
     * 2026-09-27 修复（真机复现）：原来写成「先 `postProgressNotification()`，**再**判断到点收工」——
     * 发通知那步一旦抛异常（通知权限被撤、渠道被禁、ROM 拦实况卡片…），收工判断就永远执行不到，
     * 前台服务从此挂着不自停（通知栏看不到、`dumpsys` 里 isForeground 常驻）。
     * 现在**先判后发**：到点直接收工、不再碰通知；发通知那步单独 try/catch，异常不影响排下一拍。
     */
    private val updater = object : Runnable {
        override fun run() {
            if (System.currentTimeMillis() >= classEpoch) {
                stopSelfCleanly("class ended")
                return
            }
            try {
                postProgressNotification()
            } catch (t: Throwable) {
                android.util.Log.w(TAG, "postProgressNotification failed", t)
            }
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    /** 收工：撤掉前台通知 + 停服务。任何一处抛异常都要保证 stopSelf() 走到。 */
    private fun stopSelfCleanly(reason: String) {
        android.util.Log.d(TAG, "stop: $reason")
        handler.removeCallbacks(updater)
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "stopForeground failed", t)
        }
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The service may be restarted independently from the scheduler.
        com.imsx3d.classy.SleepyApp.get().notificationScheduler.createChannels()
        courseName = intent?.getStringExtra("courseName") ?: getString(R.string.default_course_name)
        room = intent?.getStringExtra("room").orEmpty().ifBlank { getString(R.string.default_room) }
        teacher = intent?.getStringExtra("teacher").orEmpty()
        startTime = intent?.getStringExtra("startTime").orEmpty()
        notifyEpoch = intent?.getLongExtra("notifyEpoch", 0L) ?: 0L
        classEpoch = intent?.getLongExtra("classEpoch", 0L) ?: 0L

        if (notifyEpoch <= 0L || classEpoch <= notifyEpoch) {
            val now = System.currentTimeMillis()
            notifyEpoch = now
            classEpoch = now + 1L
        }

        if (classEpoch <= System.currentTimeMillis()) {
            // 修复 P0: startForegroundService 启动后，即使决定立即停止也必须先
            // startForeground()，否则 Android 12+ 抛 ForegroundServiceDidNotStartInTimeException。
            // 用最小占位通知履行契约，随后移除。
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                try {
                    val placeholder = NotificationCompat.Builder(this, CourseNotificationScheduler.CHANNEL_FLUID)
                        .setSmallIcon(R.drawable.ic_notification_time)
                        .setContentTitle(courseName)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build()
                    startForeground(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE, placeholder)
                } catch (_: Throwable) {}
            }
            androidx.core.app.NotificationManagerCompat.from(this)
                .cancel(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE)
            stopSelfCleanly("started after class end")
            return START_NOT_STICKY
        }

        handler.removeCallbacks(updater)
        // 发通知失败不能连累排下一拍：否则服务活着却不再收工（同 updater 那个坑）。
        try {
            postProgressNotification()
        } catch (t: Throwable) {
            android.util.Log.w(TAG, "postProgressNotification failed on start", t)
            // 必须给系统一个交代：startForegroundService 起来的服务，5 秒内既没 startForeground()
            // 也没 stopSelf() → Android 12+ 抛 ForegroundServiceDidNotStartInTimeException 直接崩进程。
            stopSelfCleanly("start post failed")
            return START_NOT_STICKY
        }
        handler.postDelayed(updater, UPDATE_INTERVAL_MS)
        return START_NOT_STICKY
    }

    private fun postProgressNotification() {
        val now = System.currentTimeMillis()
        updateSequence = if (updateSequence == Int.MAX_VALUE) 1 else updateSequence + 1
        val state = CourseLiveCardState(
            courseName = courseName,
            room = room,
            teacher = teacher,
            startTime = startTime,
            notifyEpoch = notifyEpoch,
            classEpoch = classEpoch,
            nowEpoch = now,
            updateSequence = updateSequence
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            // 与组件/提醒通知同源：点灵动通知进来落到课表页，不停在"上次的 tab"。
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_SCHEDULE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = VendorLiveCardRenderer.build(
            context = this,
            state = state,
            contentIntent = contentIntent,
            channelId = CourseNotificationScheduler.CHANNEL_FLUID
        )

        if (android.os.Build.VERSION.SDK_INT >= 26) {
            // 前台服务路径: startForeground 本身不需要 POST_NOTIFICATIONS 运行时权限
            startForeground(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE, notification)
        } else {
            // Lint MissingPermission: 前台服务由 startForegroundService 启动链路触发,
            //   但 API<26 notify 分支仍需权限校验兜底(权限被拒时静默跳过, 不抛 SecurityException)
            if (ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                androidx.core.app.NotificationManagerCompat.from(this)
                    .notify(CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE, notification)
            }
        }
        android.util.Log.d(
            TAG,
            "updated course progress=${state.progress} notify=$notifyEpoch class=$classEpoch"
        )
    }

    override fun onDestroy() {
        handler.removeCallbacks(updater)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FluidCloudService"
        private const val UPDATE_INTERVAL_MS = 15_000L
        // MODE_A / MODE_B 死常量已删（从未被读取——服务固定走 ProgressStyle 进度条模式）
    }
}
