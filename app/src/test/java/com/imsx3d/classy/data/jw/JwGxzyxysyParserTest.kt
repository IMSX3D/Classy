package com.imsx3d.classy.data.jw

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JwGxzyxysyParserTest {
    private fun fixture() = javaClass.getResource("/jw_fixtures/gxzyxysy/page1.html")!!.readText()
    private fun envelope(vararg pages: String, start: Int = 10, end: Int = 13) = JSONObject()
        .put("format", "gxzyxysy-v1").put("pages", JSONArray(pages.toList()))
        .put("eveningStart", start).put("eveningEnd", end).toString()
    // Only page 1 was captured. This one-page variant is synthetic; never claim 25 real rows were tested.
    private fun singlePage() = fixture().replace("找到3页，总共25条", "找到1页，总共10条")
    private fun parse(html: String = singlePage()) = JwGxzyxysyParser(envelope(html)).generateCourseList()

    @Test fun `real captured page cannot silently import only ten of twenty five rows`() {
        assertThrows(IllegalArgumentException::class.java) { parse(fixture()) }
    }
    @Test fun `numeric nodes discontinuous weeks rooms and teachers are preserved`() {
        val courses = parse()
        val row = courses.filter { it.name == "医学微生物学与免疫学" && it.startNode == 6 }
        assertEquals(listOf(1 to 5, 7 to 7), row.map { it.startWeek to it.endWeek })
        assertTrue(row.all { it.endNode == 9 && it.day == 2 && it.room == "阶梯401" && it.teacher == "教师乙" })
        assertFalse(courses.any { it.name == "中药学" && it.startWeek <= 6 && it.endWeek >= 6 && it.day == 1 })
    }
    @Test fun `evening uses explicit mapping and parity is retained`() {
        val evening = parse().single { it.name == "医学伦理学" }
        assertEquals(JwCourse("医学伦理学", "赛教504", "教师甲", 1, 10, 13, 8, 14, 2), evening)
        val changed = JwGxzyxysyParser(envelope(singlePage(), start = 1, end = 4)).generateCourseList()
        assertEquals(1, changed.single { it.name == "医学伦理学" }.startNode)
        assertEquals(4, changed.single { it.name == "医学伦理学" }.endNode)
    }
    @Test fun `blank room and single node remain valid`() {
        val result = parse()
        assertTrue(result.any { it.name == "公共体育(赛)" && it.room.isEmpty() && it.startNode == 1 && it.endNode == 3 })
        val standalone = parse(singlePage().replace("第1、2、3节赛教503", "第1、2节赛教503"))
        assertTrue(standalone.any { it.startNode == 4 && it.endNode == 4 })
    }
    @Test fun `adjacent lessons join without crossing week room or node gaps`() {
        val courses = parse().filter { it.name == "医学微生物学与免疫学" && it.room == "赛教503" }
        assertEquals(1, courses.size)
        assertEquals(1, courses.single().startNode)
        assertEquals(4, courses.single().endNode)
        val changedRoom = parse(singlePage().replace("第4节赛教503", "第4节另一教室"))
        assertTrue(changedRoom.any { it.startNode == 4 && it.room == "另一教室" })
        val changedWeeks = parse(singlePage().replace("第4节赛教503<br>8-10", "第4节赛教503<br>9-10"))
        assertTrue(changedWeeks.any { it.name == "医学微生物学与免疫学" && it.startNode == 4 && it.startWeek == 9 })
    }
    @Test fun `complete live sanitized timetable joins blocks and preserves evening parity`() {
        val html = javaClass.getResource("/jw_fixtures/gxzyxysy/full-20261009.html")!!.readText()
        val courses = parse(html)
        val diagnosis = courses.filter { it.name == "中医诊断学(赛)" && it.day == 5 }
        assertTrue(diagnosis.isNotEmpty())
        assertTrue(diagnosis.all { it.startNode == 1 && it.endNode == 4 })
        val pathology = courses.filter { it.name == "病理学(赛)" && it.day == 5 }
        assertTrue(pathology.isNotEmpty())
        assertTrue(pathology.all { it.startNode == 6 && it.endNode == 9 })
        val ethics = courses.single { it.name == "医学伦理学" }
        assertEquals(1, ethics.day)
        assertEquals(10, ethics.startNode)
        assertEquals(13, ethics.endNode)
        assertEquals(2, ethics.type)
        val mondayLab = courses.filter { it.name == "病理学实验(赛)" && it.day == 1 }
        assertTrue(mondayLab.isNotEmpty())
        assertTrue(mondayLab.all { it.type == 1 })
    }
    @Test fun `missing weeks and malformed rows are rejected rather than dropped`() {
        assertThrows(IllegalArgumentException::class.java) { parse(singlePage().replace("8-14双", "")) }
        assertThrows(IllegalArgumentException::class.java) { parse(singlePage().replace("<td>医学伦理学</td>", "")) }
    }
    @Test fun `invalid evening interval is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            JwGxzyxysyParser(envelope(singlePage(), start = 13, end = 10)).generateCourseList()
        }
    }
    @Test fun `duplicate pages mismatched semester and total are rejected`() {
        val first = fixture().replace("找到3页，总共25条", "找到2页，总共20条")
        val second = first.replace("当前页号：1", "当前页号：2")
        assertThrows(IllegalArgumentException::class.java) { JwGxzyxysyParser(envelope(first, first)).generateCourseList() }
        assertThrows(IllegalArgumentException::class.java) { JwGxzyxysyParser(envelope(first, second)).generateCourseList() }
        assertThrows(IllegalArgumentException::class.java) { JwGxzyxysyParser(envelope(first, second.replace("20261", "20262"))).generateCourseList() }
        assertThrows(IllegalArgumentException::class.java) { parse(singlePage().replace("总共10条", "总共11条")) }
    }
    @Test fun `synthetic complete pagination is accepted without joining different rooms`() {
        val first = fixture().replace("找到3页，总共25条", "找到2页，总共20条")
        val second = first.replace("当前页号：1", "当前页号：2")
            .replace(Regex("(<td>)([A-Z0-9]+_[0-9]+)(</td>)"), "$1SECOND_$2$3")
            .replace("赛教504", "另一教室")
        val courses = JwGxzyxysyParser(envelope(first, second)).generateCourseList()
        assertEquals(setOf("赛教504", "另一教室"), courses.filter { it.name == "医学伦理学" }.map { it.room }.toSet())
    }
    @Test fun `precise domain routing and parser dispatch`() {
        assertEquals(JwProtocol.TYPE_GXZYXYSY, JwImportViewModel.detectProtocolFromUrlForTest("https://jw.gxzyxysy.com/student/public/login.asp"))
        assertNull(JwImportViewModel.detectProtocolFromUrlForTest("https://jw.gxzyxysy.com.evil.test/student/public/login.asp"))
        assertEquals(JwProtocol.TYPE_GXZYXYSY, JwImportViewModel.detectProtocolFromHtmlForTest(envelope(singlePage())))
        assertEquals(parse(), JwParserRegistry.parserFor(JwProtocol.TYPE_GXZYXYSY, envelope(singlePage())).generateCourseList())
        assertTrue(JwGxzyxysyParser("<html>登录</html>").generateCourseList().isEmpty())
    }
}
