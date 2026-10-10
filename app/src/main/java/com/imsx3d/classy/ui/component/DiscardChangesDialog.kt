package com.imsx3d.classy.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R

/** Leaving an explicit-submit form: dismissal means keep editing, never discard implicitly. */
@Composable
fun DiscardChangesDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.unsaved_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SupportingText(stringResource(R.string.unsaved_body))
                DialogActionButtons(
                    confirmText = stringResource(R.string.unsaved_discard), onConfirm = onDiscard,
                    dismissText = stringResource(R.string.unsaved_keep), onDismiss = onKeepEditing,
                    destructive = true
                )
            }
        },
        confirmButton = {}
    )
}
