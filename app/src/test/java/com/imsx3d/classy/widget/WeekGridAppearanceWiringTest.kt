package com.imsx3d.classy.widget

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression for changing the unused bitmap renderer while the launcher uses RemoteViews.
 * Source contracts only: these do not assert Android pixels or launcher behaviour.
 */
class WeekGridAppearanceWiringTest {
    private fun source(name: String) = File("src/main/java/com/imsx3d/classy/$name").readText()

    @Test fun `appearance reaches the production row factory`() {
        val provider = source("widget/WeekGridWidgetProvider.kt")
            .substringAfter("fun renderWidget").substringBefore("companion object")
        assertTrue(provider.contains("WeekGridRowService::class.java"))
        val factory = source("widget/WeekGridRowService.kt")
        val snapshot = factory.substringAfter("override fun onDataSetChanged()")
            .substringBefore("override fun getCount()")
        assertTrue(snapshot.contains("AppPrefs.getCourseColorStyle(context) == AppPrefs.COURSE_STYLE_BAR"))
        val render = factory.substringAfter("override fun getViewAt")
        assertTrue(render.contains("if (s.barStyle) s.scheme.bg"))
        assertTrue(render.contains("CourseColorUtil.softenedIndicator("))
        assertTrue(render.contains("if (s.barStyle) View.VISIBLE else View.GONE"))
        assertTrue(render.contains("tone(indicator)"))
        assertTrue(render.contains("applyGridBlockTypography(context, block, room, s.barStyle)"))
        assertTrue(source("widget/WidgetGridTypography.kt").contains("setViewPadding(R.id.wg_blk_name"))
    }

    @Test fun `course block owns one full height indicator`() {
        val xml = File("src/main/res/layout/widget_weekgrid_block.xml").readText()
        val indicator = xml.substringBefore("@+id/wg_blk_indicator").substringAfterLast("<ImageView") +
            xml.substringAfter("@+id/wg_blk_indicator").substringBefore("/>")
        assertTrue(indicator.contains("layout_height=\"match_parent\""))
        assertTrue(indicator.contains("@drawable/widget_course_indicator"))
        assertTrue(File("src/main/res/drawable/widget_course_indicator.xml").exists())
    }

    @Test fun `appearance changes refresh the collection data as well as the shell`() {
        val appearance = source("ui/screen/mine/AppearanceScreen.kt")
        val onSelect = appearance.substringAfter("AppPrefs.setCourseAppearance(context, it)").substringBefore("}")
        assertTrue(onSelect.contains("refreshWidgets()"))
        val provider = source("widget/WeekGridWidgetProvider.kt")
        assertTrue(provider.contains("awm.notifyAppWidgetViewDataChanged(widgetId, R.id.wg_rows)"))
    }
}
