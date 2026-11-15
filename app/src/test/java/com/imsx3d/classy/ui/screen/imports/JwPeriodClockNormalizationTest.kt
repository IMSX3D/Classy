package com.imsx3d.classy.ui.screen.imports

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 桥回传节次时间的 HH:mm 归一 (JwWebViewLoginScreen.normalizePeriodClock)。
 *
 * 2026-11-15 真机反馈: 超星课表分享页的 lessonTimeConfigArray 是不补零的
 * `8:00-8:50`, 确认页校验用 `^\d{2}:\d{2}$` + 字符串比较 → 误报
 * 「第 1 节时间无效（开始须早于结束）」。所有 fetch 协议的 periods 都过这里。
 */
class JwPeriodClockNormalizationTest {

    @Test
    fun `single digit hour is zero padded`() {
        assertEquals("08:00", normalizePeriodClock("8:00"))
        assertEquals("08:50", normalizePeriodClock("8:50"))
        assertEquals("09:00", normalizePeriodClock("9:00"))
    }

    @Test
    fun `already padded clocks stay unchanged`() {
        assertEquals("08:00", normalizePeriodClock("08:00"))
        assertEquals("10:10", normalizePeriodClock("10:10"))
        assertEquals("22:50", normalizePeriodClock("22:50"))
        assertEquals("00:00", normalizePeriodClock("00:00"))
    }

    @Test
    fun `surrounding whitespace is trimmed`() {
        assertEquals("08:00", normalizePeriodClock(" 8:00 "))
        assertEquals("08:05", normalizePeriodClock(" 8:5"))
    }

    @Test
    fun `unparseable or out of range clocks are returned as-is for confirm-page validation`() {
        assertEquals("", normalizePeriodClock(""))
        assertEquals("abc", normalizePeriodClock("abc"))
        assertEquals("24:00", normalizePeriodClock("24:00"))
        assertEquals("8:60", normalizePeriodClock("8:60"))
        assertEquals("8:00-8:50", normalizePeriodClock("8:00-8:50"))
    }
}
