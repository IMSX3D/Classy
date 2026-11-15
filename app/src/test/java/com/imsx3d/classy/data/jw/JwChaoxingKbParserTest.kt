package com.imsx3d.classy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 超星课表分享页 (kb.chaoxing.com/…/curriculum/schedule.html?curriculumUuid=…) 解析器测试。
 *
 * 数据源: 2026-11-15 用真实分享链接接口实采 (逐周 25 次 getOtherLessons),
 * fixture 已脱敏: 课名/教师/教室替换为示例数据, 结构、字段名、周次形态保持实采原样。
 * 原始行是「每週一行」形态 — 解析端必须按 (课名,星期,起止节,教室,教师) 把
 * 逐周行合并回整门课, 并压成连续段 / 单双周。
 */
class JwChaoxingKbParserTest {

    private fun loadFixture(): String =
        runCatching {
            File("src/test/resources/jw/fixtures/chaoxing_kb/shared-curriculum.json")
                .readText(Charsets.UTF_8)
        }.getOrElse {
            javaClass.classLoader?.getResourceAsStream("jw/fixtures/chaoxing_kb/shared-curriculum.json")
                ?.bufferedReader(Charsets.UTF_8)?.readText()
        } ?: error("missing fixture jw/fixtures/chaoxing_kb/shared-curriculum.json")

    private fun parse(source: String = loadFixture()): List<JwCourse> =
        JwChaoxingKbParser(source).generateCourseList()

    @Test
    fun `merges per-week rows into 9 whole-term courses`() {
        val courses = parse()
        // 8 门课: 创新创业实践跨两段 → 2 条, 其余各 1 条 = 9
        assertEquals(9, courses.size)
    }

    @Test
    fun `consecutive weeks collapse into one full-week course`() {
        val c = parse().first { it.name == "高等数学A（II）" }
        assertEquals(1, c.day)
        assertEquals(1, c.startNode)
        assertEquals(2, c.endNode)
        assertEquals(1, c.startWeek)
        assertEquals(16, c.endWeek)
        assertEquals(0, c.type) // 1..16 连续 → 每周
        assertEquals("范老师", c.teacher)
        assertEquals("北2_103", c.room)
    }

    @Test
    fun `duplicate row in same week does not duplicate the course`() {
        assertEquals(1, parse().count { it.name == "高等数学A（II）" })
    }

    @Test
    fun `even-week-only course yields double-week type 2`() {
        val c = parse().first { it.name == "大学物理实验" }
        assertEquals(2, c.startWeek)
        assertEquals(10, c.endWeek)
        assertEquals(2, c.type)
        assertEquals("", c.room) // 体育/实验课常无固定教室
    }

    @Test
    fun `odd-week-only course yields single-week type 1`() {
        val c = parse().first { it.name == "形势与政策" }
        assertEquals(1, c.startWeek)
        assertEquals(9, c.endWeek)
        assertEquals(1, c.type)
    }

    @Test
    fun `discontinuous weeks split into two ranges`() {
        val runs = parse().filter { it.name == "创新创业实践" }.sortedBy { it.startWeek }
        assertEquals(2, runs.size)
        assertEquals(1, runs[0].startWeek)
        assertEquals(4, runs[0].endWeek)
        assertEquals(6, runs[1].startWeek)
        assertEquals(8, runs[1].endWeek)
        assertEquals(0, runs[0].type)
        assertEquals(0, runs[1].type)
        // 连堂 3 节
        assertEquals(9, runs[0].startNode)
        assertEquals(11, runs[0].endNode)
    }

    @Test
    fun `online location is used when classroom is blank`() {
        val c = parse().first { it.name == "艺术鉴赏" }
        assertEquals("腾讯会议", c.room)
        assertEquals(7, c.day) // 周日 = 7
        assertEquals(1, c.startWeek)
        assertEquals(3, c.endWeek)
        assertEquals(0, c.type) // 1..3 连续 → 每周, 不受行内缺 weeks 影响
    }

    @Test
    fun `rows without week string fall back to the request week bucket`() {
        // 艺术鉴赏行 weeks 为空, 周次只能从 bucket.week 回退 → 1..3
        val c = parse().first { it.name == "艺术鉴赏" }
        assertEquals(1, c.startWeek)
        assertEquals(3, c.endWeek)
    }

