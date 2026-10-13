package com.imsx3d.classy.ui.component

import android.os.Trace
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import com.imsx3d.classy.BuildConfig

/** Benchmark-only attribution. Production returns the original modifier without adding a node. */
internal fun Modifier.traceMeasure(label: String): Modifier {
    if (BuildConfig.BUILD_TYPE != "benchmark") return this
    return layout { measurable, constraints ->
        Trace.beginSection("Classy/measure/$label")
        val placeable = try { measurable.measure(constraints) } finally { Trace.endSection() }
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }
}
