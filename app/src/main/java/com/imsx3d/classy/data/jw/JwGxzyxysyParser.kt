package com.imsx3d.classy.data.jw

import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Sainz ASP stselect table. Pagination and evening mapping must be explicit. */
class JwGxzyxysyParser(source: String) : JwParser(source) {
    override fun confidence() = if (source.contains("\"gxzyxysy-v1\"")) 100 else 0
    override fun matchedFeatures() = if (confidence() > 0) listOf("gxzyxysy-v1") else emptyList()

    override fun generateCourseList(): List<JwCourse> {
        if (confidence() == 0) return emptyList()
        val payload = JSONObject(source)
        require(payload.getString("format") == "gxzyxysy-v1")
        val start = payload.getInt("eveningStart")
        val end = payload.getInt("eveningEnd")
        require(start in 1..30 && end in start..30) { "请确认晚间节次" }
        val pages = payload.getJSONArray("pages")
        require(pages.length() in 1..50) { "未取得完整课表" }
        val result = mutableListOf<JwCourse>()
        val ids = mutableSetOf<String>()
        var expectedCount = -1
        var term: String? = null
        for (i in 0 until pages.length()) {
            val doc = Jsoup.parse(pages.getString(i))
            val paging = PAGING.find(doc.text()) ?: error("未找到分页信息")
            val (total, count, current) = paging.destructured
            require(total.toInt() == pages.length() && current.toInt() == i + 1) { "课表分页缺失或重复，请重新导入" }
            if (expectedCount < 0) expectedCount = count.toInt()
            require(expectedCount == count.toInt()) { "采集期间课表发生变化，请重试" }
            val pageTerm = doc.selectFirst("input[name=term]")?.attr("value") ?: error("缺少学期")
            require(pageTerm.isNotBlank() && (term == null || term == pageTerm)) { "课表学期不一致" }
            term = pageTerm
            val table = doc.select("table").firstOrNull { table ->
                val headers = table.select("tr").firstOrNull()?.select("th")?.map { compact(it.text()) }.orEmpty()
                "课程序号" in headers && "课程名称" in headers && (1..7).all { day -> DAY_NAMES[day - 1] in headers }
            } ?: error("未找到赛恩斯课表")
            val headers = table.select("tr").first()!!.select("th").map { compact(it.text()) }
            val nameIndex = headers.indexOf("课程名称")
            val idIndex = headers.indexOf("课程序号")
            val teacherIndex = headers.indexOf("任课老师")
            require(teacherIndex >= 0) { "缺少教师列" }
            for (row in table.select("tr").drop(1)) {
                val cells = row.children().filter { it.tagName() == "td" }
                if (cells.firstOrNull()?.text()?.let(::compact) == "说明") continue
                if (cells.isEmpty()) continue
                require(cells.size == headers.size) { "课表行结构发生变化" }
                val id = cells[idIndex].text().trim()
                require(id.isNotEmpty() && ids.add(id)) { "课表分页重复，请重新导入" }
                val name = cells[nameIndex].text().trim()
                require(name.isNotBlank()) { "课程名称为空" }
                for (day in 1..7) {
                    result += parseCell(cells[headers.indexOf(DAY_NAMES[day - 1])], name,
                        cells[teacherIndex].text().trim(), day, start..end)
                }
            }
        }
        require(ids.size == expectedCount) { "课表采集不完整：应有 $expectedCount 条，实际 ${ids.size} 条" }
        return result.distinct()
    }

    private fun parseCell(cell: Element, name: String, teacher: String, day: Int, evening: IntRange): List<JwCourse> {
        val copy = cell.clone()
        copy.select("br").forEach { it.before("\n") }
        val lines = copy.wholeText().split('\n').map { it.replace('\u00a0', ' ').trim() }.filter { it.isNotEmpty() }
        val courses = mutableListOf<JwCourse>()
        var nodes: List<Int>? = null
        var room = ""
        var weeks = 0
        fun finish() { require(nodes == null || weeks > 0) { "课程缺少周次，不能完整导入" } }
        for (line in lines) {
            val slot = SLOT.matchEntire(line)
            if (slot != null || line.startsWith("晚上")) {
                finish()
                if (slot != null) {
                    nodes = expandNumbers(slot.groupValues[1])
                    room = slot.groupValues[2].trim()
                } else {
                    nodes = evening.toList()
                    room = line.removePrefix("晚上").trim()
                }
                require(nodes!!.all { it in 1..30 }) { "节次超出范围" }
                weeks = 0
            } else {
                val week = WEEK.matchEntire(line) ?: error("无法识别课程周次：$line")
                val sections = nodes ?: error("周次前缺少节次")
                val type = when (week.groupValues[2]) { "单" -> 1; "双" -> 2; else -> 0 }
                val values = expandNumbers(week.groupValues[1])
                require(values.all { it in 1..60 }) { "周次超出范围" }
                val applicable = values.filter { type == 0 || it % 2 == (if (type == 1) 1 else 0) }
                require(applicable.isNotEmpty()) { "周次与单双周不一致" }
                for (span in consecutive(sections)) for (range in consecutive(applicable, if (type == 0) 1 else 2)) {
                    courses += JwCourse(name, room, teacher, day, span.first, span.last, range.first, range.last, type)
                }
                weeks++
            }
        }
        finish()
        // The ASP page splits continuous lessons at short breaks (1,2,3 / 4).
        // Merge only within this source row/day and an identical week schedule.
        return courses.groupBy { listOf(it.room, it.startWeek, it.endWeek, it.type) }
            .values.flatMap { group ->
                val merged = mutableListOf<JwCourse>()
                for (course in group.sortedBy { it.startNode }) {
                    val previous = merged.lastOrNull()
                    if (previous != null && course.startNode <= previous.endNode + 1) {
                        merged[merged.lastIndex] = previous.copy(endNode = maxOf(previous.endNode, course.endNode))
                    } else merged += course
                }
                merged
            }
    }

    companion object {
        private val DAY_NAMES = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
        private val PAGING = Regex("找到\\s*(\\d+)\\s*页[，,]\\s*总共\\s*(\\d+)\\s*条[，,]\\s*当前页号[：:]\\s*(\\d+)")
        private val SLOT = Regex("第\\s*([0-9、,，\\s~至—－-]+)节(.*)")
        private val WEEK = Regex("([0-9、,，\\s~至—－-]+)(?:周)?\\s*([单双]?)")
        private fun compact(s: String) = s.replace(Regex("[\\s　]+"), "")
        private fun expandNumbers(s: String): List<Int> = s.trim().split(Regex("[、,，]")).flatMap { token ->
            val m = Regex("\\s*(\\d+)\\s*(?:[-~至—－]\\s*(\\d+))?\\s*").matchEntire(token) ?: error("无法识别数字范围")
            val first = m.groupValues[1].toInt()
            val last = m.groupValues[2].toIntOrNull() ?: first
            require(first in 1..60 && last in first..60) { "无效数字范围" }
            (first..last).toList()
        }.distinct().sorted()

        private fun consecutive(values: List<Int>, step: Int = 1): List<IntRange> {
            if (values.isEmpty()) return emptyList()
            val result = mutableListOf<IntRange>()
            var first = values.first()
            var last = first
            for (n in values.drop(1)) {
                if (n != last + step) { result += first..last; first = n }
                last = n
            }
            result += first..last
            return result
        }
    }
}
