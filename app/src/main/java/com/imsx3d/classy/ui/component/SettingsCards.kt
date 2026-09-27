package com.imsx3d.classy.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.ui.theme.SleepyTheme
import com.imsx3d.classy.ui.theme.noRippleClickable
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.foundation.layout.BoxWithConstraints

/**
 * [intentional custom] 官方 M3 无「分组标题+折叠卡+单选行+开关行」组合件;
 * 本文件 = Sleepy 设置页领域封装, 内部全部消费官方组件(Switch/Icon/AnimatedVisibility),
 * 颜色/形状/字体全部走 MaterialTheme 官方 token。
 *
 * 设置页公共卡片组件 — 自 AppearanceScreen 抽出(外观/通用两页共用):
 * SectionHeader 分组标题 / SettingsGroupCard 分组卡 / SettingsGroupRow 标准行 / DisplayModeOption 单选项。
 * UI-28a：上游那套「一项一张卡」的 SettingsCard(折叠) / SettingsFlatCard(平铺) 与薄壳 SettingToggleRow 已全部删除
 * —— 全库只剩 SettingsGroupCard + Modifier.settingsCard 两种卡片外壳（C10）。
 */

/**
 * 分组标题 —— UI-7a（2026-09-27）：对齐 Glasense 的 inset-grouped 度量
 * （`ListStack.renderSectionHeader`）：**次级小字 + 内容次要色 + 段前 20dp + 左内缩 12dp + 距卡 8dp**。
 * 原来用 titleSmall/onBackground（跟正文一个重量）→ 分组感弱、和「我的」页不像一个 App。
 */
@Composable
fun SectionHeader(title: String, subtitle: String? = null, topSpacing: Dp = 20.dp) {
    val colors = MaterialTheme.colorScheme
    // UI-24a：左缩 12dp → 0（跟随列表的 16dp 页内缩）—— 让**分组标题与页面大标题、卡片左边缘同一条线**。
    // 原来是 16+12=28dp，看起来"小字比大字往右歪"，用户 2026-09-27 报障。
    Column(Modifier.fillMaxWidth().padding(start = 0.dp, top = topSpacing, bottom = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

/**
 * 设置子页页头（UI-7a）——与「我的」页同一套大标题语言：
 * 返回箭头独占一行（子页需要返回），下面 32sp 大标题 + 可选副标题。
 * 为什么不用 M3 `TopAppBar`：那是"小标题 + 返回"的安卓式顶栏，和「我的」页 32sp 大标题
 * 摆在一起像两个 App —— 用户报「设置类三页仍是 M3 结构」就是这个观感差。
 *
 * UI-7c：`actions` = 页级动作键（如作息表编辑页的 分享/复制），排在**「返回」同一行的右端**，
 * 这是 Glasense/iOS 的导航行惯例（M3 顶栏则是标题右侧，只有大标题页头没地方放标题）。
 */
@Composable
fun SettingsPageHeader(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val g = com.nevoit.glasense.theme.GlasenseTheme
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // UI-24a：底部 4dp → 0 —— 大字下方那点余量转交给"标题→首块"的统一间距（见 SettingsScaffold），
            // 否则会叠成 32dp（用户报"大字下面留白过多"）。
            .padding(top = 4.dp, bottom = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .clip(SleepyTheme.shapes.medium)
                    .noRippleClickable(onClick = onBack)
                    // UI-24a：左右 4dp → 0 —— 返回箭头与大标题、分组标题同一条左线（16dp）
                    .padding(horizontal = 0.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(com.imsx3d.classy.R.string.back),
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(2.dp))
                Text(
                    text = stringResource(com.imsx3d.classy.R.string.back),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant
                )
            }
            Spacer(Modifier.weight(1f))
            actions()
        }
        // UI-24a：6dp → 14dp —— 原来返回与大标题贴得太近（实测 ink 间距仅 13.7dp），
        // 大字需要自己的上方空间，"上下不平衡"的观感主要来自这里。
        Spacer(Modifier.height(14.dp))
        Text(text = title, style = g.type.largeTitleEmphasized, color = g.colors.content)
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(text = subtitle, style = g.type.footnote, color = g.colors.contentVariant)
        }
    }
}

