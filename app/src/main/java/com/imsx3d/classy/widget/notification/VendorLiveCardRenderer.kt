package com.imsx3d.classy.widget.notification

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.imsx3d.classy.R
import com.imsx3d.classy.util.AppPrefs
import com.imsx3d.classy.widget.resolveSchemePublic
import androidx.compose.ui.graphics.toArgb

/** Android vendor surface selected from the runtime manufacturer string. */
enum class LiveCardVendor {
    OPPO,
    ONEPLUS,
    REALME,
    XIAOMI,
    VIVO,
    IQOO,
    MEIZU,
    HUAWEI,
    HONOR,
    SAMSUNG,
    GENERIC
}

fun detectLiveCardVendor(manufacturer: String = Build.MANUFACTURER): LiveCardVendor {
    return when (manufacturer.trim().lowercase()) {
        "oppo" -> LiveCardVendor.OPPO
        "oneplus" -> LiveCardVendor.ONEPLUS
        "realme" -> LiveCardVendor.REALME
        "xiaomi", "redmi", "poco" -> LiveCardVendor.XIAOMI
        "vivo" -> LiveCardVendor.VIVO
        "iqoo" -> LiveCardVendor.IQOO
        "meizu" -> LiveCardVendor.MEIZU
        "huawei" -> LiveCardVendor.HUAWEI
        "honor" -> LiveCardVendor.HONOR
        "samsung" -> LiveCardVendor.SAMSUNG
        else -> LiveCardVendor.GENERIC
    }
}

/**
 * Builds one notification for the shared OPPO lifecycle and adds only documented
 * vendor extras. Vendor access approval remains a device/platform concern; every
 * renderer leaves the generic Android notification usable as the fallback.
 */
object VendorLiveCardRenderer {
    fun build(
        context: Context,
        state: CourseLiveCardState,
        contentIntent: PendingIntent,
        channelId: String,
        vendor: LiveCardVendor = detectLiveCardVendor()
    ): Notification {
        val primary = AppPrefs.getBeforeClassFluidPrimary(context)
        val primaryText = when (primary) {
            "name" -> state.courseName
            "time" -> state.startTime
            else -> state.room
        }.ifBlank { state.courseName }

        val detailText = listOf(state.courseName, state.startTime, state.room, state.teacher)
            .filter { it.isNotBlank() && it != primaryText }.distinct().joinToString(" · ")

        val style = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(state.progress.coerceIn(0, 100))
        val isSystemDark = (context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val themePrimaryArgb = resolveSchemePublic(
            context,
            AppPrefs.getThemeKey(context),
            AppPrefs.isDarkMode(context, isSystemDark)
        ).primary.toArgb()
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_time)
            .setColor(themePrimaryArgb)
            .setContentTitle(primaryText)
            .setContentText(detailText)
            .setStyle(style)
            .setProgress(100, state.progress, false)
            .setOngoing(true)
            // A timed course reminder should appear at the reminder time, not after
            // the system's foreground-service notification deferral window.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setRequestPromotedOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(contentIntent)
            .setShortCriticalText(state.startTime.takeIf { it.isNotBlank() } ?: primaryText.take(7))

        when (vendor) {
            // Upstream #80: private focus extras demote the progress notification on HyperOS.
            LiveCardVendor.XIAOMI -> Unit
            LiveCardVendor.VIVO, LiveCardVendor.IQOO -> addVivoExtras(builder, state, contentIntent, context, primaryText, detailText)
            LiveCardVendor.MEIZU -> addMeizuExtras(builder, state, contentIntent, context, themePrimaryArgb, primaryText)
            LiveCardVendor.OPPO, LiveCardVendor.ONEPLUS, LiveCardVendor.REALME,
            LiveCardVendor.HUAWEI, LiveCardVendor.HONOR, LiveCardVendor.SAMSUNG,
            LiveCardVendor.GENERIC -> Unit
        }
        return builder.build()
    }

    private fun addVivoExtras(
        builder: NotificationCompat.Builder,
        state: CourseLiveCardState,
        contentIntent: PendingIntent,
        context: Context,
        primaryText: String,
        detailText: String
    ) {
        val extras = Bundle().apply {
            putInt(
                "notification.superx.operation",
                when {
                    !state.isActive -> 2
                    state.updateSequence <= 1 -> 0
                    else -> 1
                }
            )
            putBoolean("notification.superx.showNotify", true)
            putInt("notification.superx.template", 2)
            putString("notification.superx.scene", "METTING")
            putInt("notification.superx.changedRecord", state.updateSequence)
            putParcelable("notification.superx.clickResp", contentIntent)
            putBundle("notification.superx.baseInfos", Bundle().apply {
                putCharSequence("title", primaryText)
                putCharSequence("content", detailText)
                putInt("progressState", if (state.isActive) 0 else 1)
            })
            putBundle("notification.superx.infos", Bundle().apply {
                putInt("progress", state.progress)
                putParcelableArrayList("nodeIcon", arrayListOf(
                    Icon.createWithResource(context, R.drawable.ic_notification_time),
                    Icon.createWithResource(context, R.drawable.ic_notifications)
                ))
            })
            putBundle("notification.superx.shortInfos", Bundle().apply {
                putParcelable("image", Icon.createWithResource(context, R.drawable.ic_notification_time))
                putParcelable("imageClickResp", contentIntent)
                putCharSequence("describeShort", primaryText)
                putCharSequence("coreInfoShort", state.room)
            })
            putBundle("notification.superx.capsule", Bundle().apply {
                putInt("state", if (state.isActive) 1 else 0)
                putParcelable("icon", Icon.createWithResource(context, R.drawable.ic_notification_time))
                putString("content", "${primaryText} ${state.progress}%")
            })
        }
        // Preserve the standard promotion request and short critical text.
        // setExtras replaces these AndroidX fields and prevents live promotion.
        builder.addExtras(extras)
    }

    private fun addMeizuExtras(
        builder: NotificationCompat.Builder,
        state: CourseLiveCardState,
        contentIntent: PendingIntent,
        context: Context,
        themeColor: Int,
        primaryText: String
    ) {
        val isDarkTheme = AppPrefs.isDarkMode(
            context,
            (context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
        )
        val capsule = Bundle().apply {
            putInt("notification.live.capsuleStatus", 1)
            putInt("notification.live.capsuleType", 5)
            putString("notification.live.capsuleContent", "${primaryText} ${state.progress}%")
            putParcelable("notification.live.capsuleIcon", Icon.createWithResource(context, R.drawable.ic_notification_time))
            putInt("notification.live.capsuleBgColor", themeColor)
            // UI-4s: 内容色按底色亮度配对 —— 原来硬编码白字，深色主题下底色是**浅色主色**
            // （M3 深色 primary 是浅色调）→ 白字压浅底几乎看不见。改走 textColorOn 同一决策树。
            putInt(
                "notification.live.capsuleContentColor",
                com.imsx3d.classy.util.CourseColorUtil.textColorOn(
                    themeColor, isDarkTheme, 0xFFFFFFFF.toInt()
                )
            )
        }
        builder.addExtras(Bundle().apply {
            putBoolean("is_live", true)
            putInt("notification.live.operation", if (state.updateSequence <= 1) 0 else 1)
            putInt("notification.live.type", 2)
            putBundle("notification.live.capsule", capsule)
        })
    }
}