    @Test
    fun `blank name and missing day or period rows are skipped`() {
        val names = parse().map { it.name }
        assertTrue(names.none { it.isBlank() })
        assertTrue(names.none { it == "缺星期课" || it == "缺节次课" })
    }

    @Test
    fun `weeks beyond maxWeek are filtered out`() {
        assertTrue(parse().none { it.name == "超纲课" })
    }

    @Test
    fun `late period course keeps five-period block`() {
        val c = parse().first { it.name == "体育（III）" }
        assertEquals(3, c.day)
        assertEquals(3, c.startNode)
        assertEquals(4, c.endNode)
        assertEquals("中心体育场", c.room)
    }

    @Test
    fun `earlyMorningSection shifts period numbers only when time config covers it`() {
        val covered = """
            {"curriculum":{"maxWeek":20,"maxLength":12,"earlyMorningSection":1,
              "lessonTimeConfigArray":["7:30-7:50","8:00-8:50","9:00-9:50","10:10-11:00",
                "11:10-12:00","14:00-14:50","15:00-15:50","16:10-17:00","17:10-18:00",
                "19:00-19:50","20:00-20:50","21:00-21:50","22:00-22:50"]},
             "weeks":[{"week":1,"rows":[{"name":"早课偏移","dayOfWeek":1,"beginNumber":3,
                "length":2,"weeks":"1","location":"A","teacherName":"T"}]}]}
        """.trimIndent()
        val course = JwChaoxingKbParser(covered).generateCourseList().single()
        // 分享页按 lessonTimeConfigArray[beginNumber+earlyMorningSection-1] 取时间 → 节号 +1
        assertEquals(4, course.startNode)
        assertEquals(5, course.endNode)
    }

    @Test
    fun `earlyMorningSection is ignored when time config is too short`() {
        val notCovered = """
            {"curriculum":{"maxWeek":20,"maxLength":12,"earlyMorningSection":1,
              "lessonTimeConfigArray":["8:00-8:50","9:00-9:50","10:10-11:00","11:10-12:00",
                "14:00-14:50","15:00-15:50","16:10-17:00","17:10-18:00",
                "19:00-19:50","20:00-20:50","21:00-21:50","22:00-22:50"]},
             "weeks":[{"week":1,"rows":[{"name":"无偏移","dayOfWeek":1,"beginNumber":3,
                "length":2,"weeks":"1","location":"A","teacherName":"T"}]}]}
        """.trimIndent()
        val course = JwChaoxingKbParser(notCovered).generateCourseList().single()
        assertEquals(3, course.startNode)
        assertEquals(4, course.endNode)
    }

    @Test
    fun `row weeks supports comma enumeration in a single row`() {
        val src = """
            {"curriculum":{"maxWeek":20},
             "weeks":[{"week":1,"rows":[{"name":"一次多周","dayOfWeek":2,"beginNumber":1,
                "length":2,"weeks":"1,2,3,4","location":"A","teacherName":"T"}]}]}
        """.trimIndent()
        val course = JwChaoxingKbParser(src).generateCourseList().single()
        assertEquals(1, course.startWeek)
        assertEquals(4, course.endWeek)
        assertEquals(0, course.type)
    }

    @Test
    fun `empty or malformed source yields empty list`() {
        assertTrue(JwChaoxingKbParser("").generateCourseList().isEmpty())
        assertTrue(JwChaoxingKbParser("not json").generateCourseList().isEmpty())
        assertTrue(JwChaoxingKbParser("""{"weeks":[]}""").generateCourseList().isEmpty())
    }

    @Test
    fun `every parsed course is inside timetable bounds`() {
        for (c in parse()) {
            assertTrue("${c.name} day", c.day in 1..7)
            assertTrue("${c.name} node", c.startNode >= 1 && c.endNode >= c.startNode)
            assertTrue("${c.name} week", c.startWeek >= 1 && c.endWeek >= c.startWeek && c.endWeek <= 20)
            assertTrue("${c.name} type", c.type in 0..2)
            assertTrue("${c.name} name", c.name.isNotBlank())
        }
    }

    @Test
    fun `registry routes chaoxing_kb to this parser`() {
        assertNotNull(JwParserRegistry.parserFor(JwProtocol.TYPE_CHAOXING_KB, loadFixture()))
        assertTrue(JwImportViewModel.isRoutable(JwProtocol.TYPE_CHAOXING_KB))
    }
}