/**
 * 设置类子页统一骨架（UI-7b）——把 UI-7a 在「通用」页定下的那套
 * 「Scaffold(纯底色) + 状态栏内缩的大标题页头 + LazyColumn(左右 16dp)」收成一处。
 *
 * 迁移一页 = 把 `Scaffold(topBar = { TopAppBar(...) }) { padding -> LazyColumn(...) { 正文 } }`
 * 换成 `SettingsScaffold(title, onBack) { 正文 }`，正文的 item{} 原样不动。
 *
 * 页头自己吃 `statusBars` 内边距（大标题顶到状态栏下沿），所以这里不再传 top padding；
 * 底部仍保留 Scaffold 的 bottom padding（手势条），避免最后一行被导航条压住。
 *
 * UI-7c 补的四个口子（都是真页面的实际需求，不是预留）：
 *  · `actions`     —— 页级动作键，转交页头放"返回"行右端（作息表编辑页的 分享/复制）
 *  · `bottomCTA`   —— 常驻底部主按钮（作息表列表页的「新建作息表」）。走 Scaffold 的 bottomBar
 *                     而不是塞在列表末尾，位置语义（永远在手边）不变；内部补 `navigationBarsPadding`，
 *                     与它当初在 `innerPadding` 里时一样抬在手势条之上。
 *  · `containerColor` —— 整页底色（「关于」页有新版时整页轻刷主题色）
 *  · `horizontalPadding` —— 左右内缩，给自带 20dp 边距的页面（「关于」）留 0
 */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    verticalSpacing: Dp = 16.dp,
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    bottomCTA: (@Composable () -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.background,
    horizontalPadding: Dp = 16.dp,
    content: LazyListScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    // UI-17a：整页包一层 Box，内容之后盖一条状态栏遮罩（页头滚走后正文不再和时钟重叠）
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize().background(containerColor),
        containerColor = containerColor,
        snackbarHost = snackbarHost,
        bottomBar = {
            if (bottomCTA != null) {
                // 手势条内边距由这里补：Scaffold 不会给 bottomBar 加 insets，
                // 而它当初在 innerPadding 里时本来就是抬在导航条之上的。
                Column(modifier = Modifier.navigationBarsPadding()) { bottomCTA() }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(start = horizontalPadding, end = horizontalPadding, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(verticalSpacing)
        ) {
            item {
                // UI-25a：页头补 12dp 底边距 —— 于是"大字 → 第一个区块"= 12 + 16(列表间距) = **28dp**，
                // 与"首块是分组标题"的页面（16 + 12）完全一致。原来没有这段时，
                // 首块直接是卡片的 13 个页面只有 16dp，比大部队紧一截（用户报"没呼吸感"）。
                // 首块是分组标题的页面请把**第一个** SectionHeader 的 topSpacing 设为 0（否则叠成 40dp）。
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(bottom = 12.dp)
                ) {
                    SettingsPageHeader(title = title, onBack = onBack, subtitle = subtitle, actions = actions)
                }
            }
            content()
        }
    }
        StatusBarScrim(containerColor)
    }
}

/**
 * UI-10a 留白实验开关：组内每行的最小高度。
 * 48 = 紧凑 / 52 = 标准（Glasense `DefaultRowMinHeight`）/ 56 = 宽松。
 * 只影响 `SettingsGroupRow` 与 `SettingsGroupFold`（目前只有「通用」页在用），
 * 所以改这一个数就能出对比版；定档后这里就是全局值。
 */
private val GroupRowMinHeight = 48.dp

