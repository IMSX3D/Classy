package com.imsx3d.classy.ui

import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.widget.WidgetEditViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class WidgetEditLoadingTest {
    @Test fun failedLoadIsNotAnEmptyResultAndCanBeRetried() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val gate = CompletableDeferred<Unit>()
        val retryGate = CompletableDeferred<Unit>()
        var attempts = 0
        lateinit var vm: WidgetEditViewModel
        val store = ViewModelStore()
        instrumentation.runOnMainSync {
            vm = WidgetEditViewModel(-81001) {
                attempts++
                if (attempts == 1) { gate.await(); error("synthetic load failure") }
                retryGate.await()
                emptyList()
            }
            store.put("test", vm)
        }
        try {
            assertTrue(vm.state.value.loading)
            assertFalse(vm.state.value.loadFailed)
            gate.complete(Unit)
            withTimeout(5000) { vm.state.first { it.loadFailed } }
            assertFalse(vm.state.value.loading)
            instrumentation.runOnMainSync { vm.reload() }
            assertTrue("Retry must stay loading, not flash an empty list", vm.state.value.loading)
            assertFalse(vm.state.value.loadFailed)
            retryGate.complete(Unit)
            withTimeout(5000) { vm.state.first { !it.loading && !it.loadFailed } }
            assertEquals(2, attempts)
            assertTrue(vm.state.value.availableTables.isEmpty())
        } finally {
            instrumentation.runOnMainSync { store.clear() }
        }
    }
}
