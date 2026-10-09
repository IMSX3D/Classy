package com.imsx3d.classy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imsx3d.classy.ui.screen.schedule.ScheduleViewModel
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import com.imsx3d.classy.util.DateUtils
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.theme.noRippleClickable
import com.nevoit.glasense.core.component.HGap
import com.nevoit.glasense.core.component.Icon
import com.nevoit.glasense.core.component.Text
import com.nevoit.glasense.core.component.VGap
import com.nevoit.glasense.theme.GlasenseTheme

/** A passive overview followed by six task-level entry points. */
@Composable
fun MineScreen(
    onOpenManagement: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenWidgets: () -> Unit,
    onOpenReminder: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    updateNoticeVisible: Boolean = false,
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val g = GlasenseTheme.colors
    val type = GlasenseTheme.type
    val specs = GlasenseTheme.specs
    Box(modifier = Modifier.fillMaxSize().background(g.pageBackground)) {
        // Dock 悬浮底栏: 滚动尾部多留 Dock 总高(FAB 语义, 同今日页)
        val navExtra = com.imsx3d.classy.ui.component.LocalNavExtraBottomPadding.current
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                // UI-25a：20dp → 16dp —— 全 App 只有这两页（我的/今日）用 20dp，
                // 导致大标题与其它页不在同一条左线上（实测：我的 20.0dp vs 设置页 16.0dp）
                start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp + navExtra
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 大标题头
            // UI-18a 记录：这一页**不要**加状态栏遮罩 —— 内容不满一屏、列表不滚动，
            // 遮罩只会盖住标题上沿（用户 2026-09-27 报的"文字截断"）。标题本来就在状态栏下方
            // （NavHost 的 Dock 分支已给主 Tab 整块加了 WindowInsets.statusBars 内缩）。
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(
                        text = stringResource(R.string.tab_mine),
                        style = type.largeTitleEmphasized,
                        color = g.content
                    )
                    VGap(4.dp)
                    Text(
                        text = stringResource(R.string.mine_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = g.contentVariant
                    )
                }
            }

            // Restore the original flat three-column overview without extra navigation.
            item {
                val table = state.currentTable
                val semester = table?.let { DateUtils.semesterStatus(it.startDate, it.maxWeek) }
                val week = when (semester) {
                    DateUtils.SemesterStatus.BEFORE_START -> stringResource(R.string.semester_not_started)
                    DateUtils.SemesterStatus.AFTER_END -> stringResource(R.string.semester_ended)
                    DateUtils.SemesterStatus.IN_RANGE -> DateUtils.currentWeek(table!!.startDate).toString()
                    null -> "—"
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatItem(state.tables.size.toString(), stringResource(R.string.mine_stat_tables), modifier = Modifier.weight(1f))
                    StatColumnDivider()
                    StatItem(
                        state.courses.filter { it.tableId == table?.id }
                            .distinctBy { it.courseName.ifBlank { it.groupId } to it.teacher.trim() }.size.toString(),
                        stringResource(R.string.mine_stat_courses), modifier = Modifier.weight(1f)
                    )
                    StatColumnDivider()
                    StatItem(week, stringResource(R.string.mine_stat_week), modifier = Modifier.weight(1f))
                }
            }

            item {
                Column(Modifier.fillMaxWidth().clip(specs.cardShape).background(g.cardBackground)) {
                    SettingsRow(Icons.Outlined.Edit, stringResource(R.string.tab_manage), onOpenManagement)
                    RowHairline()
                    SettingsRow(Icons.Outlined.Notifications, stringResource(R.string.reminder_title), onOpenReminder)
                    RowHairline()
                    SettingsRow(Icons.Outlined.Palette, stringResource(R.string.mine_appearance), onOpenAppearance)
                    RowHairline()
                    SettingsRow(Icons.Outlined.Widgets, stringResource(R.string.appearance_section_widget), onOpenWidgets)
                    RowHairline()
                    SettingsRow(Icons.Outlined.Tune, stringResource(R.string.settings_home_title), onOpenSettings)
                    RowHairline()
                    SettingsRow(Icons.Outlined.Info, stringResource(R.string.about_title), onOpenAbout,
                        highlighted = updateNoticeVisible)
                }
            }
        }
    }
}

/** 统计格 —— 数值用强调色大号字，标签用次级小字（Glasense 字号阶梯）。 */
@Composable
private fun StatItem(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val g = GlasenseTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .padding(vertical = 6.dp)
    ) {
        Text(text = value, style = if (value.length <= 3) GlasenseTheme.type.title1Emphasized else MaterialTheme.typography.bodyMedium, color = g.primary, textAlign = TextAlign.Center)
        VGap(2.dp)
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = g.contentVariant)
    }
}

/** 栏间细竖线（Glasense 用低透明度描边做层次，0.5dp）。 */
@Composable
private fun StatColumnDivider() {
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(30.dp)
            .background(GlasenseTheme.colors.scrimBold)
    )
}

/** 行间细横线 —— 从文字起点缩进（与图标对齐），末行不画。 */
@Composable
private fun RowHairline() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 46.dp)
            .height(0.5.dp)
            .background(GlasenseTheme.colors.scrimNormal)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit = {},
    highlighted: Boolean = false
) {
    val g = GlasenseTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (highlighted) Modifier.background(g.primary.copy(alpha = 0.08f)) else Modifier)
            .noRippleClickable(onClick)
            // UI-25a：14dp → 16dp —— 配合页内缩 16dp，行文字落在 32dp（与设置页一致）
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (highlighted) g.primary else g.contentVariant,
            modifier = Modifier.size(18.dp)
        )
        HGap(14.dp)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (highlighted) g.primary else g.content,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = g.contentVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(16.dp)
        )
    }
}
