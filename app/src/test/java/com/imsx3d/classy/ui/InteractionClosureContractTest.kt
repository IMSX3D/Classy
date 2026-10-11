package com.imsx3d.classy.ui

import java.io.File
import org.junit.Assert.*
import org.junit.Test
import com.imsx3d.classy.ui.nav.SleepyRoute
import com.imsx3d.classy.ui.nav.holdsUnsavedInput

/** Source guards complement action-state behavior tests; they do not prove visual smoothness. */
class InteractionClosureContractTest {
    private fun source(path: String) =
        File("src/main/java/com/imsx3d/classy/ui/$path").readText()

    @Test fun externalWidgetNavigationCannotDiscardThemeDraft() {
        assertTrue(SleepyRoute.Appearance.holdsUnsavedInput())
        assertTrue(SleepyRoute.Reminder.holdsUnsavedInput())
        assertTrue(SleepyRoute.Holiday.holdsUnsavedInput())
        assertTrue(SleepyRoute.PeriodTables.holdsUnsavedInput())
        assertTrue(SleepyRoute.AddCourse().holdsUnsavedInput())
        assertFalse(SleepyRoute.General.holdsUnsavedInput())
    }

    @Test fun reminderMinutesPersistOnlyFromExplicitConfirmation() {
        val s = source("screen/mine/ReminderScreen.kt")
        assertFalse(s.contains("LaunchedEffect(minutesInput.text)"))
        val dialog = s.substring(s.indexOf("if (minutesDialog)"))
        val confirm = dialog.indexOf("onConfirm =")
        val write = dialog.indexOf("AppPrefs.setBeforeClassMinutes")
        val dismiss = dialog.indexOf("onDismiss =", confirm)
        assertTrue(confirm >= 0 && write > confirm && dismiss > write)
        assertEquals(1, Regex("AppPrefs.setBeforeClassMinutes").findAll(s).count())
    }

    @Test fun exportWaitsForSelectedSourcesOwnData() {
        val s = source("screen/mine/ExportScreen.kt")
        assertFalse(s.contains("loadedCourses ?: state.courses"))
        assertFalse(s.contains("?: state.currentTable"))
        assertTrue(s.contains("repository.observeCourses(id)"))
        assertTrue(s.contains("table != null && loadedCourses == null"))
        assertTrue(s.contains("throw cancelled"))
    }

    @Test fun automaticProposalDoesNotUnbindBeforeConsent() {
        val s = source("component/TimeSlotEditor.kt")
        val next = s.substring(s.indexOf("onChange = { next ->"), s.indexOf("hasPeriodTableTab ="))
        assertTrue(next.contains("next == Mode.Manual"))
        assertFalse(next.contains("next != Mode.PeriodTable"))
        val proposal = s.substring(s.indexOf("if (seed != null)"), s.indexOf("Column(modifier = modifier)"))
        assertFalse(proposal.substringBefore("} else {").contains("onSelectPeriodTable"))
        assertTrue(s.contains("pendingSeedJson by rememberSaveable"))
    }

    @Test fun failedDestructiveActionsKeepTheirConfirmationMounted() {
        for (name in listOf("EditTableScreen.kt", "PeriodTableEditScreen.kt")) {
            val s = source("screen/mine/$name")
            val dialog = s.substring(s.indexOf("if (showDeleteConfirm)"))
            val confirm = dialog.substringAfter("onConfirm = {").substringBefore("actions.launch(scope)")
            assertFalse(confirm.contains("showDeleteConfirm = false"))
            assertTrue(dialog.contains("onDismissRequest = { if (!actions.busy)"))
        }
    }

    @Test fun eachNavigationRouteHasAnInteractionContract() {
        val routes = source("nav/SleepyRoutes.kt").substringBefore("companion object")
        val names = Regex("""data (?:object|class) (\w+)""").findAll(routes).map { it.groupValues[1] }.toSet()
        val report = File("../docs/design/INTERACTION_STAGE1_CLOSURE.md").readText()
        assertEquals(19, names.size)
        names.forEach { assertTrue("Missing route contract: $it", report.contains("`$it`")) }
    }
}
