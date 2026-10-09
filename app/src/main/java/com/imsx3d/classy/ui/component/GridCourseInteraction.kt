package com.imsx3d.classy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.util.GridCourseGeometry
import com.imsx3d.classy.util.GridHitRow
import com.imsx3d.classy.util.GridSelection

internal class GridCourseInteraction {
    var selection by mutableStateOf<GridSelection?>(null)
    var moving by mutableStateOf<CourseEntity?>(null)
    var dragging by mutableStateOf(false)
    var grid: LayoutCoordinates? = null
    var days: List<Int> = emptyList()
    var rows: List<GridHitRow> = emptyList()
    var left = 0f
    var width = 0f
    var gap = 0f
    var onMove: (CourseEntity, GridSelection) -> Unit = { _, _ -> }
    var onAdd: (GridSelection) -> Unit = {}
    var pointerRoot: Offset? = null
    var resizeAnchor: Int? = null
    var grabOffset = 0

    fun clear() { selection = null; moving = null; dragging = false; pointerRoot = null; resizeAnchor = null }
    fun updatePointer(root: Offset) { pointerRoot = root; refreshPointer() }
    fun refreshPointer() {
        val root = pointerRoot ?: return
        val coordinates = grid?.takeIf { it.isAttached } ?: return
        val local = root - coordinates.positionInRoot()
        if (moving != null) move(local, grabOffset) else resizeAnchor?.let { resize(local, it) }
    }
    fun hit(position: Offset): Pair<Int, Int>? {
        val day = GridCourseGeometry.dayAt(position.x, days, left, width, gap) ?: return null
        val node = GridCourseGeometry.nodeAt(position.y, rows) ?: return null
        return day to node
    }
    fun select(position: Offset) {
        val (day, node) = hit(position) ?: run { clear(); return }
        selection = GridSelection(day, node, node)
    }
    fun resize(position: Offset, anchor: Int) {
        val node = GridCourseGeometry.nodeAt(position.y, rows) ?: return
        val old = selection ?: return
        val range = GridCourseGeometry.range(old.day, anchor, node, rows.mapNotNull { it.node }.toSet())
        if (range != null && (range.step == 1 || rows.none { it.edge && it.node in range.startNode..range.endNode })) selection = range
    }
    fun move(position: Offset, grabOffset: Int) {
        val course = moving ?: return
        val (day, node) = hit(position) ?: return
        val realRows = rows.mapNotNull { it.node }
        val sourceIndex = realRows.indexOf(node) - grabOffset
        val span = course.step.coerceAtLeast(1).coerceAtMost(realRows.size)
        val index = sourceIndex.coerceIn(0, (realRows.size - span).coerceAtLeast(0))
        val first = realRows.getOrNull(index) ?: return
        val last = realRows.getOrNull(index + span - 1) ?: return
        val range = GridCourseGeometry.range(day, first, last, realRows.toSet())
        if (range != null && (range.step == 1 || rows.none { it.edge && it.node in range.startNode..range.endNode })) selection = range
    }
}

internal fun Modifier.courseDrag(course: CourseEntity, interaction: GridCourseInteraction): Modifier = composed {
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val latestCourse by rememberUpdatedState(course)
    val haptics = LocalHapticFeedback.current
    onGloballyPositioned { coordinates = it }.semantics {
        customActions = listOf(CustomAccessibilityAction("调整课程时间") {
            interaction.onMove(latestCourse, GridSelection(latestCourse.day, latestCourse.startNode,
                latestCourse.startNode + latestCourse.step - 1))
            true
        })
    }.pointerInput(interaction, course.id) {
        detectDragGesturesAfterLongPress(
            onDragStart = { local ->
                val grid = interaction.grid
                val card = coordinates
                if (grid?.isAttached == true && card?.isAttached == true) {
                    val pointer = grid.localPositionOf(card, local)
                    val real = interaction.rows.mapNotNull { it.node }
                    val pressed = GridCourseGeometry.nodeAt(pointer.y, interaction.rows)
                    interaction.grabOffset = (real.indexOf(pressed) - real.indexOf(latestCourse.startNode)).coerceAtLeast(0)
                    interaction.moving = latestCourse
                    interaction.selection = GridSelection(latestCourse.day, latestCourse.startNode,
                        latestCourse.startNode + latestCourse.step - 1)
                    interaction.dragging = true
                    interaction.pointerRoot = card.localToRoot(local)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onDrag = { change, _ ->
                change.consume()
                coordinates?.takeIf { it.isAttached }?.let { interaction.updatePointer(it.localToRoot(change.position)) }
            },
            onDragCancel = interaction::clear,
            onDragEnd = {
                val source = interaction.moving
                val target = interaction.selection
                interaction.clear()
                if (source != null && target != null &&
                    (source.day != target.day || source.startNode != target.startNode)) {
                    interaction.onMove(source, target)
                }
            }
        )
    }
}

@Composable
internal fun BoxScope.GridSelectionOverlay(interaction: GridCourseInteraction) {
    val selection = interaction.selection ?: return
    val first = interaction.rows.firstOrNull { it.node == selection.startNode } ?: return
    val last = interaction.rows.firstOrNull { it.node == selection.endNode } ?: return
    val density = LocalDensity.current
    val left = interaction.left + interaction.days.indexOf(selection.day) * (interaction.width + interaction.gap)
    val moving = interaction.moving != null
    val shape = RoundedCornerShape(10.dp)
    val scheme = MaterialTheme.colorScheme
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var modifier = Modifier.offset(with(density) { left.toDp() }, with(density) { first.top.toDp() })
        .width(with(density) { interaction.width.toDp() })
        .height(with(density) { (last.bottom - first.top).toDp() })
        .background(scheme.primaryContainer.copy(alpha = .88f), shape)
        .border(2.dp, scheme.primary, shape)
        .onGloballyPositioned { coordinates = it }
    if (!moving) {
        modifier = modifier
            .semantics {
                contentDescription = "新增课程，第 ${selection.startNode}–${selection.endNode} 节"
                onClick("填写课程") { interaction.onAdd(selection); interaction.clear(); true }
            }
            // Stable key: updating the selection during a drag must not restart the detector.
            .pointerInput(interaction) {
                detectTapGestures(onTap = {
                    interaction.selection?.let(interaction.onAdd)
                    interaction.clear()
                })
            }
            .pointerInput(interaction) {
                detectDragGestures(
                    onDragStart = { position ->
                        val current = interaction.selection
                        if (current != null) {
                            val top = interaction.rows.first { it.node == current.startNode }.top
                            val bottom = interaction.rows.first { it.node == current.endNode }.bottom
                            interaction.resizeAnchor = if (position.y < (bottom - top) / 2) current.endNode else current.startNode
                            interaction.pointerRoot = coordinates?.takeIf { it.isAttached }?.localToRoot(position)
                            interaction.dragging = true
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        coordinates?.takeIf { it.isAttached }?.let { interaction.updatePointer(it.localToRoot(change.position)) }
                    },
                    onDragEnd = { interaction.dragging = false; interaction.pointerRoot = null },
                    onDragCancel = interaction::clear
                )
            }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(if (moving) "${interaction.moving?.courseName}\n${selection.startNode}–${selection.endNode} 节"
            else "↕\n＋\n${selection.startNode}–${selection.endNode}",
            color = scheme.onPrimaryContainer, fontSize = 12.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
