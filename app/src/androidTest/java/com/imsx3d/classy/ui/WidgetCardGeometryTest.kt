package com.imsx3d.classy.ui

import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.R
import com.imsx3d.classy.widget.SmallWidgetLayout
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt

@SdkSuppress(minSdkVersion = 31)
class WidgetCardGeometryTest {
    @Test fun smallCardFitsTallAndWideHostWithoutStretching() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val density = context.resources.displayMetrics.density
            for (size in listOf(SizeF(160f, 250f), SizeF(300f, 140f), SizeF(200f, 200f))) {
                val content = RemoteViews(context.packageName, R.layout.widget_course_list)
                val root = SmallWidgetLayout.squareContent(content, size).apply(context, FrameLayout(context))
                val w = (size.width * density).roundToInt()
                val h = (size.height * density).roundToInt()
                root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, w, h)
                val card = root.findViewById<View>(R.id.widget_list_card)
                assertEquals(card.width, card.height)
                assertEquals(minOf(w, h), card.width)
                assertTrue(card.left >= 0 && card.top >= 0 && card.right <= w && card.bottom <= h)
                assertTrue(card.clipToOutline)
            }
        }
    }
}
