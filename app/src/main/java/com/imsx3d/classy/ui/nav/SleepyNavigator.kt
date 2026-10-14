package com.imsx3d.classy.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.rememberNavBackStack
import com.imsx3d.classy.data.entity.CourseEntity

/**
 * issue#45: 官方 androidx.navigation3 的极薄封装。
 *
 * 决策:栈本体直接用官方 [NavBackStack](rememberNavBackStack 的产物) —
 * 进程死亡/配置变更的持久化由官方 NavBackStackSerializer(反射式 NavKeySerializer)
 * 承担,SleepyRoute 全系 @Serializable,不需要自写 Saver。仿 KernelSU ui/navigation3/Navigator
 * 把所有「栈意图」封成命名方法 — 不对外暴露栈的内部形态,只暴露 push/pop/popToMain/openXxx。
 *
 * Editing data is an Activity ViewModel session: configuration changes retain it.
 * Process death still drops the session; it is not serialized with the navigation stack.
 */
class SleepyNavigator(
    val backStack: NavBackStack<NavKey>,
    val session: NavSession,
) {
    fun push(route: SleepyRoute) {
        if (backStack.lastOrNull() == route) return
        backStack.add(route)
    }

    fun pop(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun popToMain() {
        while (backStack.size > 1 && backStack.last() != SleepyRoute.Main) {
            backStack.removeAt(backStack.lastIndex)
        }
    }

    fun openAddCourse(courseId: Long = NavSession.NO_ID, editing: Boolean = false,
        prefill: com.imsx3d.classy.util.CourseGridPrefill? = null) =
        push(SleepyRoute.AddCourse(courseId, editing, prefill))
    fun openAllTables() = push(SleepyRoute.AllTables)
    fun openSemester() = push(SleepyRoute.Semester)
    fun openCourseList() = push(SleepyRoute.CourseList)
    fun openAppearance() = push(SleepyRoute.Appearance)
    fun openSettings() = push(SleepyRoute.SettingsHome)
    fun openGeneral() = push(SleepyRoute.General)
    fun openScheduleDisplay() = push(SleepyRoute.ScheduleDisplay)
    fun openControlGallery() = push(SleepyRoute.ControlGallery)
    fun openHoliday() = push(SleepyRoute.Holiday)
    fun openExport() = push(SleepyRoute.Export)
    fun openReminder() = push(SleepyRoute.Reminder)
    fun openGuide() = push(SleepyRoute.Guide)
    fun openAbout() = push(SleepyRoute.About)
    fun openLicense() = push(SleepyRoute.License)
    fun openWidgetManagement() = push(SleepyRoute.WidgetManagement)
    fun openPeriodTables() = push(SleepyRoute.PeriodTables)
    fun openWidgetEdit(widgetId: Int) = push(SleepyRoute.WidgetEdit(widgetId))
    fun openPeriodEdit(periodId: Long, isNew: Boolean = false) =
        push(SleepyRoute.PeriodEdit(periodId, isNew))
    fun openEditTable(
        tableId: Long = NavSession.NO_ID,
        pendingNew: Long = NavSession.NO_ID,
        prevDefault: Long = NavSession.NO_ID,
    ) = push(SleepyRoute.EditTable(tableId, pendingNew, prevDefault))
    fun createPeriodTableAndEdit(newId: Long) = openPeriodEdit(newId, isNew = true)
}

/**
 * 把 SleepyNavigator 注入 AppRoot。栈持久化交给官方 rememberNavBackStack(Android 反射
 * 序列化器, sealed @Serializable 路由免注册);session 是会话态(不持久化)。
 */
@Composable
fun rememberSleepyNavigator(initial: SleepyRoute = SleepyRoute.Main): SleepyNavigator {
    val session: NavSession = viewModel()
    val backStack = rememberNavBackStack(*if (initial == SleepyRoute.Main) arrayOf<NavKey>(initial) else arrayOf<NavKey>(SleepyRoute.Main, initial))
    return remember(backStack, session) { SleepyNavigator(backStack, session) }
}

class NavSession : ViewModel() {
    var editingCourse: CourseEntity? by mutableStateOf(null)
        private set
    fun beginEditCourse(course: CourseEntity) { editingCourse = course }
    fun clearEditCourse() { editingCourse = null }
    companion object { const val NO_ID = SleepyRoute.NO_ID }
}
