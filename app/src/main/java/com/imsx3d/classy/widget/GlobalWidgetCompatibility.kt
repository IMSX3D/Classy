package com.imsx3d.classy.widget

/**
 * APK-side compatibility boundary for Android launchers worldwide.
 *
 * Every Android-compatible vendor in this registry consumes the public
 * AppWidget provider contract. Private negative-one-screen cards are recorded
 * as platform-bound capabilities, not guessed manifest extensions.
 */
enum class WidgetIntegrationTier {
    STANDARD_APP_WIDGET,
    PUBLIC_VENDOR_EXTENSION,
    PLATFORM_BOUND
}

data class GlobalWidgetVendor(
    val id: String,
    val displayName: String,
    val tier: WidgetIntegrationTier,
    val standardAppWidgetSupported: Boolean,
    val privateSurface: String?
)

val GLOBAL_WIDGET_VENDORS: List<GlobalWidgetVendor> = listOf(
    GlobalWidgetVendor("google", "Google Pixel / AOSP", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("samsung", "Samsung Galaxy / One UI", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("sony", "Sony Xperia", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("motorola", "Motorola / Lenovo", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("asus", "ASUS Zenfone / ROG", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("nothing", "Nothing", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("hmd", "HMD / Nokia", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("tecno", "TECNO", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("infinix", "Infinix", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("itel", "itel", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("zte", "ZTE", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("tcl", "TCL", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("sharp", "Sharp", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("fujitsu", "Fujitsu", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("panasonic", "Panasonic", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("kyocera", "Kyocera", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("cat", "CAT", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("unihertz", "Unihertz", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("doogee", "DOOGEE", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("ulefone", "Ulefone", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("blackview", "Blackview", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("vivo", "vivo / OriginOS", WidgetIntegrationTier.PUBLIC_VENDOR_EXTENSION, true, "OriginOS atomic component review"),
    GlobalWidgetVendor("xiaomi", "Xiaomi / HyperOS", WidgetIntegrationTier.PUBLIC_VENDOR_EXTENSION, true, "AppVault exposure and store review"),
    GlobalWidgetVendor("oppo", "OPPO / ColorOS", WidgetIntegrationTier.PLATFORM_BOUND, true, "Pantanal / UPK authorization"),
    GlobalWidgetVendor("honor", "HONOR / MagicOS", WidgetIntegrationTier.PLATFORM_BOUND, true, "YOYO / MagicOS card authorization"),
    GlobalWidgetVendor("huawei", "Huawei EMUI", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, null),
    GlobalWidgetVendor("harmonyos-next", "Huawei HarmonyOS NEXT", WidgetIntegrationTier.PLATFORM_BOUND, false, "Native service card HAP / ArkTS"),
    GlobalWidgetVendor("meizu", "Meizu / Flyme", WidgetIntegrationTier.STANDARD_APP_WIDGET, true, "No public Aicy card protocol"),
    GlobalWidgetVendor("snapdragon-spaces", "Qualcomm Snapdragon Spaces", WidgetIntegrationTier.PLATFORM_BOUND, false, "Separate XR platform"),
)

fun standardWidgetVendors(): List<GlobalWidgetVendor> =
    GLOBAL_WIDGET_VENDORS.filter { it.standardAppWidgetSupported }
