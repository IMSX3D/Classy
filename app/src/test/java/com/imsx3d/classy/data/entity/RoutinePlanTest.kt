package com.imsx3d.classy.data.entity

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RoutinePlanTest {
    private fun sample() = RoutinePlan(45, 10, listOf(RoutineSection("08:00", 4), RoutineSection("14:00", 4)))

    @Test fun `ordinary breaks and fixed afternoon start`() {
        val rows = sample().toConfig().derive()
        assertEquals("08:55", rows[1].start)
        assertEquals("11:30", rows[3].end)
        assertEquals("14:00", rows[4].start)
        assertEquals("14:00", sample().copy(minutes = 50).toConfig().derive()[4].start)
    }

    @Test fun `legacy irregular durations and consecutive lessons remain exact`() {
        val old = SmartPeriodConfig("07:30", 45, 6,
            listOf(BreakOption(0), BreakOption(15), BreakOption(120)), listOf(0, 1, 2, 0, 1),
            listOf(DurationOption(35)), listOf(null, 0, null, null, 0, null))
        assertEquals(old.derive(), RoutinePlan.from(old).toConfig().derive())
    }

    @Test fun `zero breaks are never replaced by guessed ten minutes`() {
        val old = SmartPeriodConfig(totalPeriods = 4)
        val plan = RoutinePlan.from(old)
        assertEquals(0, plan.gap)
        assertEquals(old.derive(), plan.toConfig().derive())
    }

    @Test fun `fixed boundaries survive save and reopen even with short gap`() {
        val plan = RoutinePlan(45, 10, listOf(RoutineSection("08:00", 2), RoutineSection("09:45", 2)))
        val saved = Json.encodeToString(plan.toConfig())
        val restored = RoutinePlan.from(Json.decodeFromString<SmartPeriodConfig>(saved))
        assertEquals(2, restored.sections.size)
        assertEquals("09:45", restored.sections[1].start)
        assertEquals(plan.toConfig().derive(), restored.toConfig().derive())
    }

    @Test fun `individual exceptions survive ordinary rule changes`() {
        val plan = sample().copy(sections = listOf(RoutineSection("08:00", 4,
            mapOf(1 to 30), mapOf(0 to 0, 2 to 20)), RoutineSection("14:00", 4)))
        val config = plan.copy(minutes = 50, gap = 15).toConfig()
        assertEquals(30, config.effectivePeriodMinutes()[1])
        assertEquals(listOf(0, 15, 20), config.effectiveTransitionMinutes().take(3))
        assertEquals("14:00", config.derive()[4].start)
    }

    @Test fun `default gap and long exception retain their meaning on reopen`() {
        val plan = RoutinePlan(45, 15, listOf(RoutineSection("08:00", 3, gaps = mapOf(0 to 60, 1 to 20))))
        val restored = RoutinePlan.from(plan.toConfig())
        assertEquals(plan, restored)
        val single = RoutinePlan(45, 20, listOf(RoutineSection("08:00", 1)))
        assertEquals(single, RoutinePlan.from(single.toConfig()))
    }

    @Test fun `overlap invalid time excessive count and overnight are rejected`() {
        val invalid = listOf(
            sample().copy(sections = listOf(RoutineSection("08:00", 4), RoutineSection("09:00", 2))),
            sample().copy(sections = listOf(RoutineSection("25:00", 2))),
            sample().copy(sections = listOf(RoutineSection("23:00", 2))),
            sample().copy(sections = listOf(RoutineSection("08:00", 0))),
            sample().copy(minutes = 0), sample().copy(gap = -1))
        invalid.forEach { assertTrue(runCatching { it.toConfig() }.isFailure) }
    }

    @Test fun `round trip many valid irregular legacy schedules`() {
        val random = kotlin.random.Random(23)
        repeat(150) {
            val n = random.nextInt(1, 13)
            val config = SmartPeriodConfig("06:00", 40, n,
                listOf(BreakOption(0), BreakOption(10), BreakOption(20), BreakOption(60)),
                List(n - 1) { random.nextInt(4) }, listOf(DurationOption(30), DurationOption(50)),
                List(n) { if (random.nextBoolean()) null else random.nextInt(2) })
            assertEquals(config.derive(), RoutinePlan.from(config).toConfig().derive())
        }
    }
}
