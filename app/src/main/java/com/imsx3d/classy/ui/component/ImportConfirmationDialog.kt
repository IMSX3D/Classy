package com.imsx3d.classy.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/** Only the form scrolls. Measure the title and actions before allocating form space. */
@Composable
fun ImportConfirmationDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    error: String? = null,
) {
    val scroll = rememberScrollState()
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxWidth()
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.85f)
                .padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ProvideTextStyle(MaterialTheme.typography.headlineSmall) { title() }
                Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(scroll)) { text() }
                // Validation is visible even when the form is scrolled to the bottom.
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                HorizontalDivider()
                confirmButton()
            }
        }
    }
}
