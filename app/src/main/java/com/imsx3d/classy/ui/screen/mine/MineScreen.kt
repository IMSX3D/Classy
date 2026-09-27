package com.imsx3d.classy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.screen.schedule.ScheduleViewModel
import com.imsx3d.classy.ui.theme.noRippleClickable
import com.nevoit.glasense.core.component.HGap
import com.nevoit.glasense.core.component.Icon
import com.nevoit.glasense.core.component.Text
import com.nevoit.glasense.core.component.VGap
import com.nevoit.glasense.theme.GlasenseTheme
import kotlinx.coroutines.launch
import com.imsx3d.classy.ui.component.GlasenseSnackbarHost

/**
 * 「我的」页 —— Glasense 版式（UI-3b，2026-09-26）。
 *
 * 与原版的差别（这是"能看出区别"的部分，纯换色看不出）：
 *   · 头部：headlineMedium + 副标题  →  **32sp 大标题（largeTitleEmphasized）** + 次级说明
 *   · 统计：一整张 18dp 内边距的大圆角卡  →  **扁平三栏**（页面底色上直接排，栏间 0.5dp 细竖线）
 *   · 设置项：每项各自一张卡 + 40dp 彩色图标块  →  **一张卡内的分组列表**
 *     （行高 52dp、图标 18dp 单色、行间 0.5dp 细线、右侧箭头），与 Cresto/iOS 的设置页同构
 *   · 按钮：M3 FilledTonalButton  →  Glasense 语言（强调色实心 + 12dp 圆角 + 同色文字）
 */
@Composable
fun MineScreen(
    viewModel: ScheduleViewModel = viewModel(),
    onOpenAllTables: () -> Unit = {},
    onOpenCourseList: () -> Unit = {},
    onOpenPeriodTables: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onOpenGeneral: () -> Unit = {},
    onOpenExport: () -> Unit = {},
    onOpenReminder: () -> Unit = {},
    onOpenAbout: () -> Unit = {},
    updateNoticeVisible: Boolean = false
) {
    val state by viewModel.state.collectAsState()
    val g = GlasenseTheme.colors
    // UI-4t: 实心主色按钮共享取色（见 ui/theme/SelectedSurface.kt）
    val selButton = com.imsx3d.classy.ui.theme.selectedSurfaceColors()
    val type = GlasenseTheme.type
    val specs = GlasenseTheme.specs
    val context = LocalContext.current
    val widgetsRefreshedMessage = stringResource(R.string.mine_refresh_widgets_done)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val showSnack: (String) -> Unit = { msg -> scope.launch { snackbar.showSnackbar(msg) } }

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

            // 数据统计 —— 扁平三栏（无卡片底，仅细竖线）
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem(
                        value = state.tables.size.toString(),
                        label = stringResource(R.string.mine_stat_tables),
                        onClick = onOpenAllTables,
                        modifier = Modifier.weight(1f)
                    )
                    StatColumnDivider()
                    StatItem(
                        // 与 CourseListScreen 同口径: 同名课程一组, 空名按 groupId 区分
                        value = state.courses
                            .distinctBy { it.courseName.ifBlank { "#${it.groupId}" } }
                            .size.toString(),
                        label = stringResource(R.string.mine_stat_courses),
                        onClick = onOpenCourseList,
                        modifier = Modifier.weight(1f)
                    )
                    StatColumnDivider()
                    StatItem(
                        value = state.currentWeek.toString(),
                        label = stringResource(R.string.mine_stat_week),
                        onClick = null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 设置项 —— 一张卡内的分组列表（行间细线，末行无线）
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(specs.cardShape)
                        .background(g.cardBackground)
                ) {
                    SettingsRow(Icons.Outlined.Edit, stringResource(R.string.all_tables), onOpenAllTables)
                    RowHairline()
                    // issue#40: 时间节次表入口 — 与课表管理并列(设计 §4.1)
                    SettingsRow(Icons.Outlined.Schedule, stringResource(R.string.mine_period_tables), onOpenPeriodTables)
                    RowHairline()
                    SettingsRow(Icons.Outlined.Share, stringResource(R.string.mine_export), onOpenExport)
                    RowHairline()
                    SettingsRow(Icons.Outlined.Notifications, stringResource(R.string.reminder_title), onOpenReminder)
                    RowHairline()
                    SettingsRow(Icons.Outlined.Palette, stringResource(R.string.mine_appearance), onOpenAppearance)
                    RowHairline()
                    SettingsRow(Icons.Outlined.Tune, stringResource(R.string.mine_general), onOpenGeneral)
                    RowHairline()
                    SettingsRow(
                        Icons.Outlined.Info,
                        stringResource(R.string.about_title),
                        onOpenAbout,
                        highlighted = updateNoticeVisible
                    )
                }
            }

            // 动作区: 刷新所有小组件（Glasense 语言按钮，与上方列表物理隔离）
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        // UI-4t: 深色下不再是一条亮青块（用户报障"刷新按钮刺眼"）
                        .background(selButton.container)
                        .noRippleClickable {
                            scope.launch {
                                com.imsx3d.classy.widget.WidgetUpdater.notifyDataChanged(context)
                                showSnack(widgetsRefreshedMessage)
                            }
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        tint = selButton.content,
                        modifier = Modifier.size(18.dp)
                    )
                    HGap(8.dp)
                    Text(
                        text = stringResource(R.string.mine_refresh_widgets),
                        style = MaterialTheme.typography.titleMedium,
                        color = selButton.content
                    )
                }
            }
        }
        GlasenseSnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

/** 统计格 —— 数值用强调色大号字，标签用次级小字（Glasense 字号阶梯）。 */
@Composable
private fun StatItem(
    value: String,
    label: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val g = GlasenseTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .then(if (onClick != null) Modifier.noRippleClickable(onClick) else Modifier)
            .padding(vertical = 6.dp)
    ) {
        Text(text = value, style = GlasenseTheme.type.title1Emphasized, color = g.primary)
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
