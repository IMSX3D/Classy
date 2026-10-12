package com.imsx3d.classy.ui.screen.imports

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.theme.SleepyTheme
import com.imsx3d.classy.ui.component.GlasenseIconButton
import com.imsx3d.classy.ui.component.settingsCard

/** UI-only representation of an import draft; persistence belongs to the caller. */
data class ImportDraft(
    val id: String,
    val name: String,
    val details: String,
    val savedAt: String? = null
)

@Composable
fun ImportDraftSheet(
    drafts: List<ImportDraft>,
    onDismiss: () -> Unit,
    onRestore: (String) -> Unit,
    onDelete: suspend (String) -> Unit
) {
    var pendingDelete by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf<String?>(null) }
    var busy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var failed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    com.imsx3d.classy.ui.component.ImportReviewPage(
        onDismissRequest = onDismiss, busy = busy, scrollContent = false,
        title = { Text(stringResource(R.string.import_drafts)) },
        text = {
            if (drafts.isEmpty()) Text(stringResource(R.string.import_drafts_empty))
            else LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(drafts, key = { it.id }) { draft ->
                    ImportDraftRow(draft,
                        onRestore = { if (!busy) onRestore(draft.id) },
                        onDelete = { if (!busy) { failed = false; pendingDelete = draft.id } })
                }
            }
        }, confirmButton = {})
    pendingDelete?.let { id ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!busy) pendingDelete = null },
            title = { Text(stringResource(R.string.import_draft_delete)) },
            text = { Column {
                Text(stringResource(R.string.import_draft_delete_confirm))
                if (failed) Text(stringResource(R.string.editor_action_failed), color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = { androidx.compose.material3.TextButton(enabled = !busy, onClick = {
                busy = true; failed = false
                scope.launch {
                    try { onDelete(id); pendingDelete = null }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { failed = true }
                    finally { busy = false }
                }
            }) { Text(stringResource(R.string.import_draft_delete)) } },
            dismissButton = { androidx.compose.material3.TextButton(enabled = !busy,
                onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@Composable
private fun ImportDraftRow(
    draft: ImportDraft,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .settingsCard(colors.surfaceContainer)
            .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = draft.name,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface
            )
            Text(
                text = draft.details,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
            draft.savedAt?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = stringResource(R.string.import_draft_saved_at, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        GlasenseIconButton(
            icon = Icons.Outlined.Restore,
            contentDescription = stringResource(R.string.import_draft_restore),
            onClick = onRestore,
            tint = colors.primary)
        GlasenseIconButton(
            icon = Icons.Outlined.DeleteOutline,
            contentDescription = stringResource(R.string.import_draft_delete),
            onClick = onDelete,
            tint = colors.error)
    }
}
