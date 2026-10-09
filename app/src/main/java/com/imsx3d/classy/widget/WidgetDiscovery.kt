package com.imsx3d.classy.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.imsx3d.classy.BuildConfig

enum class WidgetDiscoveryState { REGISTERED, MISSING, UNKNOWN }

fun classifyWidgetDiscovery(expected: Set<String>, installed: Set<String>?): WidgetDiscoveryState = when {
    installed == null -> WidgetDiscoveryState.UNKNOWN
    installed.containsAll(expected) -> WidgetDiscoveryState.REGISTERED
    else -> WidgetDiscoveryState.MISSING
}

data class WidgetDiscoverySnapshot(val state: WidgetDiscoveryState, val report: String)

enum class WidgetPinResult { REQUESTED, UNSUPPORTED, REJECTED, ERROR }

/** Only queries this app's providers; no course contents or device identifiers are collected. */
object WidgetDiscovery {
    fun inspect(context: Context): WidgetDiscoverySnapshot {
        val manager = AppWidgetManager.getInstance(context)
        val pm = context.packageManager
        val variants = ALL_WIDGET_VARIANTS
        val expected = variants.map { it.receiverClass.name }.toSet()
        val providers = runCatching {
            manager.getInstalledProvidersForPackage(context.packageName, Process.myUserHandle())
        }
        val installed = providers.getOrNull()?.map { it.provider.className }?.toSet()
        val state = classifyWidgetDiscovery(expected, installed)
        val home = runCatching {
            pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
        }.getOrNull()
        val report = buildString {
            appendLine("Classy widget discovery v5")
            appendLine("package=${context.packageName}")
            appendLine("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("device=${Build.MANUFACTURER} / ${Build.MODEL}")
            appendLine("Android=${Build.VERSION.RELEASE} API=${Build.VERSION.SDK_INT}")
            appendLine("OS build=${Build.DISPLAY}")
            appendLine("home=${home?.let { "${it.packageName}/${it.name}" } ?: "unknown"}")
            if (home != null) {
                appendLine("homeVersion=" + runCatching {
                    pm.getPackageInfo(home.packageName, 0).versionName
                }.getOrDefault("unknown"))
            }
            appendLine("pinSupported=" + runCatching { manager.isRequestPinAppWidgetSupported }
                .fold({ it.toString() }, { "unknown (${it.javaClass.simpleName})" }))
            appendLine("systemProviders=${installed?.size ?: "unknown"}/${expected.size} state=$state")
            providers.exceptionOrNull()?.let { appendLine("queryError=${it.javaClass.simpleName}") }
            variants.forEach { variant ->
                val component = ComponentName(context, variant.receiverClass)
                val info = runCatching { pm.getReceiverInfo(component, PackageManager.GET_META_DATA) }
                appendLine("${context.getString(variant.displayNameRes)}: ${component.flattenToString()}")
                appendLine("  systemRegistered=${installed?.contains(component.className) ?: "unknown"}")
                appendLine("  receiverEnabled=${info.getOrNull()?.enabled ?: "unknown"}")
                appendLine("  componentState=" + runCatching { pm.getComponentEnabledSetting(component) }
                    .fold({ it.toString() }, { "unknown (${it.javaClass.simpleName})" }))
                appendLine("  boundIds=" + runCatching { manager.getAppWidgetIds(component).joinToString() }
                    .getOrDefault("unknown"))
                providers.getOrNull()?.find { it.provider == component }?.let {
                    if (Build.VERSION.SDK_INT >= 31) appendLine("  targetCells=${it.targetCellWidth}x${it.targetCellHeight}")
                    val layout = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) it.previewLayout.toString() else "unavailable"
                    appendLine("  previewLayout=$layout previewImage=${it.previewImage} minPx=${it.minWidth}x${it.minHeight} resizeMode=${it.resizeMode} minResizePx=${it.minResizeWidth}x${it.minResizeHeight}")
                }
            }
            appendLine("Bound IDs do not prove that the launcher rendered the widget successfully.")
        }
        return WidgetDiscoverySnapshot(state, report)
    }

    /** true from requestPinAppWidget means accepted for handling, never confirmed placement. */
    fun requestPin(context: Context, variant: WidgetVariantInfo): WidgetPinResult = try {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) WidgetPinResult.UNSUPPORTED
        else if (manager.requestPinAppWidget(ComponentName(context, variant.receiverClass), null, null))
            WidgetPinResult.REQUESTED
        else WidgetPinResult.REJECTED
    } catch (_: RuntimeException) {
        WidgetPinResult.ERROR
    }
}
