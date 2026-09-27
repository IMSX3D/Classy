package com.imsx3d.classy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * issue #46 (2026-09-16 采集包 cysxun): 合工大宣城校区 12 节次布局下,
 * 旧 inferNodes 硬编码 985 五段表把 (1550,1730) 10 行错到上午第 1 节、
 * (1920,2100) 8 行错成 9-10 节 — 共 18/122 行 (15%) 节次错位。
 *
 * 修复契约 (社区 5 仓共识: HFUTer/kirsh1/ustc-timetable/AISchedule/classduck
 * + Chiu-xaH 布局端点): JS 链第 3.5 段抓 POST /ws/schedule-table/timetable-layout
 * 的 result.courseUnitList, 顶层并入 datum payload; parser 优先按
 * unit.startTime == schedule.startTime 精确查表 (122/122 零失败),
 * 无 courseUnitList 时回退旧 heuristic (CUMTB/老包零回归)。
 *
 * fixture: jw/fixtures/eams5/hfut-xc-datum-with-layout.json
 * (真实采集包脱敏: 122 schedule + 13 lessons + 12 units, 课程/教师/教室为真实字段形态)
 */
class HfutIssue46LayoutNodeMappingTest {

    private fun loadFixture(): String {
        val stream = javaClass.classLoader
            ?.getResourceAsStream("jw/fixtures/eams5/hfut-xc-datum-with-layout.json")
        assertNotNull("fixture hfut-xc-datum-with-layout.json 应存在", stream)
        return stream!!.bufferedReader().use { it.readText() }
    }

    private fun parse() = JwEams5Parser(loadFixture()).generateCourseList()

    // -------- 布局查表: 全 122 行节次精确映射 --------

    @Test
    fun `layout present - all 122 schedule rows produce courses`() {
        assertEquals(122, parse().size)
    }

    @Test
    fun `layout present - 1550 slot maps to nodes 7-8 not morning fallback`() {
        // 修复前: sectionIndex(1550)=null → 兜底 node 1..2 (错到上午)
        val rows = parse().filter { it.startWeek == 5 && it.day == 2 && it.name == "习近平新时代中国特色社会主义思想概论" }
        assertTrue("应存在 习概 周二 week5 行 (宣城 1550 时段)", rows.isNotEmpty())
        for (c in rows) {
            assertEquals("startNode (1550 → unit7)", 7, c.startNode)
            assertEquals("endNode (1730 → unit8)", 8, c.endNode)
        }
    }

    @Test
    fun `layout present - 1920 slot maps to nodes 10-11 not 9-10`() {
        // 修复前: sectionIndex(1920) 吸到 19:00 段 → node 9-10 (差 1)
        val rows = parse().filter { it.name == "情景喜剧与美国文化" }
        assertTrue("应存在 情景喜剧与美国文化 行 (1920 时段)", rows.isNotEmpty())
        for (c in rows) {
            assertEquals("startNode (1920 → unit10)", 10, c.startNode)
            assertEquals("endNode (2100 → unit11)", 11, c.endNode)
        }
    }

    @Test
    fun `layout present - morning and afternoon slots unchanged`() {
        val os = parse().filter { it.name == "操作系统" && it.day == 1 }
        assertTrue(os.isNotEmpty())
        // 1000-1140 → units 3-4 (旧行为已对, 布局查表不回归)
        assertEquals(3, os[0].startNode)
        assertEquals(4, os[0].endNode)
        val dm = parse().filter { it.name == "数字媒体技术" }
        assertTrue(dm.isNotEmpty())
        // 1400-1540 → units 5-6
        assertEquals(5, dm[0].startNode)
        assertEquals(6, dm[0].endNode)
    }

    @Test
    fun `layout present - unit pair distribution matches capture ground truth`() {
        val dist = parse().groupingBy { it.startNode to it.endNode }.eachCount()
        assertEquals(22, dist[1 to 2])
        assertEquals(47, dist[3 to 4])
        assertEquals(35, dist[5 to 6])
        assertEquals(10, dist[7 to 8])
        assertEquals(8, dist[10 to 11])
    }

    // -------- 回退: 无 courseUnitList 时旧 heuristic 保持 --------

    @Test
    fun `no layout - legacy five-slot heuristic still resolves 985 standard times`() {
        // 985 标准表: (800,950)→1-2, (1010,1200)→3-4, (1400,1800 periods=4)→5-8
        val legacy = """
            {"result":{"lessonList":[
                {"id":1001,"courseName":"高等数学"},
                {"id":1002,"courseName":"大学物理"}
              ],
              "scheduleList":[
                {"lessonId":1001,"room":{"nameZh":"A楼101"},"weekday":1,"personName":"张三","weekIndex":1,"startTime":800,"endTime":950,"periods":2},
                {"lessonId":1002,"room":{"nameZh":"B楼205"},"weekday":3,"personName":"李四","weekIndex":1,"startTime":1010,"endTime":1200,"periods":2}
              ]}
            }
        """.trimIndent()
        val courses = JwEams5Parser(legacy).generateCourseList()
        assertEquals(2, courses.size)
        assertEquals(1 to 2, courses[0].startNode to courses[0].endNode)
        assertEquals(3 to 4, courses[1].startNode to courses[1].endNode)
    }

    @Test
    fun `no layout - unmappable time keeps periods fallback semantics`() {
        // 1550 在无布局时 sectionIndex=null → 兜底 node 1..periods (旧语义, 不抛不丢行)
        val legacy = """
            {"result":{"lessonList":[{"id":7,"courseName":"习概"}],
              "scheduleList":[
                {"lessonId":7,"room":{"nameZh":"x"},"weekday":2,"personName":"周","weekIndex":5,"startTime":1550,"endTime":1730,"periods":2}
              ]}
            }
        """.trimIndent()
        val courses = JwEams5Parser(legacy).generateCourseList()
        assertEquals(1, courses.size)
        assertEquals(1, courses[0].startNode)
        assertEquals(2, courses[0].endNode)
    }

    // -------- 确认页作息表预填: periods[] 由布局 courseUnitList 生成 (JS 侧契约) --------

    @Test
    fun `layout covers all referenced start times exactly`() {
        // 服务端真实布局的 startTime 集合必须覆盖 datum 里出现的每个 startTime
        // (JS 侧 periods 预填与 parser 查表共同依赖此不变量)
        // 借 parser 内部三形态无关的独立解析: 直接对 fixture 用正则抽取 (JVM 单测 android stub 不可用,
        // 改用正则抽取 startTime 数值 — fixture 结构固定, 抽取等价)
        val src = loadFixture()
        val unitBlock = src.substringAfter("\"courseUnitList\"").substringBefore("]")
        val unitStarts = Regex(""""startTime"\s*:\s*(\d+)""").findAll(unitBlock)
            .map { it.groupValues[1].toInt() }.toSet()
        val schedBlock = src.substringAfter("\"scheduleList\"").substringBefore("],")
        val schedStarts = Regex(""""startTime"\s*:\s*(\d+)""").findAll(schedBlock + "]")
            .map { it.groupValues[1].toInt() }.toSet()
        assertTrue("布局 startTime 集必须覆盖所有 schedule startTime (差集=${schedStarts - unitStarts})",
            unitStarts.containsAll(schedStarts))
    }
}
