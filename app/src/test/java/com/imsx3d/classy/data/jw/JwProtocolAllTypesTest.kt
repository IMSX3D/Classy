package com.imsx3d.classy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T8 — JwProtocol 常量完备性 + displayName/category 一致性。
 */
class JwProtocolAllTypesTest {

    @Test
    fun `TYPE_HNUST and TYPE_HNIU are defined`() {
        assertNotNull(JwProtocol.TYPE_HNUST)
        assertNotNull(JwProtocol.TYPE_HNIU)
        assertEquals("hnust", JwProtocol.TYPE_HNUST)
        assertEquals("hniu", JwProtocol.TYPE_HNIU)
    }

    @Test
    fun `ALL_TYPES contains cf_new`() {
        assertTrue(JwProtocol.ALL_TYPES.contains(JwProtocol.TYPE_CF_NEW))
        assertEquals("青果教务（新版）", JwProtocol.displayName(JwProtocol.TYPE_CF_NEW))
        assertEquals("cf", JwProtocol.category(JwProtocol.TYPE_CF_NEW))
    }

    @Test
    fun `ALL_TYPES contains hnust and hniu`() {
        assertTrue(JwProtocol.ALL_TYPES.contains(JwProtocol.TYPE_HNUST))
        assertTrue(JwProtocol.ALL_TYPES.contains(JwProtocol.TYPE_HNIU))
    }

    @Test
    fun `displayName for hnust and hniu is non-blank`() {
        assertTrue(JwProtocol.displayName(JwProtocol.TYPE_HNUST).isNotBlank())
        assertTrue(JwProtocol.displayName(JwProtocol.TYPE_HNIU).isNotBlank())
    }

    @Test
    fun `category for hnust and hniu is hnust`() {
        assertEquals("hnust", JwProtocol.category(JwProtocol.TYPE_HNUST))
        assertEquals("hnust", JwProtocol.category(JwProtocol.TYPE_HNIU))
    }

    @Test
    fun `ALL_TYPES has consistent displayName for every entry`() {
        for (t in JwProtocol.ALL_TYPES) {
            assertTrue("type $t displayName 必须非空", JwProtocol.displayName(t).isNotBlank())
            assertTrue(
                "type $t category 必须落到已知九档之一",
                JwProtocol.category(t) in setOf("qz", "zf", "urp", "wisedu", "cqu", "chaoxing", "eams5", "hnust", "cf", "other"),
            )
        }
    }

    @Test
    fun `custom url source shows protocol name instead of generic placeholder`() {
        // 自定义链接入口：sortKey 空串 + 通用名「自定义教务」→ 显示协议名
        val custom = JwSchoolInfo(
            sortKey = "",
            name = "自定义教务",
            url = "https://kb.chaoxing.com/res/pc/curriculum/schedule.html?curriculumUuid=x",
            type = JwProtocol.TYPE_CHAOXING_KB,
        )
        assertEquals("超星课表分享", JwProtocol.sourceDisplayName(custom))
        // 学校目录条目仍显示学校名
        val catalog = JwSchoolInfo(sortKey = "H", name = "合肥工业大学", type = JwProtocol.TYPE_EAMS5)
        assertEquals("合肥工业大学", JwProtocol.sourceDisplayName(catalog))
        // 未知 type 的自定义条目回退占位名，不显示空串
        val unknown = JwSchoolInfo(sortKey = "", name = "自定义教务", type = "unknown_type")
        assertEquals("自定义教务", JwProtocol.sourceDisplayName(unknown))
    }
}
