package com.imsx3d.classy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.SettingsScaffold
import com.imsx3d.classy.ui.screen.schedule.ScheduleViewModel
import com.imsx3d.classy.ui.theme.SleepyTheme
import com.imsx3d.classy.ui.component.GlasenseIconButton
import com.imsx3d.classy.ui.theme.noRippleClickable
import com.imsx3d.classy.ui.component.GlasenseButton

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun AllTablesScreen(
    onBack: () -> Unit,
    onCreateNewTable: () -> Unit,
    onOpenEditTable: (Long) -> Unit,
    viewModel: ScheduleViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme

    // UI-7b: 页头统一成「我的」页那套 32sp 大标题 + 返回行（原来 M3 小标题顶栏）
    SettingsScaffold(
        title = stringResource(R.string.all_tables),
        onBack = onBack,
        verticalSpacing = 12.dp
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }

        itemsIndexed(state.tables) { _, table ->
            val isCurrent = table.id == state.selectedTableId
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SleepyTheme.shapes.large)
                    .background(if (isCurrent) colors.primaryContainer else colors.surfaceContainer)
                    .noRippleClickable {
                        if (!isCurrent) {
                            // v7.10.16w 用户 2026-09-10: 选表后留在本页(选中态就地高亮),
                            // 不再强制弹回 — 用户可能还要复制/编辑其他副本。返回键/← 离开。
                            viewModel.selectTable(table.id)
                        }
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCurrent) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(SleepyTheme.shapes.medium)
                            .background(colors.outlineVariant)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    // M3 对比度修正：当前行背景是 primaryContainer，文字/副标题应配对
                    // onPrimaryContainer 系（之前用 onSurface/onSurfaceVariant，自定义高对比主题下对比度不足）。
                    // 非当前行背景 surfaceContainer 维持 onSurface/onSurfaceVariant。
                    val (titleColor, subtitleColor) = if (isCurrent) {
                        colors.onPrimaryContainer to colors.onPrimaryContainer.copy(alpha = SleepyTheme.Alpha.highContent)
                    } else {
                        colors.onSurface to colors.onSurfaceVariant
                    }
                    Text(
                        text = table.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = titleColor
                    )
                    Text(
                        text = if (isCurrent) stringResource(R.string.current_table_week, state.currentWeek) else stringResource(R.string.table_start_date, table.startDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor
                    )
                    // v7.10.15 每表显示导入时间(年月日 时分秒) — 方便用户分辨多张课表
                    if (table.createdAt > 0) {
                        Text(
                            text = stringResource(
                                R.string.table_created_at,
                                java.time.Instant.ofEpochMilli(table.createdAt)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = subtitleColor
                        )
                    }
                }
                // v7.10.15 duplicate 图标 — 创建课表副本, 置于设置图标左边
                GlasenseIconButton(
                    icon = Icons.Outlined.ContentCopy,
                    contentDescription = stringResource(R.string.all_tables_duplicate),
                    onClick = { viewModel.duplicateTable(table.id) },
                    tint = colors.onSurfaceVariant)
                GlasenseIconButton(
                    icon = Icons.Outlined.Settings,
                    contentDescription = stringResource(R.string.action_settings),
                    onClick = { onOpenEditTable(table.id) },
                    tint = colors.onSurfaceVariant)
            }
        }

        item {
            GlasenseButton(
                text = stringResource(R.string.all_tables_new),
                onClick = onCreateNewTable,
                leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) }
            )
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