/**
 * 分组行版式（UI-9a 试点，Glasense inset-grouped）——和上游「一项一张卡」的区别：
 * **卡 = 一个话题**，同组若干行共用一张卡，行间用 [SettingsRowDivider]（发丝线、左缩 16dp）分隔，
 * 最后一行后面不画线；行高按 Glasense `DefaultRowMinHeight` = 52dp。
 *
 * 为什么另起一套而不是改 `SettingsCard`：上游那套（每项一卡/可折叠）在别的页面还在用，
 * 试点要能单独回滚 —— 组件并存，页面各自选。
 */
@Composable
fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().settingsCard(MaterialTheme.colorScheme.surfaceContainer),
        content = content
    )
}

/**
 * 卡片外壳（UI-28a）—— [SettingsGroupCard] 的 **Modifier 形态**。
 *
 * 什么时候用它而不是 `SettingsGroupCard`：卡内**不是一串标准行**，而是页面自己排布的内容
 * （年份步进器、数据源行、调休说明 + 分段控件、各种子卡……）。这类卡的内边距与结构由页面决定，
 * 但"圆角 + 卡面底色"必须只有一处定义 —— 页面不许再自己写 `clip(...) + background(...)`（C10）。
 * 内边距接着写在调用处：`Modifier.settingsCard(colors.surfaceContainer).padding(...)`。
 */
@Composable
fun Modifier.settingsCard(
    containerColor: Color,
    shape: CornerBasedShape = SleepyTheme.shapes.large
): Modifier = this.clip(shape).background(containerColor)

/**
 * 组内标准行：左标题（+可选副标题），右侧 `trailing`（分段控件 / 开关 / 值 / 箭头）。
 * `onClick` 非空时整行可点（与右侧控件各自独立，控件自己吃自己的点击）。
 * `leading`（UI-13a）：标题左侧的圆形头像/图标槽，只在「关于」页这类整卡都是头像行的地方用
 * —— 它会撑高整行（48dp 头像 → 约 60dp），混进纯文字卡就会破坏行高统一。
 * `titleBadge`（UI-26a）：标题后缀小胶囊（如「实验」），只在语义上确实需要给标题加限定词时用；
 * 原来是提醒页自己画的第二份实现，收进组件后全库只有一份。
 */
@Composable
fun SettingsGroupRow(
    title: String,
    subtitle: String? = null,
    titleBadge: String? = null,
    onClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    // UI-20a：破坏性动作行（如「恢复默认设置」）用 `error` 色标题 —— 符合 C3「颜色管状态」
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.noRippleClickable(onClick = onClick) else Modifier)
            .heightIn(min = GroupRowMinHeight)
            // UI-10c: 上下 6dp —— 48dp 行高 - 36dp 分段控件 = 12dp，正好两侧各 6dp；
            // 文字行（24dp 行高）与开关行（32dp 本体）也都被 minHeight 兜成同一个 48dp。
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        // UI-9a: 间距 12→8dp —— 三段 tab 的行（「网格卡片副信息」）原来标题差几个像素就换行
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        leading?.invoke()
        Column(modifier = Modifier.weight(1f)) {
            if (titleBadge == null) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = titleColor,
                        // fill=false：标题按自身宽度占位，胶囊紧跟在文字后面而不是被推到行尾
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = titleBadge,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(colors.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (subtitle != null) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        trailing?.invoke(this)
    }
}

/**
 * 组内"开关行"（UI-10c）—— 开关行的统一写法，收掉三个坑：
 * ① **整行可点**：开关本体被锁到 32dp（见下），触摸目标就只剩 32dp < 48dp 无障碍下限，
 *    所以整行接管点击（`onClick` 与开关自身都触发同一个 `onCheckedChange`）。
 * ② **开关锁 32dp**：Material3 `Switch` 默认最小触摸目标 48dp，会把这一行撑到 68dp ——
 *    同一张卡里就会出现"中间某一行特别高"的突兀感（用户 2026-09-27 截图报障）。
 *    锁回本体高度后，开关行与文字行/分段行统一为 48dp。
 * ③ **配色统一**：开关的选中轨道色在这里集中指定，页面不再各写一份。
 */
