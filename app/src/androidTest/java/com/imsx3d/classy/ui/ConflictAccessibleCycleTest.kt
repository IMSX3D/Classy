package com.imsx3d.classy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.R
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.ui.component.*
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import com.imsx3d.classy.util.ConflictCluster
import java.time.LocalTime
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class ConflictAccessibleCycleTest {
    @get:Rule val compose = createComposeRule()
    @Test fun fourLayersVisitEveryCourseAndWrap() = exercise(4,false)
    @Test fun fiveLayersVisitEveryCourseAndWrap() = exercise(5,false)
    @Test fun labelledPickerSupportsKeyboardAndExposesSelection() = exercise(4,true)
    private fun exercise(count: Int, keyboard: Boolean) {
        val rows=(1..count).map { CourseEntity(id=it.toLong(),tableId=95000,groupId="cycle-$it",
            courseName="Layer $it",day=1,startNode=1,step=2,startWeek=1,endWeek=16,color="#AACCEE") }
        val slots=(1..4).map { n -> val start=LocalTime.of(7+n,0)
            TimeSlot("$n",start,start.plusMinutes(45),start.toString(),start.plusMinutes(45).toString(),n,n) }
        var step by mutableIntStateOf(0)
        val opened=mutableListOf<Long>()
        var writes=0
        lateinit var inputMode: androidx.compose.ui.input.InputModeManager
        compose.setContent { SleepyThemeProvider {
            inputMode=androidx.compose.ui.platform.LocalInputModeManager.current
            Box(Modifier.padding(20.dp)) {
                ConflictClusterCard(ConflictCluster(1,rows),"rail",null,{ writes++ },{ opened.add(it.id) },
                    colW=120.dp,rowH=64.dp,maxNode=4,timeW=0.dp,gapW=0.dp,gapH=4.dp,
                    timeSlots=slots,isGrey=false,rotationStep=step,onRotate={step++},
                    onPickFromBadge={step=it},modifier=Modifier.testTag("cycle"))
            }
        } }
        val node=compose.onNodeWithTag("cycle")
        if (!keyboard) {
            repeat(count) {
                node.performTouchInput { click(center) }
                node.performTouchInput { click(Offset(width-8f,height-8f)) }
            }
            node.performTouchInput { click(center) }
            compose.runOnIdle {
                assertEquals(rows.map {it.id}.toSet(),opened.take(count).toSet())
                assertEquals(opened.first(),opened.last());assertEquals(count+1,opened.size)
                assertEquals(count,step);assertEquals(0,writes)
            }
        } else {
            compose.runOnIdle { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val badge=compose.onNodeWithContentDescription(context.getString(R.string.conflict_choose_visible))
            compose.runOnIdle { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
            badge.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            badge.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
            val first=compose.onNode(hasText("Layer 1") and isSelectable())
            first.assertIsSelected()
            val last=compose.onNode(hasText("Layer 4") and isSelectable())
            last.assertIsNotSelected()
            last.performKeyInput { pressKey(Key.Tab) }
            last.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            last.assertIsFocused().assertHeightIsAtLeast(48.dp)
            val evidence=java.io.File(context.getExternalFilesDir(null), "picker-keyboard-focus.png")
            evidence.outputStream().use { last.captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            last.performKeyInput { pressKey(Key.Enter) }
            node.performTouchInput { click(center) }
            compose.runOnIdle { assertEquals(listOf(4L),opened);assertEquals(0,writes) }
            compose.runOnIdle { inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard) }
            badge.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
            badge.performKeyInput { pressKey(Key.Enter) }
            compose.onNode(hasText("Layer 4") and isSelectable()).assertIsSelected()
        }
    }
}
