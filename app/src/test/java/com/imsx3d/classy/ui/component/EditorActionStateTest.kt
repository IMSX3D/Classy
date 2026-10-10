package com.imsx3d.classy.ui.component

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class EditorActionStateTest {
    @Test fun doubleTapDoesNotWriteOrNavigateTwice() = runBlocking {
        val state = EditorActionState("Failed")
        val writeCompleted = CompletableDeferred<Unit>()
        var writes = 0
        var navigations = 0
        val action: suspend () -> Unit = { writes++; writeCompleted.await(); navigations++ }
        state.launch(this, action)
        state.launch(this, action)
        assertTrue(state.busy)
        assertEquals(1, writes)
        assertEquals(0, navigations)
        writeCompleted.complete(Unit)
        yield()
        assertFalse(state.busy)
        assertEquals(1, navigations)
    }

    @Test fun failedWriteKeepsPageAndAllowsRetry() = runBlocking {
        val state = EditorActionState("Failed")
        var navigations = 0
        state.launch(this) { error("database unavailable") }
        assertEquals("Failed", state.error)
        assertFalse(state.busy)
        assertEquals(0, navigations)
        state.launch(this) { navigations++ }
        assertNull(state.error)
        assertEquals(1, navigations)
    }

    @Test fun cancellationIsNotPresentedAsSaveFailure() = runBlocking {
        val job = SupervisorJob()
        val scope = CoroutineScope(coroutineContext + job)
        val state = EditorActionState("Failed")
        var navigations = 0
        state.launch(scope) { awaitCancellation(); navigations++ }
        job.cancelAndJoin()
        assertFalse(state.busy)
        assertNull(state.error)
        assertEquals(0, navigations)
    }

    @Test fun alreadyCancelledScopeCannotLeaveBusyLatchStuck() = runBlocking {
        val job = Job().apply { cancel() }
        val state = EditorActionState("Failed")
        var writes = 0
        state.launch(CoroutineScope(coroutineContext + job)) { writes++ }
        assertFalse(state.busy)
        assertNull(state.error)
        assertEquals(0, writes)
    }
}
