package com.imsx3d.classy.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WidgetDeviceVerificationScriptTest {

    @Test
    fun `device verification script is executable and keeps destructive operations out`() {
        val script = sequenceOf(
            File("tools/verify_widget_device.sh"),
            File("../tools/verify_widget_device.sh")
        ).first { it.isFile }
        assertTrue(script.canExecute())
        val source = script.readText()
        assertTrue(source.contains("dumpsys package"))
        assertTrue(source.contains("sort -u"))
        assertTrue(source.contains("provider_count=\"$" + "("))
        assertTrue(source.contains("android.appwidget.action.APPWIDGET_UPDATE"))
        assertTrue(source.contains("miui.appwidget.action.APPWIDGET_UPDATE"))
        assertTrue(!source.contains("pm clear"))
        assertTrue(!source.contains("pm uninstall"))
    }
}
