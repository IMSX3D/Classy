package com.imsx3d.classy.data.jw

/** SWJTU Xipu/Jiuli standard from 2025-2026 semester 1.
 * Source: https://news.swjtu.edu.cn/info/1020/80965.htm
 * Only used if the school's API supplies no periods; confirmation remains editable.
 */
internal object SwjtuPeriodFallback {
    fun forSchool(url: String): List<Triple<Int, String, String>> {
        if (runCatching { java.net.URI(url).host }.getOrNull() != "yhxt.swjtu.edu.cn") return emptyList()
        return listOf(
            "08:00" to "08:45", "08:50" to "09:35", "09:50" to "10:35",
            "10:40" to "11:25", "11:30" to "12:15", "14:00" to "14:45",
            "14:50" to "15:35", "15:40" to "16:25", "16:40" to "17:25",
            "17:30" to "18:15", "19:30" to "20:15", "20:20" to "21:05",
            "21:10" to "21:55",
        ).mapIndexed { index, (start, end) -> Triple(index + 1, start, end) }
    }
}
