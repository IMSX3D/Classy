package com.imsx3d.classy.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.theme.SleepyTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.entity.TimeTableEntity
import com.imsx3d.classy.data.repository.CourseMoveConflictException
import com.imsx3d.classy.util.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class CourseMoveRequest(
    val source: CourseEntity,
    val sourceWeek: Int,
    val displayWeek: Int,
    val target: GridSelection,
    val table: TimeTableEntity,
    val transfers: List<HolidayTransferEntry>
)

@Composable
fun CourseMoveDialog(request: CourseMoveRequest, onDismiss: () -> Unit) {
    var weekText by rememberSaveable(request.source.id, request.sourceWeek, request.target) { mutableStateOf(request.displayWeek.toString()) }
    var day by rememberSaveable(request.source.id, request.sourceWeek, request.target) { mutableIntStateOf(request.target.day) }
    var startText by rememberSaveable(request.source.id, request.sourceWeek, request.target) { mutableStateOf(request.target.startNode.toString()) }
    var endText by rememberSaveable(request.source.id, request.sourceWeek, request.target) { mutableStateOf(request.target.endNode.toString()) }
    var scope by rememberSaveable(request.source.id, request.sourceWeek, request.target) { mutableStateOf(CourseMoveScope.THIS_WEEK) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var conflictDetails by remember { mutableStateOf<List<String>>(emptyList()) }
    val coroutine = rememberCoroutineScope()
    val context = LocalContext.current
    val days = context.resources.getStringArray(R.array.day_names).toList()
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    fun requestDismiss() {
        if (saving) return
        val changed = weekText != request.displayWeek.toString() || day != request.target.day ||
            startText != request.target.startNode.toString() || endText != request.target.endNode.toString() || scope != CourseMoveScope.THIS_WEEK
        if (changed) showDiscard = true else onDismiss()
    }
    fun clearError() { error = null; conflictDetails = emptyList() }
    fun save(force: Boolean) {
        if (saving) return
        val targetWeek = weekText.toIntOrNull()
        val first = startText.toIntOrNull()
        val last = endText.toIntOrNull()
        if (targetWeek == null || targetWeek !in 1..request.table.maxWeek || first == null || last == null || last < first) {
            error = context.getString(R.string.move_invalid); return
        }
        saving = true
        coroutine.launch {
            try {
                val date = DateUtils.dateOfWeek(request.table.startDate, targetWeek, day)
                val teaching = requireNotNull(CourseDateResolver.teachingDate(date, request.transfers)) {
                    context.getString(R.string.move_transferred)
                }
                SleepyApp.get().repository.moveCourseMeeting(request.source, request.sourceWeek,
                    DateUtils.currentWeek(request.table.startDate, teaching),
                    GridSelection(teaching.dayOfWeek.value, first, last), scope, request.table, request.transfers, force)
                onDismiss()
            } catch (e: CourseMoveConflictException) {
                conflictDetails = e.details
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: context.getString(R.string.move_failed)
            } finally { saving = false }
        }
    }
    AlertDialog(
        onDismissRequest = { requestDismiss() },
        title = { Text(stringResource(R.string.move_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(request.source.courseName, style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.move_original, request.sourceWeek, days[request.source.day - 1], request.source.startNode, request.source.startNode + request.source.step - 1))
                TextField(weekText, { weekText = it; clearError() }, label = { Text(stringResource(R.string.move_week, request.table.maxWeek)) },
                    shape = SleepyTheme.fieldShape, colors = SleepyTheme.fieldColors(), singleLine = true, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    days.forEachIndexed { index, name ->
                        FilterChip(selected = day == index + 1, onClick = { day = index + 1; clearError() },
                            enabled = !saving, label = { Text(name) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextField(startText, { startText = it; clearError() }, Modifier.weight(1f), label = { Text(stringResource(R.string.move_start)) },
                        shape = SleepyTheme.fieldShape, colors = SleepyTheme.fieldColors(), singleLine = true, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    TextField(endText, { endText = it; clearError() }, Modifier.weight(1f), label = { Text(stringResource(R.string.move_end)) },
                        shape = SleepyTheme.fieldShape, colors = SleepyTheme.fieldColors(), singleLine = true, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                if (request.source.ownTime || request.source.isIrregularTime) Text(androidx.compose.ui.res.stringResource(com.imsx3d.classy.R.string.course_move_custom_hint))
                CourseMoveScope.entries.forEach { option ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(scope == option, { scope = option; clearError() }, enabled = !saving)
                        Text(if (option == CourseMoveScope.THIS_WEEK) stringResource(R.string.move_this_week) else stringResource(R.string.move_future))
                    }
                }
                Text(androidx.compose.ui.res.stringResource(com.imsx3d.classy.R.string.course_move_undo_hint), style = MaterialTheme.typography.bodyMedium)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (conflictDetails.isNotEmpty()) {
                    Text(stringResource(R.string.move_conflicts), color = MaterialTheme.colorScheme.error)
                    conflictDetails.forEach { Text(it) }
                }
                DialogActionButtons(
                    confirmText = stringResource(if (saving) R.string.move_saving else if (conflictDetails.isNotEmpty()) R.string.move_force else R.string.move_confirm),
                    onConfirm = { save(conflictDetails.isNotEmpty()) },
                    confirmEnabled = !saving,
                    dismissText = stringResource(R.string.cancel),
                    onDismiss = { requestDismiss() },
                    dismissEnabled = !saving
                )
            }
        },
        confirmButton = {}
    )
    if (showDiscard) DiscardChangesDialog(
        onKeepEditing = { showDiscard = false },
        onDiscard = { showDiscard = false; onDismiss() }
    )
}
