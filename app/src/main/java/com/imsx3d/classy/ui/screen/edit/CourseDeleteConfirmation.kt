package com.imsx3d.classy.ui.screen.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.component.DialogActionButtons
import com.imsx3d.classy.ui.component.EditorActionState

/** Destructive work keeps its confirmation visible until success; failure remains retryable. */
@Composable
internal fun CourseDeleteConfirmation(name: String, action: EditorActionState, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!action.busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !action.busy, dismissOnClickOutside = !action.busy),
        title = { Text(stringResource(R.string.confirm_delete)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.delete_course_confirm, name))
                action.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                DialogActionButtons(
                    confirmText = stringResource(if (action.busy) R.string.course_deleting else R.string.delete),
                    onConfirm = { if (!action.busy) onConfirm() },
                    dismissText = stringResource(R.string.cancel),
                    onDismiss = { if (!action.busy) onDismiss() },
                    confirmEnabled = !action.busy, dismissEnabled = !action.busy,
                    destructive = true
                )
            }
        },
        confirmButton = {}, dismissButton = {}
    )
}
