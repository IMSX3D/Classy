package com.imsx3d.classy.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.data.entity.*
import com.imsx3d.classy.ui.theme.SleepyTheme

@Composable
fun SmartPeriodEditor(config: SmartPeriodConfig, onConfigChange: (SmartPeriodConfig) -> Unit,
    modifier: Modifier = Modifier) {
    var plan by remember { mutableStateOf(runCatching { RoutinePlan.from(config) }.getOrNull()) }
    var error by remember { mutableStateOf<String?>(null) }
    var special by remember { mutableStateOf(false) }
    var templateConfirm by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun update(next: RoutinePlan) {
        runCatching { next.toConfig() }.fold(onSuccess = {
            plan = next
            error = null
            onConfigChange(it)
        }, onFailure = { error = it.message })
    }
    val current = plan
    if (current == null) {
        Column(modifier) {
            Text("此作息包含特殊时间，已保留原设置，可继续使用详细编辑。")
            AdvancedSmartPeriodEditor(config, onConfigChange)
        }
        return
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("填写上课时长和各时段的开始时间，自动排好每一节。", style = MaterialTheme.typography.bodyMedium)
        error?.let { Text("未应用这次修改：$it", color = MaterialTheme.colorScheme.error) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoutineNumber("每节课 / 分钟", current.minutes, Modifier.weight(1f)) { update(current.copy(minutes = it)) }
            RoutineNumber("普通课间 / 分钟", current.gap, Modifier.weight(1f)) { update(current.copy(gap = it)) }
        }
        TextButton(onClick = { focus.clearFocus(); templateConfirm = true }) { Text("使用常见作息示例") }
        Text("分段安排", style = MaterialTheme.typography.titleMedium)
        Text("各时段按固定时间开始；修改上午课时不会推迟下午。", style = MaterialTheme.typography.bodySmall)
        current.sections.forEachIndexed { index, section ->
            val firstNode = current.sections.take(index).sumOf { it.count } + 1
            fun change(next: RoutineSection) = update(current.copy(sections = current.sections.toMutableList().also { it[index] = next }))
            Surface(shape = SleepyTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("时段 ${index + 1} · 第 $firstNode–${firstNode + section.count - 1} 节", style = MaterialTheme.typography.titleSmall)
                        if (current.sections.size > 1) TextButton(onClick = {
                            update(current.copy(sections = current.sections.filterIndexed { i, _ -> i != index }))
                        }) { Text("移除") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        key(section.start) {
                            TimePickerField(value = section.start, onValueChange = { change(section.copy(start = it)) },
                                label = "开始时间", modifier = Modifier.weight(1f))
                        }
                        RoutineNumber("上几节课", section.count, Modifier.weight(1f)) {
                            change(section.copy(count = it, durations = section.durations.filterKeys { k -> k < it },
                                gaps = section.gaps.filterKeys { k -> k < it - 1 }))
                        }
                    }
                }
            }
        }
        TextButton(onClick = {
            val last = current.toConfig().derive().last().end
            val start = (RoutinePlan.minuteOfDay(last) + 60).coerceAtMost(23 * 60)
            update(current.copy(sections = current.sections + RoutineSection("%02d:%02d".format(start / 60, start % 60), 1)))
        }) { Text("＋ 添加时段") }
        val exceptionCount = current.sections.sumOf { it.durations.size + it.gaps.size }
        TextButton(onClick = { focus.clearFocus(); special = !special }) {
            Text("${if (special) "收起" else "展开"}特殊调整 · $exceptionCount 项")
        }
        if (special) {
            Text("仅修改不同的课时或课间。0 分钟课间表示连堂；时段之间的休息自动计算。",
                style = MaterialTheme.typography.bodySmall)
            if (exceptionCount > 0) TextButton(onClick = {
                update(current.copy(sections = current.sections.map { it.copy(durations = emptyMap(), gaps = emptyMap()) }))
            }) { Text("全部恢复普通规则") }
            current.sections.forEachIndexed { sectionIndex, section ->
                val first = current.sections.take(sectionIndex).sumOf { it.count } + 1
                repeat(section.count) { position ->
                    fun change(next: RoutineSection) = update(current.copy(sections = current.sections.toMutableList().also { it[sectionIndex] = next }))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RoutineNumber("第 ${first + position} 节 / 分钟", section.durations[position] ?: current.minutes, Modifier.weight(1f)) {
                            change(section.copy(durations = if (it == current.minutes) section.durations - position else section.durations + (position to it)))
                        }
                        if (position < section.count - 1) RoutineNumber("本节后休息 / 分钟", section.gaps[position] ?: current.gap, Modifier.weight(1f)) {
                            change(section.copy(gaps = if (it == current.gap) section.gaps - position else section.gaps + (position to it)))
                        } else Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        Text("作息预览 · ${current.sections.sumOf { it.count }} 节", style = MaterialTheme.typography.titleMedium)
        val preview = current.toConfig().derive()
        current.sections.forEachIndexed { index, section ->
            val offset = current.sections.take(index).sumOf { it.count }
            Surface(shape = SleepyTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("时段 ${index + 1} · ${section.start} 开始", style = MaterialTheme.typography.titleSmall)
                    preview.drop(offset).take(section.count).forEach { row ->
                        RoutinePreviewRow(row.node, row.start, row.end)
                    }
                }
            }
        }
    }
    if (templateConfirm) AlertDialog(onDismissRequest = { templateConfirm = false },
        title = { Text("使用常见作息？") },
        text = { Text("将当前编辑内容替换为：每节 45 分钟、课间 10 分钟；08:00 起 4 节，14:00 起 4 节，19:00 起 2 节。保存后才生效。") },
        confirmButton = { TextButton(onClick = {
            update(RoutinePlan(45, 10, listOf(RoutineSection("08:00", 4), RoutineSection("14:00", 4), RoutineSection("19:00", 2))))
            templateConfirm = false
        }) { Text("使用示例") } },
        dismissButton = { TextButton(onClick = { templateConfirm = false }) { Text("取消") } })
}

/** Monospace clock column has identical digit advances even with a custom system font. */
@Composable
private fun RoutinePreviewRow(node: Int, start: String, end: String) {
    val style = MaterialTheme.typography.bodyLarge
    val clockStyle = style.copy(fontFamily = FontFamily.Monospace)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val clockWidth = with(density) {
        measurer.measure("00:00 – 00:00", clockStyle, softWrap = false).size.width.toDp()
    }
    // Reserve a two-digit lesson label for every row, keeping all sections consistent.
    val labelWidth = with(density) {
        measurer.measure("第 48 节", style, softWrap = false).size.width.toDp()
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= clockWidth + labelWidth + 12.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("第 $node 节", modifier = Modifier.weight(1f), style = style)
                Spacer(Modifier.width(12.dp))
                Text("$start – $end", modifier = Modifier.width(clockWidth),
                    style = clockStyle, softWrap = false, textAlign = TextAlign.End)
            }
        } else {
            // Small windows / large fonts: keep the clock on its own line instead of squeezing it.
            Column(Modifier.fillMaxWidth()) {
                Text("第 $node 节", style = style)
                Text("$start – $end", modifier = Modifier.fillMaxWidth(),
                    style = clockStyle, textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
private fun RoutineNumber(label: String, value: Int, modifier: Modifier, onCommit: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    var editing by remember { mutableStateOf(false) }
    val commit by rememberUpdatedState(onCommit)
    val focus = LocalFocusManager.current
    LaunchedEffect(value, editing) { if (!editing) text = value.toString() }
    TextField(value = text, onValueChange = {
        if (it.length <= 3 && it.all(Char::isDigit)) {
            text = it
            it.toIntOrNull()?.let { number -> commit(number) }
        }
    },
        label = { Text(label) }, singleLine = true, shape = SleepyTheme.fieldShape, colors = SleepyTheme.fieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = modifier.onFocusChanged {
            if (editing && !it.isFocused) {
                text = value.toString()
            }
            editing = it.isFocused
        })
}
