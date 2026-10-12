package com.imsx3d.classy.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.theme.ClassyLayout

/** Shared full-screen import task, embedded in the activity or hosted by a sheet's session. */
@Composable
fun ImportReviewPage(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    error: String? = null,
    busy: Boolean = false,
    useWindow: Boolean = true,
) {
    val body: @Composable () -> Unit = {
        BackHandler { if (!busy) onDismissRequest() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.widthIn(max = ClassyLayout.readingWidth).fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismissRequest, enabled = !busy) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                    Box(Modifier.weight(1f).padding(start = 4.dp)) {
                        ProvideTextStyle(MaterialTheme.typography.titleLarge) { title() }
                    }
                }
                Column(Modifier.weight(1f).widthIn(max = ClassyLayout.readingWidth)
                    .fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)) { text() }
                Column(Modifier.widthIn(max = ClassyLayout.readingWidth).fillMaxWidth()
                    .padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (busy) SupportingText(stringResource(R.string.editor_working))
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium) }
                    confirmButton()
                }
            }
        }
    }
    if (useWindow) Dialog(
        onDismissRequest = { if (!busy) onDismissRequest() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnBackPress = !busy, dismissOnClickOutside = false)
    ) {
        // WindowManager retains the departing surface for the exit animation. No delayed
        // dismiss callback or second persistence operation is needed.
        val view = androidx.compose.ui.platform.LocalView.current
        val lightSurface = MaterialTheme.colorScheme.background.luminance() > 0.5f
        androidx.compose.runtime.SideEffect {
            (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
                ?.let { window ->
                    window.setWindowAnimations(R.style.ClassyFullScreenTaskAnimation)
                    androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = lightSurface
                        isAppearanceLightNavigationBars = lightSurface
                    }
                }
        }
        body()
    } else body()
}
