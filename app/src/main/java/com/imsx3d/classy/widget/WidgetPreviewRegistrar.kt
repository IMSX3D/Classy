package com.imsx3d.classy.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Registers generated picker previews on Android 15+ while retaining the
 * previewLayout/previewImage XML fallback for older hosts.
 *
 * A preview is deliberately data-free: the provider's real course data must
 * never be copied into a launcher preview or exposed before the widget is
 * configured by the host.
 *
 * UI-7（2026-09-28 用户报障「组件视图展示太简陋」）: 之前这里给**所有**变体挂的是
 * `widget_bitmap_container`（里面只有一张空 ImageView，预览期没有任何位图）——
 * 认这条路的 launcher 在选择器里看到的就是一张空白卡。现在按变体挂
 * [WidgetVariantInfo.previewLayoutRes]（真实容器 + 真实行布局 + 样例数据）。
 */
object WidgetPreviewRegistrar {
    private const val TAG = "WidgetPreview"

    fun shouldRegisterGeneratedPreview(apiLevel: Int = Build.VERSION.SDK_INT): Boolean =
        apiLevel >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun register(context: Context): PreviewRegistrationResult {
        if (!shouldRegisterGeneratedPreview()) {
            return PreviewRegistrationResult.UNSUPPORTED_API
        }
        val manager = AppWidgetManager.getInstance(context)
        var registered = 0
        var failed = 0
        ALL_WIDGET_VARIANTS.forEach { variant ->
            val provider = ComponentName(context, variant.receiverClass)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                    manager.setWidgetPreview(
                        provider,
                        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                        RemoteViews(context.packageName, variant.previewLayoutRes)
                    )
                }
                registered++
            } catch (error: RuntimeException) {
                failed++
                Log.w(TAG, "Unable to register preview for $provider", error)
            }
        }
        return if (failed == 0) {
            PreviewRegistrationResult.REGISTERED(registered)
        } else {
            PreviewRegistrationResult.PARTIAL(registered, failed)
        }
    }
}

sealed interface PreviewRegistrationResult {
    data object UNSUPPORTED_API : PreviewRegistrationResult
    data class REGISTERED(val count: Int) : PreviewRegistrationResult
    data class PARTIAL(val registered: Int, val failed: Int) : PreviewRegistrationResult
}
