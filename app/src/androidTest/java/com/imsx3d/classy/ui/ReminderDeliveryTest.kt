package com.imsx3d.classy.ui

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import com.imsx3d.classy.util.AppPrefs
import com.imsx3d.classy.widget.notification.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Dedicated empty emulator only: exercises real alarms, receivers, channels and notifications. */
class ReminderDeliveryTest {
    @Test fun recoverInterruptedFixture() = runBlocking {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("recoverDelivery") == "true")
        org.junit.Assume.assumeTrue(android.os.Build.MODEL.contains("sdk_gphone"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = SleepyApp.get()
        val tables = app.repository.getAllTables()
        check(tables.all { it.name == "Delivery fixture" })
        check(tables.all { table -> app.repository.getCourses(table.id).all { it.groupId == "delivery" } })
        AppPrefs.setReminderEnabled(context, false)
        app.notificationScheduler.cancelAll()
        tables.forEach { app.repository.deleteTable(it.id) }
    }

    @Test fun realDeliveryAndIndependentBeforeClassModes() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = SleepyApp.get()
        // Never overwrite a user's saved timetable or reminder configuration.
        org.junit.Assume.assumeTrue(android.os.Build.MODEL.contains("sdk_gphone"))
        org.junit.Assume.assumeTrue(app.repository.getAllTables().isEmpty())
        val prefs = context.getSharedPreferences("sleepy_prefs", Context.MODE_PRIVATE)
        val old = prefs.all.toMap()
        val nm = context.getSystemService(NotificationManager::class.java)
        var tableId = 0L
        try {
            AppPrefs.setReminderEnabled(context, true)
            AppPrefs.setDailyReminderEnabled(context, true)
            AppPrefs.setTodayReminderEnabled(context, true)
            AppPrefs.setTomorrowReminderEnabled(context, true)
            AppPrefs.setBeforeClassEnabled(context, true)
            AppPrefs.setBeforeClassMinutes(context, 5)
            val today = LocalDate.now()
            val start = LocalTime.now().plusMinutes(3).withSecond(0).withNano(0)
            tableId = app.repository.insertTable(TimeTableEntity(name = "Delivery fixture", startDate = today.with(java.time.DayOfWeek.MONDAY).toString(), isDefault = true))
            val courseId = app.repository.insertCourse(CourseEntity(groupId = "delivery", tableId = tableId,
                courseName = "Notification fixture", room = "R101", day = today.dayOfWeek.value,
                startNode = 1, step = 1, startWeek = 1, endWeek = 20, ownTime = true,
                startTime = start.toString(), endTime = start.plusMinutes(40).toString(), color = "#123456"))
            app.notificationScheduler.reschedule()
            nm.cancelAll()
            // Real short alarm, without invoking the receiver directly. The receiver rearms tomorrow.
            for ((rc, receiver, id) in listOf(Triple(991, DailyNotifyReceiver::class.java, 1001), Triple(992, TomorrowNotifyReceiver::class.java, 1002))) {
                val pi = PendingIntent.getBroadcast(context, rc, Intent(context, receiver), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                context.getSystemService(AlarmManager::class.java).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 1500, pi)
                awaitNotification(nm, id)
            }
            val epoch = today.atTime(start).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            for ((banner, fluid) in listOf(true to false, false to true, true to true, false to false)) {
                AppPrefs.setBeforeClassBannerEnabled(context, banner)
                AppPrefs.setBeforeClassFluidEnabled(context, fluid)
                FluidCloudService.requestStop()
                Thread.sleep(300)
                nm.cancelAll()
                context.sendBroadcast(Intent(context, BeforeClassNotifyReceiver::class.java).apply {
                    putExtra("generation", context.getSharedPreferences("course_alarm_registry", 0).getString("generation", null))
                    putExtra("courseId", courseId)
                    putExtra("classEpoch", epoch)
                    putExtra("notifyEpoch", epoch - 5 * 60_000L)
                    putExtra("startTime", start.toString())
                })
                if (banner) awaitNotification(nm, CourseNotificationScheduler.NOTIFY_BANNER)
                if (fluid) awaitNotification(nm, CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE)
                Thread.sleep(600)
                val ids = nm.activeNotifications.map { it.id }
                assertEquals("banner=$banner fluid=$fluid", banner, CourseNotificationScheduler.NOTIFY_BANNER in ids)
                assertEquals("banner=$banner fluid=$fluid", fluid, CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE in ids)
                if (banner && fluid) {
                    AppPrefs.setBeforeClassEnabled(context, false)
                    app.notificationScheduler.reschedule()
                    Thread.sleep(300)
                    assertFalse(nm.activeNotifications.any { it.id == 2000 || it.id == 2001 })
                    AppPrefs.setBeforeClassEnabled(context, true)
                    app.notificationScheduler.reschedule()
                }
            }
            // The primary selector must affect the actual platform notification, not just preview text.
            val contentIntent = PendingIntent.getActivity(context, 0,
                Intent(context, com.imsx3d.classy.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val state = CourseLiveCardState("Algebra", "R101", "Teacher", "10:00", 1000, 2000, 1500)
            for ((primary, expected) in listOf("name" to "Algebra", "room" to "R101", "time" to "10:00")) {
                AppPrefs.setBeforeClassFluidPrimary(context, primary)
                for (vendor in LiveCardVendor.entries) {
                    val notification = VendorLiveCardRenderer.build(context, state, contentIntent,
                        CourseNotificationScheduler.CHANNEL_FLUID, vendor)
                    assertEquals("$vendor $primary", expected,
                        notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
                    if (primary != "name") assertTrue(notification.extras
                        .getCharSequence(android.app.Notification.EXTRA_TEXT).toString().contains("Algebra"))
                }
            }
            context.sendBroadcast(Intent(context, DailyNotifyReceiver::class.java))
            context.sendBroadcast(Intent(context, TomorrowNotifyReceiver::class.java))
            awaitNotification(nm, 1001)
            awaitNotification(nm, 1002)
            AppPrefs.setTodayReminderEnabled(context, false)
            app.notificationScheduler.reschedule()
            assertFalse(nm.activeNotifications.any { it.id == 1001 })
            assertTrue(nm.activeNotifications.any { it.id == 1002 })
            AppPrefs.setTomorrowReminderEnabled(context, false)
            app.notificationScheduler.reschedule()
            assertFalse(nm.activeNotifications.any { it.id == 1002 })
            AppPrefs.setTodayReminderEnabled(context, true)
            AppPrefs.setTomorrowReminderEnabled(context, true)
            context.sendBroadcast(Intent(context, DailyNotifyReceiver::class.java))
            context.sendBroadcast(Intent(context, TomorrowNotifyReceiver::class.java))
            awaitNotification(nm, 1001)
            awaitNotification(nm, 1002)
            AppPrefs.setDailyReminderEnabled(context, false)
            app.notificationScheduler.reschedule()
            assertFalse(nm.activeNotifications.any { it.id == 1001 || it.id == 1002 })
            AppPrefs.setDailyReminderEnabled(context, true)
            context.sendBroadcast(Intent(context, DailyNotifyReceiver::class.java))
            awaitNotification(nm, 1001)
            AppPrefs.setReminderEnabled(context, false)
            app.notificationScheduler.reschedule()
            assertTrue(nm.activeNotifications.isEmpty())
        } finally {
            AppPrefs.setReminderEnabled(context, false)
            app.notificationScheduler.cancelAll()
            if (tableId != 0L) app.repository.deleteTable(tableId)
            prefs.edit().clear().apply {
                for ((key, value) in old) when (value) {
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is String -> putString(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }.commit()
            app.notificationScheduler.reschedule()
        }
    }

    private fun awaitNotification(manager: NotificationManager, id: Int) {
        val deadline = System.currentTimeMillis() + 12_000
        while (System.currentTimeMillis() < deadline) {
            if (manager.activeNotifications.any { it.id == id }) return
            Thread.sleep(100)
        }
        fail("Notification $id was not delivered")
    }
}
