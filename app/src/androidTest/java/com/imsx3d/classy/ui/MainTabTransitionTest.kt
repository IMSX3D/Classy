package com.imsx3d.classy.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import com.imsx3d.classy.ui.component.SettingsSwitchRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.imsx3d.classy.ui.component.MainTabTransition
import org.junit.Rule
import org.junit.Test

class MainTabTransitionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun rapidReversalKeepsSingleSaveableOwnerAndHidesOutgoingSemantics() {
        var tab by mutableStateOf("A")
        compose.setContent {
            MaterialTheme {
                val holder = rememberSaveableStateHolder()
                MainTabTransition(tab) { shown ->
                    holder.SaveableStateProvider(shown) {
                        val value = rememberSaveable { "state-$shown" }
                        Text(value)
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        repeat(4) {
            compose.runOnIdle { tab = "B" }
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithText("state-A").assertDoesNotExist()
            compose.onNodeWithText("state-B").assertExists()
            compose.runOnIdle { tab = "A" }
            compose.mainClock.advanceTimeBy(32)
            compose.onNodeWithText("state-B").assertDoesNotExist()
            compose.onNodeWithText("state-A").assertExists()
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun switchRowHasOneAccessibleActionAndWritesOncePerToggle() {
        var checked by mutableStateOf(false)
        var writes = 0
        compose.setContent {
            MaterialTheme {
                SettingsSwitchRow(title = "Setting", checked = checked, onCheckedChange = {
                    checked = it
                    writes++
                })
            }
        }
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assertCountEquals(1)
        compose.onNodeWithText("Setting").assertIsOff().performClick().assertIsOn()
        compose.runOnIdle { org.junit.Assert.assertEquals(1, writes) }
        compose.onNodeWithText("Setting").performClick().assertIsOff()
        compose.runOnIdle { org.junit.Assert.assertEquals(2, writes) }
    }
}
