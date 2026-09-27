package com.imsx3d.classy.ui.screen.mine

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.DisplayModeOption
import com.imsx3d.classy.ui.component.SectionHeader
import com.imsx3d.classy.ui.component.SettingsGroupCard
import com.imsx3d.classy.ui.component.StatusBarScrim
import com.imsx3d.classy.ui.component.SettingsGroupFold
import com.imsx3d.classy.ui.component.SettingsGroupRow
import com.imsx3d.classy.ui.component.SettingsPageHeader
import com.imsx3d.classy.ui.component.SettingsRowDivider
import com.imsx3d.classy.ui.component.SettingsRowSegmented
import com.imsx3d.classy.ui.component.SettingsSwitchRow
import com.imsx3d.classy.ui.component.TimePickerField
import com.imsx3d.classy.ui.theme.SleepyTheme
import com.imsx3d.classy.ui.theme.noRippleClickable
import com.imsx3d.classy.util.AppPrefs
import com.imsx3d.classy.util.DateUtils
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.imsx3d.classy.ui.component.SettingsSegmentedRow

/**
 * 通用设置页(决策 D1 L1 ⑤): 课程显示 / 小组件 / 语言 三组。
 * 课程显示与小组件自 AppearanceScreen 迁入(2026-08-24, 用户指定), 语言卡沿用原折叠列表卡片样式。
 * 显示项变更后即时刷新小组件(refreshWidgets 管线随迁移一并保留)。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneralSettingsScreen(
    onBack: () -> Unit,
    onOpenHoliday: () -> Unit = {},
    onOpenWidgetManagement: () -> Unit = {},
    onOpenScheduleDisplay: () -> Unit = {},
    onOpenControlGallery: () -> Unit = {},
    navDock: Boolean = false,
    onNavDockChange: (Boolean) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    var language by remember { mutableStateOf(AppPrefs.getLanguage(context)) }

    val languages = listOf(
        "zh-CN" to "简体中文",
        "zh-TW" to "繁體中文",
        "en" to "English",
        "ja" to "日本語",
        "es" to "Español"
    )

    // 课程显示 / 小组件设置项状态
    // （UI-15a：主页显示 / 冲突样式 / 显示星期的状态已随内容搬到 ScheduleDisplayScreen）
    // 折叠展开态跨页保真: AppRoot 经 SaveableStateProvider 恢复本页时, 折叠卡展开集
    // 也按原样回来(Set 非内建 Bundle 类型, 用显式 Saver 转 ArrayList<String> 存取)。
    var expandedSections by rememberSaveable(
        stateSaver = Saver<Set<String>, ArrayList<String>>(
            save = { ArrayList(it) },
            restore = { it.toSet() }
        )
    ) { mutableStateOf(setOf<String>()) }
    fun toggleSection(key: String) {
        expandedSections = if (key in expandedSections) expandedSections - key else expandedSections + key
    }
    var displayMode by remember { mutableStateOf(AppPrefs.getDisplayMode(context)) }
    var gridSubInfo by remember { mutableStateOf(AppPrefs.getGridSubInfo(context)) }
    var autoHideEmptyEvening by remember { mutableStateOf(AppPrefs.isGridAutoHideEmptyEvening(context)) }
    var gridAdaptiveHeight by remember { mutableStateOf(AppPrefs.isGridAdaptiveHeight(context)) }
    // v1.0.56 T3: 双指捏放行高(实验室, 默认关)
    var gridPinchZoom by remember { mutableStateOf(AppPrefs.isGridPinchZoom(context)) }
    var nearestBusyDay by remember { mutableStateOf(AppPrefs.isNearestBusyDay(context)) }
    // v1.0.56 T4: 语言折叠卡展开态 — 默认收起; 选择语言即 recreate 重建, 会话态足够
    var languageExpanded by remember { mutableStateOf(false) }
    var eveningStart by remember { mutableStateOf(AppPrefs.getGridEveningStart(context)) }
    // issue#26: 周视图/网格视图场景别名开关
    var showDate by remember { mutableStateOf(AppPrefs.isShowDate(context)) }
    var startView by remember { mutableStateOf(AppPrefs.getStartView(context)) }
    var vertPunct by remember { mutableStateOf(AppPrefs.isVertPunctReplace(context)) }
    var widgetColorless by remember { mutableStateOf(AppPrefs.isWidgetColorless(context)) }
    var courseColorless by remember { mutableStateOf(AppPrefs.isCourseColorless(context)) }
    var widgetSeparator by remember { mutableStateOf(AppPrefs.isWidgetSeparator(context)) }

    // 显示项变更后立即刷小组件(管线自 AppearanceScreen 迁移保留)
    val widgetScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    fun refreshWidgets() {
        widgetScope.launch { com.imsx3d.classy.widget.WidgetUpdater.notifyDataChanged(context) }
    }

    // UI-20a：恢复默认设置（确认弹窗 + 重置动作）
    var showResetDialog by remember { mutableStateOf(false) }
    fun performReset() {
        AppPrefs.resetSettings(context)
        // 设置清空后要把"有副作用的东西"重新按默认值落地：
        // 通知按默认开关重排、小组件按默认外观重画、更新提醒缓存清空，最后重建 Activity 让
        // 主题/语言/底栏形态等一次性读取的值生效。
        runCatching { com.imsx3d.classy.SleepyApp.get().notificationScheduler.scheduleAll() }
        runCatching { com.imsx3d.classy.util.UpdateNotifier.clearCache() }
        widgetScope.launch { com.imsx3d.classy.widget.WidgetUpdater.notifyDataChanged(context) }
        android.widget.Toast.makeText(context, context.getString(R.string.settings_reset_done), android.widget.Toast.LENGTH_SHORT).show()
        (context as? android.app.Activity)?.recreate()
    }

    // UI-17a：整页包 Box，内容之后盖一条状态栏遮罩（否则滚动时正文与状态栏时钟重叠）
    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize().background(colors.background),
        containerColor = colors.background,
    ) { padding ->
        LazyColumn(
            // UI-7a: 页头换成大标题后，顶部不再需要 Scaffold 的 topBar padding —— 由页头自己吃状态栏
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // UI-7a（2026-09-27 用户令"设置类三页仍是 M3 结构"）：页头统一成「我的」页那套
            // 32sp 大标题 + 返回行 —— 与 M3 小标题顶栏混在一起时像两个 App。
            item {
                // UI-25a：同 SettingsScaffold —— 页头补 12dp 底边距，让"大字 → 首块"= 28dp；
                // 因此下面**第一个**分组标题的 topSpacing 用 0（见分组①）。
                Column(
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(bottom = 12.dp)
                ) {
                    SettingsPageHeader(
                        title = stringResource(R.string.mine_general),
                        onBack = onBack
                    )
                }
            }

            // ── 分组① 课表显示 (2026-09-21 改名: 与③「画面与导航」拉开边界) ──
            // UI-9a 试点：整组共用一张卡（Glasense inset-grouped）—— 组内 7 行原来是 7 张独立小卡，
            // 现在是一张卡 + 行间发丝线；"卡 = 一个话题"。组间距也从 36dp 收到 24dp（topSpacing=8 + 列表 16）。
            item {
                SectionHeader(
                    title = stringResource(R.string.appearance_section_schedule_display),
                    // UI-25a：本页第一个分组标题 → 0（页头已带 12dp 底边距）
                    topSpacing = 0.dp
                )
            }

            item {
                SettingsGroupCard {
                    // 课程时间显示: 节次 / 时间 — 二选一, 标题行右侧 tab 切换
                    SettingsSegmentedRow(
                        title = stringResource(R.string.settings_display_mode),
                        options = listOf(
                                stringResource(R.string.settings_display_node),
                                stringResource(R.string.settings_display_time)
                            ),
                        selectedKey = if (displayMode == "node") 0 else 1,
                        onSelect = { i ->
                                val v = if (i == 0) "node" else "time"
                                displayMode = v; AppPrefs.setDisplayMode(context, v); refreshWidgets()
                            },
                    )
                    SettingsRowDivider()

                    // 网格卡片副信息: 教室 / 教师 / 无 — 三选一（周视图网格卡课程名下方那行）
                    SettingsSegmentedRow(
                        title = stringResource(R.string.settings_grid_sub_info),
                        options = listOf(
                                stringResource(R.string.settings_grid_sub_room),
                                stringResource(R.string.settings_grid_sub_teacher),
                                stringResource(R.string.settings_grid_sub_none)
                            ),
                        selectedKey = when (gridSubInfo) {
                                "room" -> 0
                                "teacher" -> 1
                                else -> 2
                            },
                        onSelect = { i ->
                                val v = listOf("room", "teacher", "none")[i]
                                gridSubInfo = v; AppPrefs.setGridSubInfo(context, v); refreshWidgets()
                            },
                    )
                    SettingsRowDivider()

                    // UI-15a：三处多选项/滑杆（主页显示 8 项、冲突样式 3 选+3 滑杆、显示星期 7 开关）
                    // 原来就地折叠在这张卡里 → 一屏决策点过多，搬去「课表显示」二级页（入口行）。
                    SettingsGroupRow(
                        title = stringResource(R.string.schedule_display_more),
                        subtitle = stringResource(R.string.schedule_display_more_sub),
                        onClick = onOpenScheduleDisplay,
                        trailing = {
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    SettingsRowDivider()

                    // 节假日课程灰显: 点击进入二级页
                    SettingsGroupRow(
                        title = stringResource(R.string.settings_holiday_title),
                        onClick = onOpenHoliday,
                        trailing = {
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                    SettingsRowDivider()

                    // 课程胶囊统一底色: 仅标题 + 右侧开关（App 侧独立不刷新小组件）
                    // 2026-09-21 标签补主语「App 内」: 与②组「小组件统一课程底色」成对区分(基线 §10 开关命名带主语)。
                    // UI-10c（用户 2026-09-27 建议）：**开关行统一放本组最后一行** ——
                    // 开关本体 48dp 触摸目标会把该行撑高，夹在中间时同组行高参差、像"突然凸出来一行"；
                    // 放末尾后同类型控件连成一段，视觉节奏统一。
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_course_colorless),
                        checked = courseColorless,
                        onCheckedChange = { courseColorless = it; AppPrefs.setCourseColorless(context, it) }
                    )
                }
            }

            // ── 分组② 小组件 ──
            item {
                SectionHeader(
                    title = stringResource(R.string.appearance_section_widget),
                    topSpacing = 12.dp
                )
            }

            item {
                SettingsGroupCard {
                    // UI-9a：折叠行 + 入口行同一张卡
                    SettingsGroupFold(title = stringResource(R.string.settings_widget), expanded = "widget" in expandedSections, onToggle = { toggleSection("widget") }) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_widget_colorless),
                            subtitle = stringResource(R.string.settings_widget_colorless_sub),
                            checked = widgetColorless,
                            onCheckedChange = {
                                widgetColorless = it
                                AppPrefs.setWidgetColorless(context, it)
                                refreshWidgets()
                            }
                        )
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_widget_separator),
                            subtitle = stringResource(R.string.settings_widget_separator_sub),
                            checked = widgetSeparator,
                            onCheckedChange = {
                                widgetSeparator = it
                                AppPrefs.setWidgetSeparator(context, it)
                                refreshWidgets()
                            }
                        )
                        SettingsRowDivider()
                        SettingsSwitchRow(
                            title = stringResource(R.string.settings_nearest_busy_day),
                            subtitle = stringResource(R.string.settings_nearest_busy_day_sub),
                            checked = nearestBusyDay,
                            onCheckedChange = {
                                nearestBusyDay = it
                                AppPrefs.setNearestBusyDay(context, it)
                                refreshWidgets()
                            }
                        )
                        // 竖排标点优化已迁实验室组(2026-09-21): 实验功能唯一家门=「实验室」组
                        // 或带 [实验] 胶囊(提醒页流体云), 禁第三形态
                    }
                    SettingsRowDivider()

                    // 管理桌面小组件: 跳二级页列出已放置的小组件
                    SettingsGroupRow(
                        title = stringResource(R.string.widget_manage_entry),
                        onClick = onOpenWidgetManagement,
                        trailing = {
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }

            // ── 分组③ 画面与导航 ──
            item {
                SectionHeader(
                    title = stringResource(R.string.settings_section_display_navigation),
                    topSpacing = 12.dp
                )
            }

            item {
                SettingsGroupCard {
                    // UI-18a（用户令）：同卡内多枚分段胶囊要"短的在上面" ——
                    // 「底栏样式」(贴底/悬浮，4 字) 放在「启动默认页」(周视图/网格，5 字) 之上，
                    // 左边界自上而下单调左移，与分组①的排法一致（原顺序反了，卡中间会出现一个凹口）。
                    // 底栏样式: 贴底 / 悬浮药丸 Dock
                    // 状态由 MainActivity(AppRoot) 持有下传: 底栏与设置页同一真值, 切换即生效
                    SettingsSegmentedRow(
                        title = stringResource(R.string.settings_nav_style),
                        options = listOf(
                                stringResource(R.string.settings_nav_style_docked),
                                stringResource(R.string.settings_nav_style_floating)
                            ),
                        selectedKey = if (navDock) 1 else 0,
                        onSelect = { idx ->
                                val on = idx == 1
                                if (navDock != on) {
                                    AppPrefs.setNavDock(context, on)
                                    onNavDockChange(on)
                                }
                            },
                    )
                    SettingsRowDivider()

                    // 启动默认页: App 级启动偏好
                    SettingsSegmentedRow(
                        title = stringResource(R.string.settings_start_view),
                        options = listOf(
                                stringResource(R.string.settings_start_view_full),
                                stringResource(R.string.settings_start_view_cards)
                            ),
                        selectedKey = if (startView == "full") 0 else 1,
                        onSelect = { i ->
                                val v = if (i == 0) "full" else "cards"
                                startView = v; AppPrefs.setStartView(context, v)
                            },
                    )
                    SettingsRowDivider()

                    SettingsRowDivider()

                    // 课程色呈现：色条 / 填充（用户 2026-09-28 令「两版都保留，做成可选项」）
                    var colorStyle by remember { mutableStateOf(AppPrefs.getCourseColorStyle(context)) }
                    SettingsSegmentedRow(
                        title = stringResource(R.string.settings_course_style),
                        subtitle = stringResource(R.string.settings_course_style_sub),
                        options = listOf(
                            stringResource(R.string.settings_course_style_bar),
                            stringResource(R.string.settings_course_style_fill)
                        ),
                        selectedKey = if (colorStyle == AppPrefs.COURSE_STYLE_FILL) 1 else 0,
                        onSelect = { i ->
                            val v = if (i == 1) AppPrefs.COURSE_STYLE_FILL else AppPrefs.COURSE_STYLE_BAR
                            colorStyle = v
                            AppPrefs.setCourseColorStyle(context, v)
                        },
                    )
                    SettingsRowDivider()

                    // 高刷新率: 标题 + 开关（UI-10c：开关行移到本组末行，与「课表显示」组同一规则）
                    var highRefresh by remember { mutableStateOf(AppPrefs.isHighRefresh(context)) }
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_high_refresh),
                        checked = highRefresh,
                        onCheckedChange = { on ->
                            highRefresh = on
                            AppPrefs.setHighRefresh(context, on)
                            val activity = context as? android.app.Activity
                            if (activity == null) {
                                // 理论不可达 (本页只从 MainActivity 进入); 留日志防静默失败
                                Log.w("GeneralSettings", "high refresh toggle: context is not Activity, apply skipped")
                                return@SettingsSwitchRow
                            }
                            com.imsx3d.classy.util.HighRefreshRate.apply(activity, on)
                        }
                    )
                }
            }

            // ── 分组④ 语言 (v1.0.56 T4: 折叠卡 — 收起只显当前语言, 点开展开 5 项) ──
            item {
                SectionHeader(
                    title = stringResource(R.string.settings_language),
                    topSpacing = 12.dp
                )
            }

            item {
                // UI-9a：语言本来就是「一张卡 + 折叠」，只是换成统一的分组折叠行（标题显当前语言）
                val currentLabel = languages.firstOrNull { it.first == language }?.second ?: language
                SettingsGroupCard {
                    SettingsGroupFold(
                        title = currentLabel,
                        expanded = languageExpanded,
                        onToggle = { languageExpanded = !languageExpanded }
                    ) {
                        languages.forEach { (code, label) ->
                            val selected = language == code
                            Row(
                                modifier = Modifier.fillMaxWidth().noRippleClickable {
                                    language = code
                                    AppPrefs.setLanguage(context, code)
                                    (context as? android.app.Activity)?.recreate()
                                }.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = label, style = MaterialTheme.typography.bodyLarge, color = if (selected) colors.primary else colors.onSurface)
                                if (selected) Icon(Icons.Outlined.Check, null, tint = colors.primary, modifier = Modifier.size(20.dp))
                            }
                            if (code != languages.last().first) SettingsRowDivider()
                        }
                    }
                }
            }

            // ── 分组⑤ 实验室 (2026-09-16 用户令): 实验性功能默认全关, 可能随版本调整 ──
            item {
                SectionHeader(
                    title = stringResource(R.string.settings_lab),
                    topSpacing = 12.dp
                )
            }
            item {
                Text(
                    text = stringResource(R.string.settings_lab_sub),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item {
                SettingsGroupCard {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_grid_adaptive_height),
                        subtitle = stringResource(R.string.settings_grid_adaptive_height_sub),
                        checked = gridAdaptiveHeight,
                        onCheckedChange = {
                            gridAdaptiveHeight = it
                            AppPrefs.setGridAdaptiveHeight(context, it)
                        }
                    )
                    SettingsRowDivider()
                    // v1.0.56 T3: 双指捏放行高 — 默认关, 关=网格视图捏不动
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_grid_pinch_zoom),
                        subtitle = stringResource(R.string.settings_grid_pinch_zoom_sub),
                        checked = gridPinchZoom,
                        onCheckedChange = {
                            gridPinchZoom = it
                            AppPrefs.setGridPinchZoom(context, it)
                        }
                    )
                    SettingsRowDivider()
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_grid_auto_hide_evening),
                        subtitle = stringResource(R.string.settings_grid_auto_hide_evening_sub),
                        checked = autoHideEmptyEvening,
                        onCheckedChange = {
                            autoHideEmptyEvening = it
                            AppPrefs.setGridAutoHideEmptyEvening(context, it)
                        }
                    )
                    if (autoHideEmptyEvening) {
                        SettingsRowDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.settings_grid_evening_start),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            TimePickerField(
                                value = eveningStart,
                                onValueChange = {
                                    eveningStart = it
                                    AppPrefs.setGridEveningStart(context, it)
                                },
                                label = "",
                                modifier = Modifier.width(150.dp)
                            )
                        }
                    }
                    SettingsRowDivider()
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_vert_punct),
                        subtitle = stringResource(R.string.settings_vert_punct_sub),
                        checked = vertPunct,
                        onCheckedChange = {
                            vertPunct = it
                            AppPrefs.setVertPunctReplace(context, it)
                            refreshWidgets()
                        }
                    )
                }
            }

            // ── 分组⑤ 重置（UI-20a）：调乱了能退回来 ──
            item {
                SectionHeader(
                    title = stringResource(R.string.settings_reset_group),
                    topSpacing = 12.dp
                )
            }
            item {
                SettingsGroupCard {
                    SettingsGroupRow(
                        title = stringResource(R.string.settings_reset_all),
                        subtitle = stringResource(R.string.settings_reset_all_sub),
                        onClick = { showResetDialog = true },
                        titleColor = colors.error
                    )
                }
            }

            // UI-19b：控件预览（内部页）—— 定版前可整条删掉
            item {
                SectionHeader(title = "开发", topSpacing = 12.dp)
            }
            item {
                SettingsGroupCard {
                    SettingsGroupRow(
                        title = stringResource(R.string.control_gallery_entry),
                        subtitle = stringResource(R.string.control_gallery_entry_sub),
                        onClick = onOpenControlGallery,
                        trailing = {
                            Icon(
                                Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }
        }
    }
        StatusBarScrim(colors.background)
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.settings_reset_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_reset_confirm_body))
                    Spacer(Modifier.height(8.dp))
                    // UI-31d：破坏性动作 → destructive 色（C3 颜色管状态），按钮形态与其它弹窗一致
                    com.imsx3d.classy.ui.component.DialogActionButtons(
                        confirmText = stringResource(R.string.settings_reset_confirm_ok),
                        onConfirm = {
                            showResetDialog = false
                            performReset()
                        },
                        dismissText = stringResource(R.string.cancel),
                        onDismiss = { showResetDialog = false },
                        destructive = true
                    )
                }
            },
            // UI-31d：弹窗按钮统一走「色块按钮行」（DialogActionButtons），不再用裸 TextButton
            confirmButton = {},
            dismissButton = {}
        )
    }
}
