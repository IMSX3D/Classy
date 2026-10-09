package com.imsx3d.classy.widget.notification

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/** Coalesce writes, but never cancel a reschedule that is already touching AlarmManager. */
@OptIn(kotlinx.coroutines.FlowPreview::class)
internal class ReminderDebouncer(scope: CoroutineScope, action: suspend () -> Unit) {
    private val requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            requests.debounce(300).collect { action() }
        }
    }
    fun request() { requests.tryEmit(Unit) }
}
