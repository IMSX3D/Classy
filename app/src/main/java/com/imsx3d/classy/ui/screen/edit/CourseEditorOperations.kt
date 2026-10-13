package com.imsx3d.classy.ui.screen.edit

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.imsx3d.classy.R
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.ui.component.EditorActionState

/** Retains writes and results, never Activity/navigation callbacks, across configuration changes. */
class CourseEditorOperations : ViewModel() {
    internal val save = EditorActionState(SleepyApp.get().getString(R.string.editor_action_failed))
    internal val deletion = EditorActionState(SleepyApp.get().getString(R.string.course_delete_failed))
    internal var result by mutableStateOf<Pair<String, List<String>>?>(null)
        private set

    private var submitted = false
    internal var finished by mutableStateOf(false)
        private set

    // Empty details mean committed; nonempty details require explicit conflict confirmation.
    internal fun save(write: suspend () -> List<String>) {
        if (deletion.busy || result != null || finished) return
        submitted = true
        save.launch(viewModelScope) { publish(write()) }
    }

    internal fun delete(write: suspend () -> Unit) {
        if (save.busy || result != null || finished) return
        submitted = true
        deletion.launch(viewModelScope) { write(); publish(emptyList()) }
    }

    internal fun recoverCommittedCreation() {
        if (!submitted && !finished && result == null) { submitted = true; publish(emptyList()) }
    }

    internal fun consume(id: String) { if (result?.first == id) result = null }

    private fun publish(details: List<String>) {
        if (details.isEmpty()) finished = true
        result = java.util.UUID.randomUUID().toString() to details
    }
}
