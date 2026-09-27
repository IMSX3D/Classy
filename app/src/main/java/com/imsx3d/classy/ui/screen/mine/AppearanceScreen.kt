package com.imsx3d.classy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.SectionHeader
import com.imsx3d.classy.ui.component.SettingsGroupCard
import com.imsx3d.classy.ui.component.SettingsGroupRow
import com.imsx3d.classy.ui.component.SettingsRowSegmented
import com.imsx3d.classy.ui.component.SettingsScaffold
import com.imsx3d.classy.ui.theme.CustomSchemeDeriver
import com.imsx3d.classy.ui.theme.SleepyTheme
import com.imsx3d.classy.ui.theme.selectedSurfaceColors
import com.imsx3d.classy.ui.theme.noRippleClickable
import com.imsx3d.classy.ui.theme.ThemePreset
import com.imsx3d.classy.ui.theme.ThemePresets
import com.imsx3d.classy.data.CustomThemeStore
import com.imsx3d.classy.util.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 外观页(决策 D2 合并页): 仅主题色彩组。课程显示/小组件组已迁至 GeneralSettingsScreen(2026-08-24)。
 * 保留 refreshWidgets() 管线, 主题变更后即时刷新小组件。
 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    themeMode: String = AppPrefs.THEME_MODE_SYSTEM,
    onThemeModeChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    // UI-4s: 分段选中块走共享"选中面"取色（深色下不再实心铺浅主色）
    val selSurface = selectedSurfaceColors()
    val currentKey by AppPrefs.themeKeyFlow(context).collectAsState(initial = AppPrefs.getThemeKey(context))
    val selectedMode = themeMode

    // 自定义主题编辑器 overlay(创建时 editingTheme=null;微调时载入草稿)
    var showEditor by remember { mutableStateOf(false) }
    var editingTheme by remember { mutableStateOf<com.imsx3d.classy.data.CustomTheme?>(null) }
    // 编辑器保存/删除后刷新网格列表
    var customListVersion by remember { mutableIntStateOf(0) }

    // 选主题/模式后立即刷小组件: 之前只写 SP 不刷 widget → 小组件不跟主题变
    val widgetScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    fun refreshWidgets() {
        widgetScope.launch { com.imsx3d.classy.widget.WidgetUpdater.notifyDataChanged(context) }
    }

    // UI-7b: 页头统一成「我的」页那套 32sp 大标题 + 返回行（原来 M3 小标题顶栏）
    SettingsScaffold(
        title = stringResource(R.string.mine_appearance),
        onBack = onBack,
        verticalSpacing = 12.dp
    ) {
        // ── 分组① 主题色彩 ──
        item {
            // UI-25a：本页第一个分组标题 → 0（页头已带 12dp 底边距，避免叠成 40dp）
            SectionHeader(
                title = stringResource(R.string.appearance_section_theme),
                topSpacing = 0.dp
            )
        }

        item {
            SystemThemeCard(
                selected = currentKey == ThemePresets.KEY_SYSTEM,
                onClick = {
                    AppPrefs.setThemeKey(context, ThemePresets.KEY_SYSTEM)
                    refreshWidgets()
                }
            )
        }

        // 2 列网格 5 套预设 + 新建卡 + 自定义主题卡(混排,奇数补空位)
        item {
            val customThemes = remember(customListVersion) { CustomThemeStore.getAll(context) }
            // 网格单元序列:5 预设卡 → 各自定义卡 → 加号永远最后(2026-09-11 用户定稿:
            // 自定义卡与预设卡同列紧密堆积,加号只排在整个网格末尾)
            val cells: List<ThemeGridCell> = buildList {
                ThemePresets.all.forEach { add(ThemeGridCell.Preset(it)) }
                customThemes.forEach { add(ThemeGridCell.Custom(it)) }
                add(ThemeGridCell.NewTheme)
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                cells.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { cell ->
                            Box(Modifier.weight(1f)) {
                                when (cell) {
                                    is ThemeGridCell.Preset -> PresetThemeCard(
                                        preset = cell.preset,
                                        selected = currentKey == cell.preset.key,
                                        onClick = { AppPrefs.setThemeKey(context, cell.preset.key); refreshWidgets() }
                                    )
                                    is ThemeGridCell.NewTheme -> NewThemeCard(
                                        onClick = { showEditor = true; editingTheme = null }
                                    )
                                    is ThemeGridCell.Custom -> CustomThemeCard(
                                        theme = cell.theme,
                                        selected = currentKey == ThemePresets.CUSTOM_KEY_PREFIX + cell.theme.id,
                                        onClick = {
                                            AppPrefs.setThemeKey(context, ThemePresets.CUSTOM_KEY_PREFIX + cell.theme.id)
                                            refreshWidgets()
                                        },
                                        onEdit = { showEditor = true; editingTheme = cell.theme }
                                    )
                                }
                            }
                        }
                        if (row.size == 1) Box(Modifier.weight(1f))
                    }
                }
            }
        }

        // 外观模式: 浅色 / 深色 / 深浅色跟随系统 三态分段控件(标签与主题取色的 theme_system"跟随系统"区分)
        item {
            // UI-23a：这一段原来是**手写的第二套分段控件**（选中 = 实心主色填充，"大色块"），
            // 现已并入全 App 唯一的 `SettingsRowSegmented`（选中 = 淡主色底 + 主色文字）。
            // 控件样式只有一处定义，就不会再出现"同一种选择、两种长相"。
            // 版式：**标题一行、控件整宽一行**（而不是"标题 + 右侧控件"）——
            // 因为 4 字标签 × 3 段在这张卡里放不下行内宽度：试过"标题+控件同行"，两者互相挤
            // （标题被压到 0 宽 / 标签换行被裁，真机截图发现两次），所以回到上下两行。
            SettingsGroupCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        // UI-23a：标题从「外观」改为「深浅色」—— 这样选项可以用短标签
                        // 「跟随系统 / 浅色 / 深色」（原来那个 7 字标签"深浅色跟随系统"在三段里放不下，
                        // 真机实测会换行被裁）。语义上的区分从"标签自证"移到"行标题给语境"。
                        text = stringResource(R.string.theme_mode_row),
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface
                    )
                    SettingsRowSegmented(
                        options = listOf(
                            // 「跟随系统」复用主题色那张卡的名字（theme_system）——比原
                            // "深浅色跟随系统"（7 字）短，三段才放得下；语境由行标题「深浅色」给。
                            stringResource(R.string.theme_system),
                            stringResource(R.string.theme_mode_light),
                            stringResource(R.string.theme_mode_dark)
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        selectedKey = when (selectedMode) {
                                AppPrefs.THEME_MODE_LIGHT -> 1
                                AppPrefs.THEME_MODE_DARK -> 2
                                else -> 0
                            },
                            onSelect = { idx ->
                                val mode = when (idx) {
                                    1 -> AppPrefs.THEME_MODE_LIGHT
                                    2 -> AppPrefs.THEME_MODE_DARK
                                    else -> AppPrefs.THEME_MODE_SYSTEM
                                }
                                if (mode != selectedMode) {
                                    AppPrefs.setThemeMode(context, mode); onThemeModeChange(mode); refreshWidgets()
                                }
                        }
                    )
                }
            }
        }
    }

    // 编辑器 overlay — 全屏覆盖在外观页之上(仿其他 overlay 页的层叠样式)
    if (showEditor) {
        CustomThemeEditorScreen(
            editing = editingTheme,
            nextThemeNumber = remember(customListVersion) { CustomThemeStore.getAll(context).size + 1 },
            onBack = { showEditor = false; editingTheme = null },
            onSaved = { saved ->
                CustomThemeStore.save(context, saved)
                AppPrefs.setThemeKey(context, ThemePresets.CUSTOM_KEY_PREFIX + saved.id)
                customListVersion++
                // 保存即应用(2026-09-21 用户反馈): 无条件把 theme_key 落到本次保存的主题 —
                // themeKeyFlow 只听 sleepy_prefs 的 theme_key, 只写 custom_themes 文件
                // 不会触发 SleepyThemeProvider 重组, app 配色纹丝不动。新建主题此前更是
                // "保存了但从没被应用过"。
                refreshWidgets()
                showEditor = false
                editingTheme = null
            },
            onDeleted = { id ->
                CustomThemeStore.delete(context, id)
                customListVersion++
                // 删除的正是当前应用主题 → 写回 default(与 unknown-key 回落语义一致)
                if (currentKey == ThemePresets.CUSTOM_KEY_PREFIX + id) {
                    AppPrefs.setThemeKey(context, ThemePresets.DEFAULT_KEY)
                    refreshWidgets()
                }
                showEditor = false
                editingTheme = null
            }
        )
    }
}

