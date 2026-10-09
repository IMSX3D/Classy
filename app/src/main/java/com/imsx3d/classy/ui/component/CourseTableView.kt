package com.imsx3d.classy.ui.component

import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import com.imsx3d.classy.R
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.ui.theme.SleepyTheme
import com.imsx3d.classy.ui.theme.glasenseM3Scheme
import com.imsx3d.classy.ui.theme.noRippleClickable
import com.imsx3d.classy.util.AppPrefs
import com.imsx3d.classy.util.ConflictLayoutEngine
import com.imsx3d.classy.util.CourseColorUtil
import com.imsx3d.classy.util.CourseDisplayUtil
import com.imsx3d.classy.util.DateUtils
import com.imsx3d.classy.util.TimeTableUtils
import com.imsx3d.classy.util.TimetableViewportPolicy
import kotlinx.coroutines.flow.filter
import java.time.LocalTime
import kotlin.math.abs

/**
 * 时段定义 — 5 个时段（对应 HTML 里的 5 个 slot-row）
 * 与 WakeUp 默认 12 节对应: 1-2 / 3-5 / 6-7 / 8-10 / 11-13
 */
data class TimeSlot(
    val label: String,         // "1-2节"
    val start: LocalTime,
    val end: LocalTime,
    val displayStart: String,  // "08:00"
    val displayEnd: String,    // "09:35"
    val nodeStart: Int,
    val nodeEnd: Int
) {
    // nodeString 死属性已删（恒返回 "N-N" 且全库零调用; 界面用的是 CourseEntity.nodeString 本地化版本）
    val timeString: String get() = "$displayStart-$displayEnd"

    /**
     * 渲染期占位节次 (用户反馈 2026-09-09): 非常规课跨节次空隙时由
     * TimeTableUtils.buildRenderSlotPlan 合成的空隙占位行 — 只显示时间不显示
     * 节号(label 为空), 绝不写回 timeJson, 与 insertEdgeNode 手建节点无关。
     */
    val isPlaceholder: Boolean get() = label.isEmpty()
}

/**
 * 这一列（星期 day）在**当前显示的那一周**里是不是今天。
 *
 * 判据必须是"算出来的日期 == 今天"，**不能**用「今天是周几」（`today` 参数）：
 * 翻到下一周时周三还在列表里，但那不是"今天"。日期算不出来（没设学期开始日期）时
 * 一律返回 false —— 宁可不亮，也不能亮错列。
 */
private fun isTodayColumn(
    day: Int,
    startDate: String,
    currentWeek: Int,
    todayDate: java.time.LocalDate
): Boolean = startDate.isNotBlank() && runCatching {
    DateUtils.isDateToday(DateUtils.dateOfWeek(startDate, currentWeek, day), todayDate)
}.getOrDefault(false)

/**
 * Cards 网格视图
 *
 * 架构：
 *   BoxWithConstraints(fillMaxSize) → 算出 colW (dp)
 *     Column(fillMaxWidth, verticalScroll)
 *       Row(表头)            — Compose 自然排版
 *       Box(固定高度 = maxNode * rowH) — 时间栏 + 课程卡片全用 Modifier.offset 绝对定位
 *
 * 关键点：
 * - Column 用 fillMaxWidth（不是 fillMaxSize），内容高度 = 表头 + 固定 gridH，超出视口 → 可滚动
 * - 时间栏 / 卡片都在同一个 Box 内，Modifier.offset 定位 → 滚动完全同步
 * - 全用 dp 算 offset，不碰 px，不碰 Layout measure/place
 */
