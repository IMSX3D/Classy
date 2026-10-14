package com.imsx3d.classy.util

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Uses an isolated preference file: never resets the device owner's settings or courses. */
class DefaultSettingsTest {
    @Test fun freshInstallAndResetMatchPhoneBaselineWithoutOverwritingSavedChoices() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val file = "test_phone_defaults"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences(file, mode)
        }
        val prefs = context.getSharedPreferences("sleepy_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            assertBaseline(context)
            AppPrefs.setThemeMode(context, "dark")
            AppPrefs.setCourseColorStyle(context, "bar")
            AppPrefs.setCourseTitleCentered(context, false)
            AppPrefs.setCourseTitleCentered(context, false, widget = true)
            AppPrefs.setBeforeClassMinutes(context, 30)
            AppPrefs.setReminderEnabled(context, true)
            AppPrefs.setWidgetShowTableName(context, true)
            AppPrefs.setGridAdaptiveHeight(context, false)
            prefs.edit().putString("holiday_transfer_42", "preserved-user-data").commit()
            assertEquals("dark", AppPrefs.getThemeMode(context))
            assertEquals("bar", AppPrefs.getCourseColorStyle(context))
            assertFalse(AppPrefs.isCourseTitleCentered(context))
            AppPrefs.resetSettings(context)
            assertBaseline(context)
            assertEquals("preserved-user-data", prefs.getString("holiday_transfer_42", null))
            // Reset remains stable when repeated; no initialization flag restores old defaults.
            AppPrefs.resetSettings(context)
            assertBaseline(context)
        } finally {
            prefs.edit().clear().commit()
        }
    }

    private fun assertBaseline(c: Context) {
        assertEquals("light", AppPrefs.getThemeMode(c))
        assertFalse(AppPrefs.isDarkMode(c, isSystemDark = true))
        assertEquals("system", AppPrefs.getThemeKey(c))
        assertEquals("zh-CN", AppPrefs.getLanguage(c))
        assertTrue(AppPrefs.isNavDock(c))
        assertTrue(AppPrefs.isHighRefresh(c))
        assertEquals("fill", AppPrefs.getCourseColorStyle(c))
        assertFalse(AppPrefs.isCourseColorless(c))
        assertTrue(AppPrefs.isCourseTitleCentered(c))
        assertTrue(AppPrefs.isCourseTitleCentered(c, widget = true))
        assertTrue(AppPrefs.isGridAdaptiveHeight(c))
        assertFalse(AppPrefs.isGridAutoHideEmptyEvening(c))
        assertFalse(AppPrefs.isGridPinchZoom(c))
        assertEquals(1f, AppPrefs.getGridScale(c), 0f)
        assertEquals(1f, AppPrefs.getWeekScale(c), 0f)
        assertEquals(1f, AppPrefs.getGridRowScale(c), 0f)
        assertEquals(1f, AppPrefs.getGridCornerRatio(c), 0f)
        assertEquals("cards", AppPrefs.getStartView(c))
        assertEquals("room", AppPrefs.getGridSubInfo(c))
        assertEquals("time", AppPrefs.getDisplayMode(c))
        assertEquals((1..7).toSet(), AppPrefs.getVisibleDays(c))
        assertTrue(AppPrefs.isShowDate(c))
        assertFalse(AppPrefs.isWeekTwoColumn(c))
        assertFalse(AppPrefs.isWeekHideEmptyDays(c))
        assertFalse(AppPrefs.isWidgetShowTableName(c))
        assertFalse(AppPrefs.isWidgetColorless(c))
        assertFalse(AppPrefs.isWidgetUseAlias(c))
        assertFalse(AppPrefs.isNearestBusyDay(c))
        assertTrue(AppPrefs.isHolidayGreyHoliday(c))
        assertFalse(AppPrefs.isHolidayGreyWeekend(c))
        assertEquals("grey", AppPrefs.getHolidayStyle(c))
        assertFalse(AppPrefs.isReminderEnabled(c))
        assertFalse(AppPrefs.isDailyReminderEnabled(c))
        assertTrue(AppPrefs.isTodayReminderEnabled(c))
        assertTrue(AppPrefs.isTomorrowReminderEnabled(c))
        assertEquals("07:00", AppPrefs.getDailyReminderTime(c))
        assertEquals("22:00", AppPrefs.getTomorrowReminderTime(c))
        assertTrue(AppPrefs.isBeforeClassEnabled(c))
        assertEquals(5, AppPrefs.getBeforeClassMinutes(c))
        assertTrue(AppPrefs.isBeforeClassBannerEnabled(c))
        assertTrue(AppPrefs.isBeforeClassFluidEnabled(c))
        assertEquals("room", AppPrefs.getBeforeClassFluidPrimary(c))
        assertTrue(AppPrefs.isUpdateCheckEnabled(c))
    }
}
