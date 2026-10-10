package com.imsx3d.classy.util

/** Removed language preferences remain readable after upgrading or restoring a backup. */
object SupportedLanguages {
    fun normalize(code: String?): String = when (code?.substringBefore('-')?.lowercase()) {
        "zh", null -> "zh-CN"
        else -> "en"
    }
}
