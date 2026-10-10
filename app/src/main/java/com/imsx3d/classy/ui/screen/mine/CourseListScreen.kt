package com.imsx3d.classy.ui.screen.mine

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalDensity
import com.imsx3d.classy.ui.theme.AdaptiveContentPolicy
import com.imsx3d.classy.ui.theme.noRippleClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Class
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imsx3d.classy.R
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.ui.component.SettingsScaffold
import com.imsx3d.classy.ui.screen.schedule.ScheduleViewModel
import com.imsx3d.classy.ui.theme.SleepyTheme
import com.imsx3d.classy.ui.component.settingsCard

/**
 * 课表管理中的课程清单，按课程名和老师聚合；在本页添加课程。
 * 与管理页不同: 这里以「课程组」为单位, 一个名字出现多次(同一课程多次上课安排)聚合为
 * 一张卡显示, 副标题注明「X 个上课安排」; 字段: 课程名 / 老师 / 教室 / 全部周次段。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseListScreen(
    onBack: () -> Unit,
    onAddCourse: () -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme

    // 课程卡先按课程名和老师归组;同一张卡内部再按上课地点分组,
    // 避免不同地点混在同一行,也保留同名不同老师的独立课程卡。
    val grouped: List<CourseGroup> = remember(state.courses) {
        state.courses
            .groupBy { it.courseName.ifBlank { it.groupId } to it.teacher.trim() }
            .map { (key, rows) ->
                CourseGroup(
                    name = rows.first().courseName.ifBlank { key.first },
                    teacher = rows.first().teacher,
                    locations = rows.groupBy { it.room.trim() }
                        .map { (room, locationRows) -> CourseLocation(room, locationRows) }
                        .sortedBy { it.room }
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    var selectedId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selected = grouped.firstOrNull { group ->
        group.locations.any { location -> location.rows.any { it.id == selectedId } }
    }
    val listState = rememberLazyListState()
    val detailState = rememberSaveable(selectedId, saver = androidx.compose.foundation.lazy.LazyListState.Saver) {
        androidx.compose.foundation.lazy.LazyListState()
    }
    BackHandler(enabled = selected != null) { selectedId = null }

    BoxWithConstraints(Modifier.fillMaxSize().background(colors.background)) {
        val density = LocalDensity.current
        val horizontalInsets = WindowInsets.safeDrawing.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr) +
            WindowInsets.safeDrawing.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
        val verticalInsets = WindowInsets.safeDrawing.getTop(density) + WindowInsets.safeDrawing.getBottom(density)
        val wide = AdaptiveContentPolicy.showCourseDetailPane(
            maxWidth.value - with(density) { horizontalInsets.toDp().value },
            maxHeight.value - with(density) { verticalInsets.toDp().value }, density.fontScale
        )
        Row(Modifier.fillMaxSize()) {
            if (wide || selected == null) {
                Box(if (wide) Modifier.width(320.dp * density.fontScale.coerceAtLeast(1f)) else Modifier.weight(1f)) {
                    SettingsScaffold(
                        title = stringResource(R.string.course_list_title),
                        onBack = { if (selected != null) selectedId = null else onBack() },
                        listState = listState,
                        actions = {
                            androidx.compose.material3.TextButton(onClick = onAddCourse) {
                                Text(stringResource(R.string.manage_manual_add))
                            }
                        },
                        verticalSpacing = 12.dp
                    ) {
                        if (grouped.isEmpty()) {
                            item { Text(stringResource(R.string.course_list_empty), color = colors.onSurfaceVariant) }
                        } else {
                            item { Text(stringResource(R.string.course_list_subtitle), color = colors.onSurfaceVariant) }
                            items(grouped, key = { it.locations.first().rows.first().id }) { group ->
                                Column(
                                    Modifier.fillMaxWidth()
                                        .settingsCard(if (group == selected) colors.primaryContainer else colors.surfaceContainer)
                                        .noRippleClickable { selectedId = group.locations.first().rows.first().id }
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(group.name, style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                                    if (group.teacher.isNotBlank()) MetaRow(Icons.Outlined.Person, group.teacher)
                                    Text(stringResource(R.string.course_list_arrangements, group.locations.sumOf { it.rows.size }),
                                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            if (wide || selected != null) {
                Box(Modifier.weight(1f)) {
                    if (selected != null) {
                        SettingsScaffold(
                            title = selected.name,
                            onBack = { selectedId = null },
                            listState = detailState,
                            verticalSpacing = 12.dp
                        ) {
                            item { CourseGroupCard(selected) }
                        }
                    } else {
                        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
                            contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.course_list_select_hint), color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseGroupCard(group: CourseGroup) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .settingsCard(colors.surfaceContainer)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = group.name.ifBlank { stringResource(R.string.course_detail_title) },
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSurface
        )
        if (group.teacher.isNotBlank()) {
            MetaRow(icon = Icons.Outlined.Person, text = group.teacher)
        }
        group.locations.forEach { location ->
            if (location.room.isNotBlank()) {
                MetaRow(icon = Icons.Outlined.LocationOn, text = location.room)
            }
            Text(
                text = stringResource(R.string.course_list_arrangements, location.rows.size),
                style = MaterialTheme.typography.labelSmall,
                color = colors.onSurfaceVariant
            )
            // 每个地点单独列出该地点的全部上课安排。
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                location.rows.forEach { row -> ArrangementRow(row) }
            }
        }
    }
}

@Composable
private fun MetaRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface
        )
    }
}

@Composable
private fun ArrangementRow(row: CourseEntity) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val dayLabel = stringResource(
        when (row.day) {
            1 -> R.string.day_short_1
            2 -> R.string.day_short_2
            3 -> R.string.day_short_3
            4 -> R.string.day_short_4
            5 -> R.string.day_short_5
            6 -> R.string.day_short_6
            7 -> R.string.day_short_7
            else -> R.string.day_short_1
        }
    )
    val nodeLabel = row.shortNodeString(context)
    val weekRangeLabel = stringResource(R.string.course_week_range, nodeLabel, row.startWeek, row.endWeek)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.Class,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text(
            text = "${dayLabel} · $weekRangeLabel",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )
    }
}

private data class CourseGroup(
    val name: String,
    val teacher: String,
    val locations: List<CourseLocation>
)

private data class CourseLocation(
    val room: String,
    val rows: List<CourseEntity>
)