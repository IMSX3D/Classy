package com.imsx3d.classy.widget.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationCopyTest {
    @Test fun missingOptionalFieldsDoNotLeaveEmptySeparators() {
        assertEquals("08:00", NotificationCopy.timeAndPlace("08:00", " "))
        assertEquals("08:00 · Math", NotificationCopy.firstClass("Math", "08:00", ""))
        assertEquals("Math\n08:00 · Room 101", NotificationCopy.expanded("Math", "08:00", "Room 101", ""))
        assertEquals("Math\n08:00 · Room 101\nTeacher", NotificationCopy.expanded(" Math ", "08:00", "Room 101", "Teacher"))
    }
}
