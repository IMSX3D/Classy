package com.imsx3d.classy.data.jw

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 超星「课表分享页」解析器 (kb.chaoxing.com/res/pc/curriculum/schedule.html?curriculumUuid=…)。
 *
 * 与 [JwChaoxingParser] (超星综合教务 /pkgl/xskb/queryKbForGrdb) 是**不同数据源**:
 * 本适配面向超星学习通自带的课表分享页 — 学生把课表转发给别人 / 自己换机时拿到的
 * 公开链接，无需登录即可读取 (2026-11-15 用真实分享链接直接 curl 实锤)。
 *
 * 数据来源 (WebView 内同源 fetch, 见 JwWebViewLoginScreen 的 chaoxing_kb 分支):
 *   GET {mctx}/curriculum/getOtherLessons?curriculumUuid={uuid}&curTime={ms}
 *       → curriculum: {uuid,schoolYear,semester,maxWeek,currentWeek,maxLength,
 *                      earlyMorningSection,lessonTimeConfigArray,firstWeekDateReal,
 *                      campusLessonTimeConfigArray?,weeksLessonTimeConfigArray?}
 *   GET 同端点在带 &week=N 时按周返回该周 lessonArray (不带 week = 当前教学周);
 *       因此 fetch JS 并行抓 1..maxWeek 周, 组装成如下输入 JSON:
 *   {"curriculum":{…按页面口径归一后的有效节次配置…},
 *    "weeks":[{"week":1,"rows":[{lesson…}]}, …]}
 *
 * 字段映射 (超星 lesson → JwCourse):
 *   name                  课程名                                  → name
 *   location/onlineLocation 教室 (体育课常空; 线上课回退线上地点)  → room
 *   teacherName           教师                                    → teacher
 *   dayOfWeek             星期 1..7 (周一=1)                      → day
 *   beginNumber           起始节 (1 基)                           → startNode (+早期节次偏移)
 *   length                连堂节数 (缺失按 1)                     → endNode
 *   weeks                 逗号周次串, 按周接口常只有当前周一行     → 周次段
 *
 * 合并规则: 逐周抓取会把同一门课拆成每週一行 → 同 (课名,星期,起止节,教室,教师)
 * 的周次取并集, 再按连续段/单双周压成 JwCourse (与 [JwChaoxingParser] 口径一致)。
 * 早期节次 (earlyMorningSection) 仅在 lessonTimeConfigArray 长度覆盖
 * maxLength+earlyMorningSection 时生效 — 与分享页自身的渲染口径一致, 不硬编码。
 */
