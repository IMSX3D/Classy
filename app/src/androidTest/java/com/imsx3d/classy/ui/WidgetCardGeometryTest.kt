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