@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String? = null,
    titleBadge: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = MaterialTheme.colorScheme
    SettingsGroupRow(
        title = title,
        subtitle = subtitle,
        titleBadge = titleBadge,
        onClick = { onCheckedChange(!checked) },
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = colors.onPrimary,
                    checkedTrackColor = colors.primary
                ),
                modifier = Modifier.heightIn(max = 32.dp)
            )
        }
    )
}

/**
 * 组内折叠行：标题行（右端箭头随展开旋转）+ 展开内容，**都在同一张组卡里**。
 * 与上游 `SettingsCard` 的区别只是"没有自己的卡片外壳" —— 折叠语义、动画完全一致。
 */
@Composable
fun SettingsGroupFold(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = sleepyIndicatorSpec(),
        label = "group-fold-arrow"
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .noRippleClickable(onClick = onToggle)
                .heightIn(min = GroupRowMinHeight)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(rotation)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = sleepyExpandEnter(),
            exit = sleepyExpandExit()
        ) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) { content() }
        }
    }
}

/** 卡内行间发丝分隔线（UI-7a）：左内缩 16dp，与行内文字起点对齐（Glasense/iOS 的分隔线语言）。 */
@Composable
fun SettingsRowDivider() {
    val colors = MaterialTheme.colorScheme
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = androidx.compose.ui.unit.Dp.Hairline,
        color = colors.outlineVariant.copy(alpha = SleepyTheme.Alpha.hairline)
    )
}

/**
 * 卡内脚注行（UI-26a）—— 紧贴在**上方那一行**下面，用一行小字说明这条设置的实际效果
 * （如「明日预告」下面写"实际会推送什么"）。不是独立设置项：不可点、不画自己的分隔线，
 * 所以调用方要在它**之后**（而不是之前）画 [SettingsRowDivider]。
 *
 * `topPadding` 给「首行就是说明」的卡用（如课表显示页的「显示星期」说明），
 * 那种情况下卡的上沿需要自己补出内边距。
 */
@Composable
fun SettingsNoteRow(text: String, topPadding: Dp = 0.dp, bottomPadding: Dp = 12.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = topPadding, bottom = bottomPadding)
    )
}

/**
 * 分段控件的**期望宽度**（UI-33a 抽出）—— 与 [SettingsRowSegmented] 用同一份公式，
 * 让"行"能在排布前就知道控件要多宽，从而决定是并排还是上下排。
 *
 * 公式：n × (最宽标签实测宽 + 段内边距 26dp) + 容器内边距 8dp。
 * UI-9a 记录：段内边距原来 16dp，三段的行会把标题挤到换行，收 4dp/段后单行放下。
 */
@Composable
fun segmentedTabWidth(options: List<String>): Dp {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val maxLabelPx = options.maxOf { textMeasurer.measure(AnnotatedString(it), labelStyle).size.width }
    return with(density) {
        ((maxLabelPx + 26.dp.toPx()) * options.size + 8.dp.toPx()).toDp()
    }
}

/**
 * 「标题 + 分段控件」行（UI-33a）—— 全库这种行的**唯一写法**，取代手写
 * `SettingsGroupRow(trailing = { SettingsRowSegmented(...) })`。
 *
 * 为什么要有它（用户 2026-09-28 报「某些语言下组件被过度竖直拉长」）：
 * 原来行是「标题 weight(1f) + 控件定宽」，两者相加超出行宽时，标题只剩几十 dp ——
 * 日语「下部バーのスタイル」配「フローティング」控件时被压成**一字一行**，行高撑到 8 行。
 * 现在由**组件自己**先量：标题实测宽 + 控件期望宽 > 可用宽 ⇒ 自动改成**上下两行**
 * （标题一行、控件另起一行吃满宽度）—— 与语言无关，译文再长也不会压扁标题。
 */
