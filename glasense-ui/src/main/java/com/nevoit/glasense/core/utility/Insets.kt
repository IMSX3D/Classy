package com.nevoit.glasense.core.utility

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat

@Composable
fun getStatusBarHeight(): Dp {
    return WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()
}

@Composable
fun getNavigationBarHeight(): Dp {
    return WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
}

@Composable
fun AwaitWindowMetrics(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (view.isInEditMode) {
        content()
        return
    }

    var isReady by remember(view) {
        mutableStateOf(view.hasReadyWindowMetrics())
    }

    // Compose owns the View's insets listener, so wait without replacing it.
    LaunchedEffect(view) {
        while (!isReady) {
            withFrameNanos { }
            if (view.isAttachedToWindow) {
                ViewCompat.requestApplyInsets(view)
            }
            isReady = view.hasReadyWindowMetrics()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isReady) {
            content()
        }
    }
}

private fun View.hasReadyWindowMetrics(): Boolean {
    return isLaidOut &&
            width > 0 &&
            height > 0 &&
            ViewCompat.getRootWindowInsets(this) != null
}

@Composable
fun getWindowWidth(): Dp {
    return LocalWindowInfo.current.containerDpSize.width
}

@Composable
fun getWindowHeight(): Dp {
    return LocalWindowInfo.current.containerDpSize.height
}

@Composable
fun getWindowWidthPx(): Int {
    return LocalWindowInfo.current.containerSize.width
}

@Composable
fun getWindowHeightPx(): Int {
    return LocalWindowInfo.current.containerSize.height
}

val DefaultGap = 12.dp