/** 网格单元 — 预设卡 / 新建卡 / 自定义卡 混排 */
private sealed interface ThemeGridCell {
    data class Preset(val preset: ThemePreset) : ThemeGridCell
    data object NewTheme : ThemeGridCell
    data class Custom(val theme: com.imsx3d.classy.data.CustomTheme) : ThemeGridCell
}

// ── 以下复制自 ThemeColorScreen ──

@Composable
private fun SystemThemeCard(selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // 2026-08-25 用户指令: 全 app 纯色块禁描线 — 选中态只用色块层级+对勾表达
    val bgColor = if (selected) colors.primaryContainer else colors.surfaceContainer
    Surface(
        modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).noRippleClickable(onClick),
        color = bgColor, shape = SleepyTheme.shapes.large
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).clip(SleepyTheme.shapes.large).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.AutoAwesome, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.theme_system), style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
                Spacer(Modifier.height(2.dp))
                Text(stringResource(R.string.theme_system_desc), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Outlined.Check, stringResource(R.string.selected), tint = colors.primary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun PresetThemeCard(preset: ThemePreset, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val scheme = if (colors.background.red < 0.5f) preset.light else preset.dark
    // 2026-08-25 用户指令: 全 app 纯色块禁描线 — 选中态只用色块层级+对勾表达
    val bgColor = if (selected) colors.primaryContainer else colors.surfaceContainer
    Surface(
        modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).noRippleClickable(onClick),
        color = bgColor, shape = SleepyTheme.shapes.large
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorSwatch(scheme.primary)
                ColorSwatch(scheme.secondary)
                ColorSwatch(scheme.tertiary)
            }
            Spacer(Modifier.height(12.dp))
            // ✓槽位恒定 20dp 占位,图标仅在选中时渲染进槽位 —— 真机字体缩放(fontScale<1)
            // 下 titleSmall 行高会缩到 20dp 以下,条件渲染=选中那刻凭空多一个决定行高的孩子
            // =选中卡比未选中卡高(2026-09-22 用户实测)。禁改回 raw `if (selected) Icon(...)`。
            // 不用 alpha(0f) 隐藏:读屏会照常播报"已选中",占位 Box 才是布局+a11y 双正确。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(preset.nameRes), style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.weight(1f))
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (selected) Icon(Icons.Outlined.Check, stringResource(R.string.selected), tint = colors.primary, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun ColorSwatch(color: Color) {
    Box(Modifier.size(28.dp).clip(SleepyTheme.shapes.small).background(color))
}

/** 「新建主题」入口 — 裸虚线圆圈+加号,无卡片无背景无文字(2026-09-11 用户定稿) */
@Composable
private fun NewThemeCard(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .noRippleClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        DashedCircleWithPlus(size = 40.dp, strokeColor = colors.onSurface, tint = colors.onSurface)
    }
}

/** 虚线圆圈 + 中心加号 — Canvas PathEffect.dashPathEffect 画圆环,onSurface 描边自动适配深浅 */
@Composable
private fun DashedCircleWithPlus(size: androidx.compose.ui.unit.Dp, strokeColor: Color, tint: Color) {
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 1.5.dp.toPx()
            val radius = (this.size.minDimension - stroke) / 2f
            drawCircle(
                color = strokeColor,
                radius = radius,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        intervals = floatArrayOf(6.dp.toPx(), 5.dp.toPx())
                    )
                )
            )
        }
        Icon(
            androidx.compose.material.icons.Icons.Outlined.Add,
            contentDescription = stringResource(R.string.theme_new),
            tint = tint,
            modifier = Modifier.size(size / 2)
        )
    }
}

