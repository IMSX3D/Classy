package com.imsx3d.classy.ui.screen.imports

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 超星课表分享页 (chaoxing_kb) WebView 抓取契约 — kb.chaoxing.com 通用分享链接。
 *
 * 分享页是公开 visitor 课表层, 课程数据只在 /curriculum/getOtherLessons JSON 接口里
 * (页面 HTML 无课程数据); 且同一接口不带 week 只回当前教学周, 必须并行抓 1..maxWeek
 * 周合并, 只抓当前周会丢掉其它周才出现的课。
 *
 * 跨语言 invariant: JS 只做 fetch + 传输层归一 (周桶归属 / 选哪套节次配置),
 * 课程字段解码唯一落点 = JwChaoxingKbParser (Kotlin)。JS 全文禁现
 * name/teacherName/dayOfWeek/beginNumber 等课程字段的解码逻辑。
 */
class JwChaoxingKbWebViewContractTest {

    private fun loadSource(path: String): String {
        val candidates = sequenceOf(
            System.getProperty("sleepy.test.root")?.let { File(it, path) },
            File(path),
        ).filterNotNull()
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("Unable to load $path")
    }

    private val screenSource = loadSource(
        "app/src/main/java/com/imsx3d/classy/ui/screen/imports/JwWebViewLoginScreen.kt"
    )
    private val fetchJsSource = loadSource(
        "app/src/main/java/com/imsx3d/classy/ui/screen/imports/JwChaoxingKbFetchJs.kt"
    )

    @Test
    fun chaoxingKb_dispatch_selects_a_dedicated_fetch_branch() {
        assertTrue(
            "Capturing chaoxing_kb must not fall through to generic frame capture",
            Regex("""school\.type\s*==\s*JwProtocol\.TYPE_CHAOXING_KB""")
                .containsMatchIn(screenSource)
        )
        assertTrue(
            "chaoxing_kb 分支必须注入专用 fetch JS",
            screenSource.contains("CHAOXING_KB_FETCH_JS")
        )
    }

    @Test
    fun chaoxingKbFetchJs_reads_share_uuid_and_fetches_every_week() {
        // 只断言 JS 常量本体 (文件顶部 KDoc 说明会提到 kb.chaoxing.com)
        val js = fetchJsSource.substringAfter("const val CHAOXING_KB_FETCH_JS")

        // 1. 分享链接唯一参数 curriculumUuid, 不锁 host (镜像部署照常工作)
        assertTrue("必须从 location.search 取 curriculumUuid", js.contains("curriculumUuid"))
        assertFalse("禁止硬编码 kb.chaoxing.com (镜像部署 host 不同)", js.contains("kb.chaoxing.com"))

        // 2. mctx 前缀 + getOtherLessons 端点
        assertTrue("必须按页面同款规则从 pathname 截 /res", js.contains("'/res'") || js.contains("/res"))
        assertTrue("必须请求 /curriculum/getOtherLessons", js.contains("/curriculum/getOtherLessons"))

        // 3. 逐周抓取 (不带 week 只回当前周)
        assertTrue("必须带 week 参数", js.contains("week: week") || js.contains("week:week"))
        assertTrue("必须并行抓全部周", js.contains("Promise.all"))
        assertTrue("必须按周桶回传 weeks", js.contains("weeks: good") || js.contains("weeks:good"))
        assertTrue("必须 credentials:'include'", js.contains("credentials: 'include'") || js.contains("credentials:'include'"))

        // 4. 节次配置归一 + 开学日 + {ok,data} 信封
        assertTrue("必须处理多校区节次配置", js.contains("campusLessonTimeConfigArray"))
        assertTrue("必须处理按周节次配置", js.contains("weeksLessonTimeConfigArray"))
        assertTrue("必须回传 periods", js.contains("periods"))
        assertTrue("必须回传开学日 firstWeekDateReal", js.contains("firstWeekDateReal"))
        assertTrue("必须经 __sleepyBridge.onWiseduResult 回传", js.contains("__sleepyBridge.onWiseduResult"))
        assertTrue("必须 {ok,data} 信封", js.contains("ok: true") || js.contains("ok:true"))

        // 5. 跨语言 invariant: JS 禁解码课程字段 (解码唯一落点 = JwChaoxingKbParser)
        assertFalse("JS 禁现 teacherName 解码", js.contains("teacherName"))
        assertFalse("JS 禁现 dayOfWeek 解码", js.contains("dayOfWeek"))
        assertFalse("JS 禁现 beginNumber 解码", js.contains("beginNumber"))
    }
}
