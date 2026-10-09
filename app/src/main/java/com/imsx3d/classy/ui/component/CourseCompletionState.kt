package com.imsx3d.classy.ui.component

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.util.CourseCompletion
import com.imsx3d.classy.util.DateUtils
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** One clock per screen/page, suspended in background and refreshed immediately on resume. */
@Composable
fun rememberCourseClock(): LocalDateTime {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by produceState(LocalDateTime.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                value = LocalDateTime.now()
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }
    return now
}

internal val LocalCourseCompleted = staticCompositionLocalOf<(CourseEntity) -> Boolean> { { false } }

@Composable
fun CourseCompletionProvider(startDate: String, week: Int, timeJson: String?, content: @Composable () -> Unit) {
    val now = rememberCourseClock()
    val completed: (CourseEntity) -> Boolean = { course ->
        val date = runCatching { DateUtils.dateOfWeek(startDate, week, course.day) }.getOrNull()
        date != null && CourseCompletion.isCompleted(course, date, timeJson, now)
    }
    CompositionLocalProvider(LocalCourseCompleted provides completed, content = content)
}
