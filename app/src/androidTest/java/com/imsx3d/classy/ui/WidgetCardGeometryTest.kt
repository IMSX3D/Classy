package com.imsx3d.classy.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.R
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

class WidgetCardGeometryTest {
    @Test fun gridHeaderKeepsTextInsideHighlightAtLargeFont() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (fontScale in listOf(1f, 1.5f)) {
                val base = instrumentation.targetContext
                val config = android.content.res.Configuration(base.resources.configuration).apply {
                    this.fontScale = fontScale
                }
                val context = base.createConfigurationContext(config)
                val density = context.resources.displayMetrics.density
                for (width in listOf(200, 360)) {
                    val remote = RemoteViews(context.packageName, R.layout.widget_weekgrid_card).apply {
                        setTextViewText(R.id.wg_hdr1_day, "周二")
                        setTextViewText(R.id.wg_hdr1_date, "13补")
                        setViewVisibility(R.id.wg_hdr1_pill, View.VISIBLE)
                    }
                    val root = remote.apply(context, FrameLayout(context))
                    val w = (width * density).roundToInt()
                    val h = (300 * density).roundToInt()
                    root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, w, h)
                    val pill = root.findViewById<View>(R.id.wg_hdr1_pill)
                    val day = root.findViewById<android.widget.TextView>(R.id.wg_hdr1_day)
                    val date = root.findViewById<android.widget.TextView>(R.id.wg_hdr1_date)
                    val content = day.parent as View
                    assertTrue("top padding, font=$fontScale width=$width", content.paddingTop >= 3 * density)
                    assertTrue("date clipped, font=$fontScale width=$width",
                        date.top + date.height + content.paddingBottom <= pill.height)
                    assertTrue(day.layout.height <= day.height)
                    assertTrue(date.layout.height <= date.height)
                }
            }
        }
    }

    @Test fun cardsFillHostAndKeepPaintedCornersWithoutOutlineClipping() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            // Today and small Today use the same layout; the other two types have separate layouts.
            for (layout in listOf(R.layout.widget_course_list, R.layout.widget_twoday_columns, R.layout.widget_weekgrid_card)) {
                for ((width, height) in listOf(160 to 250, 300 to 140, 200 to 200)) {
                    for (color in listOf(Color.WHITE, Color.BLACK)) {
                        val views = RemoteViews(context.packageName, layout).apply {
                            setInt(R.id.widget_card_background, "setColorFilter", color)
                        }
                        val root = views.apply(context, FrameLayout(context))
                        // OEM drag/exit snapshots can omit RenderNode outline clipping.
                        root.clipToOutline = false
                        val w = (width * density).roundToInt()
                        val h = (height * density).roundToInt()
                        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                        root.layout(0, 0, w, h)
                        val background = root.findViewById<View>(R.id.widget_card_background)
                        assertEquals(w, background.width)
                        assertEquals(h, background.height)
                        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        root.draw(Canvas(bitmap))
                        for ((x, y) in listOf(0 to 0, w - 1 to 0, 0 to h - 1, w - 1 to h - 1)) {
                            assertEquals("layout=$layout corner=$x,$y", 0, Color.alpha(bitmap.getPixel(x, y)))
                        }
                        assertEquals(color, bitmap.getPixel(w / 2, 1))
                        bitmap.recycle()
                    }
                }
            }
        }
    }
}
