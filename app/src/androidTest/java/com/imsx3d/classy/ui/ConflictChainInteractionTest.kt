package com.imsx3d.classy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.ui.component.ConflictClusterCard
import com.imsx3d.classy.ui.component.TimeSlot
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import com.imsx3d.classy.util.ConflictCluster
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ConflictChainInteractionTest {
    @get:Rule val compose = createComposeRule()
    @Test fun everyChainMemberFoldSwitchesAndReturnsWithoutOpeningDetails() {
        val rows = listOf(course(1, 1, 3), course(2, 4, 3), course(3, 1, 4))
        var top by mutableStateOf<Long?>(2L)
        var opens = 0
        var writes = 0
        val slots = (1..6).map { n ->
            val start = LocalTime.of(7 + n, 0)
            TimeSlot("$n", start, start.plusMinutes(45), start.toString(), start.plusMinutes(45).toString(), n, n)
        }
        compose.setContent { SleepyThemeProvider {
            Box(Modifier.padding(16.dp)) {
                ConflictClusterCard(ConflictCluster(1, rows), "fold", top,
                    onPickTop = { top = it; writes++ }, onCourseClick = { opens++ },
                    colW = 120.dp, rowH = 52.dp, maxNode = 6, timeW = 0.dp,
                    gapW = 0.dp, gapH = 4.dp, timeSlots = slots, isGrey = false,
                    modifier = Modifier.testTag("chain"))
            }
        } }
        val node = compose.onNodeWithTag("chain")
        val initial = node.captureToImage().asAndroidBitmap()
        // Both head and tail have a visible folded corner. Each must switch the whole layer.
        for (startDp in listOf(156f, 0f, 156f)) {
            node.performTouchInput { down(Offset(width - 8f, height * (startDp + 6f) / 308f)) }
            compose.waitForIdle()
            node.performTouchInput { cancel() }
            compose.runOnIdle { assertEquals(2L, top); assertEquals(0, opens) }
            node.performTouchInput { click(Offset(width - 8f, height * (startDp + 6f) / 308f)) }
            compose.runOnIdle { assertEquals("each folded corner must switch", 3L, top); assertEquals(0, opens) }
            node.performTouchInput { click(Offset(width - 8f, height * 6f / 308f)) }
            compose.runOnIdle { assertEquals(2L, top); assertEquals(0, opens) }
            checkImage("chain", initial, node.captureToImage().asAndroidBitmap())
        }
        compose.runOnIdle { assertEquals(6, writes) }
        node.performTouchInput { click(Offset(width / 2f, height * 0.7f)) }
        compose.runOnIdle { assertEquals(1, opens) }
    }
    @Test fun unequalStackRoundTrip() = unequal("stack")
    @Test fun unequalRailRoundTrip() = unequal("rail")
    @Test fun unequalFoldRoundTrip() = unequal("fold")

    private fun unequal(style: String) {
        val rows = listOf(course(1, 1, 4), course(2, 2, 1))
        var top by mutableStateOf<Long?>(2L)
        var opens = 0
        var writes = 0
        val slots = (1..6).map { n ->
            val start = LocalTime.of(7 + n, 0)
            TimeSlot("$n", start, start.plusMinutes(45), start.toString(), start.plusMinutes(45).toString(), n, n)
        }
        compose.setContent { SleepyThemeProvider {
            Box(Modifier.padding(16.dp)) {
                ConflictClusterCard(ConflictCluster(1, rows), style, top,
                    onPickTop = { top = it; writes++ }, onCourseClick = { opens++ },
                    colW = 120.dp, rowH = 52.dp, maxNode = 6, timeW = 0.dp,
                    gapW = 0.dp, gapH = 4.dp, timeSlots = slots, isGrey = false,
                    modifier = Modifier.testTag("unequal"))
            }
        } }
        val node = compose.onNodeWithTag("unequal")
        val initial = node.captureToImage().asAndroidBitmap()
        repeat(3) {
            node.performTouchInput {
                val point = if (style == "fold") Offset(width - 8f, height * 58f / 204f)
                    else Offset(width / 2f, height * .8f)
                down(point)
            }
            compose.waitForIdle()
            node.performTouchInput { cancel() }
            compose.runOnIdle { assertEquals(2L, top); assertEquals(0, opens) }
            node.performTouchInput {
                click(if (style == "fold") Offset(width - 8f, height * 58f / 204f)
                    else Offset(width / 2f, height * .8f))
            }
            compose.runOnIdle { assertEquals(1L, top); assertEquals(0, opens) }
            node.performTouchInput {
                click(Offset(width - 8f, height * (if (style == "fold") 6f else 90f) / 204f))
            }
            compose.runOnIdle { assertEquals(2L, top); assertEquals(0, opens) }
            checkImage("unequal-$style", initial, node.captureToImage().asAndroidBitmap())
        }
        compose.runOnIdle { assertEquals(6, writes) }
    }
    private fun checkImage(name: String, before: android.graphics.Bitmap, after: android.graphics.Bitmap) {
        val dir = java.io.File(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .targetContext.getExternalFilesDir(null), "boundary").apply { mkdirs() }
        for ((suffix, bitmap) in listOf("before" to before, "after" to after)) {
            java.io.File(dir, "$name-$suffix.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        assertEquals(before.width, after.width)
        assertEquals(before.height, after.height)
        // Recreated folded paths can round a handful of antialias channels by one level.
        // Keep every pixel in place and reject larger differences (including stuck feedback).
        for (x in 0 until before.width) for (y in 0 until before.height) {
            val a = before.getPixel(x, y)
            val b = after.getPixel(x, y)
            for (shift in listOf(0, 8, 16, 24)) {
                assertTrue("$name changed pixel ($x,$y)",
                    kotlin.math.abs(((a ushr shift) and 255) - ((b ushr shift) and 255)) <= 1)
            }
        }
    }
    private fun course(id: Long, start: Int, count: Int) = CourseEntity(id = id, tableId = 98000,
        groupId = "chain-$id", courseName = "Chain $id", day = 1, startNode = start,
        step = count, startWeek = 1, endWeek = 16, color = "#AACCEE")
}

