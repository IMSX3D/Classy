package com.imsx3d.classy.data.jw

import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「自定义教务链接」来源的刷新基线 round-trip。
 *
 * 2026-11-15 真机反馈：通过自定义链接（超星课表分享）导入后点「从教务更新」，
 * 复核页报「未更新…」且无任何差异列表。根因：自定义条目的 `sortKey` 恒为空串
 * （SchoolSelectScreen 建条目时 sortKey=""），而 `JwImportDraftCodec.schoolFromJson`
 * 要求 sortKey 非空 → `JwRefresh.decode(table.jwSourceJson)` 返回 null →
 * `JwRefresh.plan` 的 `require(source != null) { "source" }` 抛异常。
 *
 * 修复后：sortKey 只用于学校列表分组，不参与来源身份校验；刷新基线与导入草稿
 * 都能正常解码。
 */
class JwRefreshCustomUrlSchoolTest {

    private val shareUrl =
        "https://kb.chaoxing.com/res/pc/curriculum/schedule.html" +
            "?curriculumUuid=00000000-0000-4000-8000-000000000000"

    /** 自定义链接入口建出的条目：sortKey 为空串（不是目录学校） */
    private val customSchool = JwSchoolInfo(
        sortKey = "",
        name = "自定义教务",
        url = shareUrl,
        type = JwProtocol.TYPE_CHAOXING_KB,
        status = JwSchoolInfo.STATUS_SUPPORTED,
    )

    private fun course(id: Long = 1, name: String = "高等数学A（II）") = CourseEntity(
        id = id,
        groupId = "g",
        tableId = 1,
        courseName = name,
        teacher = "范老师",
        room = "北2_103",
        day = 1,
        startNode = 1,
        step = 2,
        startWeek = 1,
        endWeek = 16,
        type = 0,
        color = "#FF6750A4",
    )

    @Test
    fun `draft codec round trips custom url school with blank sortKey`() {
        val snapshot = JwImportDraftSnapshot(
            school = customSchool,
            courses = listOf(JwCourse("高等数学A（II）", "北2_103", "范老师", 1, 1, 2, 1, 16)),
            periods = listOf(JwImportDraftPeriod(1, "08:00", "08:50")),
            termStartDate = "2026-09-07",
            tableName = "教务导入 - 自定义教务",
        )
        val decoded = JwImportDraftCodec.fromJson(JwImportDraftCodec.toJson(snapshot))
        assertNotNull("自定义链接草稿必须能恢复", decoded)
        assertEquals(snapshot, decoded)
        assertEquals("", decoded!!.school.sortKey)
        assertEquals(JwProtocol.TYPE_CHAOXING_KB, decoded.school.type)
    }

    @Test
    fun `refresh source round trips custom url school with blank sortKey`() {
        val saved = listOf(course(id = 7))
        val raw = JwRefresh.encode(
            school = customSchool,
            incoming = saved,
            links = saved.associate { JwRefresh.key(it) to it.id },
            locals = saved,
        )
        val decoded = JwRefresh.decode(raw)
        assertNotNull("jwSourceJson 刷新基线必须能解码", decoded)
        assertEquals("自定义教务", decoded!!.school.name)
        assertEquals(JwProtocol.TYPE_CHAOXING_KB, decoded.school.type)
        assertEquals(shareUrl, decoded.school.url)
        assertEquals(1, decoded.rows.size)
    }

    @Test
    fun `plan accepts refresh baseline from custom url school`() {
        val saved = listOf(course(id = 7))
        val raw = JwRefresh.encode(
            school = customSchool,
            incoming = saved,
            links = saved.associate { JwRefresh.key(it) to it.id },
            locals = saved,
        )
        val table = TimeTableEntity(
            id = 1,
            name = "导入 - 郑州大学",
            startDate = "2026-09-07",
            maxWeek = 20,
            nodesPerDay = 12,
            jwSourceJson = raw,
        )
        // 教务端本次返回同一门课 → 无新增/删除，但 plan 不能抛 "source"/"school"
        val plan = JwRefresh.plan(table, table, saved, listOf(course(id = 0)), customSchool)
        assertTrue(plan.additions.isEmpty())
        assertTrue(plan.removals.isEmpty())
        assertEquals(1, plan.retained.size)
    }

    @Test
    fun `blank school name is still rejected`() {
        // 放宽 sortKey 不得顺带放过空校名
        assertNull(
            JwImportDraftCodec.fromJson(
                """{"schemaVersion":1,"school":{"sortKey":"","name":""},"courses":[],"periods":[]}"""
            )
        )
    }
}