@Composable
fun SettingsSegmentedRow(
    title: String,
    options: List<String>,
    selectedKey: Int,
    onSelect: (Int) -> Unit,
    subtitle: String? = null,
    height: Dp = 36.dp
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val titleWidth = with(density) {
        textMeasurer.measure(AnnotatedString(title), MaterialTheme.typography.bodyLarge).size.width.toDp()
    }
    val controlWidth = segmentedTabWidth(options)
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // 行内左右各 16dp；标题与控件之间留 8dp
        val available = maxWidth - 32.dp
        if (titleWidth + 8.dp + controlWidth > available) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                SettingsGroupRow(title = title, subtitle = subtitle)
                Spacer(Modifier.height(6.dp))
                SettingsRowSegmented(
                    options = options, selectedKey = selectedKey, onSelect = onSelect,
                    height = height, modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            SettingsGroupRow(
                title = title, subtitle = subtitle,
                trailing = {
                    SettingsRowSegmented(
                        options = options, selectedKey = selectedKey, onSelect = onSelect, height = height
                    )
                }
            )
        }
    }
}

/**
 * 行右侧的定宽分段控件 —— 宽度 = n × 最宽段文字 + 段内边距 16dp×2 + 容器内边距 4dp×2。
 * 禁用 `IntrinsicSize.Min`：CJK 的 minIntrinsicWidth 是单字宽，配 `weight(1f)` 会把每段压到
 * 一个汉字宽导致全部换行 —— 必须用 `TextMeasurer` 实测宽度。
 * `modifier` 非空时改为吃满传入宽度（给「标题 + 分段控件同行」之外的整宽场景用）。
 */
@Composable
fun SettingsRowSegmented(
    options: List<String>,
    selectedKey: Int,
    onSelect: (Int) -> Unit,
    height: Dp = 36.dp,
    modifier: Modifier? = null
) {
    val tabWidth = segmentedTabWidth(options)
    SegmentedSwitcher(
        options = options.mapIndexed { i, label -> i to label },
        selected = selectedKey,
        onSelect = onSelect,
        // 高度 36dp: 介于开关本体(32)与组件默认(42)之间 — 轨道不挤, 行高仍与开关行一致
        modifier = (modifier ?: Modifier.width(tabWidth)).height(height),
        // UI-3c: 卡内轨道同样用淡遮罩（卡面之上仍可见）
        containerColor = com.nevoit.glasense.theme.GlasenseTheme.colors.scrimNormal
    )
}

@Composable
fun DisplayModeOption(label: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // UI-16a：左右内边距 4dp → 16dp、上下 10dp → 6dp —— 与 SettingsGroupRow/开关行/滑杆行统一为
    // 「卡内 16dp + 行高 48dp」。旧值会让同一张卡里的单选项比其它行的文字左移 12dp（页面上肉眼可见的错位）。
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).noRippleClickable(onClick).padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, color = if (selected) colors.primary else colors.onSurface)
            if (subtitle.isNotEmpty()) {
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
        if (selected) Icon(Icons.Outlined.Check, null, tint = colors.primary, modifier = Modifier.size(20.dp))
    }
}

/**
 * 图标动作键（UI-31c）—— 全库唯一的"点一下图标的按钮"。
 *
 * 为什么要有它：改造前 25 处直接用 M3 `IconButton`，图标尺寸散在 18/20/22/24、容器 28/32/36/40 ——
 * 同一个 App 里"编辑 / 刷新 / 关闭 / 删除"四种图标大小并存，正是 C1/C2 要收的东西。
 * 两档度量（对齐 `SleepyTheme.Buttons` 的两档高度）：
 *   · Regular（默认）：容器 40dp、图标 20dp —— 页头 / 卡片头的主要动作
 *   · `compact = true`：容器 32dp、图标 18dp —— 列表行内、字段尾部的次要动作
 * 按压反馈仍由 M3 提供（C4 口径：按钮类保留反馈）；这里只钉死度量与着色，
 * 触摸目标由 M3 的 minimumInteractiveComponentSize 兜到 48dp。
 */
