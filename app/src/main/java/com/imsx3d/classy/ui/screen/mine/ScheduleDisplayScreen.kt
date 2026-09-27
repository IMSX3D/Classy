package com.imsx3d.classy.ui.screen.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.DisplayModeOption
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

/**
 * 「课表显示」二级页（UI-15a）—— 把「通用」页原先塞在同一张卡里的三处**多选项 / 多滑杆**内容搬出来。
 *
 * 为什么搬：那三处（主页显示 8 项、冲突样式 3 选 + 3 根滑杆、显示星期 7 个开关）原来是就地折叠卡，
 * 一屏之内决策点太多（用户 2026-09-27 的顾虑："一下子灌入太多信息，影响用户在调整相关设置或
 * 做决策时候的判断"）。搬到二级页后，「通用」页只剩 5 行（两个分段 + 一个入口 + 两个入口/开关），
 * 想深入调的人再进来 —— Cresto / Pear Wall 的关于页与设置页都是这个做法（入口行带「›」）。
 *
 * 内容**逐字搬移**，只是把"折叠"换成"独立页"，行为与原折叠卡完全一致（含各样式下的条件滑杆）。
 */
@Composable
fun ScheduleDisplayScreen(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current

    var gridScale by remember { mutableStateOf(AppPrefs.getGridScale(context)) }
    var weekScale by remember { mutableStateOf(AppPrefs.getWeekScale(context)) }
    var gridCorner by remember { mutableStateOf(AppPrefs.getGridCornerRatio(context)) }
    var weekTwoColumn by remember { mutableStateOf(AppPrefs.isWeekTwoColumn(context)) }
    var weekTwoColumnMode by remember { mutableStateOf(AppPrefs.getWeekTwoColumnMode(context)) }
    var weekHideEmptyDays by remember { mutableStateOf(AppPrefs.isWeekHideEmptyDays(context)) }
    var weekUseAlias by remember { mutableStateOf(AppPrefs.isWeekUseAlias(context)) }
    var gridUseAlias by remember { mutableStateOf(AppPrefs.isGridUseAlias(context)) }
    var conflictStyle by remember { mutableStateOf(AppPrefs.getConflictStyle(context)) }
    var conflictStackInset by remember { mutableStateOf(AppPrefs.getConflictStackInset(context)) }
    var conflictRailInset by remember { mutableStateOf(AppPrefs.getConflictRailInset(context)) }
    var conflictFoldSize by remember { mutableStateOf(AppPrefs.getConflictFoldSize(context)) }
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
            SectionHeader(
                title = stringResource(R.string.settings_pill),
                topSpacing = 0.dp
            )
        }
        item {
            SettingsGroupCard {
            SettingsSliderRow(
                title = stringResource(R.string.settings_pill_scale),
                valueText = "${(gridScale * 100).roundToInt()}%",
                value = gridScale,
                onValueChange = { gridScale = (it * 20).roundToInt() / 20f },
                onValueChangeFinished = { AppPrefs.setGridScale(context, gridScale) },
                valueRange = 0.7f..1.3f
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
            SettingsSliderRow(
                title = stringResource(R.string.settings_pill_corner),
                valueText = "${(gridCorner * 100).roundToInt()}%",
                value = gridCorner,
                onValueChange = { gridCorner = (it * 20).roundToInt() / 20f },
                onValueChangeFinished = { AppPrefs.setGridCornerRatio(context, gridCorner) },
                valueRange = 0f..2f
            )
            SettingsRowDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.settings_week_two_column),
                checked = weekTwoColumn,
                onCheckedChange = { weekTwoColumn = it; AppPrefs.setWeekTwoColumn(context, it) }
            )
            // 分栏标准 — 两栏开启时才需要选
            if (weekTwoColumn) {
                SettingsRowDivider()
                DisplayModeOption(
                    label = stringResource(R.string.settings_week_two_column_days),
                    subtitle = "",
                    selected = weekTwoColumnMode == "days",
                    onClick = { weekTwoColumnMode = "days"; AppPrefs.setWeekTwoColumnMode(context, "days") }
                )
                SettingsRowDivider()
                DisplayModeOption(
                    label = stringResource(R.string.settings_week_two_column_balance),
                    subtitle = "",
                    selected = weekTwoColumnMode == "balance",
                    onClick = { weekTwoColumnMode = "balance"; AppPrefs.setWeekTwoColumnMode(context, "balance") }
                )
            }
            // 隐藏无课日 — 与两栏无关, 单栏/两栏都生效
            SettingsRowDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.settings_week_hide_empty),
                checked = weekHideEmptyDays,
                onCheckedChange = { weekHideEmptyDays = it; AppPrefs.setWeekHideEmptyDays(context, it) }
            )
            // issue#26 课程别名: 周视图/网格场景 原名/别名 二选一, 关=原名 开=别名(自独立卡挪入, 行为零变化)
            SettingsRowDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.settings_week_alias),
                checked = weekUseAlias,
                onCheckedChange = { weekUseAlias = it; AppPrefs.setWeekUseAlias(context, it) }
            )
            SettingsRowDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.settings_grid_alias),
                checked = gridUseAlias,
                onCheckedChange = { gridUseAlias = it; AppPrefs.setGridUseAlias(context, it) }
            )
            // UI-5c: 「网格视图表头显示日期」开关已移除 —— 表头恒显日期后它不再影响任何可见结果
            //（小组件真实表头本来就恒显；该开关的剩余作用只在一条已废弃的位图渲染路径上）。
            }
        }

        item {
            SectionHeader(
                title = stringResource(R.string.settings_conflict_style),
                topSpacing = 12.dp
            )
        }
        item {
            SettingsGroupCard {
                DisplayModeOption(
                    label = stringResource(R.string.settings_conflict_stack),
                    subtitle = stringResource(R.string.settings_conflict_stack_sub),
                    selected = conflictStyle == "stack",
                    onClick = { conflictStyle = "stack"; AppPrefs.setConflictStyle(context, "stack") }
                )
                SettingsRowDivider()
                DisplayModeOption(
                    label = stringResource(R.string.settings_conflict_fold),
                    subtitle = stringResource(R.string.settings_conflict_fold_sub),
                    selected = conflictStyle == "fold",
                    onClick = { conflictStyle = "fold"; AppPrefs.setConflictStyle(context, "fold") }
                )
                SettingsRowDivider()
                DisplayModeOption(
                    label = stringResource(R.string.settings_conflict_rail),
                    subtitle = stringResource(R.string.settings_conflict_rail_sub),
                    selected = conflictStyle == "rail",
                    onClick = { conflictStyle = "rail"; AppPrefs.setConflictStyle(context, "rail") }
                )
                // 折角幅度拖杆(v7.10.16o): 仅折角样式下显示 —— 其他样式没有折角符号
                if (conflictStyle == "fold") {
                    SettingsRowDivider()
                    SettingsSliderRow(
                        title = stringResource(R.string.settings_conflict_fold_size),
                        valueText = "${conflictFoldSize.roundToInt()}dp",
                        value = conflictFoldSize,
                        onValueChange = { conflictFoldSize = it.roundToInt().toFloat() },
                        onValueChangeFinished = { AppPrefs.setConflictFoldSize(context, conflictFoldSize) },
                        valueRange = AppPrefs.CONFLICT_FOLD_SIZE_RANGE.start..AppPrefs.CONFLICT_FOLD_SIZE_RANGE.endInclusive
                    )
                }
            // 叠层偏移量(用户 2026-09-04 拆分): 仅叠层样式下显示, 独立配置
            if (conflictStyle == "stack") {
                SettingsRowDivider()
                SettingsSliderRow(
                    title = stringResource(R.string.settings_conflict_stack_inset),
                    valueText = "${conflictStackInset.roundToInt()}dp",
                    value = conflictStackInset,
                    onValueChange = { conflictStackInset = it.roundToInt().toFloat() },
                    onValueChangeFinished = { AppPrefs.setConflictStackInset(context, conflictStackInset) },
                    valueRange = AppPrefs.CONFLICT_TOP_INSET_RANGE.start..AppPrefs.CONFLICT_TOP_INSET_RANGE.endInclusive
                )
            }
            // 右缘让宽(同上拆分): 仅侧边竖轨样式下显示, 与叠层互不影响
            if (conflictStyle == "rail") {
                SettingsRowDivider()
                SettingsSliderRow(
                    title = stringResource(R.string.settings_conflict_rail_inset),
                    valueText = "${conflictRailInset.roundToInt()}dp",
                    value = conflictRailInset,
                    onValueChange = { conflictRailInset = it.roundToInt().toFloat() },
                    onValueChangeFinished = { AppPrefs.setConflictRailInset(context, conflictRailInset) },
                    valueRange = AppPrefs.CONFLICT_TOP_INSET_RANGE.start..AppPrefs.CONFLICT_TOP_INSET_RANGE.endInclusive
                )
            }
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
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)
                )
                (1..7).forEach { day ->
                    val checked = day in visibleDays
                    // UI-17a：度量对齐《设计规范_控件体系》C2（卡内 16dp、行高 48dp、开关锁 32dp）。
                    // 旧值 8/4dp 让星期文字比同页其它行左移 12dp、开关贴到卡片右沿。
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).noRippleClickable {
                            val n = if (checked) visibleDays - day else visibleDays + day
                            if (n.isNotEmpty()) { visibleDays = n; AppPrefs.setVisibleDays(context, n); refreshWidgets() }
                        }.padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = DateUtils.localizedDay(day, context), style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                        Switch(checked = checked, onCheckedChange = { on ->
                            val n = if (on) visibleDays + day else visibleDays - day
                            if (n.isNotEmpty()) { visibleDays = n; AppPrefs.setVisibleDays(context, n); refreshWidgets() }
                        }, colors = SwitchDefaults.colors(checkedThumbColor = colors.onPrimary, checkedTrackColor = colors.primary),
                            modifier = Modifier.heightIn(max = 32.dp))
                    }
                    if (day != 7) SettingsRowDivider()
                }
            }
        }
    }
}