/** 自定义主题卡 — 与 PresetThemeCard 完全同构(三色板+名称+选中对勾,大小一模一样)。
 *  edit 图标在色板行右端: 色块包裹(可点性可见) + 与第一行 3 色块同行对齐
 *  (2026-09-13 用户定稿: 裸图标贴主题名旁 = 不知道点哪, 24dp IconButton 嵌名称行
 *  还把卡片撑得比预设卡高) */
@Composable
private fun CustomThemeCard(
    theme: com.imsx3d.classy.data.CustomTheme,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    // 卡片色板预览按当前深浅模式派生(与 PresetThemeCard 的探针逻辑一致)
    val isDark = colors.background.red < 0.5f
    val scheme = remember(theme, isDark) { CustomSchemeDeriver.derive(theme, isDark) }
    val bgColor = if (selected) colors.primaryContainer else colors.surfaceContainer
    Surface(
        modifier = Modifier.fillMaxWidth().clip(SleepyTheme.shapes.large).noRippleClickable(onClick),
        color = bgColor, shape = SleepyTheme.shapes.large
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorSwatch(scheme.primary)
                ColorSwatch(scheme.secondary)
                ColorSwatch(scheme.tertiary)
                Spacer(Modifier.weight(1f))
                // edit 色块: surfaceContainerHighest 与卡片底色拉开层级; 28dp 与色板同高, 首行结构与 Preset 卡完全一致不撑高
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(SleepyTheme.shapes.small)
                        .background(colors.surfaceContainerHighest)
                        .noRippleClickable(onEdit),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.theme_custom_edit),
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    theme.name.ifBlank { stringResource(R.string.theme_new) },
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // 与预设卡相同:固定 20dp 槽位,避免选中态改变标题行高度。
                Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                    if (selected) {
                        Icon(
                            Icons.Outlined.Check,
                            stringResource(R.string.selected),
                            tint = colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}
