package com.imsx3d.classy.ui.screen.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp


import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import com.imsx3d.classy.ui.component.SettingsGroupRow
import com.imsx3d.classy.ui.component.SettingsSegmentedRow
import com.imsx3d.classy.ui.component.TimePickerField
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.SectionHeader
import com.imsx3d.classy.ui.component.SettingsGroupCard
import com.imsx3d.classy.ui.component.SettingsRowDivider
import com.imsx3d.classy.ui.component.SettingsSliderRow
import com.imsx3d.classy.ui.component.SettingsSwitchRow
import com.imsx3d.classy.ui.component.SettingsScaffold
import com.imsx3d.classy.ui.theme.noRippleClickable
import com.imsx3d.classy.util.AppPrefs
import com.imsx3d.classy.util.DateUtils
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 课表显示：保留阅读与布局选择，圆角和冲突提示尺寸沿用默认值或已有偏好。 */
@Composable
fun ScheduleDisplayScreen(onBack: () -> Unit, onOpenHoliday: () -> Unit = {}) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    var holidayGrey by remember { mutableStateOf(AppPrefs.isHolidayGreyHoliday(context)) }
    var titleCentered by remember { mutableStateOf(AppPrefs.isCourseTitleCentered(context)) }
    var style by remember { mutableStateOf(AppPrefs.getHolidayStyle(context)) }
    var displayMode by remember { mutableStateOf(AppPrefs.getDisplayMode(context)) }
    var gridSubInfo by remember { mutableStateOf(AppPrefs.getGridSubInfo(context)) }
    var startView by remember { mutableStateOf(AppPrefs.getStartView(context)) }
    var autoHideEmptyEvening by remember { mutableStateOf(AppPrefs.isGridAutoHideEmptyEvening(context)) }
    var gridAdaptiveHeight by remember { mutableStateOf(AppPrefs.isGridAdaptiveHeight(context)) }
    var eveningStart by remember { mutableStateOf(AppPrefs.getGridEveningStart(context)) }

    var gridScale by remember { mutableStateOf(AppPrefs.getGridScale(context)) }
    var weekScale by remember { mutableStateOf(AppPrefs.getWeekScale(context)) }
    var weekTwoColumn by remember { mutableStateOf(AppPrefs.isWeekTwoColumn(context)) }
    var weekHideEmptyDays by remember { mutableStateOf(AppPrefs.isWeekHideEmptyDays(context)) }
    var visibleDays by remember { mutableStateOf(AppPrefs.getVisibleDays(context)) }

    // 显示项变更后立即刷小组件（与「通用」页同一条管线）
    val widgetScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    fun refreshWidgets() {
        widgetScope.launch { com.imsx3d.classy.widget.WidgetUpdater.notifyDataChanged(context) }
    }

    SettingsScaffold(
        title = stringResource(R.string.schedule_display_title),
        onBack = onBack
    ) {

        item {
            SettingsGroupCard {
                SettingsSegmentedRow(
                    title = stringResource(R.string.settings_start_view),
                    subtitle = stringResource(R.string.display_start_hint),
                    options = listOf(
                            stringResource(R.string.settings_start_view_full),
                            stringResource(R.string.settings_start_view_cards)
                        ),
                    selectedKey = if (startView == "full") 0 else 1,
                    onSelect = { i ->
                            val v = if (i == 0) "full" else "cards"
                            startView = v; AppPrefs.setStartView(context, v)
                        },
                )

            }
        }
        item {
            SectionHeader(
                title = stringResource(R.string.display_grid_section),
                topSpacing = 0.dp
            )
        }
        item {
            SettingsGroupCard {
                SettingsSegmentedRow(
                    title = stringResource(R.string.course_title_position),
                    options = listOf(stringResource(R.string.course_title_top), stringResource(R.string.course_title_center)),
                    selectedKey = if (titleCentered) 1 else 0,
                    onSelect = { titleCentered = it == 1; AppPrefs.setCourseTitleCentered(context, titleCentered) }
                )
                SettingsRowDivider()
                SettingsSegmentedRow(
                    title = stringResource(R.string.settings_grid_sub_info),
                    subtitle = stringResource(R.string.display_card_hint),
                    options = listOf(
                            stringResource(R.string.settings_grid_sub_room),
                            stringResource(R.string.settings_grid_sub_teacher),
                            stringResource(R.string.settings_grid_sub_none)
                        ),
                    selectedKey = when (gridSubInfo) {
                            "room" -> 0
                            "teacher" -> 1
                            else -> 2
                        },
                    onSelect = { i ->
                            val v = listOf("room", "teacher", "none")[i]
                            gridSubInfo = v; AppPrefs.setGridSubInfo(context, v); refreshWidgets()
                        },
                )
                SettingsRowDivider()
            SettingsSegmentedRow(
                title = stringResource(R.string.settings_grid_size),
                    subtitle = stringResource(R.string.settings_grid_adaptive_height_sub),
                options = listOf(stringResource(R.string.settings_grid_size_auto), stringResource(R.string.settings_grid_size_manual)),
                selectedKey = if (gridAdaptiveHeight) 0 else 1,
                onSelect = { gridAdaptiveHeight = it == 0; AppPrefs.setGridAdaptiveHeight(context, gridAdaptiveHeight) }
            )
            if (!gridAdaptiveHeight) {
            SettingsSliderRow(
                title = stringResource(R.string.settings_pill_scale),
                valueText = "${(gridScale * 100).roundToInt()}%",
                value = gridScale,
                onValueChange = { gridScale = (it * 20).roundToInt() / 20f },
                onValueChangeFinished = { AppPrefs.setGridScale(context, gridScale) },
                valueRange = 0.7f..1.3f
            )
                com.imsx3d.classy.ui.component.SupportingText(stringResource(R.string.settings_grid_size_hint),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
            }
        }
        item { SectionHeader(stringResource(R.string.display_week_section)) }
        item {
            SettingsGroupCard {
                SettingsSegmentedRow(
                    title = stringResource(R.string.settings_display_mode),
                    options = listOf(
                            stringResource(R.string.settings_display_node),
                            stringResource(R.string.settings_display_time)
                        ),
                    selectedKey = if (displayMode == "node") 0 else 1,
                    onSelect = { i ->
                            val v = if (i == 0) "node" else "time"
                            displayMode = v; AppPrefs.setDisplayMode(context, v); refreshWidgets()
                        },
                )
                SettingsRowDivider()
            SettingsSliderRow(
                title = stringResource(R.string.settings_pill_week_scale),
                valueText = "${(weekScale * 100).roundToInt()}%",
                value = weekScale,
                onValueChange = { weekScale = (it * 20).roundToInt() / 20f },
                onValueChangeFinished = { AppPrefs.setWeekScale(context, weekScale) },
                valueRange = 0.7f..1.3f
            )
            SettingsRowDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.settings_week_two_column),
                checked = weekTwoColumn,
                onCheckedChange = { weekTwoColumn = it; AppPrefs.setWeekTwoColumn(context, it) }
            )
            // 隐藏无课日 — 与两栏无关, 单栏/两栏都生效
            SettingsRowDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.settings_week_hide_empty),
                    subtitle = stringResource(R.string.display_empty_hint),
                checked = weekHideEmptyDays,
                onCheckedChange = { weekHideEmptyDays = it; AppPrefs.setWeekHideEmptyDays(context, it) }
            )
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.settings_visible_days),
                topSpacing = 12.dp
            )
        }
        item {
            SettingsGroupCard {
                // UI-17a：这行原来是折叠卡的**内容**，折叠标题行替它提供了上间距；
                // 搬进普通卡后变成"卡片首行"，上间距为 0 → 文字顶到卡片上沿（用户报"显示不全"）。
                Text(
                    text = stringResource(R.string.settings_visible_days_sub),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)
                )
                (1..7).forEach { day ->
                    val checked = day in visibleDays
                    SettingsSwitchRow(
                        title = DateUtils.localizedDay(day, context),
                        checked = checked,
                        onCheckedChange = { on ->
                            val next = if (on) visibleDays + day else visibleDays - day
                            if (next.isNotEmpty()) {
                                visibleDays = next
                                AppPrefs.setVisibleDays(context, next)
                                refreshWidgets()
                            } else {
                                android.widget.Toast.makeText(context, R.string.select_at_least_one_day, android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    if (day != 7) SettingsRowDivider()
                }
            }
        }
        item { SectionHeader(stringResource(R.string.display_holiday_section)) }
        item {
            SettingsGroupCard {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_holiday_holiday),
                    subtitle = stringResource(R.string.settings_holiday_holiday_sub),
                    checked = holidayGrey,
                    onCheckedChange = { holidayGrey = it; AppPrefs.setHolidayGreyHoliday(context, it); refreshWidgets() }
                )
                SettingsRowDivider()
                if (holidayGrey) {
                SettingsSegmentedRow(
                    title = stringResource(R.string.settings_holiday_style),
                    subtitle = stringResource(R.string.display_style_hint),
                    options = listOf(stringResource(R.string.settings_holiday_style_grey), stringResource(R.string.settings_holiday_style_strikethrough)),
                    selectedKey = if (style == "strikethrough") 1 else 0,
                    onSelect = { index ->
                        style = if (index == 1) "strikethrough" else "grey"
                        AppPrefs.setHolidayStyle(context, style)
                        refreshWidgets()
                    }
                )
                SettingsRowDivider()
                }
                SettingsGroupRow(
                    title = stringResource(R.string.settings_holiday_title),
                    subtitle = stringResource(R.string.settings_holiday_entry_sub),
                    onClick = onOpenHoliday,
                    trailing = {
                        Icon(
                            Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }
        // ── 分组⑤ 实验室 (2026-09-16 用户令): 实验性功能默认全关, 可能随版本调整 ──
        item {
            SectionHeader(
                title = stringResource(R.string.settings_lab),
                topSpacing = 12.dp
            )
        }
        item {
            Text(
                text = stringResource(R.string.settings_lab_sub),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        item {
            SettingsGroupCard {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_grid_auto_hide_evening),
                    subtitle = stringResource(R.string.settings_grid_auto_hide_evening_sub),
                    checked = autoHideEmptyEvening,
                    onCheckedChange = {
                        autoHideEmptyEvening = it
                        AppPrefs.setGridAutoHideEmptyEvening(context, it)
                    }
                )
                if (autoHideEmptyEvening) {
                    SettingsRowDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_grid_evening_start),
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        TimePickerField(
                            value = eveningStart,
                            onValueChange = {
                                eveningStart = it
                                AppPrefs.setGridEveningStart(context, it)
                            },
                            label = "",
                            modifier = Modifier.width(150.dp)
                        )
                    }
                }

            }
        }
    }
}
