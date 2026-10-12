package com.imsx3d.classy.widget

import android.appwidget.AppWidgetManager
import android.os.Build
import android.util.SizeF
import android.util.TypedValue
import android.widget.RemoteViews
import com.imsx3d.classy.R

/** Keep the small Today card square even when a launcher's two cells are rectangular. */
internal object SmallWidgetLayout {
    fun fit(manager: AppWidgetManager, id: Int, content: RemoteViews): RemoteViews {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.getAppWidgetInfo(id)?.provider?.className != TodaySmallWidgetReceiver::class.java.name
        ) return content
        val options = manager.getAppWidgetOptions(id)
        @Suppress("DEPRECATION")
        val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
            .orEmpty().filter { it.width.isFinite() && it.height.isFinite() && it.width > 0 && it.height > 0 }
            .distinct().take(16)
        fun square(size: SizeF): RemoteViews = squareContent(content, size)
        // Give the launcher every reported orientation instead of guessing which one is active.
        if (sizes.isNotEmpty()) return RemoteViews(sizes.associateWith(::square))
        val (width, height) = RemoteViewsWidgetHelper.computeSizeDp(options)
        return square(SizeF(width.toFloat(), height.toFloat()))
    }

    @androidx.annotation.RequiresApi(31)
    internal fun squareContent(content: RemoteViews, size: SizeF): RemoteViews = RemoteViews(content).apply {
            val side = minOf(size.width, size.height)
            setViewLayoutWidth(R.id.widget_list_card, side, TypedValue.COMPLEX_UNIT_DIP)
            setViewLayoutHeight(R.id.widget_list_card, side, TypedValue.COMPLEX_UNIT_DIP)
        }
}
