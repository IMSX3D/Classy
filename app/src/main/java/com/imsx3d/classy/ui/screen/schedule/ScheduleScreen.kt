package com.imsx3d.classy.ui.screen.schedule

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected

import com.imsx3d.classy.ui.component.CourseCompletionProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imsx3d.classy.R
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import com.imsx3d.classy.ui.component.CardsGridView
import com.imsx3d.classy.ui.component.CourseDetailSheet
import com.imsx3d.classy.ui.component.FullWeekView
import com.imsx3d.classy.ui.component.SectionHead
import com.imsx3d.classy.ui.component.SegmentedSwitcher
import com.imsx3d.classy.ui.component.ShareScheduleSheet
import com.imsx3d.classy.ui.theme.SleepyTheme
import com.imsx3d.classy.ui.theme.noRippleClickable
import com.imsx3d.classy.ui.theme.primaryFilledButtonColors
import com.imsx3d.classy.ui.theme.selectedSurfaceColors
import com.imsx3d.classy.util.AppPrefs
import com.imsx3d.classy.util.DateUtils
import com.imsx3d.classy.util.HolidayManager
import com.imsx3d.classy.util.TimeTableUtils
import com.imsx3d.classy.util.WeekDisplayContext
import com.imsx3d.classy.util.WeekDisplayResolver
import com.imsx3d.classy.util.WeekDisplayStatus
import java.time.LocalDate
import com.imsx3d.classy.ui.component.settingsCard
import kotlinx.coroutines.launch
import com.imsx3d.classy.ui.component.GlasenseButton

// 非 private: MainActivity(AppRoot 会话层)需以本类型注入 viewMode —
// 会话内切视图/编辑课程 overlay 往返/切 tab 往返都不丢(启动默认仍由 AppRoot 初始化时读 AppPrefs)。
enum class ViewMode(val labelRes: Int) {
    Full(R.string.view_full),
    Cards(R.string.view_cards)
}

