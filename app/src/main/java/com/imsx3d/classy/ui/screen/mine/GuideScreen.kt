package com.imsx3d.classy.ui.screen.mine

import androidx.compose.animation.animateContentSize
import com.imsx3d.classy.ui.component.sleepyContentSizeSpec
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.SettingsScaffold
import com.imsx3d.classy.ui.component.SettingsGroupCard
import com.imsx3d.classy.ui.component.SettingsGroupRow
import com.imsx3d.classy.ui.theme.noRippleClickable
import com.imsx3d.classy.util.CourseDimming

/** All example state is local and saveable. This screen deliberately has no repository or prefs writes. */
@Composable
fun GuideWelcomeScreen(onBack: () -> Unit, onTry: () -> Unit, onImport: () -> Unit) {
    SettingsScaffold(title = stringResource(R.string.guide_welcome), onBack = onBack) {
        item { Text(stringResource(R.string.guide_welcome_body), style = MaterialTheme.typography.titleMedium) }
        item { ExampleCourse() }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.guide_import)) }
            OutlinedButton(onClick = onTry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.guide_try)) }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.guide_skip)) }
        } }
        item { Text(stringResource(R.string.guide_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun GuideScreen(onBack: () -> Unit, onTopic: (Int) -> Unit, onWelcome: () -> Unit) {
    val titles = stringArrayResource(R.array.guide_topics)
    val subtitles = stringArrayResource(R.array.guide_topic_subtitles)
    SettingsScaffold(title = stringResource(R.string.guide_title), onBack = onBack,
        subtitle = stringResource(R.string.guide_subtitle)) {
        item { OutlinedButton(onClick = onWelcome, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.guide_replay)) } }
        items(titles.size) { index -> SettingsGroupCard {
            SettingsGroupRow(title = titles[index], subtitle = subtitles[index], onClick = { onTopic(index) })
        } }
    }
}

@Composable
fun GuideTopicScreen(topic: Int, onBack: () -> Unit, onOpen: () -> Unit, onDone: () -> Unit = onBack) {
    val index = topic.coerceIn(0, 7)
    val titles = stringArrayResource(R.array.guide_topics)
    val steps = stringArrayResource(R.array.guide_steps)
    val bodies = stringArrayResource(R.array.guide_bodies)
    var step by rememberSaveable(index) { mutableIntStateOf(0) }
    val offset = index * 3 + step
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun moveStep(next: Int) {
        // Explicit lesson navigation returns to its heading; restoration itself never scrolls.
        scope.launch { listState.scrollToItem(0); step = next }
    }
    SettingsScaffold(title = titles[index], onBack = onBack, listState = listState) {
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.guide_step, step + 1, 3), color = MaterialTheme.colorScheme.primary)
            Text(steps[offset], style = MaterialTheme.typography.titleLarge)
            Text(bodies[offset], style = MaterialTheme.typography.bodyLarge)
        } }
        item { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.guide_sample), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                when (index) {
                    0 -> ReadExample()
                    1 -> ImportExample()
                    2 -> EditExample()
                    3 -> AppearanceExample()
                    4 -> WidgetExample()
                    5 -> ReminderExample()
                    6 -> SemesterExample()
                    7 -> RoutineExample()
                }
            }
        } }
        item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { if (step < 2) moveStep(step + 1) else onDone() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (step < 2) R.string.guide_next else R.string.guide_done))
            }
            if (step > 0) TextButton(onClick = { moveStep(step - 1) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.guide_previous)) }
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.guide_open)) }
        } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Choices(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, text -> FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(text) }) }
    }
}

@Composable
private fun ExampleCourse(name: String = stringResource(R.string.guide_example_course), bar: Boolean = false,
    centered: Boolean = true, completed: Boolean = false, expanded: Boolean = false, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxWidth().clip(shape).background(colors.surface)
        .then(if (onClick != null) Modifier.noRippleClickable(onClick) else Modifier)
        .animateContentSize(animationSpec = sleepyContentSizeSpec()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().alpha(CourseDimming.opacity(completed)).heightIn(min = 140.dp)
            .clip(RoundedCornerShape(12.dp)).background(if (bar) colors.surface else colors.primaryContainer)) {
            if (bar) Box(Modifier.padding(top = 12.dp, bottom = 12.dp).width(3.dp).height(116.dp)
                .clip(RoundedCornerShape(2.dp)).background(colors.primary))
            Column(Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 64.dp), contentAlignment = if (centered) Alignment.Center else Alignment.TopCenter) {
                    Text(name, style = MaterialTheme.typography.titleMedium,
                        color = if (bar) colors.onSurface else colors.onPrimaryContainer)
                }
                Text(stringResource(R.string.guide_example_room), style = MaterialTheme.typography.bodyMedium,
                    color = if (bar) colors.onSurfaceVariant else colors.onPrimaryContainer)
            }
        }
        if (expanded) {
            Text(stringResource(R.string.guide_detail), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.guide_example_time))
            Text(stringResource(R.string.guide_example_room))
        }
    }
}

