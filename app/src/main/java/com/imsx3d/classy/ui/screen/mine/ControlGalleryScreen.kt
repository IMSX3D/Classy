package com.imsx3d.classy.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.DisplayModeOption
import com.imsx3d.classy.ui.component.GlasenseProgressBar
import com.imsx3d.classy.ui.component.GlasenseRadio
import com.imsx3d.classy.ui.component.GlasenseSpinner
import com.imsx3d.classy.ui.component.SectionHeader
import com.imsx3d.classy.ui.component.SettingsGroupCard
import com.imsx3d.classy.ui.component.SettingsGroupFold
import com.imsx3d.classy.ui.component.SettingsGroupRow
import com.imsx3d.classy.ui.component.SettingsRowDivider
import com.imsx3d.classy.ui.component.SettingsRowSegmented
import com.imsx3d.classy.ui.component.SettingsScaffold
import com.imsx3d.classy.ui.component.SettingsSliderRow
import com.imsx3d.classy.ui.component.SettingsSwitchRow
import com.imsx3d.classy.ui.component.SettingsSegmentedRow

/**
 * 控件预览（UI-19b）—— 把《设计规范_控件体系》里已收口的控件**画在一屏里**，用来做两件事：
 *  ① 改控件时一眼比对（不必去各个页面找）；
 *  ② 给"动效/强调/度量"是否统一留个可对照的基准。
 *
 * 这是**内部页**：正式定版前可以随时删掉（删它 = 删本文件 + 导航里那一条 route + 通用页底部的入口行）。
 */
@Composable
fun ControlGalleryScreen(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var segmentedIndex by remember { mutableIntStateOf(0) }
    var switchOn by remember { mutableStateOf(true) }
    var sliderValue by remember { mutableFloatStateOf(0.4f) }
    var optionIndex by remember { mutableIntStateOf(1) }
    var foldExpanded by remember { mutableStateOf(true) }

    SettingsScaffold(title = stringResource(R.string.control_gallery_title), onBack = onBack) {
        // ── 行与卡片 ──
        item { SectionHeader(title = "行与卡片", topSpacing = 0.dp) }
        item {
            SettingsGroupCard {
                SettingsGroupRow(title = "普通行", subtitle = "副标题（12sp · 次要色）")
                SettingsRowDivider()
                SettingsGroupRow(
                    title = "入口行（值 + ›）",
                    subtitle = "整行可点",
                    onClick = {},
                    trailing = {
                        Icon(
                            Icons.Outlined.ChevronRight, null,
                            tint = colors.outline, modifier = Modifier.size(20.dp)
                        )
                    }
                )
                SettingsRowDivider()
                SettingsSwitchRow(
                    title = "开关行", subtitle = "开关锁 32dp · 整行可点",
                    checked = switchOn, onCheckedChange = { switchOn = it }
                )
                SettingsRowDivider()
                SettingsSegmentedRow(
                    title = "分段行",
                    options = listOf("节次", "时间"),
                    selectedKey = segmentedIndex,
                    onSelect = { segmentedIndex = it },
                )
                SettingsRowDivider()
                SettingsGroupFold(
                    title = "折叠行", expanded = foldExpanded, onToggle = { foldExpanded = !foldExpanded }
                ) {
                    SettingsGroupRow(title = "折叠里的行")
                }
            }
        }

        // ── 滑杆与滑杆行 ──
        item { SectionHeader(title = "滑杆（4dp 轨 + 20dp 圆环拇指）", topSpacing = 12.dp) }
        item {
            SettingsGroupCard {
                SettingsSliderRow(
                    title = "滑杆行", valueText = "${(sliderValue * 100).toInt()}%",
                    value = sliderValue, onValueChange = { sliderValue = it },
                    valueRange = 0f..1f,
                    subtitle = "值放上行右侧，轨道顶满卡片内边距"
                )
            }
        }

        // ── 选择类 ──
        item { SectionHeader(title = "选择类", topSpacing = 12.dp) }
        item {
            SettingsGroupCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("单选圆环", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                        // 内部页要能自答"这是什么、在哪用"（用户 2026-09-27 就问了这一行）
                        Text(
                            "未选中 / 选中两种态；用在：课程详情（冲突课表选顶置）、提醒字段选择",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant
                        )
                    }
                    GlasenseRadio(selected = false)
                    GlasenseRadio(selected = true)
                }
                SettingsRowDivider()
                // 真实可点的一对：点哪行哪行变选中（示范"选中 = 主色 + ✓"，不是静态贴图）
                DisplayModeOption(
                    label = "单选项行 A",
                    subtitle = if (optionIndex == 0) "当前选中" else "",
                    selected = optionIndex == 0,
                    onClick = { optionIndex = 0 }
                )
                SettingsRowDivider()
                DisplayModeOption(
                    label = "单选项行 B",
                    subtitle = if (optionIndex == 1) "当前选中" else "",
                    selected = optionIndex == 1,
                    onClick = { optionIndex = 1 }
                )
            }
        }

        // ── 进度与反馈 ──
        item { SectionHeader(title = "进度与反馈", topSpacing = 12.dp) }
        item {
            SettingsGroupCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("进度条 30%", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                    GlasenseProgressBar(progress = 0.3f)
                    Text("进度条 70%", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                    GlasenseProgressBar(progress = 0.7f)
                    Text("加载圈（2dp 描边 · 100° 弧）", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        GlasenseSpinner()
                        GlasenseSpinner(size = 28.dp, strokeWidth = 3.dp)
                    }
                }
            }
        }

        // ── 文字（6 档字号 × 2 档字重）──
        item { SectionHeader(title = "文字 6 档 × 字重 2 档", topSpacing = 12.dp) }
        item {
            SettingsGroupCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("32 · 页面大标题", style = MaterialTheme.typography.headlineLarge, color = colors.onSurface)
                    Text("20 · 弹窗/卡片标题", style = MaterialTheme.typography.headlineSmall, color = colors.onSurface)
                    Text("16 · 行标题与正文", style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                    Text("16 · 当前值（SemiBold）", style = MaterialTheme.typography.titleMedium, color = colors.primary)
                    Text("14 · 密排行标题", style = MaterialTheme.typography.titleSmall, color = colors.onSurface)
                    Text("14 · 按钮文字（SemiBold）", style = MaterialTheme.typography.labelLarge, color = colors.primary)
                    Text("12 · 副标题与说明", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text("11 · 微标（角标/日期范围）", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                    Text(
                        "字重只有 Normal / SemiBold 两档",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // ── 色板（主题强调色 + 灰阶）──
        item { SectionHeader(title = "颜色分工：主色管「可点/选中」，灰阶管层级", topSpacing = 12.dp) }
        item {
            SettingsGroupCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 标签用两字中文：英文 token 名在这个宽度里会被截成两行（上一版实测）
                    listOf(
                        colors.primary to "主色",
                        colors.onSurface to "正文",
                        colors.onSurfaceVariant to "次要",
                        colors.outline to "描边",
                        colors.surfaceVariant to "容器",
                        colors.surfaceContainer to "卡片"
                    ).forEach { (c, name) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(c)
                            )
                            Text(name, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