@Composable
fun GlasenseIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    compact: Boolean = false
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(if (compact) 32.dp else 40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.38f),
            modifier = Modifier.size(if (compact) 18.dp else 20.dp)
        )
    }
}

/**
 * 页级按钮（UI-31c）—— 把"`fillMaxWidth().height(SleepyTheme.Buttons.regularHeight) + Buttons.shape`"
 * 这套在 13 处重复的写法收成一处；样式仍是叠色块按钮（M3 FilledTonalButton 的分层底色）。
 * 需要页面主 CTA 时传 `cta = true`（56dp 档）。
 */
@Composable
fun GlasenseButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    cta: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    // 两种配色：默认 tonal（次级动作）；primary = 实心主色（页面主 CTA，如「保存课程」）
    val colors = if (primary) com.imsx3d.classy.ui.theme.primaryFilledButtonColors()
    else androidx.compose.material3.ButtonDefaults.filledTonalButtonColors()
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = modifier
            .fillMaxWidth()
            .height(if (cta) SleepyTheme.Buttons.ctaHeight else SleepyTheme.Buttons.regularHeight),
        shape = SleepyTheme.Buttons.shape
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            Spacer(Modifier.width(8.dp))
        }
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}

/**
 * 全 App 统一的滑杆（UI-16a）—— 取代 Material3 `Slider` 的默认外观。
 *
 * 为什么要换：M3 的表达式滑杆是「厚轨 + 高胶囊拇指 + 轨道末端停止点 + 拇指两侧留缝」，
 * 单根看着还行，一页排 6 根就是满屏噪音（用户 2026-09-27 报"过于张扬刺眼，和简约克制不搭边"）。
 *
 * 我们的版本（参照 OriginOS「声音与振动」的收法）：
 *  · 轨道 **4dp 圆角**，未选中 `surfaceVariant`、选中 `primary` —— 只有颜色变化，没有粗细变化；
 *  · 拇指 = **20dp 圆环**（卡片底色填充 + 2dp 主色描边），不放大、不发光、不带阴影；
 *  · **无停止点、无拇指留缝、无数值气泡**；数值交给行标签那一行的右侧文字（[SettingsSliderRow]）。
 *
 * 交互（拖动、点击、无障碍语义、48dp 触摸目标）仍由 M3 `Slider` 承担 —— 只换外观，不重写手势。
 */
@Composable
fun GlasenseSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val trackHeight = 4.dp
    // 说明：本版 M3 只有 **state 版**重载带 `thumb`/`track` 插槽（float 版没有），所以这里用 SliderState，
    // 并在外部值变化时把状态同步回来（拖动中 onValueChange 已在改外部值，不会打架）。
    val state = remember(valueRange) { SliderState(value = value, trackRange = valueRange) }
    LaunchedEffect(value) { if (state.value != value) state.value = value }
    Slider(
        state = state,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier,
        // 自定义拇指：20dp 圆环；外面那圈 24dp 只是让触摸目标与轨道留一点余量
        thumb = {
            Box(modifier = Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .border(2.dp, colors.primary, CircleShape)
                )
            }
        },
        // 自定义轨道：自己画，绕开 M3 的厚度与停止点
        track = { st ->
            val span = st.trackRange.endInclusive - st.trackRange.start
            val fraction = if (span <= 0f) 0f
            else ((st.value - st.trackRange.start) / span).coerceIn(0f, 1f)
            Canvas(modifier = Modifier.fillMaxWidth().height(trackHeight)) {
                val radius = CornerRadius(size.height / 2f, size.height / 2f)
                drawRoundRect(
                    color = colors.surfaceVariant,
                    size = Size(size.width, size.height),
                    cornerRadius = radius
                )
                drawRoundRect(
                    color = colors.primary,
                    size = Size(size.width * fraction, size.height),
                    cornerRadius = radius
                )
            }
        }
    )
}

