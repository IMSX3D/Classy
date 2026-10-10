package com.imsx3d.classy.ui.component

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import androidx.compose.animation.core.TargetBasedAnimation
import androidx.compose.animation.core.VectorConverter
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.selects.select
import org.junit.Assert.*
import org.junit.Test

class NavigationThumbMotionTest {
    private class Clock : MonotonicFrameClock {
        val requests = Channel<CompletableDeferred<Long>>(Channel.UNLIMITED)
        var time = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            val request = CompletableDeferred<Long>()
            requests.send(request)
            return onFrame(request.await())
        }
        suspend fun frame() {
            requests.receive().complete(time)
            time += 16_666_667L
            yield()
        }
        suspend fun finish(job: Job) {
            while (!job.isCompleted) {
                select<Unit> {
                    job.onJoin { }
                    requests.onReceive { it.complete(time); time += 16_666_667L }
                }
                yield()
            }
            job.join()
        }
    }

    @Test fun rapidReturnToOriginalTabFinishesAtOriginalPosition() = runBlocking {
        withTimeout(5_000) {
            val clock = Clock()
            withContext(clock) {
                val state = PillBarState()
                state.moveThumb(0, 32f, snap = true)
                val outward = launch(start = CoroutineStart.UNDISPATCHED) { state.moveThumb(1, 132f, false) }
                repeat(3) { clock.frame() }
                assertTrue(state.thumbX.value > 32f && state.thumbX.value < 132f)
                outward.cancelAndJoin()
                val returning = launch(start = CoroutineStart.UNDISPATCHED) { state.moveThumb(0, 32f, false) }
                clock.finish(returning)
                assertEquals(32f, state.thumbX.value, 0.001f)
                assertEquals(state.thumbX.value, state.thumbCenter, 0.001f)
                assertEquals(0, state.placedIndex)
            }
        }
    }

    @Test fun systemAnimationScaleZeroStillReachesSelection() = runBlocking {
        withTimeout(5_000) {
            val clock = Clock()
            val disabled = object : MotionDurationScale { override val scaleFactor = 0f }
            withContext(clock + disabled) {
                val state = PillBarState()
                state.moveThumb(0, 32f, true)
                val job = launch(start = CoroutineStart.UNDISPATCHED) { state.moveThumb(2, 232f, false) }
                clock.finish(job)
                assertEquals(232f, state.thumbCenter, 0.001f)
                assertEquals(2, state.placedIndex)
            }
        }
    }

    @Test fun geometryChangesSnapEvenWhenSelectionDoesNotChange() {
        val geometry = ThumbGeometry()
        assertTrue(geometry.needsSnap(listOf(32f, 92f)))
        assertFalse(geometry.needsSnap(listOf(32f, 92f)))
        assertTrue(geometry.needsSnap(listOf(48f, 138f)))
        assertTrue(ThumbGeometry().needsSnap(listOf(48f, 138f)))
    }

    @Test fun contentExpansionDoesNotOvershootOrReverse() {
        val animation = TargetBasedAnimation(sleepyContentSizeSpec<Float>(), Float.VectorConverter, 0f, 1f)
        var previous = 0f
        var time = 0L
        while (time <= animation.durationNanos) {
            val value = animation.getValueFromNanos(time)
            assertTrue(value >= previous - 0.0001f)
            assertTrue(value in 0f..1f)
            previous = value
            time += 8_333_333L
        }
        assertEquals(1f, animation.getValueFromNanos(animation.durationNanos), 0.001f)
    }
}
