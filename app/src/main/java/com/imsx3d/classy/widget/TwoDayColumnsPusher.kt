package com.imsx3d.classy.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.imsx3d.classy.R
import com.imsx3d.classy.util.DateUtils
import java.time.LocalDate

internal object TwoDayColumnsPusher {
    suspend fun push(context: Context, manager: AppWidgetManager, id: Int, generation: Long) {
        val data = TwoDayWidgetReceiver.loadDataSync(context, id)
        val scheme = resolveSchemePublic(context, data.themeKey, data.isDark)
        val views = RemoteViews(context.packageName, R.layout.widget_twoday_columns)
        val table = WidgetTableResolver.resolveBoundTable(id) ?: WidgetTableResolver.resolveCurrentTable()
        views.setTextViewText(R.id.twoday_header, table?.name.orEmpty())
        views.setTextViewText(R.id.twoday_date, DateUtils.shortDateSlash(LocalDate.now()))
        views.setInt(R.id.twoday_root, "setBackgroundColor", scheme.bg.toArgb())
        views.setInt(R.id.twoday_divider, "setBackgroundColor", scheme.surfaceVariant.toArgb())
        listOf(R.id.twoday_header, R.id.twoday_date, R.id.twoday_left_title, R.id.twoday_right_title,
            R.id.twoday_left_empty, R.id.twoday_right_empty).forEach {
            views.setTextColor(it, scheme.onSurfaceVariant.toArgb())
        }
        views.setTextViewText(R.id.twoday_left_title, context.getString(R.string.widget_day_today))
        views.setTextViewText(R.id.twoday_right_title, context.getString(R.string.widget_day_tomorrow))
        val click = PendingIntent.getActivity(context, WidgetRoutes.tapRequestCode(id),
            WidgetRoutes.tapIntent(context), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.twoday_root, click)
        fun bind(listId: Int, emptyId: Int, scope: String) {
            val intent = Intent(context, CourseRowWidgetService::class.java).apply {
                putExtra(CourseRowWidgetService.CourseRowFactory.EXTRA_WIDGET_ID, id)
                putExtra(CourseRowWidgetService.CourseRowFactory.EXTRA_SCOPE, scope)
                // Intent extras alone do not isolate RemoteViews adapter factories.
                this.data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(listId, intent)
            views.setEmptyView(listId, emptyId)
            views.setPendingIntentTemplate(listId, click)
            views.setOnClickPendingIntent(emptyId, click)
        }
        bind(R.id.twoday_left_list, R.id.twoday_left_empty, CourseRowWidgetService.CourseRowFactory.SCOPE_LEFT)
        bind(R.id.twoday_right_list, R.id.twoday_right_empty, CourseRowWidgetService.CourseRowFactory.SCOPE_RIGHT)
        if (generation > 0 && WidgetResizeCore.isStale(id, generation)) return
        manager.updateAppWidget(id, views)
        manager.notifyAppWidgetViewDataChanged(id, R.id.twoday_left_list)
        manager.notifyAppWidgetViewDataChanged(id, R.id.twoday_right_list)
    }
}