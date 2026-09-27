package com.imsx3d.classy.widget

import android.content.Context
import android.content.Intent
import com.imsx3d.classy.MainActivity

/**
 * Single source of truth for widget → app tap routing.
 *
 * Three render paths (bitmap face via [RemoteViewsWidgetHelper.renderAndPush],
 * scrollable template via [RemoteViewsWidgetHelper.pushScrollable], and the
 * grid provider's own face via [WeekGridWidgetProvider]) previously
 * duplicated the same intent construction inline. Centralizing it pins the
 * per-instance routing contract that issue #24 Feature 1 depends on:
 *
 * - requestCode = widgetId: with FLAG_UPDATE_CURRENT, PendingIntents are
 *   keyed on (requestCode, intent-filter-equality); a shared code would let
 *   one instance's update clobber another instance's tap action.
 * - flags = NEW_TASK | CLEAR_TOP: widget taps launch/reuse the app task.
 * - target = MainActivity (the app renders the bound schedule there).
 * - extra = [MainActivity.EXTRA_OPEN_TAB]: 组件点击的意图是「看一眼课表」,但
 *   MainActivity 是 singleTask — 点击时 App 往往已在后台,系统走 onNewIntent 复用
 *   实例,组合里的 currentTab 还原样停在「我的」等页。带上这个 extra 才能落到课表。
 *   (extras 不参与 PendingIntent.filterEquals,所以不会因此多出新的 PendingIntent 身份。)
 *
 * Behavior is identical to the previously duplicated inline form, plus the tab hint.
 */
internal object WidgetRoutes {

    /**
     * PendingIntent requestCode for a widget tap — must be unique per widget
     * instance so updates to one widget never overwrite another's tap action.
     */
    fun tapRequestCode(widgetId: Int): Int = widgetId

    /** Flags for the widget tap intent (launch or reuse the app task). */
    const val TAP_FLAGS: Int =
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP

    /** The tap intent shared by every widget face of one instance. */
    fun tapIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = TAP_FLAGS
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_SCHEDULE)
        }
}