package com.imsx3d.classy.ui.screen.mine

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.imsx3d.classy.R
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.ui.component.SettingsGroupCard
import com.imsx3d.classy.ui.component.SettingsNoteRow
import com.imsx3d.classy.util.AppPrefs
import com.imsx3d.classy.widget.notification.CourseNotificationScheduler

/** Report system gates separately from the user's saved reminder preferences. */
@Composable
internal fun ReminderDeliveryStatus() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var notifications by remember { mutableStateOf(true) }
    var blockedChannels by remember { mutableStateOf(false) }
    var exact by remember { mutableStateOf(true) }
    var promotionBlocked by remember { mutableStateOf(false) }
    var quietMode by remember { mutableStateOf(false) }
    DisposableEffect(lifecycle) {
        fun refresh() {
            notifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
            val manager = context.getSystemService(NotificationManager::class.java)
            promotionBlocked = Build.VERSION.SDK_INT >= 36 && AppPrefs.isBeforeClassFluidEnabled(context) && !manager.canPostPromotedNotifications()
            quietMode = manager.currentInterruptionFilter in listOf(
                NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                NotificationManager.INTERRUPTION_FILTER_NONE,
                NotificationManager.INTERRUPTION_FILTER_ALARMS
            )
            val channels = buildList {
                if (AppPrefs.isDailyReminderEnabled(context) && (AppPrefs.isTodayReminderEnabled(context) || AppPrefs.isTomorrowReminderEnabled(context))) add(CourseNotificationScheduler.CHANNEL_DAILY)
                if (AppPrefs.isBeforeClassEnabled(context)) {
                    if (AppPrefs.isBeforeClassBannerEnabled(context)) add(CourseNotificationScheduler.CHANNEL_BEFORE_CLASS)
                    if (AppPrefs.isBeforeClassFluidEnabled(context)) add(CourseNotificationScheduler.CHANNEL_FLUID)
                }
            }
            blockedChannels = channels.any { manager.getNotificationChannel(it)?.importance == NotificationManager.IMPORTANCE_NONE }
            exact = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        }
        refresh()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh()
                SleepyApp.get().notificationScheduler.scheduleAll()
            }
        }
        lifecycle.addObserver(observer)
        val prefs = context.getSharedPreferences("sleepy_prefs", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            lifecycle.removeObserver(observer)
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    SettingsGroupCard {
        Column(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
            SettingsNoteRow(stringResource(when {
                !notifications -> R.string.reminder_notifications_blocked
                blockedChannels -> R.string.reminder_channels_blocked
                else -> R.string.reminder_notifications_allowed
            }))
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }) { Text(stringResource(R.string.reminder_system_notifications)) }
            if (promotionBlocked) {
                SettingsNoteRow(stringResource(R.string.reminder_promotion_blocked))
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 36) {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
                        else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                    }
                }) { Text(stringResource(R.string.reminder_promotion_settings)) }
            }
            if (quietMode) SettingsNoteRow(stringResource(R.string.reminder_quiet_mode))
            if (!exact) {
                SettingsNoteRow(stringResource(R.string.reminder_exact_needed))
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 31) {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                        if (intent.resolveActivity(context.packageManager) != null) context.startActivity(intent)
                        else context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                    }
                }) { Text(stringResource(R.string.reminder_allow_exact)) }
            }
            SettingsNoteRow(stringResource(
                if (Build.MANUFACTURER.lowercase() in listOf("vivo", "iqoo"))
                    R.string.reminder_background_vivo else R.string.reminder_background_help
            ), topPadding = 8.dp, bottomPadding = 0.dp)
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")))
            }) { Text(stringResource(R.string.reminder_background_settings)) }
        }
    }
}