@Composable
fun ScheduleScreen(
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    onGoImport: () -> Unit = {},
    onManualAdd: () -> Unit = {},
    onQuickAdd: (com.imsx3d.classy.util.CourseGridPrefill) -> Unit = {},
    onCreateTable: () -> Unit = {},
    onEditCourse: (CourseEntity) -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var gestureActive by remember { mutableStateOf(false) }
    var moveRequest by remember(state.selectedTableId, state.selectedWeek, viewMode) {
        mutableStateOf<com.imsx3d.classy.ui.component.CourseMoveRequest?>(null)
    }
    moveRequest?.let { request ->
        com.imsx3d.classy.ui.component.CourseMoveDialog(request, onDismiss = { moveRequest = null })
    }
    var selectedCourse by remember { mutableStateOf<CourseEntity?>(null) }
    // v7.10.5 会话级置顶 override — 网格 onPickTop 与详情弹窗 radio 共用真相源。
    // radio 点击 → 这里瞬时换层(同帧) + AppPrefs 持久化(跨会话),两条通道一次写齐。
    var topOverrides by remember { mutableStateOf(mapOf<String, Long>()) }
    fun setTopOverride(key: String, courseId: Long?) {
        topOverrides = if (courseId == null) topOverrides - key else topOverrides + (key to courseId)
    }
    // v7.10.16r 轮换态(issue#10, 评审 #1): 簇键 → 轮换步数,与 topOverrides 同级持有 —
    // HorizontalPager 翻页/周切换不丢;纯会话级不落盘,离开课表页即重置。
    var rotationSteps by remember { mutableStateOf(mapOf<String, Int>()) }
    val displayMode = remember { AppPrefs.getDisplayMode(context) }
    // UI-5c（2026-09-27 用户令）：表头**恒显日期**，不再区分是否本周。
    // 演进史：UI-5b 曾按"翻到非本周才带日期"做上下文自动；用户实测后认为"一直带着也不乱"，
    // 于是简化为恒显 —— 少一个分支、少一处状态依赖，也顺手让「显示日期」这个设置失去意义
    // （小组件真实表头本来就是恒显；该开关此前只影响这里和一条已废弃的位图渲染路径）。
    val showDate = true
    val visibleDays = remember { AppPrefs.getVisibleDays(context) }
    // 双指行高缩放 (2026-09-16 用户令): 长期手势 — 初始=上次 tick 确认的持久值;
    // 捏合只改会话值, 顶栏 tick=落盘长期生效, 撤回=回到上次确认值。
    var rowHeightScale by remember(state.selectedTableId) { mutableFloatStateOf(AppPrefs.getGridRowScale(context)) }
    var savedRowScale by remember(state.selectedTableId) { mutableFloatStateOf(AppPrefs.getGridRowScale(context)) }
    val scaleUncommitted = kotlin.math.abs(rowHeightScale - savedRowScale) > 0.001f

    val hasTable = state.tables.isNotEmpty()
    val hasCourses = state.courses.isNotEmpty()

    com.imsx3d.classy.ui.component.ScheduleBackground {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        if (!hasTable) {
            // 真的没表：导入或建表 (不用加课 — 无表载体时加课无从谈起)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                EmptyState(
                    modifier = Modifier.align(Alignment.Center),
                    onGoImport = onGoImport,
                    onCreateTable = onCreateTable
                )
            }
        } else {
            // v7.10.7 顶栏分享 → 底部弹窗(格式选择)
            var showShareSheet by remember { mutableStateOf(false) }
            // v7.10.14 顶栏 logo → 课表切换弹窗
            var showTableSwitcher by remember { mutableStateOf(false) }
            val guidePrefs = remember(context) { context.getSharedPreferences("schedule_guidance", android.content.Context.MODE_PRIVATE) }
            ScheduleHeader(
                showInitialGuide = viewMode == ViewMode.Cards && !guidePrefs.getBoolean("grid_seen", false),
                onGuideDismissed = { guidePrefs.edit().putBoolean("grid_seen", true).apply() },
                tableName = state.currentTable?.name.orEmpty(),
                viewMode = viewMode,
                onViewModeChange = onViewModeChange,
                currentWeek = state.selectedWeek,
                maxWeek = state.currentTable?.maxWeek ?: 20,
                startDate = state.currentTable?.startDate ?: "",
                displayContext = state.weekDisplayContext,
                onSwitchTable = { showTableSwitcher = true },
                scaleUncommitted = scaleUncommitted,
                onScaleCommit = {
                    AppPrefs.setGridRowScale(context, rowHeightScale)
                    savedRowScale = rowHeightScale
                },
                onJumpToActual = {
                    val start = state.currentTable?.startDate ?: return@ScheduleHeader
                    viewModel.changeWeek(DateUtils.currentWeek(start))
                },
                onSelectWeek = { week -> viewModel.changeWeek(week) },
                onAddCourse = onManualAdd,
                onShare = { showShareSheet = true },
                onRefresh = {
                    state.currentTable?.id?.let { id ->
                        context.startActivity(android.content.Intent(context,
                            com.imsx3d.classy.ui.screen.imports.JwImportActivity::class.java)
                            .putExtra(com.imsx3d.classy.ui.screen.imports.JwImportActivity.EXTRA_REFRESH_TABLE_ID, id))
                    }
                }
            )

            if (showShareSheet) {
                state.currentTable?.let { table ->
                    ShareScheduleSheet(
                        table = table,
                        courses = state.courses,
                        onDismiss = { showShareSheet = false }
                    )
                }
            }

            if (showTableSwitcher) {
                TableSwitcherDialog(
                    tables = state.tables,
                    selectedTableId = state.selectedTableId,
                    onSelect = { id ->
                        viewModel.selectTable(id)
                        showTableSwitcher = false
                    },
                    onDismiss = { showTableSwitcher = false }
                )
            }

            // UI-3d: 原第二行的 SegmentedSwitcher 已并入顶栏（图标按钮），此处留空
            // 主体视图 — 左右滑动切换周次
            val pagerMaxWeek = state.currentTable?.maxWeek ?: 20
            val pagerState = rememberPagerState(
                initialPage = (state.selectedWeek - 1).coerceIn(0, (pagerMaxWeek - 1).coerceAtLeast(0)),
                pageCount = { pagerMaxWeek.coerceAtLeast(1) }
            )

            // 标记：是否正在由 ViewModel 驱动 Pager 滚动（防止双向同步打架）
            var syncingFromState by remember { mutableStateOf(false) }

            // Pager 滑动（用户手势）→ 更新 ViewModel
            // 2026-09-14: 回调必须经恢复闸 — 条件组合(overlay/tab 往返)使
            // ScheduleScreen 整页离开组合树再回来, pagerState 按离开时的 page
            // 恢复而 syncingFromState (普通 remember) 恢复帧归零 false,
            // LaunchedEffect(pagerState.currentPage) 立即以恢复的 page 回调
            // changeWeek → 与 selectedWeek effect 的 scrollToPage 双打 → 返回后
            // 多周之间反复跳变闪烁 (无需用户操作)。恢复帧两个条件都不成立:
            // !syncingFromState 刚初始化 (拦不住首回调), isScrollInProgress=false
            // (无手势) → 恢复帧写路径静默丢弃, 环断; 手势拖动时 isScrollInProgress
            // =true 放行, 行为不变。
            LaunchedEffect(pagerState.currentPage) {
                if (!syncingFromState && pagerState.isScrollInProgress) {
                    viewModel.changeWeek(pagerState.currentPage + 1)
                }
            }

            // ViewModel 变化（TopBar 箭头/下拉菜单点击 / 切表）→ 同步 Pager
            // ponytail: syncingFromState 阻止 scrollToPage 期间 currentPage 回调反向
            // 触发 changeWeek, 否侧切表 A→B 异步重置 selectedWeek 即导致 Pager 滚动↔
            // ViewModel 双打反向同步 → 多周之间闪烁反复跳变 (Realme OS 复现, ColorOS 同源)
            LaunchedEffect(state.selectedWeek) {
                val targetPage = (state.selectedWeek - 1).coerceIn(0, pagerMaxWeek - 1)
                if (pagerState.currentPage != targetPage) {
                    syncingFromState = true
                    try {
                        pagerState.scrollToPage(targetPage)
                    } finally {
                        syncingFromState = false
                    }
                }
            }

            if (!hasCourses && viewMode != ViewMode.Cards) {
                Box(Modifier.fillMaxSize().padding(16.dp)) {
                    NoCourseState(Modifier.align(Alignment.Center),
                        tableName = state.currentTable?.name.orEmpty(),
                        onAddCourse = onManualAdd, onImport = onGoImport)
                }
            } else {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !gestureActive,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val renderCourses = state.effectiveCurrentTable?.let { table ->
                    com.imsx3d.classy.util.CourseDateResolver.displayWeek(
                        page + 1, table.startDate, table.maxWeek, state.courses, state.transfers
                    ).map { it.normalizeNode(table.timeJson) }
                }.orEmpty()
                // 本周哪些天该"整列变淡" —— 受用户三个灰显开关控制（可关）。
                // 传入表 ID 使命中调休映射的放假日不灰。
                val greyDays by produceState<Set<Int>>(
                    emptySet(), page, state.currentTable?.startDate, state.transfers
                ) {
                    val start = state.currentTable?.startDate
                    if (start.isNullOrBlank()) {
                        value = emptySet()
                    } else {
                        val set = mutableSetOf<Int>()
                        for (day in 1..7) {
                            val date = DateUtils.dateOfWeek(start, page + 1, day)
                            if (HolidayManager.shouldGrey(context, date, state.effectiveCurrentTable?.id)) {
                                set.add(day)
                            }
                        }
                        value = set
                    }
                }
                // 星期头"休/补"角标 —— 只认节假日数据，与上面三个灰显开关解耦：
                // 用户关掉灰显（不再变淡）后，今天是不是法定假日/补班日照样标出来。
                val greyMarks by produceState<Map<Int, String>>(
                    emptyMap(), page, state.currentTable?.startDate
                ) {
                    val start = state.currentTable?.startDate
                    if (start.isNullOrBlank()) {
                        value = emptyMap()
                    } else {
                        val marks = mutableMapOf<Int, String>()
                        for (day in 1..7) {
                            val date = DateUtils.dateOfWeek(start, page + 1, day)
                            HolidayManager.dayMarker(context, date)?.let { marks[day] = it }
                        }
                        value = marks
                    }
                }
                CourseCompletionProvider(
                    startDate = state.currentTable?.startDate.orEmpty(),
                    week = page + 1,
                    timeJson = state.effectiveCurrentTable?.timeJson
                ) {
                when (viewMode) {
                    ViewMode.Full -> FullWeekView(
                        courses = renderCourses,
                        visibleDays = visibleDays,
                        displayMode = displayMode,
                        timeJson = state.effectiveCurrentTable?.timeJson ?: "",
                        startDate = state.currentTable?.startDate ?: "",
                        currentWeek = page + 1,
                        onCourseClick = { selectedCourse = it },
                        greyDays = greyDays,
                        greyMarks = greyMarks
                    )
                    ViewMode.Cards -> CardsGridView(
                        courses = renderCourses,
                        allCourses = state.courses,
                        timeSlots = TimeTableUtils.timeSlotsFor(state.effectiveCurrentTable),
                        visibleDays = visibleDays,
                        showDate = showDate,
                        startDate = state.currentTable?.startDate ?: "",
                        currentWeek = page + 1,
                        onCourseClick = { selectedCourse = it },
                        greyDays = greyDays,
                        greyMarks = greyMarks,
                        topOverrides = topOverrides,
                        onSetTopOverride = ::setTopOverride,
                        rotationSteps = rotationSteps,
                        onRotationStep = { key, step ->
                            rotationSteps = if (step <= 0) rotationSteps - key
                            else rotationSteps + (key to step)
                        },
                        // 用户反馈 2026-09-09: 非常规课跨节次空隙 → 渲染期合成占位节次,
                        // 比例定位与聚簇都基于扩展后的槽位表(真实分钟语义)
                        timeJson = state.effectiveCurrentTable?.timeJson,
                        rowHeightScale = rowHeightScale,
                        onRowHeightScaleChange = { rowHeightScale = it },
                        // v1.0.56 T3: 实验室开关 — 默认关=手势不挂(顶栏 tick 按钮也随 scaleUncommitted 恒 false 不亮)
                        pinchZoomEnabled = AppPrefs.isGridPinchZoom(context),
                        interactionKey = state.selectedTableId ?: 0L,
                        onGestureActive = { gestureActive = it },
                        onQuickAdd = { selected ->
                            state.effectiveCurrentTable?.let { table ->
                                onQuickAdd(com.imsx3d.classy.util.CourseGridPrefill(table.id,
                                    selected.day, selected.startNode, selected.step, table.maxWeek,
                                    selected.startNode !in 1..TimeTableUtils.maxStandardNode(table.timeJson)))
                            }
                        },
                        onMoveCourse = { displayed, target ->
                            val table = state.effectiveCurrentTable
                            val original = state.courses.firstOrNull { it.id == displayed.id }
                            if (table != null && original != null) {
                                val date = DateUtils.dateOfWeek(table.startDate, page + 1, displayed.day)
                                val sourceDate = com.imsx3d.classy.util.CourseDateResolver.teachingDate(date, state.transfers)
                                if (sourceDate != null) {
                                    moveRequest = com.imsx3d.classy.ui.component.CourseMoveRequest(
                                        original, DateUtils.currentWeek(table.startDate, sourceDate),
                                        page + 1, target, table, state.transfers)
                                }
                            }
                        }
                    )
                }
                }
            }
        }
        }

        // 详情 Bottom Sheet
        // v7.10.16q: allCourses 必须与网格同周域(state.selectedWeek 过滤) —
        // 此前传全周课程, ICS 往返/换教师拆出的周次不相交同行(如周四 8-10 的
        // 周1-4 与 周6-13 两行)被当成同时存在 → 幽灵图层 → 误弹"选择默认置顶"。
        // 网格一直传的是 inWeek 过滤后的 weekCourses, 弹窗对齐同一语义。
        CourseDetailSheet(
            course = selectedCourse,
            timeString = selectedCourse?.let { it.nodeString(LocalContext.current) },
            allCourses = state.currentWeekCourses,
            // 用户报障 2026-09-10: 详情页聚簇与网格同一时间域 — ownTime 课
            // 按真实分钟判重叠, 节点占位值不再制造假冲突。
            timeJson = state.effectiveCurrentTable?.timeJson,
            onDismiss = { selectedCourse = null },
            onEdit = { course ->
                selectedCourse = null
                onEditCourse(course)
            },
            onDefaultTopChanged = { clusterKey, repId ->
                // 勾选瞬间: 会话级换层(网格同帧刷新) + 持久化(跨会话默认)。
                // v7.10.16r(评审 #4): 同簇轮换态一并清除 — 用户显式选默认置顶,
                // 临时轮换让位,否则该簇轮换步数仍遮蔽 radio 的新决定。
                rotationSteps = rotationSteps - clusterKey
                setTopOverride(clusterKey, repId)
                AppPrefs.putConflictDefaultTop(context, clusterKey, repId)
            }
        )
    }
}
}

