package com.imsx3d.classy.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** One successful check per session, including a successful "no update" result.
 * Failed/cancelled checks can retry. The caller owns the job's lifetime; reset invalidates
 * even a blocking request that returns after cancellation. Callbacks must be short/nonblocking.
 */
internal class StartupCheck {
    private val lock = Any()
    private var job: Job? = null
    private var completed = false
    private var generation = 0L

    fun <T> start(scope: CoroutineScope, fetch: suspend () -> T, publish: (T) -> Unit) {
        synchronized(lock) {
            if (completed || job?.isCompleted == false) return
            val token = generation
            job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val result = fetch()
                    coroutineContext.ensureActive()
                    synchronized(lock) {
                        if (token == generation) {
                            publish(result)
                            completed = true
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // Automatic check is silent. A subsequent entry may retry a failed request.
                }
            }
            job?.start()
        }
    }

    fun reset(clear: () -> Unit) = synchronized(lock) {
        generation++
        job?.cancel()
        job = null
        completed = false
        clear()
    }
}
