package com.imsx3d.classy.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * QQ 加群拉起链路契约测试(纯 JVM, 字符串级断言)。
 *
 * 正确通道(经查证):
 * 1. mqqapi://card/show_pslcard — 凭群号直接拉起群资料卡, 无需官网 key
 * 2. mqqapi://card/show_pslg — 另一形态群资料卡(部分版本接受)
 * 3. 官方加群组件 mqqopensdkapi://bizAgent/qm/qr 需要群主在 qun.qq.com 生成的
 *    k=<key>, 凭空拼 URL 无法通过 QQ JumpActivity 校验, 这正是旧实现失效的根因
 */
class QqJoinTest {

    @Test
    fun groupCardScheme_carriesGroupNumberAsUin() {
        val uri = QqJoin.groupCardUri("1063407652")
        assertTrue("必须是 mqqapi scheme: $uri", uri.startsWith("mqqapi://card/show_pslcard?"))
        val params = uri.queryPairs()
        assertEquals("internal", params["src_type"])
        assertEquals("1", params["version"])
        assertEquals("group", params["card_type"])
        assertEquals("1063407652", params["uin"])
    }

    @Test
    fun groupCardSchemeAlternate_acceptsGroupNumber() {
        val uri = QqJoin.groupCardUriAlt("1063407652")
        assertTrue("必须是 show_pslg 备选形态: $uri", uri.startsWith("mqqapi://card/show_pslg?"))
        val params = uri.queryPairs()
        assertEquals("1063407652", params["groupuin"])
    }

    @Test
    fun officialComponentUri_requiresKey_neverBuiltFromGroupNumberAlone() {
        // 旧实现的错误: 把群号拼进 join_group= 无名字段, QQ 解析不出群
        // 契约: 没有官方 key 时绝不生成该通道的 URI
        assertNull(QqJoin.officialJoinComponentUri("1063407652", key = null))
        assertNull(QqJoin.officialJoinComponentUri("1063407652", key = "  "))

        val withKey = requireNotNull(QqJoin.officialJoinComponentUri("1063407652", key = "lTKabc123HTg"))
        val params = withKey.queryPairs()
        val inner = java.net.URLDecoder.decode(requireNotNull(params["url"]), "UTF-8")
        assertTrue("内层 URL 必须带 k=<key>", inner.contains("k=lTKabc123HTg"))
        assertTrue("内层 URL 必须是 qm.qq.com 加群组件链接", inner.contains("qm.qq.com"))
    }

    @Test
    fun webFallbackUri_opensQmGroupPage() {
        val uri = QqJoin.webFallbackUri("1063407652")
        assertTrue("必须是 https qm.qq.com: $uri", uri.startsWith("https://qm.qq.com/"))
        val params = uri.queryPairs()
        assertEquals("1063407652", params["href"])
    }

    private fun String.queryPairs(): Map<String, String> =
        substringAfter('?', "").split('&').filter { it.contains('=') }.associate {
            val i = it.indexOf('=')
            java.net.URLDecoder.decode(it.substring(0, i), "UTF-8") to
                java.net.URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }
}
