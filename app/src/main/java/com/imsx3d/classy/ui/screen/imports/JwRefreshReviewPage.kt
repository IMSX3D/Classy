package com.imsx3d.classy.ui.screen.imports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.SleepyApp
import com.imsx3d.classy.data.entity.CourseEntity
import com.imsx3d.classy.data.jw.*
import com.imsx3d.classy.ui.component.ImportReviewPage
import com.imsx3d.classy.ui.component.SettingsGroupRow
import com.imsx3d.classy.ui.component.SupportingText
import kotlinx.coroutines.launch

@Composable
internal fun JwRefreshReviewPage(tableId: Long, school: JwSchoolInfo, incoming: List<CourseEntity>,
                                 onBack: () -> Unit, onFinished: () -> Unit,
                                 repository: com.imsx3d.classy.data.repository.ScheduleRepository = SleepyApp.get().repository) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var plan by remember(tableId, incoming) { mutableStateOf<JwRefreshPlan?>(null) }
    var error by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirmedStart by rememberSaveable(tableId, incoming) { mutableStateOf<String?>(null) }
    var selectedAdds by rememberSaveable(tableId, incoming) { mutableStateOf<List<String>?>(null) }
    var selectedRemovals by rememberSaveable(tableId, incoming) { mutableStateOf<List<Long>?>(null) }
    val addKeys = selectedAdds?.toSet() ?: plan?.additions?.filter { it.defaultSelected }?.map { it.key }?.toSet().orEmpty()
    val removeIds = selectedRemovals?.toSet() ?: plan?.removals?.filter { it.safe }?.map { it.course.id }?.toSet().orEmpty()
    val termConfirmed = plan != null && confirmedStart == plan?.table?.startDate
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(tableId, incoming, retry) {
        error = false
        try { plan = repository.prepareJwRefresh(tableId, incoming, school) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error = true }
    }
    ImportReviewPage(useWindow = false, scrollContent = false, busy = busy, onDismissRequest = onBack,
        title = { Text(stringResource(R.string.schedule_refresh_school)) },
        text = {
            val p = plan
            if (p == null) {
                if (!error) CircularProgressIndicator()
            } else LazyColumn(Modifier.fillMaxSize()) {
                item {
                Text(p.table.name, style = MaterialTheme.typography.titleMedium)
                SupportingText(stringResource(R.string.jw_refresh_preserve))
                if (p.firstLink) SupportingText(stringResource(R.string.jw_refresh_first))
                SupportingText(stringResource(R.string.jw_refresh_diff_note))
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.jw_refresh_counts, addKeys.size, removeIds.size))
                }
                items(p.additions, key = { "add-${it.key}" }) { add ->
                    val checked = add.key in addKeys
                    SettingsGroupRow(stringResource(R.string.jw_refresh_add, add.course.courseName),
                        subtitle = arrangement(add.course),
                        onClick = if (busy) null else ({ selectedAdds = (if (checked) addKeys - add.key else addKeys + add.key).toList() }),
                        modifier = Modifier.semantics { role = Role.Checkbox; toggleableState = ToggleableState(checked) },
                        trailing = { Checkbox(checked, null, enabled = !busy) })
                }
                items(p.removals, key = { "remove-${it.course.id}" }) { remove ->
                    val checked = remove.course.id in removeIds
                    SettingsGroupRow(stringResource(R.string.jw_refresh_remove, remove.course.courseName),
                        subtitle = arrangement(remove.course) + if (!remove.safe) "\n" + stringResource(R.string.jw_refresh_local) else "",
                        onClick = if (busy) null else ({ selectedRemovals = (if (checked) removeIds - remove.course.id else removeIds + remove.course.id).toList() }),
                        modifier = Modifier.semantics { role = Role.Checkbox; toggleableState = ToggleableState(checked) },
                        trailing = { Checkbox(checked, null, enabled = !busy) })
                }
                item {
                if (p.additions.isEmpty() && p.removals.isEmpty()) SupportingText(stringResource(R.string.jw_refresh_no_changes))
                SettingsGroupRow(stringResource(R.string.jw_refresh_term, school.name, p.table.startDate),
                    onClick = if (busy) null else ({ confirmedStart = if (termConfirmed) null else p.table.startDate }),
                    modifier = Modifier.semantics { role = Role.Checkbox; toggleableState = ToggleableState(termConfirmed) },
                    trailing = { Checkbox(termConfirmed, null, enabled = !busy) })
                }
            }
        },
        error = if (error) stringResource(R.string.jw_refresh_failed) else null,
        confirmButton = {
            if (error) TextButton(onClick = { plan = null; selectedAdds = null; selectedRemovals = null; confirmedStart = null; retry++ }, enabled = !busy) { Text(stringResource(R.string.update_retry)) }
            Button(onClick = {
                val snapshot = plan ?: return@Button
                if (busy) return@Button
                busy = true
                scope.launch {
                    try {
                        repository.applyJwRefresh(snapshot, addKeys, removeIds)
                        android.widget.Toast.makeText(context, context.getString(R.string.jw_refresh_done), android.widget.Toast.LENGTH_SHORT).show()
                        onFinished()
                    }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { error = true }
                    finally { busy = false }
                }
            }, enabled = plan != null && termConfirmed && !busy && !error, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.jw_refresh_apply))
            }
        })
}

@Composable
private fun arrangement(c: CourseEntity): String = stringResource(R.string.jw_refresh_arrangement,
    c.day, c.startNode, c.startNode + c.step - 1, c.startWeek, c.endWeek, c.room)
