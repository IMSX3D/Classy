package com.imsx3d.classy.ui.screen.today

import androidx.compose.ui.draw.alpha
import com.imsx3d.classy.ui.component.rememberCourseClock
import com.imsx3d.classy.util.CourseCompletion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.imsx3d.classy.R
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.ui.component.CourseDetailSheet
import com.imsx3d.classy.ui.component.LocalNavExtraBottomPadding
import com.imsx3d.classy.ui.screen.schedule.ScheduleViewModel
import com.imsx3d.classy.ui.theme.noRippleClickable
import com.imsx3d.classy.util.AppPrefs
import com.imsx3d.classy.util.CourseColorUtil
import com.imsx3d.classy.util.DateUtils
import com.imsx3d.classy.util.TimeTableUtils
import com.nevoit.glasense.core.component.HGap
import com.nevoit.glasense.core.component.Icon
import com.nevoit.glasense.core.component.Text
import com.nevoit.glasense.core.component.VGap
import com.nevoit.glasense.theme.GlasenseTheme
import java.time.LocalDate

/**
 * 今日页 —— Glasense 视觉层（UI-1，2026-09-25）。
 *
 * 本次只替换**展示层**：数据来源、周次/调休/学期状态判定、冲突分栏（weekLaneRows）
 * 与详情弹层的行为与之前逐字一致，改的是版式与配色语言：
 *   · 页面底色走 Glasense pageBackground（中性）
 *   · 头部由「大圆角卡片」改为 Glasense 的「大标题 + 日期 + 状态胶囊」
 *   · 课程卡由「高饱和满铺色块」改为「中性卡 + 课程色色条 + 课程色时间字」，
 *     信息层级靠字号与颜色深浅区分（不再靠大色块压过内容）
 *   · 圆角统一由 Glasense specs 提供（卡片 12dp）
 */