@Composable
fun CardsGridView(
    courses: List<CourseEntity>,
    allCourses: List<CourseEntity> = courses,
    timeSlots: List<TimeSlot>,
    visibleDays: Set<Int> = (1..7).toSet(),
    showDate: Boolean = false,
    startDate: String = "",
    currentWeek: Int = 1,
    today: Int = DateUtils.todayDayOfWeek(),
    todayDate: java.time.LocalDate = java.time.LocalDate.now(),
    onCourseClick: (CourseEntity) -> Unit,
    modifier: Modifier = Modifier,
    greyDays: Set<Int> = emptySet(),  // 本周应灰显的星期几 (1-7)
    greyMarks: Map<Int, String> = emptyMap(),  // 灰显原因短标（休/补），星期头角标用
    topOverrides: Map<String, Long> = emptyMap(),           // v7.10.5: 会话级置顶 override,上提到 ScheduleScreen(radio 瞬时更新写入同一真相源)
    onSetTopOverride: (String, Long?) -> Unit = { _, _ -> }, // (clusterKey, layerRepId|null)
    // v7.10.16r 轮换态(issue#10, 评审 #1): 与 topOverrides 同级持有(离开课表页重置)。
    // remember 留在页内会在 HorizontalPager 翻页时销毁( beyondViewportPageCount=0 ),
    // 周切换一次轮换态即丢。
    rotationSteps: Map<String, Int> = emptyMap(),
    onRotationStep: (String, Int) -> Unit = { _, _ -> },     // (clusterKey, step)
    // 用户反馈 2026-09-09: 非常规课跨节次空隙时按当前课程集合合成渲染期占位节次,
    // 比例定位基于扩展后的槽位表。null = 不合成(旧调用方兼容)。
    timeJson: String? = null,
    rowHeightScale: Float = 1f,
    onRowHeightScaleChange: (Float) -> Unit = {},
    // v1.0.56 T3(实验室): 双指捏放行高总开关, 默认 false = 手势整个不挂(存量缩放值不清)。
    pinchZoomEnabled: Boolean = false
) {
    val colors = glasenseM3Scheme()
    // 设置页改 scale / cornerRatio 后强制 recompose
    var prefVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        AppPrefs.changeBus.filter {
            it == AppPrefs.KEY_GRID_SCALE || it == AppPrefs.KEY_GRID_CORNER_RATIO ||
                it == AppPrefs.KEY_GRID_USE_ALIAS ||
                // 课程色呈现方式 / 统一底色：改完从设置页返回要立刻生效（2026-09-28）
                it == AppPrefs.KEY_COURSE_COLOR_STYLE || it == AppPrefs.KEY_COURSE_COLORLESS ||
                it == AppPrefs.KEY_GRID_ADAPTIVE_HEIGHT ||
                it == AppPrefs.KEY_GRID_AUTO_HIDE_EMPTY_EVENING ||
                it == AppPrefs.KEY_GRID_EVENING_START
        }.collect { prefVersion++ }
    }
    val context = LocalContext.current
    // 实验室开关 (2026-09-16 用户令): 自适应行高/晚间收起默认全关 —
    // 默认行为回归原固定行高 + 完整时间轴; 晚间起始时间用户自定义, 不再机械 18:00
    val adaptiveHeight = AppPrefs.isGridAdaptiveHeight(context)
    val autoHideEmptyEvening = AppPrefs.isGridAutoHideEmptyEvening(context)
    val eveningStart = TimetableViewportPolicy.parseEveningStart(
        AppPrefs.getGridEveningStart(context)
    )

    val visibleSlotResult = remember(
        allCourses,
        timeSlots,
        visibleDays,
        timeJson,
        autoHideEmptyEvening,
        eveningStart,
        prefVersion
    ) {
        TimetableViewportPolicy.selectVisibleSlots(
            allCourses = allCourses,
            timeSlots = timeSlots,
            visibleDays = visibleDays,
            timeJson = timeJson,
            autoHideEmptyEvening = autoHideEmptyEvening,
            eveningStart = eveningStart
        )
    }
    val baseSlots = visibleSlotResult.slots
    // 先裁剪稳定的学期级基础槽位，再为当前周的非常规时间课程合成占位行。
    val renderPlan = remember(baseSlots, timeJson, courses) {
        if (timeJson != null) TimeTableUtils.buildRenderSlotPlan(courses, timeJson, baseSlots)
        else TimeTableUtils.RenderSlotPlan(baseSlots)
    }
    val renderSlots = renderPlan.slots
    val maxNode = renderSlots.maxOfOrNull { it.nodeEnd } ?: 12
    val sortedDays = visibleDays.sorted()
    val dayCount = sortedDays.size

    // 用户反馈 2026-09-16: 占位行"文字放不下 → 灰块 + 点击展开/再点折叠"。
    // 展开态键 = 占位行时间串("11:40-12:30", 周内多天共享同一空隙 → 同键联动展开);
    // 会话级 remember — 翻周/离开页面即复位(与 rotationSteps 同生命周期哲学)。
    // 展开/折叠通过改写 effectiveWeights 的该行权重实现, yOfRows/rowHeightAt/gridH
    // 全部读同一张表 → 行高与 y 前缀和天然同源(时间轴不破)。
    var expandedPlaceholders by remember { mutableStateOf(setOf<String>()) }
    fun togglePlaceholder(key: String) {
        expandedPlaceholders = if (key in expandedPlaceholders) expandedPlaceholders - key
        else expandedPlaceholders + key
    }

    // issue#23: 边缘节次节点的"行号"按 renderSlots 自然顺序取(已按 node ASC 排序);
    // 前置节点(-1, 0)排到 grid 顶部, 后置节点(N+1, N+2)排到 grid 底部,
    // 视觉上就是"第 0 节在第 1 节之上" / "第 N+1 节在第 N 节之下", 与插入直觉一致。
    //   返回 -1 = 该节点不在 renderSlots(典型场景: 课程来自已删除的旧 timeJson, 数据脏)
    //   调用方需先判 >= 0 再绘;cardY 那侧 .coerceAtLeast(0) 兜底防负坐标。
    fun slotIndexOf(node: Int): Int = renderSlots.indexOfFirst { it.nodeStart == node }

    // issue#8 网格整体缩放: 0.7~1.3, 字号/行高/间距/圆角/内边距等比联动
    // (12节连堂课表缩到 0.7 可一屏放下; 只影响本 Cards 视图, 小组件与列表视图不受影响)
    val scale = AppPrefs.getGridScale(context)
    val cornerRatio = AppPrefs.getGridCornerRatio(context)
    val d = { v: Float -> (v * scale).dp }

    // 布局常量（全 dp, 乘 scale）
    // UI-4p: 显示日期时表头加高 —— 表头改两行（周X / 日期，同网格组件版式）。
    // 起因：单列只有 ~40dp，"周一 09-21" 一行要 ~64dp，实测溢出 ~24dp，
    // 日期被压到隔壁列上（且今天的主色胶囊会被白色日期文字"破框"）。
    val headH = d(if (showDate) HEAD_CELL_H_DATE_SP else HEAD_CELL_H_SP)
    val timeW = d(44f)
    val gapH = d(4f)
    val gapW = d(4f)

    // UI-2c: 容器圆角统一到 Glasense 卡片规范(12dp), 不再与上方组件的形状语言割裂
    val gridBgShape = com.nevoit.glasense.theme.GlasenseTheme.specs.cardShape

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.surfaceContainerHigh, gridBgShape)
            .padding(d(6f))
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // 自动适配只改变纵向行高；横向宽度、字号和卡片内容仍由原 gridScale 控制。
            val navExtra = com.imsx3d.classy.ui.component.LocalNavExtraBottomPadding.current
            val availableGridHeight = (maxHeight - headH - gapH - navExtra)
                .value
                .coerceAtLeast(0f)
            // 行高基座 (2026-09-16 用户令): 实验室开自适应=拟合高度; 默认关=原固定 52dp×scale。
            // 双指手势相对基座缩放, 上限 96dp 下限 36dp (×scale); 顶栏 tick 确认后长期生效, 撤回回退上次确认值。
            val baseRowHeight = TimetableViewportPolicy.baseRowHeightDp(
                adaptive = adaptiveHeight,
                fitRowHeightDp = TimetableViewportPolicy.fitRowHeightDp(
                    availableGridHeightDp = availableGridHeight,
                    slotWeights = renderPlan.slotWeights,
                    slotCount = renderSlots.size,
                    contentScale = scale
                ),
                contentScale = scale
            )
            val rowHeightDp = TimetableViewportPolicy.manualRowHeightDp(
                baseRowHeightDp = baseRowHeight,
                verticalScale = rowHeightScale,
                minRowHeightDp = TimetableViewportPolicy.MIN_ROW_DP * scale.coerceIn(0.7f, 1.3f),
                maxRowHeightDp = TimetableViewportPolicy.MAX_ROW_DP * scale.coerceIn(0.7f, 1.3f)
            )
            val rowH = rowHeightDp.dp

            // 用户反馈 2026-09-16: 展开的占位行权重换成"正好显示完文字"的展开权重 —
            // yOfRows / rowHeightAt / gridH 都读 effectiveWeights 同一张表, 行高与 y 同源。
            // 占位行文字适配检测也是几何函数(随 rowH/scale 联动), 不放得下才允许灰置。
            val effectiveWeights: List<Float>? = run {
                val base = renderPlan.slotWeights ?: return@run null
                base.mapIndexed { i, w ->
                    val slot = renderSlots.getOrNull(i)
                    if (slot != null && slot.isPlaceholder && slot.timeString in expandedPlaceholders) {
                        maxOf(
                            w,
                            TimeTableUtils.placeholderExpandedWeight(
                                rowHeightDp = rowHeightDp,
                                gapDp = TimetableViewportPolicy.ROW_GAP_DP,
                                requiredTextHeightDp = PLACEHOLDER_TEXT_REQUIRED_DP
                            )
                        )
                    } else w
                }
            }

            // 用户反馈 2026-09-09 (精度): 时间轴按分钟加权 — 占位行只占真实分钟占比
            // (5 分钟占位 ≈ 0.111 标准行), 不再整行拉满把时间轴歪曲。
            // yOfRows(r) = 加权行坐标 r(0.0=网格顶, 1.0=一标准行) → dp;
            fun yOfRows(r: Float): Dp {
                val ws = effectiveWeights ?: return rowH * r
                var acc = 0f
                val full = r.toInt().coerceAtMost(ws.size)
                for (i in 0 until full) acc += ws[i]
                if (full < ws.size && r > full) acc += ws[full] * (r - full)
                return rowH * acc
            }
            fun rowHeightAt(i: Int): Dp = rowH * (effectiveWeights?.getOrNull(i) ?: 1f)

            // 算出每列宽度 (dp)
            val colW = (maxWidth - timeW - gapW * (dayCount + 1)) / dayCount
            // issue#23: grid 高度按 renderSlots 行数算(maxNode 已不反映边缘节点总数)——
            // edge 节点在 timeSlots 末尾, 视觉上自然排到第 N 节之下;
            // 用户反馈 2026-09-09: 占位节次行同样扩展网格高度(按分钟加权, 不占满整行)。
            val gridH = yOfRows(renderSlots.size.coerceAtLeast(1).toFloat())

            val scrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        // v1.0.56 T3: 实验室开关默认关 — 手势不挂即捏不动; 开=原行为
                        if (pinchZoomEnabled) Modifier.verticalResizeGesture(
                            baseRowHeightDp = baseRowHeight,
                            currentRowHeightDp = rowHeightDp,
                            contentScale = scale,
                            onRowHeightScaleChange = onRowHeightScaleChange
                        ) else Modifier
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                ) {
                // ---- 表头：自然 Compose Row ----
                Row(
                    modifier = Modifier.fillMaxWidth().height(headH),
                    horizontalArrangement = Arrangement.spacedBy(gapW),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(timeW))
                    for (day in sortedDays) {
                        val dateStr = if (showDate && startDate.isNotBlank()) {
                            try {
                                val ds = DateUtils.dateOfWeek(startDate, currentWeek, day)
                                DateUtils.shortDate(ds)
                            } catch (_: Exception) { null }
                        } else null
                        DayHeadCell(
                            day = day,
                            isToday = isTodayColumn(day, startDate, currentWeek, todayDate),
                            isGrey = day in greyDays,
                            mark = greyMarks[day],
                            courseCount = courses.count { it.day == day },
                            dateStr = dateStr,
                            scale = scale,
                            cornerRatio = cornerRatio,
                            modifier = Modifier.width(colW).fillMaxHeight()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(gapH))

                // ---- Grid 主体：固定高度 Box，内部全用 Modifier.offset 绝对定位 ----
                Box(modifier = Modifier.fillMaxWidth().height(gridH)) {
                    // 时间栏：每个节次一个 Row，用 offset 定位到正确 y (分钟加权)
                    for ((i, slot) in renderSlots.withIndex()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeightAt(i) - gapH)
                                .offset(y = yOfRows(i.toFloat())),
                            horizontalArrangement = Arrangement.spacedBy(gapW),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 用户反馈 2026-09-16: 占位行几何检测 — 文字放不下 = 灰块,
                            // 点击展开(该行权重换展开权重, 下方时间轴同帧下移), 再点折叠。
                            val phFitsText = if (!slot.isPlaceholder) true else {
                                TimeTableUtils.placeholderTextFits(
                                    rowWeight = effectiveWeights?.getOrNull(i) ?: 1f,
                                    rowHeightDp = rowHeightDp,
                                    gapDp = TimetableViewportPolicy.ROW_GAP_DP,
                                    requiredTextHeightDp = PLACEHOLDER_TEXT_REQUIRED_DP
                                )
                            }
                            SingleTimeHeadCell(
                                slot = slot,
                                scale = scale,
                                modifier = Modifier.width(timeW).fillMaxHeight(),
                                cornerRatio = cornerRatio,
                                textFits = phFitsText,
                                onToggleExpand = if (slot.isPlaceholder && !phFitsText) {
                                    { togglePlaceholder(slot.timeString) }
                                } else null
                            )
                            // 透明占位：保证行宽和表头一致
                            for (day in sortedDays) {
                                Spacer(modifier = Modifier.width(colW).fillMaxHeight())
                            }
                        }
                    }

                    // 课程卡片：用 offset 绝对定位 — 冲突簇整簇走 ConflictClusterCard,
                    // 非簇课保持原 CourseOverlayCard 单卡路径(回归保护)
                    val context = LocalContext.current
                    val conflictStyle = AppPrefs.getConflictStyle(context)
                    // v7.10.16r 轮换态(issue#10): 簇键 → 轮换步数 — 与 topOverrides 同级
                    // 持有(ScheduleScreen),翻页/周切换不丢(评审 #1);纯会话级不落盘,
                    // 离开课表页即重置。默认序按用户置顶偏好走,轮换只改当次视图。
                    // v7.10.5: 交换置顶状态上提到 ScheduleScreen — 详情弹窗 radio 点击
                    // 与网格 onPickTop 写同一真相源,radio 勾选瞬间网格同帧换层。
                    // (此前 radio 只写持久化 defaultTopMap,被会话级 topOverrides 遮蔽 → 看似不生效)
                    fun setTopOverride(key: String, courseId: Long?) = onSetTopOverride(key, courseId)

                    // v7.9 默认置顶:跨会话持久化偏好,由详情弹窗 radio 写入。
                    // 与 topOverrides 同簇键,作 topOverrideId fallback — 当次切换仍由 topOverrides 主导。
                    val defaultTopMap by AppPrefs.conflictDefaultTopFlow(context).collectAsState(
                        initial = AppPrefs.getConflictDefaultTop(context)
                    )

                    // 引擎聚簇: 同天区间相交(含链式)课程归簇,簇键=主课判定序首课三元组
                    // (override 不改变首课——findClusters 输出固定,键稳定)
                    // 用户反馈 2026-09-09: 带 timeJson 按真实时间区间(分钟级)聚簇 —
                    // 跨空隙反算的节点范围不再制造假冲突(12:30 结束被吸进 14:00 节)。
                    val clusters = ConflictLayoutEngine.findClusters(courses, timeJson)
                    val clusteredIds = clusters.flatMap { c -> c.courses.map { it.id } }.toSet()

                    for (cluster in clusters) {
                        // 簇内课若因 visibleDays 过滤或节点不在 timeJson 则整簇跳过
                        if (cluster.day !in visibleDays) continue
                        // issue#23: 边缘节点同样允许 — 用 slotIndexOf 兜底,数据脏返回 -1 也直接过滤掉
                        val inGrid = cluster.courses.filter {
                            slotIndexOf(it.startNode) >= 0
                        }
                        if (inGrid.isEmpty()) continue
                        val anchor = cluster.courses.first() // 主课判定序首位,决定簇基点
                        val dayIdx = sortedDays.indexOf(cluster.day)
                        val cardX = timeW + gapW + (colW + gapW) * dayIdx
                        // 用户报障 2026-09-10: 簇锚点用锚课**小数行坐标**(ownTime 锚课
                        // 不再被 slotIndexOf 整格吸附丢掉小数偏移, 整簇上移 ~0.9 行的本体);
                        // 加权 dp 与簇内 spanDpOf 同一真值。
                        val anchorFrac = if (anchor.ownTime && anchor.startTime.isNotBlank() && anchor.endTime.isNotBlank()) {
                            TimeTableUtils.timeToFractionalRows(anchor.startTime, anchor.endTime, renderSlots)?.first
                        } else null
                        val anchorRow = anchorFrac ?: slotIndexOf(anchor.startNode).coerceAtLeast(0).toFloat()
                        val cardY = yOfRows(anchorRow)
                        val clusterKey = ConflictLayoutEngine.conflictClusterKey(cluster)

                        ConflictClusterCard(
                            cluster = cluster,
                            style = conflictStyle,
                            topOverrideId = topOverrides[clusterKey] ?: defaultTopMap[clusterKey],
                            onPickTop = { id -> setTopOverride(clusterKey, id) },
                            // v7.10.16r 轮换(issue#10): 步数与回调都按簇键隔离,纯会话态不落盘
                            rotationStep = rotationSteps[clusterKey],
                            onRotate = {
                                onRotationStep(clusterKey, (rotationSteps[clusterKey] ?: 0) + 1)
                            },
                            onPickFromBadge = { layerPos ->
                                // 气泡选课 = 换来看: 步数使目标层转到基准序首位(会话态)
                                onRotationStep(clusterKey, layerPos)
                            },
                            onCourseClick = onCourseClick,
                            colW = colW,
                            rowH = rowH,
                            maxNode = maxNode,
                            timeSlots = renderSlots,
                            spanDpOf = { from, to -> yOfRows(to) - yOfRows(from) },
                            timeW = timeW,
                            gapW = gapW,
                            gapH = gapH,
                            isGrey = cluster.day in greyDays,
                            modifier = Modifier.offset(x = cardX, y = cardY)
                        )
                    }

                    for (course in courses) {
                        if (course.day !in visibleDays) continue
                        // issue#23: 边缘节点允许 — slotIndexOf = -1 表示该课 startNode 不在当前 timeJson,
                        // 通常是数据脏(老 timeJson 残留了已删节点), 静默跳过不渲染避免越界
                        val nodeIdx = slotIndexOf(course.startNode)
                        if (nodeIdx < 0) continue
                        if (course.id in clusteredIds) continue // 簇内课已由 ConflictClusterCard 绘制
                        val dayIdx = sortedDays.indexOf(course.day)
                        // 步长上限按剩余行数算(边缘节点也按 renderSlots 总行数取模)
                        val steps = course.step.coerceAtLeast(1)
                            .coerceAtMost(renderSlots.size - nodeIdx)
                        val cardX = timeW + gapW + (colW + gapW) * dayIdx
                        // issue#23 §5: 非常规时间(ownTime)课按真实分钟比例定位(1.0 = 一整行);
                        // 用户反馈 2026-09-09: 比例映射基于 renderSlots(含占位节次) —
                        // 12:30 落在 11:40~12:30 占位行内, 不再侵入 14:00 行;
                        // 时间映射失败退回整格吸附(normalizeNode 已反算 nodeIdx/steps)
                        val frac = if (course.ownTime) TimeTableUtils.timeToFractionalRows(
                            course.startTime, course.endTime, renderSlots
                        ) else null
                        val cardY = frac?.let { yOfRows(it.first) } ?: yOfRows(nodeIdx.toFloat())
                        val cardH = if (frac != null) {
                            // 按比例(分钟加权), 保底 0.3 标准行避免过短课胶囊塌缩到不可点
                            (yOfRows(frac.second) - yOfRows(frac.first)).coerceAtLeast(rowH * 0.3f) - gapH
                        } else {
                            yOfRows((nodeIdx + steps).toFloat()) - yOfRows(nodeIdx.toFloat()) - gapH
                        }

                        CourseOverlayCard(
                            course = course,
                            onClick = { onCourseClick(course) },
                            modifier = Modifier
                                .offset(x = cardX, y = cardY)
                                .width(colW)
                                .height(cardH),
                            isGrey = course.day in greyDays,
                            scale = scale,
                            cornerRatio = cornerRatio,
                            groupRows = courses.filter { it.groupId == course.groupId }
                        )
                    }
                }
                    if (navExtra > 0.dp) Spacer(modifier = Modifier.height(navExtra))
                }
            }
        }
    }
}

