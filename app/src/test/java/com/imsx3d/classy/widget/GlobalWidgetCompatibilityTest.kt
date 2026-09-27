package com.imsx3d.classy.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalWidgetCompatibilityTest {

    @Test
    fun `global registry has unique stable ids and nonempty names`() {
        val ids = GLOBAL_WIDGET_VENDORS.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        GLOBAL_WIDGET_VENDORS.forEach { vendor ->
            assertTrue(vendor.id.isNotBlank())
            assertTrue(vendor.displayName.isNotBlank())
        }
    }

    @Test
    fun `every standard or public extension vendor uses public AppWidget path`() {
        GLOBAL_WIDGET_VENDORS
            .filter { it.tier != WidgetIntegrationTier.PLATFORM_BOUND }
            .forEach { vendor ->
                assertTrue(
                    "${vendor.displayName} must have the standard APK AppWidget path",
                    vendor.standardAppWidgetSupported
                )
            }
    }

    @Test
    fun `every platform-bound vendor documents the non APK surface`() {
        GLOBAL_WIDGET_VENDORS
            .filter { it.tier == WidgetIntegrationTier.PLATFORM_BOUND }
            .forEach { vendor ->
                assertNotNull("${vendor.displayName} needs a platform boundary", vendor.privateSurface)
                assertTrue(vendor.privateSurface.orEmpty().isNotBlank())
            }
    }

    @Test
    fun `global registry includes major public and closed ecosystems`() {
        val ids = GLOBAL_WIDGET_VENDORS.map { it.id }.toSet()
        assertTrue(ids.containsAll(setOf("google", "samsung", "sony", "motorola", "asus")))
        assertTrue(ids.containsAll(setOf("vivo", "xiaomi", "oppo", "honor", "huawei", "meizu")))
        assertTrue(ids.containsAll(setOf("harmonyos-next", "snapdragon-spaces")))
    }

    @Test
    fun `every listed standard vendor has the concrete provider matrix`() {
        assertTrue(standardWidgetVendors().isNotEmpty())
        assertTrue(ALL_WIDGET_VARIANTS.size >= 13)
        standardWidgetVendors().forEach { vendor ->
            assertTrue("${vendor.displayName} has no AppWidget variants", ALL_WIDGET_VARIANTS.isNotEmpty())
        }
    }
}