/**
 * v7.10.14 顶栏 logo 点击弹出的课表切换弹窗 —
 * 列出全部课表, 当前行 primaryContainer 高亮 + 对勾, 点击即切换。
 */
@Composable
internal fun TableSwitcherDialog(
    tables: List<TimeTableEntity>,
    selectedTableId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        titleContentColor = colors.onSurface,
        textContentColor = colors.onSurfaceVariant,
        title = { Text(stringResource(R.string.schedule_switch_table)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                tables.forEach { table ->
                    val isCurrent = table.id == selectedTableId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SleepyTheme.shapes.small)
                            .background(if (isCurrent) colors.primaryContainer else colors.surfaceContainer)
                            .noRippleClickable { if (isCurrent) onDismiss() else onSelect(table.id) }
                            .heightIn(min = 48.dp)
                            .semantics { role = androidx.compose.ui.semantics.Role.RadioButton; selected = isCurrent }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = table.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isCurrent) colors.onPrimaryContainer else colors.onSurface,
                            maxLines = 2,
                            modifier = Modifier.weight(1f)
                        )
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = colors.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun WeekNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDescriptionRes: Int? = null
) {
    // UI-3d: 去掉 32dp 圆底（顶栏 6 个实心圆点是"杂乱"主因），只留图标本身
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .noRippleClickable(onClick)
            .padding(5.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescriptionRes?.let { stringResource(it) },
            tint = com.nevoit.glasense.theme.GlasenseTheme.colors.contentVariant
        )
    }
}

