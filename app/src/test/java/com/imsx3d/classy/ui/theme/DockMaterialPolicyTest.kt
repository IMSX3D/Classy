package com.imsx3d.classy.ui.theme

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DockMaterialPolicyTest {
    @Test fun unsupportedDevicesNeverEnterRefraction() {
        for (sdk in 26..32) assertFalse(DockMaterialPolicy.useGlass(true, sdk, true, false))
    }
    @Test fun supportedAcceleratedDeviceCanOptIn() {
        assertTrue(DockMaterialPolicy.useGlass(true, 33, true, false))
    }
    @Test fun userOffAndPowerSavingAlwaysUseSolid() {
        assertFalse(DockMaterialPolicy.useGlass(false, 36, true, false))
        assertFalse(DockMaterialPolicy.useGlass(true, 36, true, true))
    }
    @Test fun softwareRenderingUsesSolid() {
        assertFalse(DockMaterialPolicy.useGlass(true, 36, false, false))
    }
}
