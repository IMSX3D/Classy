package com.imsx3d.classy.ui.nav

import kotlinx.serialization.Serializable
import androidx.navigation3.runtime.NavKey

/** Stable, typed keys for the app's single navigation stack. */
@Serializable
sealed interface SleepyRoute : NavKey {
    @Serializable data object Main : SleepyRoute
    @Serializable data class AddCourse(val courseId: Long = NO_ID, val editing: Boolean = false, val prefill: com.imsx3d.classy.util.CourseGridPrefill? = null) : SleepyRoute
    @Serializable data object AllTables : SleepyRoute
    @Serializable data object CourseList : SleepyRoute
    @Serializable data class EditTable(
        val tableId: Long = NO_ID,
        val pendingNew: Long = NO_ID,
        val prevDefault: Long = NO_ID,
    ) : SleepyRoute
    @Serializable data object Appearance : SleepyRoute
    @Serializable data object SettingsHome : SleepyRoute
    @Serializable data object General : SleepyRoute
    @Serializable data object ScheduleDisplay : SleepyRoute
    @Serializable data object ControlGallery : SleepyRoute
    @Serializable data object Holiday : SleepyRoute
    @Serializable data object Export : SleepyRoute
    @Serializable data object Reminder : SleepyRoute
    @Serializable data object About : SleepyRoute
    @Serializable data object License : SleepyRoute
    @Serializable data object WidgetManagement : SleepyRoute
    @Serializable data class WidgetEdit(val widgetId: Int) : SleepyRoute
    @Serializable data object PeriodTables : SleepyRoute
    @Serializable data class PeriodEdit(val periodId: Long, val isNew: Boolean = false) : SleepyRoute

    companion object { const val NO_ID = -1L }
}

/**
 * 是否是「正在填内容的表单页」(新增/编辑课程、编辑课表、编辑作息表)。
 *
 * 组件点击直达课表要回到主页面(见 MainActivity.openScheduleOnceState),但**不能**把
 * 这几类页面一并弹掉 —— 用户可能填了一半,弹栈 = 输入丢失。二级设置页(外观/通用/
 * 提醒/节假日…)都是即改即生效,没有未保存态,可以放心弹。
 * 宁可这次少跳一步,也不丢用户已经填的内容。
 */
fun NavKey.holdsUnsavedInput(): Boolean = when (this) {
    is SleepyRoute.AddCourse, is SleepyRoute.EditTable, is SleepyRoute.PeriodEdit -> true
    else -> false
}
