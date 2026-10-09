package com.imsx3d.classy.widget.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Cross-checked protocol contract test. Locks the post-review fixes:
 * vivo operation flips 0→1 on update, clickResp is sent, baseInfos.progress
 * is gone (use infos.progress), shortInfos has image/icon, capsule has icon,
 * Meizu capsule has icon + colors. Xiaomi uses the standard progress path;
 * private focus payloads are covered by VendorLiveCardRendererExtrasTest.
 */
class VendorLiveCardRendererFixContractTest {

    private val source: String
        get() {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                val file = File(dir, "app/src/main/java/com/imsx3d/classy/widget/notification/VendorLiveCardRenderer.kt")
                if (file.isFile) return file.readText()
                dir = dir.parentFile
            }
            error("renderer source not found")
        }

    @Test
    fun `renderer keeps promoted ongoing Android 16 path`() {
        assertTrue(
            "Promoted ongoing is part of the OPPO baseline lifecycle",
            source.contains("setRequestPromotedOngoing(true)")
        )
    }

    @Test
    fun `vivo operation covers create update and end`() {
        assertTrue(
            "vivo renderer must emit operation 2 after the class ends",
            source.contains("!state.isActive -> 2")
        )
        assertTrue(
            "vivo renderer must switch operation 0 -> 1 while active",
            source.contains("state.updateSequence <= 1 -> 0") &&
                source.contains("else -> 1")
        )
    }

    @Test
    fun `vivo registers clickResp as PendingIntent bundle`() {
        assertTrue(
            "vivo renderer must write notification.superx.clickResp",
            source.contains("putParcelable(\"notification.superx.clickResp\", contentIntent)")
        )
    }

    @Test
    fun `vivo progress uses infos progress not baseInfos progress`() {
        assertFalse(
            "vivo renderer must NOT use baseInfos.progress",
            source.contains("notification.superx.baseInfos.progress")
        )
        assertTrue(
            "vivo renderer must use infos.progress",
            source.contains("\"progress\", state.progress)")
        )
        assertTrue(
            "vivo renderer must use infos.nodeIcon (template 2 required)",
            source.contains("\"nodeIcon\"")
        )
    }

    @Test
    fun `vivo shortInfos includes icon and imageClickResp`() {
        assertTrue(
            "shortInfos.image required",
            source.contains("putParcelable(\"image\",")
        )
        assertTrue(
            "shortInfos.imageClickResp required",
            source.contains("putParcelable(\"imageClickResp\", contentIntent)")
        )
    }

    @Test
    fun `vivo capsule has icon`() {
        assertTrue(
            "capsule.icon is required by vivo protocol",
            source.contains("putParcelable(\"icon\", Icon.createWithResource")
        )
    }

    @Test
    fun `meizu capsule icon and colors are sent`() {
        assertTrue(
            "Meizu capsule must ship icon",
            source.contains("putParcelable(\"notification.live.capsuleIcon\"")
        )
        assertTrue(
            "Meizu capsule must ship bg color",
            source.contains("\"notification.live.capsuleBgColor\"")
        )
        assertTrue(
            "Meizu capsule must ship content color",
            source.contains("\"notification.live.capsuleContentColor\"")
        )
    }
}
