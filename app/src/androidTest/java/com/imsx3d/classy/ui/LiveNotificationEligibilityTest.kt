package com.imsx3d.classy.ui

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import com.imsx3d.classy.MainActivity
import com.imsx3d.classy.widget.notification.*
import org.junit.Assert.assertTrue
import org.junit.Test

/** Inspects standard Android eligibility without posting or changing notification preferences. */
class LiveNotificationEligibilityTest {
    @Test fun optionalDevicePresentationProbe(): Unit = kotlinx.coroutines.runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveProbe") == "true")
        val context = instrumentation.targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        org.junit.Assume.assumeTrue(manager.activeNotifications.none { it.id == CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE })
        // This opt-in hardware probe must not wait for ActivityScenario's global
        // idle synchronization (OEM launch surfaces can keep it waiting indefinitely).
        instrumentation.uiAutomation.executeShellCommand(
            "am start -W -n ${context.packageName}/${MainActivity::class.java.name}"
        ).use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
        try {
            Thread.sleep(1500)
            val now = System.currentTimeMillis()
            FluidCloudService.start(context, Intent(context, FluidCloudService::class.java).apply {
                putExtra("courseName", "Classy 实时通知测试")
                putExtra("room", "测试教室")
                putExtra("startTime", java.time.LocalTime.now().plusMinutes(5).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")))
                putExtra("notifyEpoch", now - 60_000)
                putExtra("classEpoch", now + 300_000)
            })
            val deadline = android.os.SystemClock.elapsedRealtime() + 15_000
            while (manager.activeNotifications.none { it.id == CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE } &&
                android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(200)
            val posted = manager.activeNotifications.firstOrNull { it.id == CourseNotificationScheduler.NOTIFY_BEFORE_CLASS_BASE }?.notification
            assertTrue("Course live card was not delivered", posted != null)
            if (Build.VERSION.SDK_INT >= 36) instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "POSTED_CHARACTERISTICS=${posted!!.hasPromotableCharacteristics()}; PROMOTED_FLAG=${posted.flags and android.app.Notification.FLAG_PROMOTED_ONGOING != 0}\n") })
            Thread.sleep(30_000) // bounded window for screenshots; the finally block removes this probe.
        } finally {
            FluidCloudService.requestStop()
        }
    }

    @Test fun vendorMetadataPreservesStandardLiveRequest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val pending = PendingIntent.getActivity(context, 704,
            Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        try {
            val now = System.currentTimeMillis()
            val state = CourseLiveCardState("Course", "Room", "Teacher", "10:00", now, now + 600_000, now + 1000)
            val generic = VendorLiveCardRenderer.build(context, state, pending,
                CourseNotificationScheduler.CHANNEL_FLUID, LiveCardVendor.GENERIC)
            for (vendor in LiveCardVendor.entries) {
                val notification = VendorLiveCardRenderer.build(context, state, pending,
                    CourseNotificationScheduler.CHANNEL_FLUID, vendor)
                assertTrue("$vendor erased standard promotion request",
                    androidx.core.app.NotificationCompat.isRequestPromotedOngoing(notification))
                org.junit.Assert.assertEquals("$vendor erased compact time", "10:00",
                    androidx.core.app.NotificationCompat.getShortCriticalText(notification))
                if (Build.VERSION.SDK_INT >= 36) {
                    org.junit.Assert.assertEquals("$vendor changed platform eligibility",
                        generic.hasPromotableCharacteristics(), notification.hasPromotableCharacteristics())
                }
            }
            if (Build.VERSION.SDK_INT >= 36) {
                InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
                    putString("stream", "STANDARD_PROMOTION_ALLOWED=${context.getSystemService(NotificationManager::class.java).canPostPromotedNotifications()}; CHARACTERISTICS=${generic.hasPromotableCharacteristics()}; OEM_PRESENTATION=not_verified\n")
                })
            }
        } finally { pending.cancel() }
    }
}
