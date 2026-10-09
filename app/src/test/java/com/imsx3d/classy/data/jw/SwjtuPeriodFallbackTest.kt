package com.imsx3d.classy.data.jw

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class SwjtuPeriodFallbackTest {
    @Test fun standardHasThirteenSequentialNonOverlappingPeriods() {
        val periods = SwjtuPeriodFallback.forSchool("https://yhxt.swjtu.edu.cn/")
        assertEquals((1..13).toList(), periods.map { it.first })
        periods.forEach { assertTrue(LocalTime.parse(it.second).isBefore(LocalTime.parse(it.third))) }
        periods.zipWithNext().forEach { (a,b) -> assertTrue(LocalTime.parse(a.third).isBefore(LocalTime.parse(b.second))) }
        assertEquals("21:55", periods.last().third)
    }
    @Test fun otherYethanSchoolsNeverReceiveSwjtuTimes() {
        assertTrue(SwjtuPeriodFallback.forSchool("https://other.edu.cn").isEmpty())
        assertTrue(SwjtuPeriodFallback.forSchool("https://yhxt.swjtu.edu.cn.example.com").isEmpty())
    }
}
