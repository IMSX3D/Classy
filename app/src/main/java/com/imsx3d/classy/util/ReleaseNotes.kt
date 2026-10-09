package com.imsx3d.classy.util

import java.util.Locale
import org.json.JSONObject

/** Version codes are monotonic; reopening, rotation and downgrades are not updates. */
object ReleaseNotesPolicy {
    fun shouldShow(current: Long, acknowledged: Long?, wasUpdated: Boolean): Boolean =
        if (acknowledged == null) wasUpdated else current > acknowledged
}

data class ReleaseNotes(val versionCode: Long, val versionName: String, val items: List<String>) {
    companion object {
        fun parse(json: String, locale: Locale): ReleaseNotes {
            val root = JSONObject(json)
            val translations = root.getJSONObject("notes")
            val language = when {
                locale.language == "zh" && (locale.script == "Hant" || locale.country in listOf("TW", "HK", "MO")) -> "zh-Hant"
                locale.language == "zh" -> "zh"
                else -> locale.language
            }
            val rows = translations.optJSONArray(language) ?: translations.getJSONArray("en")
            return ReleaseNotes(root.getLong("versionCode"), root.getString("versionName"),
                List(rows.length()) { rows.getString(it) })
        }
    }
}
