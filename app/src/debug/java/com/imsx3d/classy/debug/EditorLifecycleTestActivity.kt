package com.imsx3d.classy.debug

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable

/** Instrumentation-only host; excluded from Release and not exported. */
class EditorLifecycleTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { content?.invoke() }
    }
    companion object { var content: (@Composable () -> Unit)? = null }
}