class JwChaoxingKbParser(source: String) : JwParser(source) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 周次串 → 周次集合 (兼容逗号枚举 "1,2,3" 与区间 "1-16" 两种形态) */
    private fun expandWeeks(zcstr: String): Set<Int> {
        val weeks = mutableSetOf<Int>()
        for (part in zcstr.split(',', '，')) {
            val t = part.trim()
            if (t.isEmpty()) continue
            if (t.contains('-') || t.contains('～') || t.contains('~')) {
                val se = t.split('-', '～', '~').map { it.trim().toIntOrNull() }
                if (se.size == 2 && se[0] != null && se[1] != null && se[0]!! <= se[1]!!) {
                    for (w in se[0]!!..se[1]!!) weeks.add(w)
                }
            } else {
                t.toIntOrNull()?.let { weeks.add(it) }
            }
        }
        return weeks
    }

    /** 周次集合 → 连续段 [(startWeek, endWeek, type)]，口径与 [JwChaoxingParser] 一致：
     *  单连续段=每周(0)；整体等差 step=2=单周(1)/双周(2)；其余拆多段每段 0。 */
    private fun weekRuns(weeks: List<Int>): List<Triple<Int, Int, Int>> {
        if (weeks.isEmpty()) return emptyList()
        val runs = mutableListOf<Pair<Int, Int>>()
        var start = weeks[0]
        var prev = weeks[0]
        for (w in weeks.drop(1)) {
            if (w == prev + 1) prev = w
            else {
                runs += start to prev
                start = w
                prev = w
            }
        }
        runs += start to prev
        if (runs.size == 1) return listOf(Triple(runs[0].first, runs[0].second, 0))
        if (weeks.size >= 2 && (1 until weeks.size).all { weeks[it] - weeks[it - 1] == 2 }) {
            val type = if (weeks.first() % 2 == 1) 1 else 2
            return listOf(Triple(weeks.first(), weeks.last(), type))
        }
        return runs.map { Triple(it.first, it.second, 0) }
    }

    private fun JsonObject.str(key: String): String =
        this[key]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }?.trim().orEmpty()

    private fun JsonObject.int(key: String): Int? =
        this[key]?.let {
            runCatching { it.jsonPrimitive.contentOrNull?.trim()?.toIntOrNull() }.getOrNull()
        }

    /**
     * 早期节次偏移：分享页渲染时 lessonTimeConfigArray[beginNumber+earlyMorningSection-1]
     * 才是课程时间，所以 JwCourse 的节号 = beginNumber + 偏移。配置数组没覆盖
     * (maxLength+earlyMorningSection) 个节次时按 0 处理，避免把课挤到错误节次。
     */
    private fun resolveEarlyMorningOffset(curriculum: JsonObject?): Int {
        if (curriculum == null) return 0
        val raw = curriculum.int("earlyMorningSection") ?: 0
        if (raw <= 0) return 0
        val maxLength = curriculum.int("maxLength") ?: 0
        val configSize = runCatching {
            curriculum["lessonTimeConfigArray"]?.jsonArray?.size ?: 0
        }.getOrDefault(0)
        return if (maxLength > 0 && configSize >= maxLength + raw) raw else 0
    }

    override fun generateCourseList(): List<JwCourse> {
        val root = runCatching { json.parseToJsonElement(source).jsonObject }.getOrNull()
            ?: return emptyList()
        val curriculum = runCatching { root["curriculum"]?.jsonObject }.getOrNull()
        val buckets = runCatching { root["weeks"]?.jsonArray }.getOrNull() ?: return emptyList()

        val maxWeek = curriculum?.int("maxWeek") ?: 0
        val offset = resolveEarlyMorningOffset(curriculum)

        data class Arrangement(
            val name: String,
            val day: Int,
            val startNode: Int,
            val endNode: Int,
            val room: String,
            val teacher: String,
        )

        val weeksByArrangement = linkedMapOf<Arrangement, MutableSet<Int>>()

        for (bucketEl in buckets) {
            val bucket = runCatching { bucketEl.jsonObject }.getOrNull() ?: continue
            val bucketWeek = bucket.int("week")
            val rows = runCatching { bucket["rows"]?.jsonArray }.getOrNull() ?: continue
            for (rowEl in rows) {
                val row = runCatching { rowEl.jsonObject }.getOrNull() ?: continue

                val name = row.str("name").ifBlank { row.str("courseName") }
                if (name.isBlank()) continue

                val day = row.int("dayOfWeek") ?: row.int("day") ?: continue
                if (day !in 1..7) continue

                val beginNumber = row.int("beginNumber") ?: row.int("begin") ?: continue
                if (beginNumber < 1) continue
                val length = (row.int("length") ?: 1).coerceAtLeast(1)
                val startNode = beginNumber + offset
                val endNode = startNode + length - 1

                val room = row.str("location").ifBlank { row.str("onlineLocation") }
                val teacher = row.str("teacherName").ifBlank { row.str("teacher") }

                // 按周接口正常每行只含当前周; 若部署返回完整周次串则直接采信,
                // 行里没有周次时才回退到请求周 (传输层 bucket.week)
                val weeks = expandWeeks(row.str("weeks")).ifEmpty {
                    bucketWeek?.let { setOf(it) } ?: emptySet()
                }
                if (weeks.isEmpty()) continue

                val arrangement = Arrangement(
                    name = name,
                    day = day,
                    startNode = startNode,
                    endNode = endNode,
                    room = room,
                    teacher = teacher,
                )
                weeksByArrangement.getOrPut(arrangement) { mutableSetOf() }.addAll(weeks)
            }
        }

        val courses = mutableListOf<JwCourse>()
        for ((arrangement, weekSet) in weeksByArrangement) {
            val weeks = weekSet.filter { maxWeek <= 0 || it in 1..maxWeek }.sorted()
            if (weeks.isEmpty()) continue
            for ((startWeek, endWeek, type) in weekRuns(weeks)) {
                courses += JwCourse(
                    name = arrangement.name,
                    room = arrangement.room,
                    teacher = arrangement.teacher,
                    day = arrangement.day,
                    startNode = arrangement.startNode,
                    endNode = arrangement.endNode,
                    startWeek = startWeek,
                    endWeek = endWeek,
                    type = type,
                )
            }
        }
        return courses.sortedWith(
            compareBy({ it.day }, { it.startNode }, { it.name }, { it.startWeek }, { it.type })
        )
    }
}
