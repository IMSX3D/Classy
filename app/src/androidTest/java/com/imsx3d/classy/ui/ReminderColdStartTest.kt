package com.imsx3d.classy.ui

import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.util.AppPrefs
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.LocalTime
import java.time.LocalDate

/** Two-phase host-driven check: kill (not force-stop) the idle process between phases.
 * Opt-in, empty emulator only. The host waits for real configured reminder time.
 */
class ReminderColdStartTest {
    private fun context(): Context {
        assumeTrue(InstrumentationRegistry.getArguments().getString("coldDelivery") == "true")
        assumeTrue(android.os.Build.MODEL.contains("sdk_gphone"))
        return InstrumentationRegistry.getInstrumentation().targetContext
    }

    @Test fun prepare() = runBlocking {
        val context = context()
        val app = SleepyApp.get()
        assumeTrue(app.repository.getAllTables().isEmpty())
        val prefs = context.getSharedPreferences("sleepy_prefs", 0)
        val backup = context.getSharedPreferences("cold_delivery_backup", 0)
        check(!backup.contains("fixture_saved")) { "Complete the previous verification/cleanup first" }
        copyPrefs(prefs, backup)
        backup.edit().putBoolean("fixture_saved", true).commit()
        AppPrefs.setReminderEnabled(context, true)
        AppPrefs.setDailyReminderEnabled(context, true)
        AppPrefs.setTodayReminderEnabled(context, true)
        AppPrefs.setTomorrowReminderEnabled(context, true)
        AppPrefs.setBeforeClassEnabled(context, true)
        AppPrefs.setBeforeClassBannerEnabled(context, true)
        AppPrefs.setBeforeClassFluidEnabled(context, false)
        AppPrefs.setBeforeClassMinutes(context, 1)
        val time = LocalTime.now().plusMinutes(2).withSecond(0).withNano(0).toString()
        AppPrefs.setDailyReminderTime(context, time)
        AppPrefs.setTomorrowReminderTime(context, time)
        val date = LocalDate.now().plusDays(if (LocalTime.parse(time).isBefore(LocalTime.now())) 1 else 0)
        val classDateTime = date.atTime(LocalTime.parse(time)).plusMinutes(1)
        val tableId = app.repository.insertTable(TimeTableEntity(name = "Cold delivery fixture",
            startDate = date.with(java.time.DayOfWeek.MONDAY).toString(), isDefault = true))
        backup.edit().putLong("fixture_table", tableId).commit()
        app.repository.insertCourse(CourseEntity(groupId = "cold-delivery", tableId = tableId,
            courseName = "Cold reminder", room = "R101", day = classDateTime.dayOfWeek.value,
            startNode = 1, step = 1, startWeek = 1, endWeek = 20, ownTime = true,
            startTime = classDateTime.toLocalTime().toString(),
            endTime = classDateTime.plusMinutes(40).toLocalTime().toString(), color = "#123456"))
        kotlinx.coroutines.delay(1500) // Let repository edit debounce finish before recording generation.
        app.notificationScheduler.reschedule()
        context.getSystemService(NotificationManager::class.java).cancelAll()
        backup.edit().putString("fixture_generation",
            context.getSharedPreferences("course_alarm_registry", 0).getString("generation", null)).commit()
        println("COLD_DELIVERY_TIME=$time")
    }

    @Test fun verifyAndRestore() = runBlocking {
        val context = context()
        val backup = context.getSharedPreferences("cold_delivery_backup", 0)
        check(backup.getBoolean("fixture_saved", false))
        val app = SleepyApp.get()
        try {
            val ids = context.getSystemService(NotificationManager::class.java).activeNotifications.map { it.id }
            assertTrue("Cold today's summary missing: $ids", 1001 in ids)
            assertTrue("Cold tomorrow's summary missing: $ids", 1002 in ids)
            assertTrue("Cold before-class reminder missing: $ids", 2001 in ids)
            assertEquals("Cold Application startup invalidated alarms", backup.getString("fixture_generation", null),
                context.getSharedPreferences("course_alarm_registry", 0).getString("generation", null))
        } finally {
            AppPrefs.setReminderEnabled(context, false)
            app.notificationScheduler.cancelAll()
            app.repository.deleteTable(backup.getLong("fixture_table", 0))
            backup.edit().remove("fixture_saved").remove("fixture_generation").remove("fixture_table").commit()
            copyPrefs(backup, context.getSharedPreferences("sleepy_prefs", 0))
            backup.edit().clear().commit()
            app.notificationScheduler.reschedule()
        }
    }

    private fun copyPrefs(source: SharedPreferences, target: SharedPreferences) {
        target.edit().clear().apply {
            source.all.forEach { (key, value) ->
                when (value) {
                    is Boolean -> putBoolean(key, value)
                    is String -> putString(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }.commit()
    }
}
