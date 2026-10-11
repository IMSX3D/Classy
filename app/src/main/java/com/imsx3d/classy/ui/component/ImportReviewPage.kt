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
                Column(Modifier.widthIn(max = ClassyLayout.readingWidth).fillMaxWidth()
                    .padding(16.dp)) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
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
    ) { body() } else body()
}
