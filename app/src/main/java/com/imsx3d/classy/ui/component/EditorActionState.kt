package com.imsx3d.classy.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Serializes explicit editor actions. Callers navigate only inside the successful action. */
internal class EditorActionState(private val failureMessage: String) {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun clearError() { if (!busy) error = null }

    fun launch(scope: CoroutineScope, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                currentCoroutineContext().ensureActive()
                action()
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { error = failureMessage }
            finally { busy = false }
        }
    }
}