private fun Modifier.verticalResizeGesture(
    baseRowHeightDp: Float,
    currentRowHeightDp: Float,
    contentScale: Float,
    onRowHeightScaleChange: (Float) -> Unit
): Modifier {
    return composed {
        val latestBaseRowHeight by rememberUpdatedState(baseRowHeightDp)
        val latestRowHeight by rememberUpdatedState(currentRowHeightDp)
        val latestScale by rememberUpdatedState(contentScale)
        val latestOnChange by rememberUpdatedState(onRowHeightScaleChange)

        pointerInput(Unit) {
            awaitPointerEventScope {
                var startVerticalSpan: Float? = null
                var startHorizontalSpan = 0f
                var startRowHeightDp = latestRowHeight
                var locked = false

                while (true) {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size >= 2) {
                        val first = pressed[0].position
                        val second = pressed[1].position
                        val verticalSpan = abs(first.y - second.y)
                        val horizontalSpan = abs(first.x - second.x)
                        if (startVerticalSpan == null) {
                            startVerticalSpan = verticalSpan.coerceAtLeast(1f)
                            startHorizontalSpan = horizontalSpan
                            startRowHeightDp = latestRowHeight
                        }

                        if (!locked && TimetableViewportPolicy.locksVerticalResize(
                                startVerticalSpan = startVerticalSpan!!,
                                currentVerticalSpan = verticalSpan,
                                startHorizontalSpan = startHorizontalSpan,
                                currentHorizontalSpan = horizontalSpan
                            )
                        ) {
                            locked = true
                        }

                        if (locked) {
                            val minRow = TimetableViewportPolicy.MIN_ROW_DP * latestScale.coerceIn(0.7f, 1.3f)
                            val maxRow = TimetableViewportPolicy.MAX_ROW_DP * latestScale.coerceIn(0.7f, 1.3f)
                            val nextRowHeight = TimetableViewportPolicy.rowHeightFromVerticalSpan(
                                startRowHeightDp = startRowHeightDp,
                                startVerticalSpan = startVerticalSpan!!,
                                currentVerticalSpan = verticalSpan,
                                minRowHeightDp = minRow,
                                maxRowHeightDp = maxRow
                            )
                            latestOnChange(
                                nextRowHeight / latestBaseRowHeight.coerceAtLeast(1f)
                            )
                            event.changes.forEach { it.consume() }
                        } else {
                            val verticalDelta = abs(verticalSpan - startVerticalSpan!!)
                            val horizontalDelta = abs(horizontalSpan - startHorizontalSpan)
                            // 双指明确横向展开/合拢时不改变高度，也不让 Pager 抢走这次手势。
                            if (horizontalDelta >= TimetableViewportPolicy.VERTICAL_GESTURE_THRESHOLD_DP &&
                                horizontalDelta > verticalDelta * TimetableViewportPolicy.VERTICAL_DOMINANCE_RATIO
                            ) {
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } else {
                        startVerticalSpan = null
                        locked = false
                    }
                }
            }
        }
    }
}

/** 用户反馈 2026-09-16: 占位行时间文字适配的最小内容区需求 = micro 一行 lineHeight 11dp
 *  + SingleTimeHeadCell 上下 padding 8dp。放不下 → 只显示灰块(文字隐藏), 点击展开。 */
private const val PLACEHOLDER_TEXT_REQUIRED_DP = 19f

@Composable
private fun SingleTimeHeadCell(slot: TimeSlot, scale: Float = 1f, modifier: Modifier = Modifier, cornerRatio: Float = 1f, textFits: Boolean = true, onToggleExpand: (() -> Unit)? = null) {
    val colors = glasenseM3Scheme()
    val sd = { v: Float -> (v * scale).dp }
    val isPh = slot.isPlaceholder
    // 占位节次：文字放不下时保持"纯灰块、点击展开"的原交互（只是不再有卡片底）
    val phCollapsed = isPh && !textFits
    // UI-2c 重设计（2026-09-26）: 原「68dp 宽 + 圆角卡片底」的时间列既占横向空间，
    // 又和课程色块互抢视觉重心（一列白卡 + 一列彩块）。
    // 改为无底色纯文字列：节号 + 起止时间分两行，宽度收到 44dp，横向空间还给课程。
    val parts = slot.timeString.split("-", limit = 2).takeIf { it.size == 2 }
    Column(
        modifier = modifier
            .padding(horizontal = sd(1f))
            .then(if (onToggleExpand != null) Modifier.noRippleClickable { onToggleExpand() } else Modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (phCollapsed) {
            // 灰置占位行（非常规时间的空档）：原为纯灰块，容易被读成"禁用/课程消失"。
            // 给一个"点击展开"的暗示（点后显示该空档的时间文字）。
            Text(
                text = "＋",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp,
                    fontSize = (13 * scale).sp,
                    lineHeight = (15 * scale).sp
                ),
                color = colors.onSurfaceVariant.copy(alpha = 0.45f),
                maxLines = 1
            )
        } else {
            if (!isPh) {
                Text(
                    text = stringResource(R.string.period_format_node, slot.label),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (11 * scale).sp,
                        lineHeight = (13 * scale).sp
                    ),
                    color = colors.onSurface,
                    maxLines = 1
                )
            }
            val timeStyle = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp,
                fontSize = (9 * scale).sp,
                lineHeight = (11 * scale).sp
            )
            if (parts != null) {
                Text(text = parts[0], style = timeStyle, color = colors.onSurfaceVariant, maxLines = 1)
                Text(text = parts[1], style = timeStyle, color = colors.onSurfaceVariant, maxLines = 1)
            } else {
                Text(
                    text = slot.timeString, style = timeStyle, color = colors.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// EmptyGridCell 死组件已删（注释自述弃用, 全库零调用——Cards 网格走 SingleTimeHeadCell + CourseOverlayCard）。

@Composable
private fun CourseOverlayCard(
    course: CourseEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isGrey: Boolean = false,
    scale: Float = 1f,
    cornerRatio: Float = 1f,
    groupRows: List<CourseEntity> = listOf(course)
) {
    val palette = SleepyTheme.palette
    val colors = glasenseM3Scheme()
    val context = androidx.compose.ui.platform.LocalContext.current
    // 统一取色入口（决策 D3）— colorless 读取 AppPrefs course_colorless 独立开关
    // issue#22: 同名课程多地点 — 用 groupRows 传同 groupId 全行,支持 AUTO/CUSTOM 模式取色
    val colorSlots = com.imsx3d.classy.util.LocalCourseColorSlots.current
    val bg = CourseColorUtil.pickCourseColorComposeWithGroupRows(
        row = course,
        groupRows = groupRows,
        isDark = CourseColorUtil.isPaletteDark(palette),
        neutralColor = colors.surfaceVariant,
        colorless = AppPrefs.isCourseColorless(context),
        slot = colorSlots[CourseColorUtil.colorSeed(course)]
    )
    // 文字色亮度自适应（决策 D5-13）— 深色自定义课色上切白字，浅色底仍 onSurface
    val fg = CourseColorUtil.textColorOn(bg, CourseColorUtil.isPaletteDark(palette), colors.onSurface)
    // 课色条那版要的强调色 + 中性底/中性字（口径与今日页、周视图一致）
    // 课程色承载方式（设置项：通用 → 课表显示 → 课程色呈现）
    //   bar  = 中性底 + 左侧 3dp 课色条；fill = 整块课色填充 + 自适应字色。
    //   两版共用同一套取色，只差"颜色铺满格子"还是"只占左缘一条"。
    val accentBarStyle = AppPrefs.getCourseColorStyle(context) == AppPrefs.COURSE_STYLE_BAR
    val accentBar = if (accentBarStyle) CourseColorUtil.pickAccentComposeWithGroupRows(
        row = course,
        groupRows = groupRows,
        isDark = CourseColorUtil.isPaletteDark(palette),
        neutralColor = colors.onSurfaceVariant,
        colorless = AppPrefs.isCourseColorless(context),
        slot = colorSlots[CourseColorUtil.colorSeed(course)]
    ) else bg
    val shape = RoundedCornerShape((minOf(12f * cornerRatio, 16f) * scale).dp)
    val sd = { v: Float -> (v * scale).dp }
    // 副信息（教室/教师/无）— 左栏 SingleTimeHeadCell 已有节次+时间，卡片 y 位置本身编码节次，
    // 故卡内不再显示节次/时间，改由 grid_sub_info 设置决定
    val subInfo = AppPrefs.getGridSubInfo(context)
    // issue#26: 网格场景别名 — 网格设置开且别名非空才显示别名, 否则原名
    val name = CourseDisplayUtil.displayName(course, AppPrefs.isGridUseAlias(context))
    // UI-2d（2026-09-26）: 用户反馈"教室显示不全"+"课名顶部留白不一致"。原因：
    //   原实现把"教室 · 教师"拼成一行，竖向又是"课名先占、副信息垫底"，
    //   矮卡(1 节课)时副信息只剩一行 → "教4502" 被截成 "教450…"；课名垂直居中则
    //   让不同高度卡片的顶部留白各不一样。
    //   现改为：① 教室/教师各自成行；② 课名顶部对齐；③ 课名用 weight **最后测量**，
    //   空间不足时先牺牲课名行数，保证教室信息完整；④ 教师行只在卡片 ≥2 节课高时出现。
    val roomText = course.room.trim()
    val teacherText = course.teacher.trim()
    val infoEnabled = subInfo == "room" || subInfo == "teacher"
    val primaryInfo = if (subInfo == "teacher") teacherText else roomText
    val secondaryInfo = if (subInfo == "teacher") roomText else teacherText

    // Keep the original palette; dim the composed card once, regardless of the reason.
    val effectiveBg = if (accentBarStyle) colors.surfaceContainer else bg
    val effectiveFg = if (accentBarStyle) colors.onSurface else fg
    val effectiveInfoFg = if (accentBarStyle) colors.onSurfaceVariant
        else effectiveFg.copy(alpha = SleepyTheme.Alpha.highContent)
    val effectiveAccentBar = accentBar
    val holidayStyle = AppPrefs.getHolidayStyle(context)
    val textDecoration = if (isGrey && holidayStyle == "strikethrough") {
        androidx.compose.ui.text.style.TextDecoration.LineThrough
    } else null
    val nameStyle = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp,
        fontWeight = FontWeight.SemiBold,
        fontSize = (11 * scale).sp,
        lineHeight = (14 * scale).sp,
        textDecoration = textDecoration
    )
    val infoStyle = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp,
        fontSize = (9 * scale).sp,
        lineHeight = (11 * scale).sp,
        textDecoration = textDecoration
    )

    BoxWithConstraints(
        modifier = modifier
            .alpha(if (isGrey || LocalCourseCompleted.current(course)) com.imsx3d.classy.util.CourseCompletion.DIM_ALPHA else 1f)
            .padding(sd(1.5f))
            .clip(shape)
            .background(effectiveBg)
            .noRippleClickable(onClick)
            .padding(sd(3f))
    ) {
        if (accentBarStyle) {
            // 课色条：贴左缘、随格子高度（与今日页/周视图同一套观感）
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(sd(3f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(sd(1.5f)))
                    .background(effectiveAccentBar)
            )
        }
        if (!infoEnabled || primaryInfo.isBlank()) {
            // 无副信息: 课程名整体居中(原行为)
            Text(
                text = name,
                style = nameStyle,
                color = effectiveFg,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.Center)
                    .then(if (accentBarStyle) Modifier.padding(start = sd(4f)) else Modifier)
            )
        } else {
            // 卡片 ≥2 节课高(≈104dp×scale)才多给一行给教师，避免挤掉教室
            val tall = maxHeight >= sd(96f)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (accentBarStyle) Modifier.padding(start = sd(4f)) else Modifier),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    // UI-4g（用户报障）：课程名没做居中 —— 短名字（如"微观经济学"竖排 5 个字）
                    // 会贴在胶囊左侧、右边留一大片空。textAlign 缺省 = Start，必须显式居中；
                    // 同时 fillMaxWidth 让每一行都在卡宽内居中（教室/教师行一直是居中的，故只有名字出问题）。
                    Text(
                        text = name,
                        style = nameStyle,
                        color = effectiveFg,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                // 主信息（默认=教室）恒定 2 行上限：窄列里自动折行，保证"教4502"这类完整可见
                Text(
                    text = primaryInfo,
                    style = infoStyle,
                    color = effectiveInfoFg,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                if (tall && secondaryInfo.isNotBlank()) {
                    Text(
                        text = secondaryInfo,
                        style = infoStyle,
                        color = effectiveInfoFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 休/补 角标 —— 单字方章。
 *
 * 2026-09-26 修复（用户报障"文字不在小方块内部，部分凸出来"）：
 * 旧实现是给 Text 挂 `padding(3dp, 0.5dp) + RoundedCornerShape(4dp)` —— 方章高度 = 行高 + 1dp，
 * 竖向只留 0.5dp 余量；而 4dp 圆角随 gridScale 一起放大，圆角把方章四角啃掉，
 * 汉字笔画（亻的撇、木的竖，都贴着 em 边界）正好落在被啃掉的位置上 → 看着"字凸出方章"。
 * 现改法：方章边长、圆角、字号**全部由 sp 换算**（同源缩放：gridScale 调网格、
 * 系统字体放大，三者一起变），字居中后四周余量 ≈ 边长的 20%（远大于圆角啃掉的 8%），
 * 任何缩放档位下字都在章内，且方章是"方"的（圆角比例固定），不再是糊成一团的圆块。
 *
 * 横向占位另见各调用点：网格列宽只有 ~42dp，角标必须独占一行才放得下。
 */
@Composable
private fun DayMarkBadge(mark: String, scale: Float, modifier: Modifier = Modifier, containerColor: Color? = null, contentColor: Color? = null) {
    val colors = glasenseM3Scheme()
    val density = LocalDensity.current
    val side = with(density) { (BADGE_SIDE_SP * scale).sp.toDp() }
    val radius = with(density) { (BADGE_RADIUS_SP * scale).sp.toDp() }
    Box(
        modifier = modifier
            .size(side)
            .clip(RoundedCornerShape(radius))
            .background(containerColor ?: colors.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = mark,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp,
                fontWeight = FontWeight.SemiBold,
                fontSize = (BADGE_FONT_SP * scale).sp,
                lineHeight = (BADGE_FONT_SP * scale).sp
            ),
            color = contentColor ?: colors.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

private const val BADGE_SIDE_SP = 15f
private const val BADGE_FONT_SP = 9f
private const val BADGE_RADIUS_SP = 4f

/** 角标槽高（sp）——略大于方章边长，给抗锯齿留余量。恒占高，保证各列星期文字同基线。 */
private const val BADGE_SLOT_SP = 16f

/** 表头格高（sp, ×scale）：默认 34dp；显示日期时 46dp 容纳"周X / 日期"两行。 */
private const val HEAD_CELL_H_SP = 34f
private const val HEAD_CELL_H_DATE_SP = 46f

@Composable
private fun DayHeadCell(day: Int, isToday: Boolean, isGrey: Boolean = false, mark: String? = null, courseCount: Int, dateStr: String? = null, dayLabel: String = DateUtils.localizedDay(day, androidx.compose.ui.platform.LocalContext.current), modifier: Modifier = Modifier, scale: Float = 1f, cornerRatio: Float = 1f) {
    val colors = glasenseM3Scheme()
    val sd = { v: Float -> (v * scale).dp }
    val density = LocalDensity.current
    val stampSlotH = with(density) { (BADGE_SLOT_SP * scale).sp.toDp() }
    // UI-2c 重设计（2026-09-26）: 原「56dp 高的大圆角胶囊（周一 / N门）」小列宽下
    // 16~18dp 圆角看着"圆不像圆、方不像方"，且纵向吃掉 22dp 课程空间、形状与全 App 割裂。
    // 改为一行式：星期文字 + 日期小方章（今天=主色实心），高度 34dp。
    // 课程数不再单独占一行；需要日期时在网格设置里开「显示日期」。
    // UI-2f（2026-09-26 用户报障）: 「休/补」角标上移到**独占一行**（槽位恒占高，
    //   无角标也留空，各列星期文字因此仍同基线）——7 列网格单列只有 ~42dp，
    //   角标与"周五"同行放不下，会压住隔壁列（用户截图：周五的章盖在 周六 上）。
    // UI-4p（2026-09-26 用户令「组件里有今日选中特效，App 里没有」）:
    //   今天 = 表头铺主色胶囊（对齐网格组件的 wg_hdrN_pill），不再是"只有日期方章染个色"。
    //   灰显不覆盖今天的高亮 —— 与组件同款行为，"今天在哪一列"是方向锚点，不该被休假淡出掉。
    // UI-4r（2026-09-26 用户报障「深色下胶囊刺眼、字与底糊在一起」）:
    //   浅色保持实心主色 + onPrimary 字（用户定稿）；深色改 M3 容器语义
    //   （主色半透明暗底 + 主色当字色）—— 取值与理由见 util/TodayHighlight.kt。
    val dark = com.nevoit.glasense.theme.GlasenseTheme.darkTheme
    val pillBg = if (dark) {
        colors.primary.copy(alpha = com.imsx3d.classy.util.TodayHighlight.DARK_PILL_ALPHA)
    } else colors.primary
    val pillFg = if (dark) colors.primary else colors.onPrimary
    // UI-5c（2026-09-27 用户令"表头恒显日期后，今日选中特效重新想一下"）：
    // 胶囊内不再叠"次级小块"（休/补 角标 + 日期方章各压一块半透明底会变成**块里套块**），
    // 内部一律纯文字；层次交给"有胶囊 vs 无胶囊"这一对比。
    // UI-36a（2026-09-28 用户报障）：胶囊**只包文字**（角标槽位不含在内）——见下方注释。
    val fg = when {
        isToday -> pillFg
        isGrey -> colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.inactive)
        else -> colors.onSurfaceVariant
    }
    val todayPill = if (isToday) {
        Modifier.clip(RoundedCornerShape(sd(9f))).background(pillBg)
    } else Modifier
    // UI-4p: 有日期 → 两行（周X / 日期），与网格组件的表头同构；
    // 一行放不下是几何事实（列宽 ~40dp vs 内容 ~64dp），不是字号问题。
    val stackedDate = dateStr != null
    Column(
        modifier = modifier
            .height(sd(if (stackedDate) HEAD_CELL_H_DATE_SP else HEAD_CELL_H_SP)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 休/补 角标槽位恒占高（UI-2f：无角标也留空，各列星期文字才同基线）。
        Box(modifier = Modifier.height(stampSlotH), contentAlignment = Alignment.Center) {
            val mk = mark
            if (mk != null) DayMarkBadge(mark = mk, scale = scale)
        }
        // 今日胶囊**只包文字**，角标留在胶囊外（它本来就是独立的"休/补"标记）。
        //
        // 用户 2026-09-28 报障：胶囊原来套在**整格**上（含上面那 16sp 角标槽位）——
        // 今日那列若恰好没有休/补角标，槽位是空的，于是胶囊上半截变成"一大块没有内容的
        // 主色"，看着像选中特效自己撑高了。胶囊改成只裹文字块后，高度随内容收紧，
        // 有没有角标都不会再出现空块；文字基线不受影响（槽位照样占高）。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(todayPill)
                // 只有一行（关掉「显示日期」时）补 2dp 垂直呼吸，免得胶囊退化成一条细杠；
                // 两行时"角标槽位 + 两行"已经刚好填满 46dp 格高，不能再加内边距（会挤出格）。
                .padding(vertical = if (stackedDate) 0.dp else sd(2f)),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.sp,
                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                    fontSize = (12 * scale).sp,
                    lineHeight = (14 * scale).sp
                ),
                color = fg,
                maxLines = 1
            )
            val ds = dateStr
            if (ds != null) {
                Text(
                    text = ds,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = (10 * scale).sp,
                        lineHeight = (12 * scale).sp
                    ),
                    color = if (isToday) pillFg else colors.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = sd(2f))
                        .clip(RoundedCornerShape(sd(6f)))
                        .then(
                            // 恒显日期后一律不加色块：非今天写在中性底上，今天直接写在实心胶囊上
                            // （原来今天会在胶囊里再压一块半透明底 = 块中块，已按 UI-5c 去掉）
                            Modifier
                        )
                        .padding(horizontal = sd(4f), vertical = sd(1f)),
                    maxLines = 1
                )
            }
        }
    }
}


// TimeHeadCell / SpannedTimeHeadCell 死组件已删（SpannedTimeHeadCell 注释自述弃用,
// TimeHeadCell 被 SingleTimeHeadCell 取代, 两者全库零调用; CELL_H 常量随之删除）。

// pickCourseColor / isPaletteDark / hslToColor 三函数已收敛至 util/CourseColorUtil.kt（决策 D3 单一事实来源）。
// 原注释 S/L 值写错（0.48/0.88、0.35/0.26），实际为亮色 S=0.55 L=0.82 / 暗色 S=0.40 L=0.28，正确值见 CourseColorUtil 常量。

// =====================================================================================
// 7days full 视图 — switchable.html #fullView
// =====================================================================================

@Composable
fun FullWeekView(
    courses: List<CourseEntity>,
    visibleDays: Set<Int> = (1..7).toSet(),
    displayMode: String = "node",
    timeJson: String = "",
    today: Int = DateUtils.todayDayOfWeek(),
    todayDate: java.time.LocalDate = java.time.LocalDate.now(),
    startDate: String = "",
    currentWeek: Int = 1,
    onCourseClick: (CourseEntity) -> Unit,
    modifier: Modifier = Modifier,
    greyDays: Set<Int> = emptySet(),
    greyMarks: Map<Int, String> = emptyMap()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // 设置页改 weekScale/cornerRatio/twoColumn/hideEmptyDays/别名后强制 recompose
    var prefVersion by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        AppPrefs.changeBus.filter {
            it == AppPrefs.KEY_WEEK_SCALE || it == AppPrefs.KEY_GRID_CORNER_RATIO ||
            it == AppPrefs.KEY_WEEK_TWO_COLUMN || it == AppPrefs.KEY_WEEK_TWO_COLUMN_MODE ||
            it == AppPrefs.KEY_WEEK_HIDE_EMPTY_DAYS || it == AppPrefs.KEY_WEEK_USE_ALIAS ||
            it == AppPrefs.KEY_COURSE_COLOR_STYLE || it == AppPrefs.KEY_COURSE_COLORLESS
        }.collect { prefVersion++ }
    }
    val scale = AppPrefs.getWeekScale(context)
    val cornerRatio = AppPrefs.getGridCornerRatio(context)
    val twoColumn = AppPrefs.isWeekTwoColumn(context)
    val twoColumnMode = AppPrefs.getWeekTwoColumnMode(context)
    val hideEmptyDays = AppPrefs.isWeekHideEmptyDays(context)
    // issue#26: 周视图场景别名 — 周视图设置开才用别名; 传给所有子渲染单元
    val useAlias = AppPrefs.isWeekUseAlias(context)
    val byDay = courses.groupBy { it.day }
    val navExtra = com.imsx3d.classy.ui.component.LocalNavExtraBottomPadding.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        WeekStrip(
            byDay = byDay,
            visibleDays = visibleDays,
            today = today,
            todayDate = todayDate,
            startDate = startDate,
            currentWeek = currentWeek,
            greyDays = greyDays,
            greyMarks = greyMarks,
            useAlias = useAlias,
            scale = scale,
            cornerRatio = cornerRatio,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
        DetailPanel(
            byDay = byDay,
            visibleDays = visibleDays,
            displayMode = displayMode,
            timeJson = timeJson,
            today = today,
            todayDate = todayDate,
            startDate = startDate,
            currentWeek = currentWeek,
            onCourseClick = onCourseClick,
            greyDays = greyDays,
            // UI-7c 顺带修：顶部 WeekStrip 有「休/补」角标，下面的详情卡却没有 —— 同屏两套口径
            greyMarks = greyMarks,
            scale = scale,
            cornerRatio = cornerRatio,
            twoColumn = twoColumn,
            twoColumnMode = twoColumnMode,
            hideEmptyDays = hideEmptyDays
        )
        // Dock 悬浮底栏: 滚动尾部多留 Dock 总高(同网格视图)
        if (navExtra > 0.dp) Spacer(modifier = Modifier.height(navExtra))
    }
}

@Composable
private fun WeekStrip(
    greyMarks: Map<Int, String> = emptyMap(),
    byDay: Map<Int, List<CourseEntity>>,
    today: Int,
    todayDate: java.time.LocalDate,
    startDate: String,
    currentWeek: Int,
    visibleDays: Set<Int>,
    greyDays: Set<Int> = emptySet(),
    useAlias: Boolean = false,
    scale: Float = 1f,
    cornerRatio: Float = 1f,
    modifier: Modifier = Modifier
) {
    val sd = { v: Float -> (v * scale).dp }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(sd(6f))
    ) {
        for (day in visibleDays.sorted()) {
            val dayCourses = byDay[day].orEmpty()
            val isToday = startDate.isNotBlank() && runCatching {
                DateUtils.isDateToday(DateUtils.dateOfWeek(startDate, currentWeek, day), todayDate)
            }.getOrDefault(false)
            DaySummaryCell(
                day = day,
                courses = dayCourses,
                isToday = isToday,
                isGrey = day in greyDays,
                mark = greyMarks[day],
                useAlias = useAlias,
                scale = scale,
                cornerRatio = cornerRatio,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DaySummaryCell(
    day: Int,
    mark: String? = null,
    courses: List<CourseEntity>,
    isToday: Boolean,
    isGrey: Boolean = false,
    useAlias: Boolean = false,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    cornerRatio: Float = 1f
) {
    val colors = glasenseM3Scheme()
    val context = androidx.compose.ui.platform.LocalContext.current
    val sd = { v: Float -> (v * scale).dp }
    val stampSlotH = with(LocalDensity.current) { (BADGE_SLOT_SP * scale).sp.toDp() }
    val bg = if (isToday) colors.primaryContainer else colors.surfaceContainer
    val fg = if (isGrey) colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.inactive) else if (isToday) colors.onPrimaryContainer else colors.onSurface
    // Chip: solid surfaceVariant with full alpha for dark mode readability
    val chipBg = colors.surfaceVariant
    val chipFg = colors.onSurfaceVariant.copy(alpha = if (isGrey) SleepyTheme.Alpha.inactive else 1f)

    Column(
        modifier = modifier
            .height((132 * scale).dp)
            .clip(RoundedCornerShape((minOf(12f * cornerRatio, 16f) * scale).dp))
            .background(bg)
            .padding(horizontal = sd(6f), vertical = sd(8f))
    ) {
        // 休/补 角标独占一行（同 DayHeadCell）：7 列时单列只有 ~50dp，
        // 与"周一"同行放不下会压住隔壁列。槽位恒占高，各列星期文字仍同基线。
        Box(modifier = Modifier.fillMaxWidth().height(stampSlotH), contentAlignment = Alignment.Center) {
            val mk = mark
            if (mk != null) DayMarkBadge(mark = mk, scale = scale)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            // ⚠️ 网格内文字是**独立的一档**（不受 `设计规范_字体体系.md` 的 6 档约束）：
            // 这里所有字号都乘 `scale`（用户的「网格缩放」），且受格子高度硬约束 ——
            // 表头 13、格内课名 9、计数胶囊 10、空列提示 12、侧边节次 10 都是**基准值 × scale**。
            // 改这些基准值等于改整张网格的排版，必须连着格子高度一起调，别单独动。
            Text(
                text = DateUtils.localizedDay(day, context),
                style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold, fontSize = (13 * scale).sp, lineHeight = (18 * scale).sp),
                color = fg,
                maxLines = 1
            )
        }

        Spacer(modifier = Modifier.height(sd(6f)))

        // Chip: 课程数 — 完整文字在列宽内换行就退化为纯数字，胶囊自动缩到数字宽度
        if (courses.isEmpty()) {
            Spacer(modifier = Modifier.height(sd(14f)))
        } else {
            val fullText = stringResource(R.string.course_count_format, courses.size)
            val numberText = courses.size.toString()
            val chipStyle = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold, fontSize = (10 * scale).sp, lineHeight = (14 * scale).sp)
            val textMeasurer = rememberTextMeasurer()
            BoxWithConstraints(modifier = Modifier.align(Alignment.CenterHorizontally)) {
                val chipPaddingPx = with(LocalDensity.current) { sd(14f).toPx() }.toInt()
                val availablePx = with(LocalDensity.current) { maxWidth.toPx() }.toInt()
                val layoutResult = textMeasurer.measure(
                    text = fullText,
                    style = chipStyle,
                    constraints = Constraints(maxWidth = (availablePx - chipPaddingPx).coerceAtLeast(0))
                )
                val showNumberOnly = layoutResult.lineCount > 1
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(chipBg)
                        .padding(horizontal = sd(7f), vertical = sd(2f))
                ) {
                    Text(
                        text = if (showNumberOnly) numberText else fullText,
                        style = chipStyle,
                        color = chipFg,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(sd(4f)))

        // Mini-list: 前 5 门课名（改掉 take(3)，空间够就全显示）
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(sd(2f))
        ) {
            courses.take(5).forEach { c ->
                Text(
                    text = com.imsx3d.classy.util.CourseDisplayUtil.displayName(c, useAlias),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp, fontSize = (9 * scale).sp, lineHeight = (11 * scale).sp),
                    color = if (isToday) colors.onPrimaryContainer.copy(alpha = SleepyTheme.Alpha.highContent) else colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DetailPanel(
    byDay: Map<Int, List<CourseEntity>>,
    today: Int,
    todayDate: java.time.LocalDate,
    startDate: String,
    currentWeek: Int,
    visibleDays: Set<Int>,
    displayMode: String,
    timeJson: String,
    onCourseClick: (CourseEntity) -> Unit,
    greyDays: Set<Int> = emptySet(),
    greyMarks: Map<Int, String> = emptyMap(),
    scale: Float = 1f,
    cornerRatio: Float = 1f,
    twoColumn: Boolean = false,
    twoColumnMode: String = "days",
    hideEmptyDays: Boolean = false
) {
    val colors = glasenseM3Scheme()
    val sd = { v: Float -> (v * scale).dp }
    // issue#8 隐藏无课日 — 单栏/两栏都生效
    val sortedDays = visibleDays.sorted().let {
        if (hideEmptyDays) it.filter { d -> byDay[d].orEmpty().isNotEmpty() } else it
    }

    // issue#8 周视图两栏, 省纵向滚动; 分栏标准由设置选择:
    //   days    = 按天对半分 — 前半周左/后半周右, 天数固定
    //   balance = 按课程数动态平衡 — 逐天放进当天卡片(约)更矮的栏, 两栏高度接近
    // 隐藏无课日后按剩余天数平分: 6 天=3+3, 4 天=2+2; 奇数天多的一天落左栏
    if (twoColumn && sortedDays.size >= 2) {
        val split: Pair<List<Int>, List<Int>> = if (twoColumnMode == "balance") {
            // 贪心: 按天序遍历, 每天记权重 = 课程数(权重按 DetailDayCard 高度近似, 空天也有卡头所以记 1)
            var l = 0; var r = 0
            val left = mutableListOf<Int>(); val right = mutableListOf<Int>()
            for (day in sortedDays) {
                val w = (byDay[day].orEmpty().size).coerceAtLeast(1)
                if (l <= r) { left.add(day); l += w } else { right.add(day); r += w }
            }
            left to right
        } else {
            val splitIdx = (sortedDays.size + 1) / 2
            sortedDays.subList(0, splitIdx) to sortedDays.subList(splitIdx, sortedDays.size)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(sd(12f)),
            horizontalArrangement = Arrangement.spacedBy(sd(10f)),
            verticalAlignment = Alignment.Top
        ) {
            DayColumn(
                days = split.first,
                byDay = byDay, today = today, todayDate = todayDate, startDate = startDate,
                currentWeek = currentWeek, displayMode = displayMode, timeJson = timeJson,
                onCourseClick = onCourseClick, greyDays = greyDays, greyMarks = greyMarks,
                scale = scale, cornerRatio = cornerRatio,
                modifier = Modifier.weight(1f)
            )
            if (split.second.isNotEmpty()) {
                DayColumn(
                    days = split.second,
                    byDay = byDay, today = today, todayDate = todayDate, startDate = startDate,
                    currentWeek = currentWeek, displayMode = displayMode, timeJson = timeJson,
                    onCourseClick = onCourseClick, greyDays = greyDays, greyMarks = greyMarks,
                    scale = scale, cornerRatio = cornerRatio,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    } else {
        // 单栏(或两栏下过滤后不足 2 天) — 显示剩余星期(全空周时=全部所选星期, 不吃掉无课日)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape((minOf(16f * cornerRatio, 18f) * scale).dp))
                .background(colors.surfaceContainerHigh)
                .padding(sd(12f)),
            verticalArrangement = Arrangement.spacedBy(sd(10f))
        ) {
            for (day in sortedDays) {
                val dayCourses = byDay[day].orEmpty().sortedBy { it.startNode }
                DetailDayCard(
                    day = day,
                    courses = dayCourses,
                    isToday = startDate.isNotBlank() && runCatching {
                        DateUtils.isDateToday(DateUtils.dateOfWeek(startDate, currentWeek, day), todayDate)
                    }.getOrDefault(false),
                    displayMode = displayMode,
                    timeJson = timeJson,
                    onCourseClick = onCourseClick,
                    isGrey = day in greyDays,
                    mark = greyMarks[day],
                    scale = scale,
                    cornerRatio = cornerRatio
                )
            }
        }
    }
}

/** 两栏模式的单侧栏 — 半周的天卡片竖排在一个独立面板里 */
@Composable
private fun DayColumn(
    days: List<Int>,
    byDay: Map<Int, List<CourseEntity>>,
    today: Int,
    todayDate: java.time.LocalDate,
    startDate: String,
    currentWeek: Int,
    displayMode: String,
    timeJson: String,
    onCourseClick: (CourseEntity) -> Unit,
    greyDays: Set<Int>,
    greyMarks: Map<Int, String> = emptyMap(),
    scale: Float,
    cornerRatio: Float,
    modifier: Modifier = Modifier
) {
    val colors = glasenseM3Scheme()
    val sd = { v: Float -> (v * scale).dp }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape((minOf(16f * cornerRatio, 18f) * scale).dp))
            .background(colors.surfaceContainerHigh)
            .padding(sd(10f)),
        verticalArrangement = Arrangement.spacedBy(sd(10f))
    ) {
        for (day in days) {
            val dayCourses = byDay[day].orEmpty().sortedBy { it.startNode }
            DetailDayCard(
                day = day,
                courses = dayCourses,
                isToday = startDate.isNotBlank() && runCatching {
                    DateUtils.isDateToday(DateUtils.dateOfWeek(startDate, currentWeek, day), todayDate)
                }.getOrDefault(false),
                displayMode = displayMode,
                timeJson = timeJson,
                onCourseClick = onCourseClick,
                isGrey = day in greyDays,
                mark = greyMarks[day],
                scale = scale,
                cornerRatio = cornerRatio
            )
        }
    }
}

@Composable
private fun DetailDayCard(
    day: Int,
    courses: List<CourseEntity>,
    isToday: Boolean,
    isGrey: Boolean = false,
    mark: String? = null,
    displayMode: String = "node",
    timeJson: String = "",
    onCourseClick: (CourseEntity) -> Unit,
    scale: Float = 1f,
    cornerRatio: Float = 1f
) {
    val colors = glasenseM3Scheme()
    val context = androidx.compose.ui.platform.LocalContext.current
    val sd = { v: Float -> (v * scale).dp }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape((minOf(12f * cornerRatio, 16f) * scale).dp))
            .background(if (courses.isEmpty()) colors.surfaceContainerLow else colors.surface)
            .padding(sd(10f)),
        verticalArrangement = Arrangement.spacedBy(sd(8f))
    ) {
        // 头部：星期 + 今天标记 / 右端「休·补」角标（口径同顶部 WeekStrip 与网格表头）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = DateUtils.localizedDay(day, context) + if (isToday) stringResource(R.string.today_suffix) else "",
                style = MaterialTheme.typography.titleSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.SemiBold),
                color = if (isGrey) colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.inactive) else colors.onSurface
            )
            val mk = mark
            if (mk != null) DayMarkBadge(mark = mk, scale = scale)
        }

        if (courses.isEmpty()) {
            Text(
                text = DateUtils.localizedDay(day, context) + stringResource(R.string.no_course_today),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp, fontSize = (12 * scale).sp, lineHeight = (16 * scale).sp),
                color = if (isGrey) colors.onSurfaceVariant.copy(alpha = SleepyTheme.Alpha.inactive) else colors.onSurfaceVariant
            )
        } else {
            // v7.10.6 行分组下沉引擎: mergeOverlapping 区域是划分(每课恰属一区域),
            // 冲突区域整区域一行(横向 laneCount 栏,同栏多门课纵向堆叠),
            // 无冲突课一行一门全宽。结构上保证不丢课、不重复(用户 2026-09-02 报障修复)。
            // 用户报障 2026-09-10: 分组走时间域 — FullWeekView 的 courses 输入虽已
            // normalizeNode, ownTime 课的真实分钟重叠判定仍需 timeJson 才与网格一致。
            val rows = remember(courses, timeJson) {
                ConflictLayoutEngine.weekLaneRows(courses, timeJson.ifBlank { null })
            }

            Column(verticalArrangement = Arrangement.spacedBy(sd(7f))) {
                rows.forEach { row ->
                    if (row.laneCount == 1) {
                        LessonRow(
                            course = row.courses[0], displayMode = displayMode, timeJson = timeJson,
                            onClick = { onCourseClick(row.courses[0]) }, isGrey = isGrey,
                            scale = scale, cornerRatio = cornerRatio,
                            groupRows = courses.filter { it.groupId == row.courses[0].groupId }
                        )
                    } else {
                        // 冲突行: 按 lane 并排,每列 weight 均分,同栏课程纵向堆叠
                        // (栏内课互不重叠——chainGroups 独立集保证,堆叠即正确时序)。
                        // v7.10.4: BoxWithConstraints 拿 lane 实宽 → 字体/间距按比例压缩
                        // (两栏模式下 lane 半宽,不缩则字全挤在一起——用户 2026-09-02)
                        // v7.10.10: 栏间画浅细竖分隔线(用户 2026-09-02)
                        val laneCount = row.laneCount
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val laneGap = sd(6f)
                            val laneW = (maxWidth - laneGap * (laneCount - 1)) / laneCount
                            val laneScale = weekLaneFontScale(laneW)
                            val hideSide = weekLaneHideSideLabel(laneW)
                            Row(
                                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(laneGap)
                            ) {
                                repeat(laneCount) { li ->
                                    if (li > 0) {
                                        // 栏间浅细竖线: 0.5dp 宽, onSurface 0.3 透明度, 高度随行
                                        // (Row IntrinsicSize.Min → fillMaxHeight 有界, 随最高栏撑满)
                                        Box(
                                            modifier = Modifier
                                                .width(0.5.dp)
                                                .fillMaxHeight()
                                                .background(
                                                    colors.onSurface.copy(alpha = SleepyTheme.Alpha.hairline)
                                                )
                                        )
                                    }
                                    val laneCourses = row.courses.filter { row.laneOf[it.id] == li }
                                    Box(modifier = Modifier.weight(1f)) {
                                        Column(verticalArrangement = Arrangement.spacedBy(sd(5f))) {
                                            laneCourses.forEach { laneCourse ->
                                                LessonRow(
                                                    course = laneCourse, displayMode = displayMode,
                                                    timeJson = timeJson,
                                                    onClick = { onCourseClick(laneCourse) },
                                                    isGrey = isGrey, scale = scale, cornerRatio = cornerRatio,
                                                    laneScale = laneScale, hideSideLabel = hideSide,
                                                    groupRows = courses.filter { it.groupId == laneCourse.groupId }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =====================================================================================
// v7.10.4 冲突栏字号/间距压缩 — 用户 2026-09-02: 两栏模式下分栏字全挤到一起,
// 需按 lane 实宽调画面比例。
// =====================================================================================

/** lane 宽压缩基准: 单栏半宽量级(用户实测"比较好"的现状),低于它开始线性缩。 */
internal val WEEK_LANE_SCALE_BASE = 150f

/** 压缩下限 — 再窄也不无限缩,可读性由隐藏侧栏标签/副信息兜底。 */
internal const val WEEK_LANE_SCALE_FLOOR = 0.6f

/**
 * 冲突栏内 LessonRow 的缩放比(纯函数可测):
 *   laneW ≥ 150dp → 1.0(保现状);以下线性压缩;0.6 封底。
 */
internal fun weekLaneFontScale(laneW: Dp): Float =
    if (laneW >= WEEK_LANE_SCALE_BASE.dp) 1f
    else (laneW.value / WEEK_LANE_SCALE_BASE).coerceAtLeast(WEEK_LANE_SCALE_FLOOR)

/** 极窄 lane 判定: 侧栏节次/时间标签(42dp+8dp 间距)挤占正文 → 隐藏它。 */
internal fun weekLaneHideSideLabel(laneW: Dp): Boolean = laneW < 110.dp

@Composable
private fun LessonRow(
    course: CourseEntity,
    displayMode: String,
    timeJson: String,
    onClick: () -> Unit,
    isGrey: Boolean = false,
    scale: Float = 1f,
    cornerRatio: Float = 1f,
    laneScale: Float = 1f,
    hideSideLabel: Boolean = false,
    groupRows: List<CourseEntity> = listOf(course)
) {
    val colors = glasenseM3Scheme()
    val palette = SleepyTheme.palette
    val context = androidx.compose.ui.platform.LocalContext.current
    // issue#26: 周视图场景别名 — 与 colorless 同模式, 叶子直接读场景开关
    val name = CourseDisplayUtil.displayName(course, AppPrefs.isWeekUseAlias(context))
    // 双层缩放: scale=全局周视图缩放(issue#8), laneScale=v7.10.4 冲突栏按实宽压缩
    val effScale = scale * laneScale
    val sd = { v: Float -> (v * effScale).dp }
    // 2026-09-28（用户报「色块对比度过高、刺眼」→ 随后定「两版都留，做成设置项」）：
    //   bar   = 中性卡片 + 左侧 4dp 课色条 + 时间用课色（克制，与今日页同源）；
    //   fill  = 整行填课色 + 自适应文字色（醒目，即本页原来的观感）。
    //   两种承载共用同一套取色，只差"颜色铺多大"。设置项：通用 → 课表显示 → 课程色呈现。
    val barStyle = AppPrefs.getCourseColorStyle(context) == AppPrefs.COURSE_STYLE_BAR
    val fillBg = CourseColorUtil.pickCourseColorComposeWithGroupRows(
        row = course,
        groupRows = groupRows,
        isDark = CourseColorUtil.isPaletteDark(palette),
        neutralColor = colors.surfaceVariant,
        colorless = AppPrefs.isCourseColorless(context),
        slot = com.imsx3d.classy.util.LocalCourseColorSlots.current[CourseColorUtil.colorSeed(course)]
    )
    val accent = CourseColorUtil.pickAccentComposeWithGroupRows(
        row = course,
        groupRows = groupRows,
        isDark = CourseColorUtil.isPaletteDark(palette),
        neutralColor = colors.onSurfaceVariant,
        colorless = AppPrefs.isCourseColorless(context),
        slot = com.imsx3d.classy.util.LocalCourseColorSlots.current[CourseColorUtil.colorSeed(course)]
    )
    val effectiveBg = if (barStyle) colors.surfaceContainer else fillBg
    val effectiveAccent = accent
    val effectiveFg = if (barStyle) colors.onSurface
        else CourseColorUtil.textColorOn(fillBg, CourseColorUtil.isPaletteDark(palette), colors.onSurface)
    val effectiveFgVariant = if (barStyle) colors.onSurfaceVariant
        else effectiveFg.copy(alpha = SleepyTheme.Alpha.highContent)
    val holidayStyle = AppPrefs.getHolidayStyle(context)
    val textDecoration = if (isGrey && holidayStyle == "strikethrough") androidx.compose.ui.text.style.TextDecoration.LineThrough else null

    // time 模式：「08:00-\n08:45」——时间段在连字符后折行，行距收紧读成一个整体
    val timeParts = if (displayMode == "time" && timeJson.isNotBlank()) {
        TimeTableUtils.courseTimeParts(course.startNode, course.step, timeJson, course.ownTime, course.startTime, course.endTime)
    } else null
    val nodeLabel = course.shortNodeString(context)

    Row(
        modifier = Modifier
            .alpha(if (isGrey || LocalCourseCompleted.current(course)) com.imsx3d.classy.util.CourseCompletion.DIM_ALPHA else 1f)
            .fillMaxWidth()
            // IntrinsicSize.Min：左侧色条要 fillMaxHeight 撑到内容高（今日页同款写法）
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(sd(minOf(12f * cornerRatio, 16f))))
            .background(effectiveBg)
            .noRippleClickable(onClick)
            .padding(start = sd(if (barStyle) 8f else 9f), end = sd(9f), top = sd(9f), bottom = sd(9f)),
        horizontalArrangement = Arrangement.spacedBy(sd(8f))
    ) {
        // 课程色条（贴左缘，高度随内容）—— 与今日页同一套；只有 bar 版画
        if (barStyle) {
            Box(
                modifier = Modifier
                    .width(sd(4f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(sd(2f)))
                    .background(effectiveAccent)
            )
        }
        val sideStyle = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp,
            fontSize = (12 * effScale).sp,
            lineHeight = (16 * effScale).sp,
            fontWeight = FontWeight.SemiBold,
            textDecoration = textDecoration
        )
        // 极窄 lane: 侧栏标签(42dp+8dp)挤占正文 → 隐藏,节次信息由卡片纵向位置表达
        if (!hideSideLabel) {
            if (timeParts != null) {
                Text(
                    text = "${timeParts.first}-\n${timeParts.second}",
                    style = sideStyle,
                    color = effectiveAccent,
                    modifier = Modifier.width(sd(42f))
                )
            } else {
                Text(
                    text = nodeLabel,
                    style = sideStyle,
                    color = if (barStyle) effectiveAccent else effectiveFg,
                    modifier = Modifier.width(sd(42f)),
                    maxLines = 1
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = (12 * effScale).sp,
                    lineHeight = (16 * effScale).sp,
                    lineHeightStyle = LineHeightStyle(
                        alignment = LineHeightStyle.Alignment.Top,
                        trim = LineHeightStyle.Trim.FirstLineTop
                    ),
                    textDecoration = textDecoration
                ),
                color = effectiveFg,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = buildString {
                if (course.teacher.isNotBlank()) append(course.teacher)
                if (course.room.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(course.room)
                }
            }
            // 极窄 lane 下副信息(教师/教室)也让位给课名
            if (meta.isNotEmpty() && !hideSideLabel) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp,
                        fontSize = (11 * effScale).sp,
                        lineHeight = (14 * effScale).sp,
                        textDecoration = textDecoration
                    ),
                    color = effectiveFgVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// =====================================================================================
// 公共小组件
// =====================================================================================

@Composable
fun SectionHead(title: String, action: String? = null) {
    val colors = glasenseM3Scheme()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface
        )
        if (action != null) {
            Text(
                text = action,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp,
                    color = colors.primary
                )
            )
        }
    }
}


// sp 已被上面 textStyle 直接用 inline
