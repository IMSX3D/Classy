package com.imsx3d.classy.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Picker previews now come from provider XML, following upstream #92's ColorOS/EMUI fix.
 * An upgrade must also remove generated previews cached by older Classy versions;
 * merely stopping setWidgetPreview calls would leave those previews in the system.
 */
object WidgetPreviewRegistrar {
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun clearLegacyGeneratedPreviews(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        ALL_WIDGET_VARIANTS.forEach { variant ->
            val provider = ComponentName(context, variant.receiverClass)
            try {
                manager.removeWidgetPreview(provider, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN)
            } catch (error: RuntimeException) {
                // A launcher/framework failure must not prevent startup or the other providers' cleanup.
                // Retry on the next process start rather than marking a failed migration complete.
                Log.w("WidgetPreview", "Unable to clear legacy preview for $provider", error)
            }
        }
    }
}