@Composable
private fun ReadExample() {
    var week by rememberSaveable { mutableIntStateOf(0) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var ended by rememberSaveable { mutableStateOf(false) }
    Choices(listOf(stringResource(R.string.guide_week, 1), stringResource(R.string.guide_week, 2)), week) { week = it }
    Text(stringResource(R.string.guide_week, week + 1), style = MaterialTheme.typography.titleSmall)
    Text(stringResource(R.string.guide_tap_course), style = MaterialTheme.typography.bodyMedium)
    ExampleCourse(expanded = expanded, completed = ended, onClick = { expanded = !expanded })
    TextButton(onClick = { ended = !ended }) { Text(stringResource(if (ended) R.string.guide_normal else R.string.guide_ended)) }
}

@Composable
private fun ImportExample() {
    var method by rememberSaveable { mutableIntStateOf(0) }
    Text(stringResource(R.string.guide_import_sample))
    Choices(listOf(stringResource(R.string.guide_school), stringResource(R.string.guide_file)), method) { method = it }
    Text(stringResource(if (method == 0) R.string.guide_school_hint else R.string.guide_file_hint))
}

@Composable
private fun EditExample() {
    val sample = stringResource(R.string.guide_example_course)
    var draft by rememberSaveable { mutableStateOf(sample) }
    var saved by rememberSaveable { mutableStateOf(sample) }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    ExampleCourse(name = saved)
    OutlinedTextField(value = draft, onValueChange = { draft = it.take(80); confirmed = false },
        label = { Text(stringResource(R.string.guide_example_title)) }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
    OutlinedButton(onClick = { saved = draft.trim(); confirmed = true }, enabled = draft.isNotBlank()) { Text(stringResource(R.string.guide_save)) }
    if (confirmed) Text(stringResource(R.string.guide_saved), color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun AppearanceExample() {
    var style by rememberSaveable { mutableIntStateOf(0) }
    var position by rememberSaveable { mutableIntStateOf(1) }
    ExampleCourse(bar = style == 1, centered = position == 1)
    Choices(listOf(stringResource(R.string.guide_fill), stringResource(R.string.guide_bar)), style) { style = it }
    Choices(listOf(stringResource(R.string.guide_top), stringResource(R.string.guide_center)), position) { position = it }
}

@Composable
private fun WidgetExample() {
    var choice by rememberSaveable { mutableIntStateOf(0) }
    val labels = listOf(R.string.guide_widget_small, R.string.guide_widget_today, R.string.guide_widget_two, R.string.guide_widget_week).map { stringResource(it) }
    Choices(labels, choice) { choice = it }
    Text(labels[choice], style = MaterialTheme.typography.titleMedium)
    when (choice) {
        0 -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.guide_example_course), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.guide_notice_body))
        }
        1 -> ExampleCourse(bar = true, expanded = true)
        2 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(R.string.guide_today, R.string.guide_tomorrow).forEach { day ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(day), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.guide_example_course), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.guide_notice_body))
                }
            }
        }
        else -> {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(5) { i ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringArrayResource(R.array.guide_weekdays)[i], style = MaterialTheme.typography.labelSmall)
                        Box(Modifier.fillMaxWidth().height(if (i % 2 == 0) 48.dp else 72.dp)
                            .clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primaryContainer))
                        if (i % 2 == 0) Box(Modifier.fillMaxWidth().height(32.dp).clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer))
                    }
                }
            }
        }
    }
    Text(stringResource(R.string.guide_widget_hint), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun ReminderExample() {
    var minutes by rememberSaveable { mutableIntStateOf(0) }
    val values = listOf(5, 10, 15)
    Choices(values.map { stringResource(R.string.guide_notify, it) }, minutes) { minutes = it }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.guide_notify, values[minutes]), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(R.string.guide_notice), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.guide_notice_body))
        }
    }
}

@Composable
private fun SemesterExample() {
    var week by rememberSaveable { mutableIntStateOf(0) }
    Text(stringResource(R.string.guide_week_pattern))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(4) { i -> Box(Modifier.weight(1f).height(28.dp).clip(RoundedCornerShape(6.dp))
            .background(if (i == week) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)) }
    }
    Choices((1..4).map { stringResource(R.string.guide_week, it) }, week) { week = it }
    Text(stringResource(R.string.guide_pattern_date, 1 + week * 7), color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun RoutineExample() {
    var alternative by rememberSaveable { mutableStateOf(false) }
    ExampleCourse()
    Text(stringResource(if (alternative) R.string.guide_period_alt else R.string.guide_period), style = MaterialTheme.typography.titleMedium)
    OutlinedButton(onClick = { alternative = !alternative }) { Text(stringResource(R.string.guide_switch_period)) }
}