@Composable
private fun NoCourseState(
    modifier: Modifier = Modifier,
    tableName: String,
    onAddCourse: () -> Unit,
    onImport: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .widthIn(max = com.imsx3d.classy.ui.theme.ClassyLayout.readingWidth)
            .fillMaxWidth()
            .settingsCard(colors.surfaceContainer)
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.schedule_empty_name, tableName),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface
        )
        Text(
            text = stringResource(R.string.schedule_empty_name_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        Button(
                colors = primaryFilledButtonColors(),
            onClick = onAddCourse,
            modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight),
            shape = SleepyTheme.Buttons.shape,
        ) {
            Text(stringResource(R.string.schedule_manual_first))
        }
        // [intentional custom] FilledTonalButton + ctaHeight: 官方变体自带动效/形状,
        // 仅保留 Sleepy 的 56dp CTA 高度档位(官方无此 token)。
        GlasenseButton(
            text = stringResource(R.string.schedule_go_manage),
            onClick = onImport,
            cta = true
        )
    }
}

@Composable
private fun EmptyState(
    modifier: Modifier = Modifier,
    onGoImport: () -> Unit = {},
    onCreateTable: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = modifier
            .widthIn(max = com.imsx3d.classy.ui.theme.ClassyLayout.readingWidth)
            .padding(horizontal = 22.dp)
            .settingsCard(colors.surfaceContainer)
            .padding(horizontal = 22.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.schedule_empty),
            style = MaterialTheme.typography.titleLarge,
            color = colors.onSurface
        )
        Text(
            text = stringResource(R.string.schedule_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant
        )
        // 主按钮 = 导入第一张课表 (用户反馈: "前往课表管理"引导性不足)
        Button(
                colors = primaryFilledButtonColors(),
            onClick = onGoImport,
            modifier = Modifier.fillMaxWidth().height(SleepyTheme.Buttons.ctaHeight),
            shape = SleepyTheme.Buttons.shape,
        ) {
            Text(stringResource(R.string.schedule_empty_import))
        }
        // 副按钮 = 手动创建第一张课表 (建表流, 非加课 — 无表载体时"创建第一门课"无从谈起)
        // [intentional custom] FilledTonalButton + ctaHeight: 同上, 仅保留 56dp CTA 档位。
        GlasenseButton(
            text = stringResource(R.string.schedule_empty_create_table),
            onClick = onCreateTable,
            cta = true
        )
    }
}