@Composable
fun TodayScreen(
    onEditCourse: (CourseEntity) -> Unit = {},
    viewModel: ScheduleViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val now = rememberCourseClock()
    val today = now.toLocalDate()
    val actualWeek = state.currentTable?.let { DateUtils.currentWeek(it.startDate, today) } ?: state.currentWeek
    // 学期外感知: BEFORE_START/AFTER_END 时今日课不按周过滤展示
    val semesterStatus = state.currentTable?.let {
        val source = com.imsx3d.classy.util.CourseDateResolver.teachingDate(today, state.transfers) ?: today
        DateUtils.semesterStatus(it.startDate, it.maxWeek, source)
    } ?: DateUtils.SemesterStatus.IN_RANGE
    val todayCourses = state.effectiveCurrentTable?.let { table ->
        com.imsx3d.classy.util.CourseDateResolver.coursesOn(today, table.startDate, table.maxWeek, state.courses, state.transfers)
    }.orEmpty().let { list ->
        // 用户报障 2026-09-10: ownTime 课渲染前按真实时间归一化节点(与网格同一预处理),
        // 落库的表单占位节点不再影响今日页分组与显示。
        val tj = state.effectiveCurrentTable?.timeJson
        if (tj == null) list else list.map { c -> c.normalizeNode(tj) }
    }.sortedBy { it.startNode }

    // v7.10.10 今日页冲突分栏 — 与周视图同一引擎同一分组(weekLaneRows):
    // 冲突区域一行内并排分栏(栏间浅细竖线), 无冲突课整宽单行。
    // 分组在 LazyColumn 外 remember(LazyListScope 非 composable 上下文)。
    val laneRows = remember(todayCourses, state.effectiveCurrentTable?.timeJson) {
        com.imsx3d.classy.util.ConflictLayoutEngine.weekLaneRows(
            todayCourses, state.effectiveCurrentTable?.timeJson
        )
    }

    var selectedCourse by remember { mutableStateOf<CourseEntity?>(null) }

    // Dock 悬浮底栏: 滚动尾部多留 Dock 总高, 最后一项能滚到 Dock 上方(FAB 语义)
    val navExtra = LocalNavExtraBottomPadding.current
    val g = GlasenseTheme.colors
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(g.pageBackground),
        contentPadding = PaddingValues(
            // UI-25a：20dp → 16dp（同"我的"页：全 App 统一 16dp 左线）
            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp + navExtra
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TodayHeroHeader(
                date = today, week = actualWeek,
                count = todayCourses.size, semesterStatus = semesterStatus
            )
        }

        if (todayCourses.isEmpty()) {
            item { EmptyToday(semesterStatus = semesterStatus) }
        } else {
            item { SectionLabel(title = stringResource(R.string.widget_today_label), trailing = stringResource(R.string.n_periods, todayCourses.size)) }
            // v7.10.10 今日页冲突分栏 — 分组已提至 LazyColumn 外
            laneRows.forEach { row ->
                if (row.laneCount == 1) {
                    item(key = row.courses[0].id) {
                        TodayCourseCard(
                            course = row.courses[0],
                            timeJson = state.effectiveCurrentTable?.timeJson,
                            now = now,
                            onClick = { selectedCourse = row.courses[0] },
                            groupRows = todayCourses.filter { it.groupId == row.courses[0].groupId }
                        )
                    }
                } else {
                    item(key = "conflict-${row.courses.first().id}") {
                        val laneGap = 10.dp
                        Row(
                            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(laneGap)
                        ) {
                            repeat(row.laneCount) { li ->
                                if (li > 0) {
                                    // 栏间浅细竖线 — 与周视图分栏同款
                                    Box(
                                        modifier = Modifier
                                            .width(0.5.dp)
                                            .fillMaxHeight()
                                            .background(g.scrimBold)
                                    )
                                }
                                val laneCourses = row.courses.filter { row.laneOf[it.id] == li }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    laneCourses.forEach { laneCourse ->
                                        TodayCourseCard(
                                            course = laneCourse,
                                            timeJson = state.effectiveCurrentTable?.timeJson,
                                            now = now,
                                            onClick = { selectedCourse = laneCourse },
                                            groupRows = todayCourses.filter { it.groupId == laneCourse.groupId }
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

    // 详情 Bottom Sheet — 与课表页同一组件同一交互（视觉换装留待 UI-3）
    CourseDetailSheet(
        course = selectedCourse,
        timeString = selectedCourse?.let { it.nodeString(LocalContext.current) },
        allCourses = todayCourses,
        timeJson = state.effectiveCurrentTable?.timeJson,
        onDismiss = { selectedCourse = null },
        onEdit = { course ->
            selectedCourse = null
            onEditCourse(course)
        }
    )
}

/** 大标题 + 日期 + 状态胶囊 —— Glasense 的「大标题头」版式（替代原大圆角卡片）。 */
@Composable
private fun TodayHeroHeader(
    date: LocalDate,
    week: Int,
    count: Int,
    semesterStatus: DateUtils.SemesterStatus = DateUtils.SemesterStatus.IN_RANGE
) {
    val g = GlasenseTheme.colors
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = stringResource(R.string.today_today),
            style = GlasenseTheme.type.footnoteEmphasized,
            color = g.contentVariant
        )
        VGap(4.dp)
        /** 标题行：日期用 largeTitleEmphasized(32sp 半粗)，星期紧随其后、基线略上抬 */
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = stringResource(R.string.date_long_format, date.monthValue, date.dayOfMonth),
                style = GlasenseTheme.type.largeTitleEmphasized,
                color = g.content
            )
            HGap(8.dp)
            Text(
                text = DateUtils.localizedDay(date.dayOfWeek.value, context),
                style = GlasenseTheme.type.title3Emphasized,
                color = g.contentVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        VGap(10.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // 学期外: 周次 chip 换学期状态, 不再显示误导性的"第 1 周"
            when (semesterStatus) {
                DateUtils.SemesterStatus.BEFORE_START ->
                    StatPill(stringResource(R.string.semester_not_started))
                DateUtils.SemesterStatus.AFTER_END ->
                    StatPill(stringResource(R.string.semester_ended))
                else ->
                    StatPill(stringResource(R.string.schedule_current_week, week))
            }
            StatPill(
                if (count == 0) stringResource(R.string.no_course)
                else stringResource(R.string.n_course_periods, count)
            )
        }
    }
}

/** 状态胶囊 —— 卡面底 + 次要文字色（页面是 #F3F4F6 浅灰，胶囊用卡面白才分得清）。
 *  Glasense 的 scrim 系列只有 5%~20% 黑，直接铺在页面底上几乎看不见，故取 cardBackground。 */
@Composable
private fun StatPill(label: String) {
    val g = GlasenseTheme.colors
    Text(
        text = label,
        style = GlasenseTheme.type.footnoteEmphasized,
        color = g.contentVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(g.cardBackground)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

/** 分组小标题：左标题 + 右计数，均为 Glasense 字号阶梯内的次级样式。 */
@Composable
private fun SectionLabel(title: String, trailing: String) {
    val g = GlasenseTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, style = GlasenseTheme.type.subHeadlineEmphasized, color = g.content)
        Box(modifier = Modifier.weight(1f))
        Text(text = trailing, style = GlasenseTheme.type.footnote, color = g.contentVariant)
    }
}

@Composable
private fun EmptyToday(semesterStatus: DateUtils.SemesterStatus = DateUtils.SemesterStatus.IN_RANGE) {
    val g = GlasenseTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlasenseTheme.specs.cardShape)
            .background(g.cardBackground)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = g.contentVariant,
            modifier = Modifier.size(40.dp)
        )
        val (title, hint) = when (semesterStatus) {
            DateUtils.SemesterStatus.BEFORE_START ->
                stringResource(R.string.semester_not_started) to stringResource(R.string.today_semester_out_hint)
            DateUtils.SemesterStatus.AFTER_END ->
                stringResource(R.string.semester_ended) to stringResource(R.string.today_semester_out_hint)
            else ->
                stringResource(R.string.schedule_no_course_today) to stringResource(R.string.today_no_course)
        }
        Text(text = title, style = GlasenseTheme.type.subHeadlineEmphasized, color = g.content)
        Text(text = hint, style = GlasenseTheme.type.footnote, color = g.contentVariant)
    }
}

/**
 * 课程卡 —— 中性卡面 + 课程色色条 + 课程色时间字。
 * 原实现是「课程色满铺卡面 + 自动反色文字」，饱和度高、整屏色块抢眼；
 * 这里把课程色降级为**强调用色**（色条/时间/节次），文字回到中性前景，阅读层级更清楚。
 * 冲突分栏（同 groupId 多行取色）与无色模式逻辑保持不变。
 */
@Composable
private fun TodayCourseCard(
    course: CourseEntity,
    now: java.time.LocalDateTime,
    timeJson: String? = null,
    onClick: (() -> Unit)? = null,
    groupRows: List<CourseEntity> = listOf(course)
) {
    val g = GlasenseTheme.colors
    val context = LocalContext.current
    val colorless = AppPrefs.isCourseColorless(context)
    // 统一取色入口 — 种子是**课程名**（同门课在任何课表/任何一次导入里恒为同色）。
    // 2026-09-28：色条/时间属于"强调用色"，必须取**强调档**（更浓）——
    // 早先误用填充档（L=0.80 的粉彩），细条在浅色模式下几乎看不见，三门课看着一个色。
    // issue#22: 同名课程多地点 — 用 groupRows 传同 groupId 全行, 支持 AUTO/CUSTOM 模式取色
    val accent = if (colorless) g.contentVariant else CourseColorUtil.pickAccentComposeWithGroupRows(
        row = course,
        groupRows = groupRows,
        isDark = GlasenseTheme.darkTheme,
        neutralColor = g.contentVariant,
        colorless = false,
        slot = com.imsx3d.classy.util.LocalCourseColorSlots.current[CourseColorUtil.colorSeed(course)]
    )
    val time = if (course.ownTime && course.startTime.isNotBlank() && course.endTime.isNotBlank()) {
        "${course.startTime}-${course.endTime}"
    } else {
        timeJson?.let { TimeTableUtils.courseTimeString(course.startNode, course.step, it) }
    }

    Row(
        modifier = Modifier
            .alpha(if (CourseCompletion.isCompleted(course, now.toLocalDate(), timeJson, now)) CourseCompletion.DIM_ALPHA else 1f)
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(GlasenseTheme.specs.cardShape)
            .background(g.cardBackground)
            .then(if (onClick != null) Modifier.noRippleClickable(onClick = onClick) else Modifier)
            .padding(start = 10.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 课程色色条（贴在卡片左缘，高度随内容）
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        HGap(12.dp)
        // 时间槽 — 固定宽度避免 "10:20-12:45" 被截断
        Column(modifier = Modifier.width(74.dp)) {
            Text(
                text = course.shortNodeString(context),
                style = GlasenseTheme.type.subHeadlineEmphasized,
                color = accent
            )
            if (time != null) {
                VGap(2.dp)
                Text(
                    text = time,
                    style = GlasenseTheme.type.footnote,
                    color = g.contentVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
        HGap(12.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = course.courseName,
                style = GlasenseTheme.type.headline,
                color = g.content,
                maxLines = 2
            )
            if (course.teacher.isNotBlank() || course.room.isNotBlank()) {
                VGap(4.dp)
                val meta = buildString {
                    if (course.teacher.isNotBlank()) append(course.teacher)
                    if (course.room.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(course.room)
                    }
                }
                Text(
                    text = meta,
                    style = GlasenseTheme.type.footnote,
                    color = g.contentVariant,
                    maxLines = 2
                )
            }
        }
    }
}
