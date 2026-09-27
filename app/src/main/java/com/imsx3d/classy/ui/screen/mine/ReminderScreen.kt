package com.imsx3d.classy.ui.screen.mine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.imsx3d.classy.R
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.ui.component.SettingsGroupCard
import com.imsx3d.classy.ui.component.SettingsGroupRow
import com.imsx3d.classy.ui.component.SettingsNoteRow
import com.imsx3d.classy.ui.component.SettingsRowDivider
import com.imsx3d.classy.ui.component.SettingsScaffold
import com.imsx3d.classy.ui.component.SettingsSwitchRow
import com.imsx3d.classy.ui.theme.SleepyTheme
import com.imsx3d.classy.ui.theme.noRippleClickable
import com.imsx3d.classy.util.AppPrefs
import com.imsx3d.classy.util.DateUtils
import com.imsx3d.classy.util.TimeTableUtils
import com.imsx3d.classy.widget.WidgetTableResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private enum class DailyReminderTimeTarget { Today, Tomorrow }

/** 真实课表存在时，提醒设置页用来生成示例的最小数据集。 */
private data class ReminderSchedulePreview(
    val today: ReminderDayPreview,
    val tomorrow: ReminderDayPreview,
    val nextClass: ReminderCoursePreview?
)

private data class ReminderDayPreview(
    val date: LocalDate,
    val courses: List<CourseEntity>,
    val firstCourse: ReminderCoursePreview?
)

private data class ReminderCoursePreview(
    val date: LocalDate,
    val course: CourseEntity,
    val startTime: String
)

private suspend fun loadReminderSchedulePreview(): ReminderSchedulePreview? = withContext(Dispatchers.IO) {
    val table = runCatching { WidgetTableResolver.resolveCurrentTable() }.getOrNull() ?: return@withContext null
    val allCourses = runCatching {
        SleepyApp.get().repository.getCourses(table.id)
    }.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@withContext null
    val nodes = TimeTableUtils.parseNodes(table.timeJson)
    val today = LocalDate.now()

    fun coursesOn(date: LocalDate): List<CourseEntity> {
        if (DateUtils.semesterStatus(table.startDate, table.maxWeek, date) != DateUtils.SemesterStatus.IN_RANGE) {
            return emptyList()
        }
        val week = DateUtils.currentWeek(table.startDate, date)
        return allCourses
            .filter { it.day == DateUtils.todayDayOfWeek(date) && it.inWeek(week) }
            .sortedWith(compareBy<CourseEntity> { courseStartTime(it, nodes) ?: LocalTime.MAX }.thenBy { it.startNode })
    }

    fun dayPreview(date: LocalDate): ReminderDayPreview {
        val courses = coursesOn(date)
        val first = courses.firstOrNull()?.let { course ->
            ReminderCoursePreview(
                date = date,
                course = course,
                startTime = courseStartTime(course, nodes)?.format(PREVIEW_TIME_FORMAT) ?: "--:--"
            )
        }
        return ReminderDayPreview(date = date, courses = courses, firstCourse = first)
    }

    var nextClass: ReminderCoursePreview? = null
    val searchDays = table.maxWeek.coerceAtLeast(1) * 7 + 7
    for (offset in 0..searchDays) {
        val date = today.plusDays(offset.toLong())
        val candidate = coursesOn(date)
            .mapNotNull { course ->
                val start = courseStartTime(course, nodes) ?: return@mapNotNull null
                if (offset == 0 && !start.isAfter(LocalTime.now())) return@mapNotNull null
                ReminderCoursePreview(date, course, start.format(PREVIEW_TIME_FORMAT))
            }
            .minByOrNull { it.startTime }
        if (candidate != null) {
            nextClass = candidate
            break
        }
    }

    ReminderSchedulePreview(
        today = dayPreview(today),
        tomorrow = dayPreview(today.plusDays(1)),
        nextClass = nextClass
    )
}

