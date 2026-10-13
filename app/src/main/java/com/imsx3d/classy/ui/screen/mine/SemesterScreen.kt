package com.imsx3d.classy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.ui.theme.noRippleClickable
import java.time.LocalDate
import java.time.YearMonth
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import java.time.format.TextStyle
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.*
import com.imsx3d.classy.ui.screen.schedule.ScheduleViewModel
import com.imsx3d.classy.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Overview and calendar are distinct navigation entries, so forward/back motion and saved
 * scroll state use the same navigation owner as other pages. SettingsScaffold supplies safe
 * insets and a readable maximum width on tablets. Computation never blocks first layout.
 */
@Composable
fun SemesterScreen(onBack: () -> Unit, onSelect: (Long) -> Unit,
    courseId: Long? = null, vm: ScheduleViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val table = state.effectiveCurrentTable
    var query by rememberSaveable { mutableStateOf("") }
    var retry by remember { mutableIntStateOf(0) }
    // A changed source must render loading immediately, never a new title over old dates.
    val result by key(table, state.courses, state.transfers, state.loading, retry) {
        produceState<Result<List<SemesterCourse>>?>(null) {
            if (!state.loading) value = withContext(Dispatchers.Default) {
                runCatching { if (table == null) emptyList() else SemesterSchedule.build(table, state.courses, state.transfers) }
            }
        }
    }
    val groups = result?.getOrNull().orEmpty()
    val selected = groups.firstOrNull { group -> group.rows.any { it.id == courseId } }
    val filtered = remember(groups, query) { groups.filter {
        it.name.contains(query, true) || it.teacher.contains(query, true)
    } }
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    SettingsScaffold(title = selected?.name ?: stringResource(R.string.semester_schedule_title),
        onBack = onBack, subtitle = table?.name) {
        when {
            result == null -> item { SupportingText(stringResource(R.string.loading)) }
            result?.isFailure == true -> item { SettingsGroupCard {
                SettingsGroupRow(title = stringResource(R.string.semester_schedule_retry), onClick = { retry++ })
            } }
            courseId == null -> {
                item { OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text(stringResource(R.string.semester_schedule_search)) }, modifier = Modifier.fillMaxWidth()) }
                if (filtered.isEmpty()) item { SupportingText(stringResource(R.string.semester_schedule_empty)) }
                item { SupportingText(stringResource(R.string.semester_distribution_legend)) }
                items(filtered, key = { it.id }) { group ->
                    SemesterOverview(group, table!!.startDate, table.maxWeek, locale) { onSelect(group.id) }
                }
            }
            selected == null -> item { SupportingText(stringResource(R.string.semester_schedule_missing)) }
            else -> {
                item {
                    key(table?.id, selected.id) {
                        SemesterCalendar(selected, locale, state.transfers)
                    }
                }
                item { SupportingText(stringResource(R.string.semester_schedule_note)) }
            }
        }
    }
}


/** Week cells are a non-interactive distribution graphic, not undersized buttons. Each card
 * is one accessible action. Ten weeks per row keeps numbers readable without horizontal drag.
 * Actual dates drive occupancy; transferred classes outside the semester remain in the calendar.
 */
