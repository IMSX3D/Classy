package com.imsx3d.classy.util

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class StartupCheckTest {
    @Test fun overlappingRequestsAndNoUpdateResultOnlyFetchOnce() = runBlocking {
        val check = StartupCheck()
        val response = CompletableDeferred<Boolean>()
        var calls = 0
        var published: Boolean? = null
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            repeat(10) { check.start(scope, { calls++; response.await() }) { published = it } }
            assertEquals(1, calls)
            response.complete(false)
            check.start(scope, { calls++; true }) { published = it }
            assertEquals(false, published)
            assertEquals(1, calls)
        } finally { scope.cancel() }
    }

    @Test fun resetRejectsLateResultAndAllowsFreshCheck() = runBlocking {
        val check = StartupCheck()
        val response = CompletableDeferred<Int>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var published = 0
        try {
            check.start(scope, { withContext(NonCancellable) { response.await() } }) { published = it }
            check.reset { published = 0 }
            check.start(scope, { 2 }) { published = it }
            response.complete(1)
            assertEquals(2, published)
        } finally { scope.cancel() }
    }

    @Test fun parentCancellationAndFailedRequestsCanRetry() = runBlocking {
        val check = StartupCheck()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var cancelled = false
        check.start(scope, { try { awaitCancellation() } finally { cancelled = true } }) { fail() }
        scope.cancel()
        assertTrue(cancelled)
        val replacement = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var published = 0
        try {
            check.start(replacement, { error("offline") }) { fail() }
            check.start(replacement, { 3 }) { published = it }
            assertEquals(3, published)
        } finally { replacement.cancel() }
    }
}
