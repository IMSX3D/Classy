package com.imsx3d.classy.ui.nav

import android.app.Activity
import android.os.Build
import com.imsx3d.classy.R

/** Platform-owned task transitions; never delay finishing or persistence for an animation. */
internal object TaskActivityTransitions {
    @Suppress("DEPRECATION")
    fun install(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN,
                R.anim.task_window_enter, R.anim.task_window_hold)
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE,
                R.anim.task_window_hold, R.anim.task_window_exit)
        } else {
            activity.overridePendingTransition(R.anim.task_window_enter, R.anim.task_window_hold)
        }
    }

    @Suppress("DEPRECATION")
    fun afterFinish(activity: Activity) {
        if (Build.VERSION.SDK_INT < 34) {
            activity.overridePendingTransition(R.anim.task_window_hold, R.anim.task_window_exit)
        }
    }
}
