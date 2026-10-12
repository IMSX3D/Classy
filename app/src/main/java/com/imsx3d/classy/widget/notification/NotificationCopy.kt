package com.imsx3d.classy.widget.notification

/** Shared by the notification and its settings preview; omit absent fields, never empty separators. */
object NotificationCopy {
    fun timeAndPlace(time: String, room: String): String = listOf(time.trim(), room.trim())
        .filter(String::isNotBlank).joinToString(" · ")
    fun expanded(name: String, time: String, room: String, teacher: String): String =
        listOf(name.trim(), timeAndPlace(time, room), teacher.trim()).filter(String::isNotBlank).joinToString("\n")
    fun firstClass(name: String, time: String, room: String): String =
        listOf(time.trim(), name.trim(), room.trim()).filter(String::isNotBlank).joinToString(" · ")
}
