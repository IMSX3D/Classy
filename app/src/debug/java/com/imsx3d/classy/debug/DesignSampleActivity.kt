package com.imsx3d.classy.debug

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.ui.screen.mine.ControlGalleryScreen
import com.imsx3d.classy.ui.screen.mine.GeneralSettingsScreen
import com.imsx3d.classy.ui.screen.today.TodayContent
import com.imsx3d.classy.ui.theme.SleepyThemeProvider
import com.imsx3d.classy.util.DateUtils
import java.time.LocalDateTime

/** Debug-only deterministic visual fixture. Never writes sample courses to the database. */
class DesignSampleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SleepyThemeProvider(darkTheme = intent.getBooleanExtra("dark", false)) {
                when (intent.getStringExtra("page")) {
                    "settings" -> GeneralSettingsScreen(onBack = ::finish)
                    "controls" -> ControlGalleryScreen(onBack = ::finish)
                    else -> Box(Modifier.safeDrawingPadding()) {
                        val now = LocalDateTime.of(2026, 10, 12, 10, 30)
                        var selected by remember { mutableStateOf<CourseEntity?>(null) }
                        val scenario = intent.getStringExtra("scenario")
                        val status = when (scenario) {
                            "before" -> DateUtils.SemesterStatus.BEFORE_START
                            "after" -> DateUtils.SemesterStatus.AFTER_END
                            else -> DateUtils.SemesterStatus.IN_RANGE
                        }
                        TodayContent(
                            today = now.toLocalDate(), actualWeek = 6, semesterStatus = status,
                            todayCourses = if (scenario in listOf("empty", "before", "after")) emptyList()
                                else DesignSampleCourses.rows,
                            timeJson = null, now = now, onSelectCourse = { selected = it }
                        )
                        selected?.let { course ->
                            androidx.compose.material3.AlertDialog(
                                onDismissRequest = { selected = null },
                                title = { Text(course.courseName) },
                                text = { Text("${course.startTime}–${course.endTime}\n${course.room}") },
                                confirmButton = {
                                    androidx.compose.material3.TextButton(onClick = { selected = null }) {
                                        Text(getString(com.imsx3d.classy.R.string.ok))
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

internal object DesignSampleCourses {
    val rows = listOf(
        course(1, "高等数学 · 已结束", 1, 2, "08:00", "09:40"),
        course(2, "跨学科创新研究方法与实践 / Interdisciplinary Research Workshop", 3, 2, "10:00", "11:40"),
        course(3, "大学物理实验（冲突样例）", 3, 1, "10:00", "10:45"),
        course(4, "设计基础", 5, 2, "14:00", "15:40"),
        course(5, "Academic English", 7, 1, "16:00", "16:45"),
        course(6, "晚间自习", 9, 2, "19:00", "20:40")
    )

    private fun course(id: Long, name: String, node: Int, step: Int, start: String, end: String) =
        CourseEntity(
            id = id, groupId = "design-$id", tableId = -1, courseName = name,
            teacher = "示例教师", room = "综合教学楼东区 B 座 1208 · Innovation Laboratory",
            day = 1, startNode = node, step = step, startWeek = 1, endWeek = 20,
            color = if (id % 2L == 0L) "#FF007AFF" else "#FFFF6600",
            ownTime = true, startTime = start, endTime = end
        )
}
