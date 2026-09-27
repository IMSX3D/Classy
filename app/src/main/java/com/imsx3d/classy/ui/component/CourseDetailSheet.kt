package com.imsx3d.classy.ui.component

import androidx.compose.foundation.background
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.ui.theme.SleepyTheme
import com.imsx3d.classy.util.AppPrefs
import com.imsx3d.classy.util.ConflictCluster
import com.imsx3d.classy.util.ConflictLayoutEngine
import com.imsx3d.classy.ui.theme.noRippleClickable

/**
 * 课程详情 Bottom Sheet — 仿 switchable.html .modal-backdrop
 *
 * 结构:
 * ┌────────────────────────────┐
 * │ 课程详情              [×] │  ← modal-header (surface-container)
 * ├────────────────────────────┤
 * │ ⏰ 1-2节 · 08:00-09:35    │  ← modal-time (secondary-container pill)
 * │ 课程  高数                 │
 * │ 老师  张三                 │
 * │ 地点  21B4115中            │
 * │ ─── 选择默认置顶课程 ─── │ ← 仅当 course ∈ 冲突簇时显示
 * │ ( ) 工科数学分析          │
 * │ (•) 电路与电子            │
 * │ [   编辑课程   ]          │
 * └────────────────────────────┘
 */
@Composable
fun CourseDetailSheet(
    course: CourseEntity?,
    timeString: String? = null,
    allCourses: List<CourseEntity> = emptyList(),
    onDismiss: () -> Unit,
    onEdit: ((CourseEntity) -> Unit)? = null,
    onDefaultTopChanged: ((clusterKey: String, layerRepId: Long?) -> Unit)? = null,
    // 用户报障 2026-09-10: 非网格面(详情页)聚簇必须与网格同一时间域 —
    // ownTime 课落库的 startNode/step 是表单占位值, 节点域聚簇会把时间零交集的
    // 两门 ownTime 课(节点区间恰好相同)误判成冲突簇。null = 旧行为(节点域)。
    timeJson: String? = null
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // UI-4t: 实心主色按钮共享取色（深色下不再实心亮主色，见 ui/theme/SelectedSurface.kt）
    val selButton = com.imsx3d.classy.ui.theme.selectedSurfaceColors()

    if (course != null) {
        // 找出 course 所在冲突簇(仅当 day 下 ≥2 课区间相交才有)。
        // findClusters 按 day 分桶, 簇键 = "${day}:${anchor.startNode}:${anchor.step}",
        // 与 ConflictClusterCard / topOverrides 用同一公式。
        // 用户报障 2026-09-10: 带 timeJson 走分钟域(与网格一致), ownTime 课先归一化。
        val clusterInfo: ConflictCluster? = remember(course, allCourses, timeJson) {
            val sameDay = allCourses.filter { it.day == course.day }
                .let { list -> if (timeJson == null) list else list.map { it.normalizeNode(timeJson) } }
            ConflictLayoutEngine.findClusters(sameDay, timeJson)
                .firstOrNull { it.courses.any { c -> c.id == course.id } }
                ?.takeIf { it.courses.size >= 2 }
        }

        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            // UI-3b: 圆角改用 Glasense dialog 规范（24dp），与卡片 12dp 同一套比例
            shape = RoundedCornerShape(
                topStart = com.nevoit.glasense.theme.GlasenseTheme.specs.dialogCorner,
                topEnd = com.nevoit.glasense.theme.GlasenseTheme.specs.dialogCorner
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                // Header
                SheetHeader(
                    title = course.courseName.ifBlank { stringResource(R.string.course_detail_title) }
                )

                // Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (timeString != null) {
                        TimeChip(text = timeString)
                    }

                    DetailRow(key = stringResource(R.string.course_field_name), value = course.courseName.ifBlank { "—" })
                    if (course.teacher.isNotBlank()) {
                        DetailRow(key = stringResource(R.string.course_field_teacher), value = course.teacher)
                    }
                    if (course.room.isNotBlank()) {
                        DetailRow(key = stringResource(R.string.course_field_room), value = course.room)
                    }
                    DetailRow(key = stringResource(R.string.course_field_week), value = stringResource(R.string.course_week_range, course.shortNodeString(LocalContext.current), course.startWeek, course.endWeek))
                    if (course.note.isNotBlank()) {
                        DetailRow(key = stringResource(R.string.course_field_note), value = course.note)
                    }

                    // 默认置顶选择区 — 仅冲突簇显示
                    if (clusterInfo != null) {
                        DefaultTopPickerSection(
                            cluster = clusterInfo,
                            onDefaultTopChanged = onDefaultTopChanged
                        )
                    }

                    if (onEdit != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                // UI-4t: 深色下不再实心亮主色
                                .background(selButton.container)
                                .noRippleClickable { onEdit(course) },
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.course_detail_edit_course),
                                style = com.nevoit.glasense.theme.GlasenseTheme.type.bodyEmphasized,
                                color = selButton.content
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 默认置顶选择区 — v7.9 设计:
 * - 按图层(链式分组)一行;每行 = 单选 + 该图层全部课程名(顿号分隔)
 * - 默认无勾选(系统按 primaryComparator 自动)
 * - 选中 → 写入 AppPrefs.KEY_CONFLICT_DEFAULT_TOP, 持久化
 * - 行名过长 → 省略号
 */
@Composable
private fun DefaultTopPickerSection(
    cluster: ConflictCluster,
    onDefaultTopChanged: ((clusterKey: String, layerRepId: Long?) -> Unit)?
) {
    val context = LocalContext.current
    val g = com.nevoit.glasense.theme.GlasenseTheme.colors

    // 簇键:公式唯一真值在引擎(v7.10.16p) — 与 ConflictClusterCard / topOverrides 同源
    val clusterKey = ConflictLayoutEngine.conflictClusterKey(cluster)

    // 图层列表(链式分组后的层)
    // v7.10.16p: 单图层(全部课并排无真重叠,链组把它们串成一条链而已)没有"谁压谁",
    // 选择置顶无意义 — 用户报障「没有冲突的课也弹默认置顶」。≥2 图层才显示本区。
    val layers = remember(cluster) { ConflictLayoutEngine.chainGroups(cluster.courses) }
    if (layers.size < 2) return

    // 监听 prefs 变化:用户在其他入口改了也要同步刷新选中态
    val defaultTopMap by AppPrefs.conflictDefaultTopFlow(context).collectAsState(initial = AppPrefs.getConflictDefaultTop(context))
    val savedRepId: Long? = defaultTopMap[clusterKey]

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        // 标题行
        Text(
            text = stringResource(R.string.conflict_default_top_title),
            style = com.nevoit.glasense.theme.GlasenseTheme.type.subHeadlineEmphasized,
            color = g.content,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )

        for (layer in layers) {
            val layerRepId = layer.first().id
            // 行文本 = 该图层全部课程名("工科数学分析、大学物理")
            val label = layer.joinToString("、") { it.courseName.ifBlank { "—" } }
            // 当前是否已存:已存 = 该 repId 即上次所选;未存 = 系统默认 = 无勾选
            val selected = savedRepId == layerRepId

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected,
                        onClick = {
                            val rep = if (selected) null else layerRepId
                            // v7.10.5: 回调优先 — 同帧驱动网格换层(会话级 override);
                            // 无回调宿主(小组件等)时退回纯持久化路径
                            if (onDefaultTopChanged != null) {
                                onDefaultTopChanged(clusterKey, rep)
                            } else {
                                AppPrefs.putConflictDefaultTop(context, clusterKey, rep)
                            }
                        },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // UI-19a：M3 RadioButton → 统一圆环（与滑杆拇指同族；选中语义仍由整行 selectable 承担）
                com.imsx3d.classy.ui.component.GlasenseRadio(selected = selected)
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = g.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String) {
    val g = com.nevoit.glasense.theme.GlasenseTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(g.cardBackground)
            .padding(start = 20.dp, top = 16.dp, bottom = 12.dp, end = 20.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = title,
            style = com.nevoit.glasense.theme.GlasenseTheme.type.title2Emphasized,
            color = g.content,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TimeChip(text: String) {
    val g = com.nevoit.glasense.theme.GlasenseTheme.colors
    Text(
        text = text,
        style = com.nevoit.glasense.theme.GlasenseTheme.type.footnoteEmphasized,
        color = g.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(g.primary.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

@Composable
private fun DetailRow(key: String, value: String) {
    val g = com.nevoit.glasense.theme.GlasenseTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = key,
            style = com.nevoit.glasense.theme.GlasenseTheme.type.footnote,
            color = g.contentVariant,
            modifier = Modifier.width(56.dp)
        )
        Text(
            text = value,
            style = com.nevoit.glasense.theme.GlasenseTheme.type.body,
            color = g.content,
            modifier = Modifier.weight(1f)
        )
    }
}
