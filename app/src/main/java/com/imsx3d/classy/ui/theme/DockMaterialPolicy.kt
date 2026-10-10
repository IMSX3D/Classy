package com.imsx3d.classy.ui.theme

/** Full blur + refraction requires Android 13. Conservative fallback for the prototype. */
internal object DockMaterialPolicy {
    fun useGlass(requested: Boolean, sdk: Int, hardwareAccelerated: Boolean, powerSave: Boolean): Boolean =
        requested && sdk >= 33 && hardwareAccelerated && !powerSave
}
