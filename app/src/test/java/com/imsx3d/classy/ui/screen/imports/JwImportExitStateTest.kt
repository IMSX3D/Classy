package com.imsx3d.classy.ui.screen.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JwImportExitStateTest {

    @Test
    fun `request exit finishes directly when no import is active`() {
        val result = reduceExitDraftState(ExitDraftState(), ExitDraftEvent.RequestExit)

        assertEquals(ExitDraftOutcome.FinishDirectly, result.outcome)
        assertFalse(result.state.confirmationVisible)
    }

    @Test
    fun `request exit opens confirmation while import is active`() {
        val result = reduceExitDraftState(
            ExitDraftState(activeImport = true),
            ExitDraftEvent.RequestExit
        )

        assertEquals(ExitDraftOutcome.None, result.outcome)
        assertTrue(result.state.confirmationVisible)
    }

    @Test
    fun `continue closes confirmation without completing exit`() {
        val result = reduceExitDraftState(
            ExitDraftState(activeImport = true, confirmationVisible = true),
            ExitDraftEvent.Choose(ExitDraftChoice.Continue)
        )

        assertEquals(ExitDraftOutcome.None, result.outcome)
        assertFalse(result.state.confirmationVisible)
        assertTrue(result.state.activeImport)
    }

    @Test
    fun `keep draft and delete draft are distinct terminal choices`() {
        val keep = reduceExitDraftState(
            ExitDraftState(activeImport = true, confirmationVisible = true),
            ExitDraftEvent.Choose(ExitDraftChoice.KeepDraft)
        )
        val delete = reduceExitDraftState(
            ExitDraftState(activeImport = true, confirmationVisible = true),
            ExitDraftEvent.Choose(ExitDraftChoice.DeleteDraft)
        )

        assertEquals(ExitDraftOutcome.KeepDraft, keep.outcome)
        assertEquals(ExitDraftOutcome.DeleteDraft, delete.outcome)
        assertFalse(keep.state.confirmationVisible)
        assertFalse(delete.state.confirmationVisible)
    }
}
