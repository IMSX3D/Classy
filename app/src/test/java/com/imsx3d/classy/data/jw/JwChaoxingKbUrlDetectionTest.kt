package com.imsx3d.classy.data.jw

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 超星课表分享页 URL 判型 — 「自定义教务链接」入口把分享链接直接路由到
 * chaoxing_kb 专用抓取分支, 而不是通用 HTML 抓取 (页面 HTML 无课程数据, 必 0 课)。
 *
 * 接口形态见 [JwChaoxingKbParser]; 判型锚点 = `curriculumUuid=` + `/curriculum/schedule`
 * 路径。无 uuid 的 schedule.html 是"我的课表"登录态页面, 不臆测为分享页。
 */
class JwChaoxingKbUrlDetectionTest {

    private fun detect(url: String): String? =
        JwImportViewModel.detectProtocolFromUrlForTest(url)

    @Test
    fun `pc share url detects chaoxing_kb`() {
        assertEquals(
            JwProtocol.TYPE_CHAOXING_KB,
            detect(
                "https://kb.chaoxing.com/res/pc/curriculum/schedule.html" +
                    "?curriculumUuid=00000000-0000-4000-8000-000000000000"
            )
        )
    }

    @Test
    fun `mobile share path also detects chaoxing_kb`() {
        assertEquals(
            JwProtocol.TYPE_CHAOXING_KB,
            detect(
                "https://kb.chaoxing.com/res/app/curriculum/schedule.html" +
                    "?curriculumUuid=00000000-0000-4000-8000-000000000000"
            )
        )
    }

    @Test
    fun `extra share params and visitor mode do not break detection`() {
        assertEquals(
            JwProtocol.TYPE_CHAOXING_KB,
            detect(
                "https://kb.chaoxing.com/res/pc/curriculum/schedule.html?visitor=1" +
                    "&curriculumUuid=00000000-0000-4000-8000-000000000000&from=share"
            )
        )
    }

    @Test
    fun `mirror deployment sub path keeps detection`() {
        // 镜像部署可挂在子路径下 (页面脚本用 mctx 前缀), 判型不锁 host / 前缀
        assertEquals(
            JwProtocol.TYPE_CHAOXING_KB,
            detect(
                "https://kb.example.edu.cn/chaoxing/res/pc/curriculum/schedule.html" +
                    "?curriculumUuid=00000000-0000-4000-8000-000000000000"
            )
        )
    }

    @Test
    fun `uppercase url is normalized before detection`() {
        assertEquals(
            JwProtocol.TYPE_CHAOXING_KB,
            detect(
                "HTTPS://KB.CHAOXING.COM/RES/PC/CURRICULUM/SCHEDULE.HTML" +
                    "?CURRICULUMUUID=1E82F944-9DAB-4629-AB21-A333E03DB043"
            )
        )
    }

    @Test
    fun `schedule page without curriculumUuid is not a share page`() {
        // 无 uuid = 本人登录态课表页, 不应判成公开分享协议
        assertNull(detect("https://kb.chaoxing.com/res/pc/curriculum/schedule.html"))
    }

    @Test
    fun `uuid on a non curriculum path is not a share page`() {
        assertNull(detect("https://kb.chaoxing.com/res/pc/index.html?curriculumUuid=abc"))
        assertNull(detect("https://example.com/schedule?curriculumUuid=abc"))
    }

    @Test
    fun `chaoxing integrated jw admin login page stays unrecognized`() {
        // 综合教务 (TYPE_CHAOXING) 的登录入口不带分享参数, 不得被本锚点吸走
        assertNull(detect("https://jw.chaoxing.com/admin/login"))
    }
}
