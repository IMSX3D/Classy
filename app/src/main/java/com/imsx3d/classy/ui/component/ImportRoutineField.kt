package com.imsx3d.classy.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.data.entity.SmartPeriodConfig
import com.imsx3d.classy.ui.theme.noRippleClickable
import com.imsx3d.classy.util.TimeTableUtils

/** A summary opens a separate routine draft; only Done changes the import review. */
@Composable
fun ImportRoutineField(
    rows: List<TimeTableUtils.TimeSlotRow>,
    onRowsChange: (List<TimeTableUtils.TimeSlotRow>) -> Unit,
    smartConfig: SmartPeriodConfig,
    onSmartConfigChange: (SmartPeriodConfig) -> Unit,
    periodTableOptions: List<PeriodTableOption>,
    selectedPeriodTableId: Long?,
    onSelectPeriodTable: (Long?) -> Unit,
    enabled: Boolean = true,
) {
    var open by rememberSaveable { mutableStateOf(false) }
    val selected = periodTableOptions.find { it.id == selectedPeriodTableId }
    Column(Modifier.fillMaxWidth().settingsCard(MaterialTheme.colorScheme.surfaceContainer)
        .noRippleClickable { if (enabled) open = true }.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.import_routine_title), Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium)
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
        }
        Spacer(Modifier.height(8.dp))
        SupportingText(if (selected != null && selected.id > 0) selected.name
            else stringResource(R.string.import_routine_local))
        SupportingText(stringResource(R.string.period_table_nodes_count, selected?.nodesPerDay ?: rows.size))
        if ((selectedPeriodTableId == null || selectedPeriodTableId <= 0) && rows.any { it.start.isBlank() || it.end.isBlank() })
            SupportingText(stringResource(R.string.import_routine_incomplete))
    }
    if (open) {
        val draftRows = rememberSaveable(saver = PeriodRowsSaver) {
            mutableStateListOf<TimeTableUtils.TimeSlotRow>().apply { addAll(rows) }
        }
        var draftConfig by rememberSaveable(stateSaver = SmartPeriodDraftSaver) { mutableStateOf(smartConfig) }
        var draftId by rememberSaveable { mutableStateOf(selectedPeriodTableId) }
        var discard by rememberSaveable { mutableStateOf(false) }
        val changed = draftRows.toList() != rows || draftConfig != smartConfig || draftId != selectedPeriodTableId
        val back = { if (changed) discard = true else open = false }
        ImportReviewPage(
            onDismissRequest = back,
            title = { Text(stringResource(R.string.import_routine_title)) },
            text = {
                TimeSlotEditor(
                    rows = draftRows.toList(),
                    onRowsChange = { draftRows.clear(); draftRows.addAll(it) },
                    smartConfig = draftConfig,
                    onSmartConfigChange = { draftConfig = it },
                    periodTableOptions = periodTableOptions,
                    selectedPeriodTableId = draftId,
                    onSelectPeriodTable = { draftId = it },
                    clearBindingOnEdit = true,
                    scrollPeriodTableList = false
                )
            },
            confirmButton = {
                DialogActionButtons(
                    confirmText = stringResource(R.string.ok),
                    onConfirm = {
                        onRowsChange(draftRows.toList())
                        onSmartConfigChange(draftConfig)
                        onSelectPeriodTable(draftId)
                        open = false
                    },
                    dismissText = stringResource(R.string.back), onDismiss = back
                )
            }
        )
        if (discard) DiscardChangesDialog(
            onKeepEditing = { discard = false },
            onDiscard = { discard = false; open = false }
        )
    }
}
