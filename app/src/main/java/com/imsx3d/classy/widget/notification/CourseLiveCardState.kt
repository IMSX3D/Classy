package com.imsx3d.classy.widget.notification

/**
 * The single course-card state shared by every Android vendor renderer.
 * Vendor adapters may change presentation, never the lifecycle semantics.
 */
data class CourseLiveCardState(
    val courseName: String,
    val room: String,
    val teacher: String,
    val startTime: String,
    val notifyEpoch: Long,
    val classEpoch: Long,
    val nowEpoch: Long,
    val updateSequence: Int = 0
) {
    val progress: Int
        get() = progressPercent(notifyEpoch, classEpoch, nowEpoch)

    val isActive: Boolean
        get() = nowEpoch < classEpoch

    val primaryText: String
        get() = courseName

    val detailText: String
        get() = buildList {
            if (startTime.isNotBlank()) add(startTime)
            if (room.isNotBlank()) add(room)
            if (teacher.isNotBlank()) add(teacher)
        }.joinToString("  ·  ")
}

internal fun progressPercent(notifyEpoch: Long, classEpoch: Long, nowEpoch: Long): Int {
    val total = (classEpoch - notifyEpoch).coerceAtLeast(1L)
    val elapsed = (nowEpoch - notifyEpoch).coerceIn(0L, total)
    return ((elapsed * 100L) / total).toInt().coerceIn(0, 100)
}
