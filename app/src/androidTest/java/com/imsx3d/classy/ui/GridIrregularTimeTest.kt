package com.imsx3d.classy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.ui.component.CardsGridView
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import com.imsx3d.classy.util.ConflictLayoutEngine
import com.imsx3d.classy.util.TimeTableUtils
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GridIrregularTimeTest {
    @get:Rule val compose = createComposeRule()
    @Test fun overlapEndsInGapAndKeepsRealTimeWhenOpened() {
        val json = """[{"node":1,"start":"09:00","end":"09:45"},{"node":2,"start":"10:00","end":"10:45"},{"node":3,"start":"11:00","end":"11:45"}]"""
        val regular = CourseEntity(id=96001, groupId="regular", tableId=96000,
            courseName="Regular", day=1,startNode=1,step=1,startWeek=1,endWeek=16,color="#AACCEE")
        val irregular=regular.copy(id=96002,groupId="irregular",courseName="Irregular",
            ownTime=true,startTime="09:15",endTime="09:50",color="#BBDEAA")
        val next=regular.copy(id=96003,groupId="next",courseName="Next class",startNode=2)
        val rows=listOf(regular,irregular,next)
        val cluster=ConflictLayoutEngine.findClusters(rows,json).single()
        assertEquals(setOf(regular.id,irregular.id),cluster.courses.map { it.id }.toSet())
        val key=ConflictLayoutEngine.conflictClusterKey(cluster)
        var top by mutableStateOf<Long?>(irregular.id)
        var opened: CourseEntity?=null
        compose.setContent { SleepyThemeProvider {
            Box(Modifier.width(350.dp).height(600.dp)) {
                CardsGridView(rows,timeSlots=TimeTableUtils.timeSlotsFor(json),timeJson=json,
                    visibleDays=setOf(1,2,3),topOverrides=mapOf(key to top!!),
                    onSetTopOverride={ _,id -> top=id },onCourseClick={ opened=it })
            }
        } }
        val card=compose.onNode(hasText("Irregular") and hasClickAction())
        val bounds=card.fetchSemanticsNode().boundsInRoot
        val firstStart=compose.onNode(hasText("Regular") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        val nextBounds=compose.onNode(hasText("Next class") and hasClickAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("fractional start must not snap to first period",bounds.top>firstStart.top)
        assertTrue("gap end must not extend into next course",bounds.bottom<=nextBounds.top)
        card.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(irregular,opened) }
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"irregular").apply { mkdirs() }
        File(dir,"grid-gap.png").outputStream().use { compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
    }
}
