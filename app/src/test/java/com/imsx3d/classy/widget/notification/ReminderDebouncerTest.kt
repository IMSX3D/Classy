package com.imsx3d.classy.widget.notification

import kotlinx.coroutines.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ReminderDebouncerTest {
    @Test fun burstCoalescesAndRequestDuringExecutionSurvives() = runBlocking {
        val job = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + job)
        try {
            var count = 0
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val again = CompletableDeferred<Unit>()
            var cancelled = false
            val debouncer = ReminderDebouncer(scope) {
                count++
                if (count == 1) {
                    started.complete(Unit)
                    try { release.await() } catch (e: CancellationException) { cancelled = true; throw e }
                } else again.complete(Unit)
            }
            repeat(5) { debouncer.request(); delay(30) }
            withTimeout(3000) { started.await() }
            assertEquals(1, count)
            debouncer.request()
            delay(400)
            assertFalse(cancelled)
            release.complete(Unit)
            withTimeout(3000) { again.await() }
            assertEquals(2, count)
        } finally { job.cancelAndJoin() }
    }
}