@Composable
private fun SemesterOverview(group: SemesterCourse, startDate: String, maxWeek: Int,
    locale: java.util.Locale, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val first = remember(startDate) { DateUtils.dateOfWeek(startDate, 1, 1) }
    val occupied = remember(group, first) { group.occurrences.map {
        Math.floorDiv(ChronoUnit.DAYS.between(first, it.date).toInt(), 7) + 1
    }.toSet() }
    val current = Math.floorDiv(ChronoUnit.DAYS.between(first, LocalDate.now()).toInt(), 7) + 1
    val regular = remember(group, locale) { group.rows.sortedWith(compareBy({ it.day }, { it.startNode }))
        .map { row ->
            val day = DayOfWeek.of(row.day.coerceIn(1, 7)).getDisplayName(TextStyle.SHORT, locale)
            val time = group.occurrences.firstOrNull { it.course.id == row.id }?.time
            listOfNotNull(day, time).joinToString(" · ")
        }.distinct().joinToString(" / ") }
    val accent = CourseColorUtil.softenedIndicator(
        CourseColorUtil.pickCourseColorComposeWithGroupRows(group.rows.first(), group.rows,
            CourseColorUtil.contrastRatio(colors.surface, androidx.compose.ui.graphics.Color.White) > 4.5f,
            colors.primary), colors.surfaceContainer)
    val distributionDescription = stringResource(R.string.semester_active_weeks,
        occupied.filter { it in 1..maxWeek }.sorted().joinToString(", "))
    Column(Modifier.fillMaxWidth().settingsCard(colors.surfaceContainer)
        .noRippleClickable(onClick).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(32.dp).background(accent, RoundedCornerShape(2.dp)))
            Text(group.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text("›", color = colors.onSurfaceVariant, style = MaterialTheme.typography.titleLarge)
        }
        Text(stringResource(R.string.semester_regular_pattern, regular), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        Column(Modifier.clearAndSetSemantics { contentDescription = distributionDescription },
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..maxWeek).chunked(10).forEach { weeks ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    weeks.forEach { week ->
                        val shape = RoundedCornerShape(5.dp)
                        Box(Modifier.weight(1f).heightIn(min = 26.dp)
                            .background(if (week in occupied) accent.copy(alpha = .30f) else colors.surface, shape)
                            .then(if (week == current) Modifier.border(1.dp, colors.primary, shape) else Modifier),
                            contentAlignment = Alignment.Center) {
                            Text(week.toString(), style = MaterialTheme.typography.labelSmall,
                                color = if (week in occupied) colors.onSurface else colors.onSurfaceVariant)
                        }
                    }
                    repeat(10 - weeks.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        if (occupied.any { it !in 1..maxWeek }) Text(stringResource(R.string.semester_outside_term),
            style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        Text(stringResource(R.string.semester_schedule_count, group.occurrences.size),
            style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
    }
}

/** Selection is stored as ISO strings, surviving recreation without a custom date Saver.
 * Choose the nearest upcoming occurrence on the first composition; never jump after entry.
 * Every date has a full-date accessibility label and a matching rounded press/selection shape.
 */
@Composable
private fun SemesterCalendar(group: SemesterCourse, locale: java.util.Locale,
    transfers: List<HolidayTransferEntry>) {
    val byDate = remember(group) { group.occurrences.groupBy { it.date } }
    if (byDate.isEmpty()) {
        SupportingText(stringResource(R.string.semester_schedule_empty))
        return
    }
    val dates = remember(byDate) { byDate.keys.sorted() }
    val initial = remember(group.id) { dates.firstOrNull { !it.isBefore(LocalDate.now()) } ?: dates.first() }
    var selectedIso by rememberSaveable { mutableStateOf(initial.toString()) }
    var monthIso by rememberSaveable { mutableStateOf(YearMonth.from(initial).toString()) }
    val selectedDate = LocalDate.parse(selectedIso)
    val month = YearMonth.parse(monthIso)
    val firstMonth = YearMonth.from(dates.first())
    val lastMonth = YearMonth.from(dates.last())
    val colors = MaterialTheme.colorScheme
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale) }
    val monthFormat = remember(locale) { DateTimeFormatter.ofPattern("LLLL yyyy", locale) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SettingsGroupCard {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(enabled = month > firstMonth, onClick = {
                    monthIso = month.minusMonths(1).toString()
                    selectedIso = dates.firstOrNull { YearMonth.from(it).toString() == monthIso }?.toString()
                        ?: YearMonth.parse(monthIso).atDay(1).toString()
                }) { Icon(Icons.Outlined.ChevronLeft, stringResource(R.string.semester_previous_month)) }
                Text(month.format(monthFormat), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium)
                IconButton(enabled = month < lastMonth, onClick = {
                    monthIso = month.plusMonths(1).toString()
                    selectedIso = dates.firstOrNull { YearMonth.from(it).toString() == monthIso }?.toString()
                        ?: YearMonth.parse(monthIso).atDay(1).toString()
                }) { Icon(Icons.Outlined.ChevronRight, stringResource(R.string.semester_next_month)) }
            }
            Row(Modifier.padding(horizontal = 4.dp)) {
                (1..7).forEach { day -> Text(DayOfWeek.of(day).getDisplayName(TextStyle.NARROW, locale),
                    Modifier.weight(1f).padding(vertical = 8.dp), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant) }
            }
            val offset = month.atDay(1).dayOfWeek.value - 1
            val cells = ((offset + month.lengthOfMonth() + 6) / 7) * 7
            (0 until cells).chunked(7).forEach { week ->
                Row(Modifier.padding(horizontal = 4.dp)) {
                    week.forEach { cell ->
                        val day = cell - offset + 1
                        if (day !in 1..month.lengthOfMonth()) {
                            Spacer(Modifier.weight(1f).height(52.dp))
                        } else {
                            val date = month.atDay(day)
                            val hasClass = date in byDate
                            val chosen = date == selectedDate
                            val shape = RoundedCornerShape(12.dp)
                            val description = date.format(dateFormat) + ", " + stringResource(
                                if (hasClass) R.string.semester_has_class else R.string.semester_no_class)
                            Column(Modifier.testTag("semester-date-$date").weight(1f).heightIn(min = 52.dp).padding(2.dp).clip(shape)
                                .background(if (chosen) colors.secondaryContainer else colors.surfaceContainer)
                                .noRippleClickable(feedbackShape = shape) { selectedIso = date.toString() }
                                .semantics { contentDescription = description; selected = chosen }
                                .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(day.toString(), style = MaterialTheme.typography.bodyMedium,
                                    color = if (chosen) colors.onSecondaryContainer else colors.onSurface)
                                Box(Modifier.size(4.dp).background(if (hasClass) colors.primary else
                                    androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(2.dp)))
                            }
                        }
                    }
                }
            }
            SupportingText(stringResource(R.string.semester_calendar_hint), Modifier.padding(12.dp))
        }
        Text(selectedDate.format(dateFormat), style = MaterialTheme.typography.titleSmall)
        val occurrences = byDate[selectedDate].orEmpty()
        if (occurrences.isEmpty()) SupportingText(stringResource(R.string.semester_no_class))
        occurrences.forEach { occurrence -> SettingsGroupCard {
            SettingsGroupRow(title = occurrence.time ?: stringResource(R.string.semester_schedule_time_missing),
                subtitle = listOf(occurrence.course.room, occurrence.course.teacher)
                    .filter { it.isNotBlank() }.joinToString(" · "))
            transfers.firstOrNull { it.targetDate == selectedDate }?.let { transfer ->
                SupportingText(stringResource(R.string.semester_transferred_from,
                    transfer.sourceDate.format(dateFormat)), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }
        } }
    }
}
