package com.imsx3d.classy.util

import android.content.Context

/** Tutorial history is separate from appearance resets. Existing installs are never interrupted. */
object GuidePrefs {
    fun shouldOffer(decided: Boolean, firstInstall: Long, lastUpdate: Long, external: Boolean) =
        !decided && firstInstall > 0 && firstInstall == lastUpdate && !external

    fun offerWelcome(context: Context, external: Boolean): Boolean {
        val prefs = context.getSharedPreferences("classy_guide", Context.MODE_PRIVATE)
        if (prefs.getBoolean("welcome_decided", false)) return false
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val offer = shouldOffer(false, info.firstInstallTime, info.lastUpdateTime, external)
        prefs.edit().putBoolean("welcome_decided", true).apply()
        return offer
    }
}
