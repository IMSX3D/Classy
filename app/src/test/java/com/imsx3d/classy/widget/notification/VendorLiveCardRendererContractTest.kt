package com.imsx3d.classy.widget.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class VendorLiveCardRendererContractTest {
    @Test
    fun `manufacturer detection covers vendor families`() {
        assertEquals(LiveCardVendor.OPPO, detectLiveCardVendor("OPPO"))
        assertEquals(LiveCardVendor.ONEPLUS, detectLiveCardVendor("OnePlus"))
        assertEquals(LiveCardVendor.REALME, detectLiveCardVendor("realme"))
        assertEquals(LiveCardVendor.XIAOMI, detectLiveCardVendor("Redmi"))
        assertEquals(LiveCardVendor.VIVO, detectLiveCardVendor("vivo"))
        assertEquals(LiveCardVendor.IQOO, detectLiveCardVendor("iQOO"))
        assertEquals(LiveCardVendor.MEIZU, detectLiveCardVendor("Meizu"))
        assertEquals(LiveCardVendor.HUAWEI, detectLiveCardVendor("HUAWEI"))
        assertEquals(LiveCardVendor.HONOR, detectLiveCardVendor("HONOR"))
        assertEquals(LiveCardVendor.GENERIC, detectLiveCardVendor("google"))
    }
}
