package com.imsx3d.classy.util

import android.net.Uri

/**
 * QQ 加群拉起链路(纯 URI 字符串构造, 便于 JVM 单测; 拉起时序见 AboutScreen.joinQqGroup)。
 *
 * 通道优先级:
 * 1. [groupCardUri] — mqqapi://card/show_pslcard 凭群号拉群资料卡, 无需官网 key
 * 2. [groupCardUriAlt] — mqqapi://card/show_pslg, 部分版本接受的备选形态
 * 3. [officialJoinComponentUri] — 官方加群组件, 需要群主在 qun.qq.com 生成的 key;
 *    没有 key 时返回 null(旧实现凭空拼这个 URL 正是"点了没反应"的根因)
 * 4. [webFallbackUri] — 浏览器打开 qm.qq.com 加群页兜底
 */
object QqJoin {

    const val SCHEME_MAIN = "mqqapi"
    const val CARD_TYPE_GROUP = "card_type=group"

    fun groupCardUri(groupNumber: String): String =
        "mqqapi://card/show_pslcard?src_type=internal&version=1&card_type=group&uin=$groupNumber"

    fun groupCardUriAlt(groupNumber: String): String =
        "mqqapi://card/show_pslg?src_type=internal&version=1&groupuin=$groupNumber"

    fun officialJoinComponentUri(groupNumber: String, key: String?): String? {
        if (key.isNullOrBlank()) return null
        val inner = "https://qm.qq.com/cgi-bin/qm/qr?from=app&p=android&jump_from=webapi&k=$key"
        return "mqqopensdkapi://bizAgent/qm/qr?url=${urlEncode(inner)}"
    }

    /** 纯 Kotlin percent-encode(与 android.net.Uri.encode 同保留集), JVM 单测可验证 */
    private fun urlEncode(s: String): String = buildString {
        val keep = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-!.~'()*"
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = (b.toInt() and 0xFF).toChar()
            if (keep.indexOf(c) >= 0) append(c)
            else append('%').append(HEX[(b.toInt() shr 4) and 0xF]).append(HEX[b.toInt() and 0xF])
        }
    }

    private const val HEX = "0123456789ABCDEF"

    fun webFallbackUri(groupNumber: String): String =
        "https://qm.qq.com/cgi-bin/qm/qr?from=app&jump_from=webapi&href=$groupNumber"
}