private fun courseStartTime(course: CourseEntity, nodes: List<TimeTableUtils.NodeTime>): LocalTime? {
    if (course.ownTime && course.startTime.isNotBlank()) {
        return runCatching {
            LocalTime.parse(course.startTime, DateTimeFormatter.ofPattern("H:mm"))
        }.getOrNull()
    }
    return nodes.find { it.node == course.startNode }?.start
}

private val PREVIEW_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun buildDailyPreviewText(
    context: Context,
    day: ReminderDayPreview,
    dateLabelRes: Int
): String {
    val dateLabel = context.getString(
        dateLabelRes,
        DateUtils.shortDateSlash(day.date),
        DateUtils.localizedDay(day.date.dayOfWeek.value, context)
    )
    val first = day.firstCourse ?: return context.getString(
        R.string.reminder_daily_preview_dynamic_no_course,
        dateLabel
    )
    val courseName = first.course.courseName.ifBlank { context.getString(R.string.default_course_name) }
    val room = first.course.room.ifBlank { context.getString(R.string.notif_room_unknown) }
    val teacher = first.course.teacher.trim().takeIf { it.isNotEmpty() }?.let {
        context.getString(R.string.reminder_preview_teacher, it)
    }.orEmpty()
    return context.getString(
        R.string.reminder_daily_preview_dynamic,
        dateLabel,
        day.courses.size,
        courseName,
        first.startTime,
        room,
        teacher
    )
}

