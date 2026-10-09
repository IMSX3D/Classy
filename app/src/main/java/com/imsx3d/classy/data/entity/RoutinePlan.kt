package com.imsx3d.classy.data.entity

/** UI model; persisted through the existing SmartPeriodConfig format. */
data class RoutineSection(
    val start: String,
    val count: Int,
    val durations: Map<Int, Int> = emptyMap(),
    val gaps: Map<Int, Int> = emptyMap(),
)

data class RoutinePlan(
    val minutes: Int,
    val gap: Int,
    val sections: List<RoutineSection>,
) {
    fun toConfig(): SmartPeriodConfig {
        require(minutes in 1..240) { "每节时长需为 1–240 分钟" }
        require(gap in 0..180) { "普通课间需为 0–180 分钟" }
        require(sections.isNotEmpty() && sections.sumOf { it.count } in 1..48) { "总节数需为 1–48 节" }
        // Keep the base rule even for a one-lesson timetable or all-special gaps.
        val breaks = mutableListOf(BreakOption(gap, false, DEFAULT_GAP_LABEL))
        val assignments = mutableListOf<Int?>()
        val durations = mutableListOf<DurationOption>()
        val periodAssignments = mutableListOf<Int?>()
        var previousEnd = -1
        sections.forEachIndexed { sectionIndex, section ->
            require(section.count in 1..24) { "每个时段需为 1–24 节" }
            var clock = minuteOfDay(section.start)
            if (sectionIndex > 0) {
                require(clock >= previousEnd) { "时段重叠：后一时段需在前一时段下课后开始" }
                assignments += breaks.size
                breaks += BreakOption(clock - previousEnd, true, SECTION_LABEL)
            }
            repeat(section.count) { index ->
                val duration = section.durations[index] ?: minutes
                require(duration in 1..240) { "特殊课时需为 1–240 分钟" }
                if (duration == minutes) periodAssignments += null else {
                    periodAssignments += durations.size
                    durations += DurationOption(duration, duration > minutes)
                }
                clock += duration
                require(clock < 24 * 60) { "当天课程需在 24:00 前结束，请减少节数或时长" }
                if (index < section.count - 1) {
                    val rest = section.gaps[index] ?: gap
                    require(rest in 0..180) { "特殊课间需为 0–180 分钟" }
                    assignments += breaks.size
                    breaks += BreakOption(rest, rest >= 40)
                    clock += rest
                }
            }
            previousEnd = clock
        }
        return SmartPeriodConfig(sections.first().start, minutes, sections.sumOf { it.count },
            breaks, assignments, durations, periodAssignments)
    }

    companion object {
        private const val SECTION_LABEL = "时段开始（固定时间）"
        private const val DEFAULT_GAP_LABEL = "普通课间（默认规则）"

        fun minuteOfDay(time: String): Int {
            require(time.matches(Regex("\\d{2}:\\d{2}"))) { "请输入有效的开始时间" }
            val (h, m) = time.split(':').map { it.toInt() }
            require(h in 0..23 && m in 0..59) { "请输入有效的开始时间" }
            return h * 60 + m
        }

        /** Inference never changes existing start/end times, including zero-length gaps. */
        fun from(config: SmartPeriodConfig): RoutinePlan {
            val rows = config.derive()
            require(rows.isNotEmpty())
            val gaps = config.effectiveTransitionMinutes()
            val assignments = config.effectiveAssignments()
            val explicit = gaps.indices.filter {
                config.breaks.getOrNull(assignments[it] ?: -1)?.label == SECTION_LABEL
            }.toSet()
            // Long rests in old configurations suggest a new segment; retain every exact time.
            val savedGap = config.breaks.firstOrNull { it.label == DEFAULT_GAP_LABEL }?.minutes
            val boundaries = if (savedGap != null || explicit.isNotEmpty()) explicit else gaps.indices.filter { gaps[it] >= 40 }.toSet()
            val ordinary = gaps.filterIndexed { i, _ -> i !in boundaries }
            val defaultGap = savedGap ?: ordinary.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: 10
            val starts = listOf(0) + boundaries.sorted().map { it + 1 }
            val lengths = config.effectivePeriodMinutes()
            val sections = starts.mapIndexed { i, start ->
                val end = starts.getOrNull(i + 1) ?: rows.size
                RoutineSection(rows[start].start, end - start,
                    (start until end).filter { lengths[it] != config.periodMinutes }
                        .associate { (it - start) to lengths[it] },
                    (start until end - 1).filter { gaps[it] != defaultGap }
                        .associate { (it - start) to gaps[it] })
            }
            return RoutinePlan(config.periodMinutes, defaultGap, sections).also { it.toConfig() }
        }
    }
}
