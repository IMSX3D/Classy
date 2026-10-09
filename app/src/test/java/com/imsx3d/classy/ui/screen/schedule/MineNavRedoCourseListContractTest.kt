package com.imsx3d.classy.ui.screen.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2026-09-21 用户令: 4 点行为契约 — 锁实现,以防后续重构破坏新行为。
 *
 *  A. 管理页「当前课表摘要卡」整张可点 → 所有课表页(用户直觉:点开就有课表列表)
 *  B. 我的页六个任务入口；课程清单与作息表、导出等能力迁入课表管理。
 *  C. 课表主页 TopBar 撤回/取消撤回合胶囊: 一体显隐(hasUndo||hasRedo 才挂载),
 *     体育场形状(CircleShape+两半 32dp)+中缝 1dp 淡淡竖线
 *  D. 课程清单(CourseListScreen)路由+导航入口齐全; 课程清单按 courseName 聚合,
 *     课名空时按 groupId 兜底; 空态显示 course_list_empty
 *  E. 6 locale 必须含 schedule_redo / schedule_redo_none / manage_view_all_tables
 *     / course_list_{title,subtitle,empty,arrangements}
 *  F. Navigator 必须有 openCourseList 入口(由 NavHostMigrationContractTest 验 15 个)
 *  G. UndoManagerTest 提供 redo 互斥/新写清 redo/clear 双清等独立用例,本类不重复
 *
 *  仓库无 Robolectric — 与其他契约测试同风格,源文件 token 扫描。
 */
class MineNavRedoCourseListContractTest {

