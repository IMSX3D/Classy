package com.imsx3d.classy.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** OEM widget broadcasts that are public compatibility extensions of AppWidget. */
object WidgetVendorActions {
    const val XIAOMI_UPDATE_ACTION = "miui.appwidget.action.APPWIDGET_UPDATE"

    /** Reuses the receiver's normal update path; no vendor-only renderer is introduced. */
    fun dispatchXiaomiUpdate(
        provider: AppWidgetProvider,
        context: Context,
        intent: Intent
    ) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, provider::class.java))
        if (ids.isNotEmpty()) {
            provider.onUpdate(context, manager, ids)
        }
    }
}