/**
 * 设置页标准「滑杆行」：上行 `标题（+可选副标题）……当前值`，下行整宽滑杆。
 *
 * 为什么值放上行右侧而不是滑杆旁边：滑杆旁边放个定宽数值框，会把轨道挤短半截，
 * 而轨道长度本身就是"这个量能调多少"的直观信息；值放到标题行右侧后，
 * 轨道可以顶到卡片左右内边距（与其它行的文字对齐），一页排下来左边界完全一致。
 */
@Composable
fun SettingsSliderRow(
    title: String,
    valueText: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    subtitle: String? = null,
    onValueChangeFinished: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, color = colors.onSurface)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = colors.primary
            )
        }
        GlasenseSlider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished
        )
    }
}

/**
 * 状态栏遮罩（UI-17a）—— 页面内容滚动时不再从时钟底下穿过去。
 *
 * 我们所有列表页都是「大标题页头随内容滚走」的写法，页头滚走之后**没有任何东西挡住状态栏**，
 * 于是正文（分组标题、卡片文字）会和状态栏的时间/信号叠在一起（用户 2026-09-27 截图报障：
 * "部分页面的文字显示不全"）。最省且最克制的解法：在页面最上层盖一条**与页面同色**的矩形，
 * 高度 = 状态栏内边距 —— 内容滚到那里就"滑出去"，不再与系统 UI 打架。
 *
 * 用法：页面根节点是 Box 时，在内容之后调用 `StatusBarScrim(底色)`（本函数是 BoxScope 扩展）。
 */
@Composable
fun BoxScope.StatusBarScrim(containerColor: Color) {
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(statusBarHeight)
            .background(containerColor)
    )
}

/**
 * 统一单选圆环（UI-19a）—— 取代 Material3 `RadioButton` 的默认外观。
 *
 * 为什么换：M3 的单选是"大圆环 + 实心圆点"，一列排下来和我们的滑杆拇指圆环不是一族；
 * 这里改成**与 `GlasenseSlider` 拇指同族**的 18dp 圆环（2dp 描边），选中 = 主色环 + 中心实心点。
 * 只负责"长什么样"，选中逻辑与语义仍由外层的行（`selectable(role = Role.RadioButton)`）承担。
 */
@Composable
fun GlasenseRadio(selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) colors.primary else colors.outline, CircleShape)
        )
        if (selected) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(colors.primary))
        }
    }
}

/**
 * 统一进度条（UI-19a）—— 取代 Material3 `LinearProgressIndicator` 的表达式外观
 * （默认带"轨道缺口 + 末端停止点"，与滑杆那批同样的问题）。
 * 外观：4dp 圆角轨（`surfaceVariant`）+ 主色填充，无缺口、无圆点。
 */
@Composable
fun GlasenseProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    thickness: Dp = 4.dp
) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier = modifier.fillMaxWidth().height(thickness)) {
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(color = colors.surfaceVariant, size = size, cornerRadius = radius)
        drawRoundRect(
            color = colors.primary,
            size = Size(size.width * progress.coerceIn(0f, 1f), size.height),
            cornerRadius = radius
        )
    }
}

/**
 * 统一加载圈（UI-19a）—— 取代 Material3 `CircularProgressIndicator`
 * （它的表达式版本同样有缺口与停止点）。这里是自己画的单色弧：2dp 描边、固定 100° 弧长、匀速旋转，
 * 只表达"在处理"，不吸引眼球。
 */
@Composable
fun GlasenseSpinner(modifier: Modifier = Modifier, size: Dp = 18.dp, strokeWidth: Dp = 2.dp) {
    val colors = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "glasense-spinner")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = SleepySpinMillis, easing = LinearEasing)),
        label = "glasense-spinner-angle"
    )
    Canvas(modifier = modifier.size(size)) {
        val stroke = strokeWidth.toPx()
        drawArc(
            color = colors.primary,
            startAngle = angle,
            sweepAngle = 100f,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(this.size.width - stroke, this.size.height - stroke)
        )
    }
}
