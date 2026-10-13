package com.imsx3d.classy.ui.screen.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.theme.SleepyTheme
import com.imsx3d.classy.ui.theme.noRippleClickable
import com.imsx3d.classy.util.DateUtils
import com.imsx3d.classy.util.WeekDisplayContext

/** Reading context stays visible; infrequent commands live in one anchored menu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScheduleHeader(
    tableName: String,
    currentWeek: Int,
    maxWeek: Int,
    startDate: String,
    displayContext: WeekDisplayContext?,
    onSwitchTable: () -> Unit,
    scaleUncommitted: Boolean,
    onScaleCommit: () -> Unit,
    onJumpToActual: () -> Unit,
    onSelectWeek: (Int) -> Unit,
    onAddCourse: () -> Unit,
    onShare: () -> Unit,
    onRefresh: () -> Unit,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    showInitialGuide: Boolean = false,
    onGuideDismissed: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val actualWeek = displayContext?.actualWeek ?: remember(startDate) {
        if (startDate.isBlank()) 1 else DateUtils.currentWeek(startDate)
    }
    val semester = displayContext?.semesterStatus ?: DateUtils.semesterStatus(startDate, maxWeek)
    val hint = when (semester) {
        DateUtils.SemesterStatus.BEFORE_START -> stringResource(R.string.semester_not_started)
        DateUtils.SemesterStatus.AFTER_END -> stringResource(R.string.semester_ended)
        else -> null
    }
    var weeksOpen by rememberSaveable { mutableStateOf(false) }
    var actionsOpen by rememberSaveable { mutableStateOf(false) }
    var guideOpen by rememberSaveable { mutableStateOf(showInitialGuide) }
    val switchLabel = stringResource(R.string.schedule_switch_table)
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)) {
        val weekLabel = stringResource(R.string.schedule_week_prefix, currentWeek)
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val weekWidth = with(density) {
            measurer.measure(weekLabel, style = MaterialTheme.typography.labelLarge).size.width.toDp()
        } + 30.dp
        val tableControl: @Composable (Modifier) -> Unit = { modifier ->
            Row(modifier.heightIn(min = 48.dp).clip(SleepyTheme.shapes.medium)
                .noRippleClickable(onSwitchTable).semantics { contentDescription = switchLabel; role = Role.Button }
                .padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tableName, Modifier.weight(1f, fill = false),
                    color = colors.onSurface, style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.padding(start = 4.dp).size(18.dp), tint = colors.onSurfaceVariant)
            }
        }
        val weekControl: @Composable () -> Unit = {
            Row(Modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).clip(SleepyTheme.shapes.medium)
                .noRippleClickable { weeksOpen = true }
                .semantics(mergeDescendants = true) { role = Role.Button }
                .padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(weekLabel, color = colors.primary, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Outlined.ExpandMore, null, Modifier.padding(start = 4.dp).size(18.dp), tint = colors.primary)
            }
        }
        val moreControl: @Composable () -> Unit = {
            Box {
                IconButton(onClick = { actionsOpen = true }) {
                    Icon(Icons.Outlined.MoreHoriz, stringResource(R.string.schedule_actions), tint = colors.onSurface)
                }
                DropdownMenu(expanded = actionsOpen, onDismissRequest = { actionsOpen = false }) {
                    ViewMode.entries.forEach { mode ->
                        DropdownMenuItem(text = { Text(stringResource(mode.labelRes)) },
                            onClick = { actionsOpen = false; onViewModeChange(mode) },
                            leadingIcon = { Icon(if (mode == ViewMode.Cards) Icons.Outlined.GridView else Icons.Outlined.ViewAgenda, null) },
                            trailingIcon = { if (mode == viewMode) Icon(Icons.Outlined.Check, null) },
                            modifier = Modifier.semantics { selected = mode == viewMode })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.schedule_add_course)) },
                        leadingIcon = { Icon(Icons.Outlined.Add, null) },
                        onClick = { actionsOpen = false; onAddCourse() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.schedule_refresh_school)) },
                        leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                        onClick = { actionsOpen = false; onRefresh() })
                    DropdownMenuItem(text = { Text(stringResource(R.string.schedule_share_table)) },
                        leadingIcon = { Icon(Icons.Outlined.IosShare, null) },
                        onClick = { actionsOpen = false; onShare() })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text(stringResource(R.string.schedule_usage_title)) },
                        leadingIcon = { Icon(Icons.Outlined.HelpOutline, null) },
                        onClick = { actionsOpen = false; guideOpen = true })
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Reserve a readable title fragment and the actual localized week label before choosing one row.
            val oneRow = maxWidth >= weekWidth + 48.dp + 8.dp + (112 * density.fontScale).dp
            Column(Modifier.fillMaxWidth().testTag(if (oneRow) "schedule-header-single" else "schedule-header-wrapped")) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    tableControl(Modifier.weight(1f))
                    if (oneRow) {
                        Spacer(Modifier.width(8.dp))
                        weekControl()
                    }
                    moreControl()
                }
                if (!oneRow) { weekControl() }
            }
        }
        if (scaleUncommitted) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onScaleCommit) { Text(stringResource(R.string.schedule_scale_keep)) }
            }
        }
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp)) }
    }
    if (guideOpen) {
        val dismiss = { guideOpen = false; onGuideDismissed() }
        AlertDialog(onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.schedule_usage_title)) },
            text = { Text(stringResource(R.string.schedule_usage_body), modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.schedule_usage_done)) } })
    }
    if (weeksOpen) {
        AlertDialog(onDismissRequest = { weeksOpen = false },
            title = { Text(stringResource(R.string.schedule_jump_week)) },
            text = {
                FlowRow(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..maxWeek.coerceAtLeast(1)).forEach { week ->
                        val range = remember(startDate, week) {
                            runCatching {
                                val first = DateUtils.dateOfWeek(startDate, week, 1)
                                val last = DateUtils.dateOfWeek(startDate, week, 7)
                                "${DateUtils.shortDateSlash(first)}–${DateUtils.shortDateSlash(last)}"
                            }.getOrNull()
                        }
                        val chosen = currentWeek == week
                        Column(Modifier.width(88.dp).heightIn(min = 52.dp)
                            .clip(SleepyTheme.shapes.medium)
                            .background(if (chosen) colors.primaryContainer else colors.surfaceContainerHigh)
                            .noRippleClickable { weeksOpen = false; onSelectWeek(week) }
                            .semantics { selected = chosen; role = Role.Button }
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.schedule_week_prefix, week),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (chosen) colors.onPrimaryContainer else colors.onSurface)
                            range?.let { Text(it, style = MaterialTheme.typography.labelSmall,
                                color = if (week == actualWeek) colors.primary else colors.onSurfaceVariant) }
                        }
                    }
                }
            },
            dismissButton = {
                if (currentWeek != actualWeek && actualWeek in 1..maxWeek) {
                    TextButton(onClick = { weeksOpen = false; onJumpToActual() }) {
                        Text(stringResource(R.string.schedule_back_to_current_week))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { weeksOpen = false }) { Text(stringResource(R.string.cancel)) } })
    }
}
