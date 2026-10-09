package com.imsx3d.classy.util

import com.imsx3d.classy.data.entity.CourseEntity
import kotlinx.serialization.Serializable
import java.time.LocalTime
import java.time.temporal.ChronoUnit

@Serializable
data class CourseGridPrefill(val tableId: Long, val day: Int, val startNode: Int, val step: Int, val maxWeek: Int,
    val isIrregularNode: Boolean = false)

data class GridSelection(val day: Int, val startNode: Int, val endNode: Int) {
    val step: Int get() = endNode - startNode + 1
}

/** All coordinates use the grid body's coordinate system, including weighted gap rows. */
data class GridHitRow(val node: Int?, val top: Float, val bottom: Float, val edge: Boolean = false)

object GridCourseGeometry {
    fun dayAt(x: Float, days: List<Int>, left: Float, width: Float, gap: Float): Int? {
        if (x < left || width <= 0) return null
        val index = ((x - left) / (width + gap)).toInt()
        if (index !in days.indices || x - left - index * (width + gap) >= width) return null
        return days[index]
    }

    fun nodeAt(y: Float, rows: List<GridHitRow>): Int? =
        rows.firstOrNull { y >= it.top && y < it.bottom }?.node

    fun range(day: Int, anchor: Int, end: Int, nodes: Set<Int>): GridSelection? {
        val first = minOf(anchor, end)
        val last = maxOf(anchor, end)
        if (last - first > nodes.size || (first..last).any { it !in nodes }) return null
        // Edge slots are independent meetings, never a bridge into standard periods.
        if (first <= 0 && first != last) return null
        return GridSelection(day, first, last)
    }
}

enum class CourseMoveScope { THIS_WEEK, THIS_AND_FUTURE }

/** A single row is the selected meeting; other meetings in its group are untouched. */
object CourseMovePlanner {
    data class Plan(val retained: List<CourseEntity>, val moved: List<CourseEntity>, val rows: List<CourseEntity>)
    fun plan(
        source: CourseEntity,
        sourceWeek: Int,
        targetWeek: Int,
        target: GridSelection,
        scope: CourseMoveScope,
        maxWeek: Int,
        timeJson: String
    ): List<CourseEntity> = details(source, sourceWeek, targetWeek, target, scope, maxWeek, timeJson).rows

    fun details(source: CourseEntity, sourceWeek: Int, targetWeek: Int, target: GridSelection,
        scope: CourseMoveScope, maxWeek: Int, timeJson: String): Plan {
        require(sourceWeek in 1..maxWeek && source.inWeek(sourceWeek)) { "源周次没有这次课程" }
        require(target.day in 1..7 && target.step > 0) { "请选择有效的星期和节次" }
        val slots = TimeTableUtils.parseTimeSlotRows(timeJson)
        require(target.step <= slots.size) { "目标节次超出课表范围" }
        val first = slots.firstOrNull { it.node == target.startNode }
        require(first != null) { "目标节次已变化，请重新拖动" }
        require((target.startNode..target.endNode).all { n -> slots.any { it.node == n } }) { "目标节次不连续" }
        require(target.step == 1 || slots.filter { it.node in target.startNode..target.endNode }.none { it.edgeClass != null }) {
            "非常规节次需单独选择，不能跨入普通节次"
        }
        val delta = targetWeek - sourceWeek
        val active = (source.startWeek..source.endWeek).filter(source::inWeek)
        val movedWeeks = active.filter { it == sourceWeek || (scope == CourseMoveScope.THIS_AND_FUTURE && it > sourceWeek) }
        require(movedWeeks.all { it + delta in 1..maxWeek }) { "调课后超出学期周数，请调整目标周或选择仅本周" }
        val retained = active - movedWeeks.toSet()
        val moved = if (source.ownTime || source.isIrregularTime) {
            val duration = ChronoUnit.MINUTES.between(LocalTime.parse(source.startTime), LocalTime.parse(source.endTime))
            val start = LocalTime.parse(first.start)
            require(duration > 0 && start.toSecondOfDay() / 60 + duration < 24 * 60) { "目标时间超出当天" }
            source.copy(day = target.day, startNode = target.startNode, step = target.step,
                ownTime = true, isIrregularTime = true,
                startTime = start.toString(), endTime = start.plusMinutes(duration).toString(),
                isIrregularNode = target.startNode !in 1..TimeTableUtils.maxStandardNode(timeJson))
        } else source.copy(day = target.day, startNode = target.startNode, step = target.step,
            isIrregularNode = target.startNode !in 1..TimeTableUtils.maxStandardNode(timeJson))
        fun rows(base: CourseEntity, weeks: List<Int>, type: Int): List<CourseEntity> {
            if (weeks.isEmpty()) return emptyList()
            val stride = if (type == 1 || type == 2) 2 else 1
            val groups = mutableListOf<MutableList<Int>>()
            for (week in weeks.sorted()) {
                if (groups.isEmpty() || week - groups.last().last() != stride) groups.add(mutableListOf())
                groups.last().add(week)
            }
            return groups.map { base.copy(id = 0, startWeek = it.first(), endWeek = it.last(), type = type) }
        }
        val movedType = if (scope == CourseMoveScope.THIS_WEEK) 3 else when {
            source.type in 1..2 && delta % 2 != 0 -> 3 - source.type
            else -> source.type
        }
        val retainedRows = rows(source, retained, source.type)
        val movedRows = rows(moved, movedWeeks.map { it + delta }, movedType)
        val allRows = (retainedRows + movedRows).mapIndexed { index, row -> if (index == 0) row.copy(id = source.id) else row }
        return Plan(retainedRows, movedRows, allRows)
    }
}
