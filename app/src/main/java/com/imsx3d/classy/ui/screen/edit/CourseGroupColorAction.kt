package com.imsx3d.classy.ui.screen.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Owned by the editor session: rotation must not cancel or duplicate a confirmed write. */
class CourseGroupColorAction : ViewModel() {
    var busy by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set
    var result by mutableStateOf<Pair<String, String>?>(null)
        private set

    fun submit(hex: String, write: suspend () -> Unit) {
        if (busy) return
        busy = true
        failed = false
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                currentCoroutineContext().ensureActive()
                write()
                result = java.util.UUID.randomUUID().toString() to hex
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failed = true
            } finally {
                busy = false
            }
        }
    }

    fun clearFailure() { if (!busy) failed = false }
}