private fun buildBeforeClassPreviewText(
    context: Context,
    preview: ReminderSchedulePreview
): String {
    val next = preview.nextClass ?: return context.getString(R.string.reminder_before_class_preview_dynamic_no_course)
    val dateLabel = context.getString(
        R.string.reminder_preview_date,
        DateUtils.shortDateSlash(next.date),
        DateUtils.localizedDay(next.date.dayOfWeek.value, context)
    )
    val courseName = next.course.courseName.ifBlank { context.getString(R.string.default_course_name) }
    val room = next.course.room.ifBlank { context.getString(R.string.notif_room_unknown) }
    val teacher = next.course.teacher.trim().takeIf { it.isNotEmpty() }?.let {
        context.getString(R.string.reminder_preview_teacher, it)
    }.orEmpty()
    return context.getString(
        R.string.reminder_before_class_preview_dynamic,
        dateLabel,
        courseName,
        next.startTime,
        room,
        teacher
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderScreen(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    var masterEnabled by remember { mutableStateOf(AppPrefs.isReminderEnabled(context)) }
    var dailyEnabled by remember { mutableStateOf(AppPrefs.isDailyReminderEnabled(context)) }
    var todayEnabled by remember { mutableStateOf(AppPrefs.isTodayReminderEnabled(context)) }
    var dailyTime by remember { mutableStateOf(AppPrefs.getDailyReminderTime(context)) }
    var tomorrowEnabled by remember { mutableStateOf(AppPrefs.isTomorrowReminderEnabled(context)) }
    var tomorrowTime by remember { mutableStateOf(AppPrefs.getTomorrowReminderTime(context)) }
    var beforeClassEnabled by remember { mutableStateOf(AppPrefs.isBeforeClassEnabled(context)) }
    var beforeClassMinutes by remember { mutableStateOf(AppPrefs.getBeforeClassMinutes(context)) }
    var timePickerTarget by remember { mutableStateOf<DailyReminderTimeTarget?>(null) }
    var minutesDialog by remember { mutableStateOf(false) }
    // 提前分钟输入态：用 TextFieldValue 而不是 String —— String 版内部初值是 selection=0，
    // 自动聚焦后光标停在"10"前面，再敲数字会变成"510"（UI-26a 真机发现）。
    var minutesInput by remember { mutableStateOf(TextFieldValue(beforeClassMinutes.toString())) }
    var fluidEnabled by remember { mutableStateOf(AppPrefs.isBeforeClassFluidEnabled(context)) }
    var bannerEnabled by remember { mutableStateOf(AppPrefs.isBeforeClassBannerEnabled(context)) }
    var fluidPrimary by remember { mutableStateOf(AppPrefs.getBeforeClassFluidPrimary(context)) }
    var fieldsMenuExpanded by remember { mutableStateOf(false) }
    var schedulePreview by remember { mutableStateOf<ReminderSchedulePreview?>(null) }

    // 示例只读取当前课表，不参与提醒调度；无可分析课表时保留资源中的通用示例。
    LaunchedEffect(Unit) {
        schedulePreview = loadReminderSchedulePreview()
    }

    val todayPreviewText = schedulePreview?.let {
        buildDailyPreviewText(context, it.today, R.string.reminder_preview_today_date)
    } ?: stringResource(R.string.reminder_daily_preview)
    val tomorrowPreviewText = schedulePreview?.let {
        buildDailyPreviewText(context, it.tomorrow, R.string.reminder_preview_tomorrow_date)
    } ?: stringResource(R.string.reminder_tomorrow_preview)
    val beforeClassPreviewText = schedulePreview?.let {
        buildBeforeClassPreviewText(context, it)
    } ?: stringResource(R.string.reminder_before_class_preview)

    // debounce：分钟输入停止 500ms 后才持久化并重排提醒，
    //   避免每敲一键就触发一次全量 cancelAll + scheduleAll（查库 + 重排全部闹钟）。
    LaunchedEffect(minutesInput.text) {
        val raw = minutesInput.text
        if (raw.isBlank()) return@LaunchedEffect
        delay(500)
        val v = raw.toIntOrNull()?.coerceIn(1, 999) ?: return@LaunchedEffect
        beforeClassMinutes = v
        AppPrefs.setBeforeClassMinutes(context, v)
        SleepyApp.get().notificationScheduler.scheduleAll()
    }

    // 输入框唯一的写入口：只留数字、最多 3 位，且光标恒在末尾（数字字段的追加式输入语义）
    fun setMinutesInput(text: String) {
        val digits = text.filter { it.isDigit() }.take(3)
        minutesInput = TextFieldValue(digits, selection = TextRange(digits.length))
    }

    // Permission launcher — NOT one-shot, can be re-triggered by clicking toggle again
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            masterEnabled = true
            AppPrefs.setReminderEnabled(context, true)
            SleepyApp.get().notificationScheduler.scheduleAll()
        } else {
            // Permission denied → revert to off
            masterEnabled = false
            AppPrefs.setReminderEnabled(context, false)
        }
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Pre-Android 13: permission auto-granted at install
            masterEnabled = true
            AppPrefs.setReminderEnabled(context, true)
            SleepyApp.get().notificationScheduler.scheduleAll()
        }
    }

    fun onMasterToggle(on: Boolean) {
        if (on) {
            // Check if already granted
            val alreadyGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else true

            if (alreadyGranted) {
                masterEnabled = true
                AppPrefs.setReminderEnabled(context, true)
                SleepyApp.get().notificationScheduler.scheduleAll()
            } else {
                requestNotificationPermission()
            }
        } else {
            // 关闭 master 只设 reminder_master=false + cancelAll(); scheduleAll 与各 Receiver 均双重检查
            //   isReminderEnabled, 无需覆写子开关(否则重开 master 后 daily/beforeClass 配置全丢)。
            masterEnabled = false
            AppPrefs.setReminderEnabled(context, false)
            // cancelAll 现为 suspend，由 IO 协程调用，避免主线程查库阻塞
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                SleepyApp.get().notificationScheduler.cancelAll()
            }
        }
    }

    // 出厂默认 master = 开（用户 2026-09-28 定）。但 Android 13+ 的通知权限只能在运行时申请，
    //   若"默认开着 + 权限未给"，进本页补一次申请 —— 否则开关看着是开的、通知却被系统静默丢掉。
    //   冷启动依旧不弹（UI-* 定下的"不在首次启动骚扰"原则不变，只在本页补）。
    LaunchedEffect(Unit) {
        if (masterEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotificationPermission()
        }
    }

    // UI-7b: 页头统一成「我的」页那套 32sp 大标题 + 返回行（原来 M3 小标题顶栏）
    SettingsScaffold(
        title = stringResource(R.string.reminder_title),
        onBack = onBack
    ) {
        // ① 提醒总开关（UI-26a：整页从「ReminderCard + 左侧图标盒」换成与其它设置页同一套
        //    SettingsGroupCard + 标准行。旧版式的行文字落在 84dp（16 页边 + 16 卡边 + 4 + 36 图标盒
        //    + 12 间距），而全库标准是 32dp；图标本身只在重复标题、没有回答新问题，按 §〇 一并去掉。）
        item {
            SettingsGroupCard {
                SettingsSwitchRow(
                    title = stringResource(R.string.reminder_master_title),
                    subtitle = stringResource(R.string.reminder_master_sub),
                    checked = masterEnabled,
                    onCheckedChange = { onMasterToggle(it) }
                )
            }
        }

        // ②③ 总开关打开后才出现（条件与原实现一致）
        if (masterEnabled) {
            // ② 每日提醒：主开关 + 今日摘要 / 明日预告两个子块
            item {
                SettingsGroupCard {
                    SettingsSwitchRow(
                        title = stringResource(R.string.reminder_daily_title),
                        subtitle = stringResource(R.string.reminder_daily_sub),
                        checked = dailyEnabled,
                        onCheckedChange = { enabled ->
                            dailyEnabled = enabled
                            AppPrefs.setDailyReminderEnabled(context, enabled)
                            SleepyApp.get().notificationScheduler.scheduleAll()
                        }
                    )
                    if (dailyEnabled) {
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            title = stringResource(R.string.reminder_daily_today_toggle_title),
                            subtitle = stringResource(R.string.reminder_daily_today_toggle_sub),
                            checked = todayEnabled,
                            onCheckedChange = { enabled ->
                                todayEnabled = enabled
                                AppPrefs.setTodayReminderEnabled(context, enabled)
                                SleepyApp.get().notificationScheduler.scheduleAll()
                            }
                        )
                        SettingsRowDivider()
                        ReminderTimeValueRow(
                            label = stringResource(R.string.reminder_daily_time_label),
                            time = dailyTime,
                            onClick = { timePickerTarget = DailyReminderTimeTarget.Today }
                        )
                        // 预览小字紧跟它的时间行（之间不画线），说明这条提醒实际会推送什么
                        SettingsNoteRow(todayPreviewText)
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            title = stringResource(R.string.reminder_tomorrow_toggle_title),
                            subtitle = stringResource(R.string.reminder_tomorrow_toggle_sub),
                            checked = tomorrowEnabled,
                            onCheckedChange = { enabled ->
                                tomorrowEnabled = enabled
                                AppPrefs.setTomorrowReminderEnabled(context, enabled)
                                SleepyApp.get().notificationScheduler.scheduleAll()
                            }
                        )
                        SettingsRowDivider()
                        ReminderTimeValueRow(
                            label = stringResource(R.string.reminder_tomorrow_time_label),
                            time = tomorrowTime,
                            onClick = { timePickerTarget = DailyReminderTimeTarget.Tomorrow }
                        )
                        SettingsNoteRow(tomorrowPreviewText)
                    }
                }
            }

            // ③ 每节课前提醒
            item {
                SettingsGroupCard {
                    SettingsSwitchRow(
                        title = stringResource(R.string.reminder_before_class_title),
                        subtitle = stringResource(R.string.reminder_before_class_sub),
                        checked = beforeClassEnabled,
                        onCheckedChange = { on ->
                            beforeClassEnabled = on
                            AppPrefs.setBeforeClassEnabled(context, on)
                            SleepyApp.get().notificationScheduler.scheduleAll()
                        }
                    )
                    if (beforeClassEnabled) {
                        SettingsRowDivider()
                        // 提前 N 分钟（UI-26a）：原来是把文本框直接塞进行里 —— 卡片里唯一一个 68dp 的行
                        // 加一个灰色大色块，与同页上面两个"值 + 弹窗"的时间行是两种交互。改成同一套：
                        // 行右端显值，点开弹窗输入。
                        SettingsGroupRow(
                            title = stringResource(R.string.reminder_before_minutes_label),
                            onClick = {
                                val t = beforeClassMinutes.toString()
                                minutesInput = TextFieldValue(t, selection = TextRange(t.length))
                                minutesDialog = true
                            },
                            trailing = {
                                Text(
                                    text = "${beforeClassMinutes} ${stringResource(R.string.reminder_before_minutes_unit)}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.primary
                                )
                            }
                        )
                        SettingsNoteRow(beforeClassPreviewText)
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            title = stringResource(R.string.reminder_banner_title),
                            subtitle = stringResource(R.string.reminder_banner_sub),
                            checked = bannerEnabled,
                            onCheckedChange = {
                                bannerEnabled = it
                                AppPrefs.setBeforeClassBannerEnabled(context, it)
                                SleepyApp.get().notificationScheduler.scheduleAll()
                            }
                        )
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            title = stringResource(R.string.reminder_fluid_title),
                            subtitle = stringResource(R.string.reminder_fluid_sub),
                            titleBadge = stringResource(R.string.reminder_experimental_tag),
                            checked = fluidEnabled,
                            onCheckedChange = {
                                fluidEnabled = it
                                AppPrefs.setBeforeClassFluidEnabled(context, it)
                                SleepyApp.get().notificationScheduler.scheduleAll()
                            }
                        )
                        if (fluidEnabled) {
                            SettingsRowDivider()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.reminder_fluid_fields),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(SleepyTheme.fieldShape)
                                        .noRippleClickable { fieldsMenuExpanded = true }
                                ) {
                                    TextField(
                                        value = fluidPrimaryLabel(context, fluidPrimary),
                                        onValueChange = {},
                                        readOnly = true,
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth(),
                                        label = { Text(stringResource(R.string.reminder_fluid_fields_hint)) },
                                        trailingIcon = {
                                            Icon(Icons.Outlined.ExpandMore, contentDescription = null, tint = colors.onSurfaceVariant)
                                        },
                                        shape = SleepyTheme.fieldShape,
                                        colors = SleepyTheme.fieldColors()
                                    )
                                    DropdownMenu(
                                        expanded = fieldsMenuExpanded,
                                        onDismissRequest = { fieldsMenuExpanded = false },
                                        // 菜单浮在 surfaceContainer 卡片上, 用 Highest 拉开对比(默认 High 与卡片几乎同色=隐形)
                                        containerColor = colors.surfaceContainerHighest
                                    ) {
                                        listOf(
                                            "name" to R.string.reminder_fluid_field_name,
                                            "time" to R.string.reminder_fluid_field_time,
                                            "room" to R.string.reminder_fluid_field_room
                                        ).forEach { (key, labelRes) ->
                                            DropdownMenuItem(
                                                text = { Text(stringResource(labelRes)) },
                                                onClick = {
                                                    fluidPrimary = key
                                                    AppPrefs.setBeforeClassFluidPrimary(context, key)
                                                    SleepyApp.get().notificationScheduler.scheduleAll()
                                                    fieldsMenuExpanded = false
                                                },
                                                leadingIcon = {
                                                    com.imsx3d.classy.ui.component.GlasenseRadio(
                                                        selected = key == fluidPrimary
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            SettingsNoteRow(stringResource(R.string.reminder_fluid_note), topPadding = 8.dp)
                        }
                    }
                }
            }
        }
    }

    // The same picker edits either daily-summary time without duplicating its behavior.
    timePickerTarget?.let { target ->
        val selectedTime = when (target) {
            DailyReminderTimeTarget.Today -> dailyTime
            DailyReminderTimeTarget.Tomorrow -> tomorrowTime
        }
        val parts = selectedTime.split(":")
        val timeState = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: if (target == DailyReminderTimeTarget.Today) 7 else 22,
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { timePickerTarget = null },
            title = { Text(stringResource(R.string.reminder_pick_time)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 默认 TimePicker 配色 — 与 TimePickerField 弹窗一致, 不再单独覆写表盘色
                    TimePicker(state = timeState)
                    Spacer(modifier = Modifier.height(12.dp))
                    // 2026-09-16 用户: 裸 TextButton 无边界无色块 — 统一色块按钮行
                    com.imsx3d.classy.ui.component.DialogActionButtons(
                        confirmText = stringResource(R.string.action_confirm),
                        onConfirm = {
                            val h = String.format("%02d", timeState.hour)
                            val m = String.format("%02d", timeState.minute)
                            val newTime = "$h:$m"
                            when (target) {
                                DailyReminderTimeTarget.Today -> {
                                    dailyTime = newTime
                                    AppPrefs.setDailyReminderTime(context, newTime)
                                }
                                DailyReminderTimeTarget.Tomorrow -> {
                                    tomorrowTime = newTime
                                    AppPrefs.setTomorrowReminderTime(context, newTime)
                                }
                            }
                            SleepyApp.get().notificationScheduler.scheduleAll()
                            timePickerTarget = null
                        },
                        dismissText = stringResource(R.string.action_cancel),
                        onDismiss = { timePickerTarget = null }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {},
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceVariant
        )
    }

    // 提前 N 分钟（UI-26a）：与时间行同一套「点行开弹窗」；值的持久化仍由上面那条 500ms 防抖负责，
    // 所以这里"确定"只负责关窗，不必重复写盘。
    if (minutesDialog) {
        val minutesFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            // 弹窗里的输入框不会自己拿焦点；等它挂到窗口上再要，否则 requestFocus 会被丢掉
            delay(150)
            minutesFocus.requestFocus()
        }
        AlertDialog(
            onDismissRequest = { minutesDialog = false },
            title = { Text(stringResource(R.string.reminder_before_minutes_label)) },
            // 输入框色块是 surfaceContainerHighest，弹窗默认底 surfaceContainerHigh 只差一级、几乎看不出边界
            containerColor = colors.surfaceContainer,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(
                        value = minutesInput,
                        onValueChange = { tfv -> setMinutesInput(tfv.text) },
                        modifier = Modifier.fillMaxWidth().focusRequester(minutesFocus),
                        shape = SleepyTheme.fieldShape,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        suffix = {
                            Text(
                                text = stringResource(R.string.reminder_before_minutes_unit),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurfaceVariant
                            )
                        },
                        colors = SleepyTheme.fieldColors()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    com.imsx3d.classy.ui.component.DialogActionButtons(
                        confirmText = stringResource(R.string.action_confirm),
                        onConfirm = { minutesDialog = false },
                        dismissText = stringResource(R.string.action_cancel),
                        onDismiss = { minutesDialog = false }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {},
            titleContentColor = colors.onSurface,
            textContentColor = colors.onSurfaceVariant
        )
    }
}

/** 时间值行：整行可点，右端显示当前时间（UI-26a 从 ReminderTimeRow 收进 SettingsGroupRow）。 */
@Composable
private fun ReminderTimeValueRow(label: String, time: String, onClick: () -> Unit) {
    SettingsGroupRow(
        title = label,
        onClick = onClick,
        trailing = {
            Text(
                text = time,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    )
}

private fun fluidPrimaryLabel(context: android.content.Context, primary: String): String =
    context.getString(
        when (primary) {
            "name" -> R.string.reminder_fluid_field_name
            "time" -> R.string.reminder_fluid_field_time
            else -> R.string.reminder_fluid_field_room
        }
    )