    private fun findUpward(rel: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, rel)
            if (f.exists()) return f
            dir = dir.parentFile
        }
        error("$rel not found")
    }

    private val scheduleScreen: String by lazy {
        findUpward("app/src/main/java/com/imsx3d/classy/ui/screen/schedule/ScheduleScreen.kt").readText()
    }
    private val managementPage: String by lazy {
        findUpward("app/src/main/java/com/imsx3d/classy/ui/screen/manage/ManagementPage.kt").readText()
    }
    private val mineScreen: String by lazy {
        findUpward("app/src/main/java/com/imsx3d/classy/ui/screen/mine/MineScreen.kt").readText()
    }
    private val courseListScreen: String by lazy {
        findUpward("app/src/main/java/com/imsx3d/classy/ui/screen/mine/CourseListScreen.kt").readText()
    }
    private val routes: String by lazy {
        findUpward("app/src/main/java/com/imsx3d/classy/ui/nav/SleepyRoutes.kt").readText()
    }
    private val navigator: String by lazy {
        findUpward("app/src/main/java/com/imsx3d/classy/ui/nav/SleepyNavigator.kt").readText()
    }
    private val noRipple: String by lazy {
        findUpward("app/src/main/java/com/imsx3d/classy/ui/theme/NoRippleClickable.kt").readText()
    }
    private fun stringsFor(loc: String): String =
        findUpward("app/src/main/res/$loc/strings.xml").readText()

    // ---- A. 管理页: 当前表卡可点 → 所有课表 ----

    @Test
    fun `current table card on management page is clickable and routes to AllTables`() {
        // 卡内必须有 noRippleClickable(onOpenAllTables) 整卡包裹
        val m = Regex("""noRippleClickable\(\s*onOpenAllTables\s*\)""").findAll(managementPage).toList()
        assertTrue(
            "管理页必须有 noRippleClickable(onOpenAllTables) 整卡点击,且至少出现 1 次," +
                "实际 ${m.size}",
            m.isNotEmpty()
        )
    }

    @Test
    fun `management page declares onOpenAllTables parameter`() {
        assertTrue(
            "ManagementPage 形参列表必须有 onOpenAllTables: () -> Unit = {}",
            Regex("""onOpenAllTables:\s*\(\)\s*->\s*Unit\s*=\s*\{\}""").containsMatchIn(managementPage)
        )
    }

    // A passive overview must not increase the six task-level choices.
    @Test
    fun `mine retains six task entries alongside the passive overview`() {
        assertEquals(6, Regex("SettingsRow\\(Icons").findAll(mineScreen).count())
        assertTrue(mineScreen.contains("onOpenManagement"))
        assertTrue(mineScreen.contains("onOpenSettings"))
    }

    @Test
    fun `course list is accessible from management and can add courses`() {
        assertTrue(managementPage.contains("onClick = onOpenCourseList"))
        assertTrue(courseListScreen.contains("onClick = onAddCourse"))
        val main = findUpward("app/src/main/java/com/imsx3d/classy/MainActivity.kt").readText()
        assertTrue(main.contains("onOpenCourseList = { navigator.openCourseList() }"))
        val host = findUpward("app/src/main/java/com/imsx3d/classy/ui/nav/SleepyNavHost.kt").readText()
        assertTrue(host.contains("onAddCourse = { navigator.openAddCourse() }"))
    }

    @Test
    fun `management retains import period tables and export without duplicate all tables entry`() {
        assertFalse(managementPage.contains("R.string.all_tables"))
        assertTrue(managementPage.contains("onClick = onOpenPeriodTables"))
        assertTrue(managementPage.contains("onClick = onExportRequested"))
        assertTrue(managementPage.contains("onJwImportRequested ="))
    }

    // ---- C. 课表页撤回胶囊：UI-4w 已整块摘掉（含数据层的 capture/undo/redo 也只剩无 UI 入口的代码）----
    // 原先锁"胶囊两半/中缝/配对显隐/onRedo 形参"的 4 条契约随之作废 —— 功能没了，契约留着只会误导。


    // ---- D. 课程清单路由 + 导航入口 + 聚合/空态 ----

    @Test
    fun `routes declare CourseList data object`() {
        assertTrue(
            "SleepyRoutes 必须新增 @Serializable data object CourseList : SleepyRoute",
            Regex("""@Serializable\s+data\s+object\s+CourseList\s*:\s*SleepyRoute""").containsMatchIn(routes)
        )
    }

    @Test
    fun `navigator exposes openCourseList`() {
        assertTrue(
            "SleepyNavigator 必须新增 fun openCourseList() = push(SleepyRoute.CourseList)",
            Regex("""fun\s+openCourseList\(\)\s*=\s*push\(SleepyRoute\.CourseList\)""")
                .containsMatchIn(navigator)
        )
    }

    @Test
    fun `course list screen groups by courseName with empty-name fallback`() {
        // groupBy { it.courseName.ifBlank { it.groupId } }
        assertTrue(
            "CourseListScreen 必须 groupBy { it.courseName.ifBlank { it.groupId } } 按课程名聚合," +
                "空名按 groupId 兜底",
            courseListScreen.contains("it.courseName.ifBlank { it.groupId }")
        )
    }

    @Test
    fun `course list screen groups locations inside each course card`() {
        assertTrue("课程卡必须保留老师标题", courseListScreen.contains("teacher = rows.first().teacher"))
        assertTrue("课程卡必须按 room 分组地点", courseListScreen.contains("rows.groupBy { it.room.trim() }"))
        assertTrue("课程卡必须渲染地点安排", courseListScreen.contains("group.locations.forEach"))
    }

    @Test
    fun `week view filters empty days in single-column detail panel`() {
        val courseTableView = findUpward(
            "app/src/main/java/com/imsx3d/classy/ui/component/CourseTableView.kt"
        ).readText()
        val sortedDays = Regex("""val sortedDays = visibleDays\.sorted\(\)""")
            .find(courseTableView)
            ?: error("DetailPanel sortedDays declaration not found")
        val singleColumn = courseTableView.indexOf("// 单栏(或两栏下过滤后不足 2 天)")
        assertTrue("DetailPanel single-column branch must exist", singleColumn >= 0)
        val loop = courseTableView.indexOf("for (day in sortedDays)", singleColumn)
        assertTrue("single-column branch must render filtered sortedDays", loop > sortedDays.range.last)
    }

    @Test
    fun `course list screen renders empty state with course_list_empty`() {
        // grouped.isEmpty 分支必须显示 course_list_empty 文案
        val emptyBranch = Regex(
            """if\s*\(grouped\.isEmpty\(\)\)[\s\S]{0,400}?course_list_empty"""
        ).containsMatchIn(courseListScreen)
        assertTrue("空态必须显示 R.string.course_list_empty", emptyBranch)
    }

    @Test
    fun `adding a slot reuses the previous slot teacher and room`() {
        val addBlock = findUpward("app/src/main/java/com/imsx3d/classy/ui/screen/edit/AddCourseScreen.kt").readText()
        assertTrue("新增时段必须读取上一张卡", addBlock.contains("val previous = meetingBlocks.lastOrNull()"))
        assertTrue("新增时段必须复用上一张卡地点", addBlock.contains("room = previous?.roomState.orEmpty()"))
        assertTrue("新增时段必须复用上一张卡老师", addBlock.contains("teacher = previous?.teacherState.orEmpty()"))
    }

    // ---- E. 6 locale 字符串齐备 ----

    @Test
    fun `six locales contain redo and course list strings`() {
        val keys = listOf(
            "schedule_redo",
            "schedule_redo_none",
            "manage_view_all_tables",
            "course_list_title",
            "course_list_subtitle",
            "course_list_empty",
            "course_list_arrangements"
        )
        val locs = listOf("values", "values-en", "values-es", "values-ja", "values-zh-rCN", "values-zh-rTW")
        for (loc in locs) {
            val s = stringsFor(loc)
            for (k in keys) {
                assertTrue(
                    "locale=$loc 缺少 string key=$k",
                    Regex("""<string\s+name="${k}">""").containsMatchIn(s)
                )
            }
        }
    }

    @Test
    fun `table_info string relabeled to arrangement count in CN zh-rCN and zh-rTW`() {
        // 中文三 locale: 旧「%3$d门课」必须改成「%3$d个上课安排 / 个上課安排」
        // ja 保留 コマ 原状, en/es/cl 不在改列
        val zhs = listOf("values", "values-zh-rCN", "values-zh-rTW")
        for (loc in zhs) {
            val s = stringsFor(loc)
            val m = Regex("""<string\s+name="table_info">([^<]+)</string>""").find(s)?.groupValues?.get(1)
                ?: error("locale=$loc 缺 table_info")
            assertTrue(
                "locale=$loc table_info 必须含「%3\$d...个上课安排」/「%3\$d...個上課安排」," +
                    "实际:${m}",
                m.contains("个上课安排") || m.contains("個上課安排")
            )
            assertFalse(
                "locale=$loc table_info 仍含旧「门课」措辞 — 用户令要口径统一",
                m.contains("%3\$d门课") || m.contains("%3\$d門課")
            )
        }
    }
}