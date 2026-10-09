package com.imsx3d.classy.ui.component

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var weekText by remember { mutableStateOf(request.displayWeek.toString()) }
    var day by remember { mutableIntStateOf(request.target.day) }
    var startText by remember { mutableStateOf(request.target.startNode.toString()) }
    var endText by remember { mutableStateOf(request.target.endNode.toString()) }
    var scope by remember { mutableStateOf(CourseMoveScope.THIS_WEEK) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var conflictDetails by remember { mutableStateOf<List<String>>(emptyList()) }
    val coroutine = rememberCoroutineScope()
    val days = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
    fun clearError() { error = null; conflictDetails = emptyList() }
    fun save(force: Boolean) {
        if (saving) return
        val targetWeek = weekText.toIntOrNull()
        val first = startText.toIntOrNull()
        val last = endText.toIntOrNull()
        if (targetWeek == null || targetWeek !in 1..request.table.maxWeek || first == null || last == null || last < first) {
            error = "请填写有效的目标周次和起止节次"; return
        }
        saving = true
        coroutine.launch {
            try {
                val date = DateUtils.dateOfWeek(request.table.startDate, targetWeek, day)
                val teaching = requireNotNull(CourseDateResolver.teachingDate(date, request.transfers)) {
                    "目标日期的课程已整体调走，请选择其他日期"
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
                error = e.message ?: "调课失败，请重试"
            } finally { saving = false }
        }
    }
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("调整课程时间") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(request.source.courseName, style = MaterialTheme.typography.titleMedium)
                Text("原安排：第 ${request.sourceWeek} 周 · ${days[request.source.day - 1]} · ${request.source.startNode}–${request.source.startNode + request.source.step - 1} 节")
                OutlinedTextField(weekText, { weekText = it; clearError() }, label = { Text("目标周次（1–${request.table.maxWeek}）") },
                    singleLine = true, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    days.forEachIndexed { index, name ->
                        FilterChip(selected = day == index + 1, onClick = { day = index + 1; clearError() },
                            enabled = !saving, label = { Text(name) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startText, { startText = it; clearError() }, Modifier.weight(1f), label = { Text("开始节次") },
                        singleLine = true, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(endText, { endText = it; clearError() }, Modifier.weight(1f), label = { Text("结束节次") },
                        singleLine = true, enabled = !saving, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                if (request.source.ownTime || request.source.isIrregularTime) Text("自定义时间课程将从目标节次开始，保持原上课时长。")
                CourseMoveScope.entries.forEach { option ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(scope == option, { scope = option; clearError() }, enabled = !saving)
                        Text(if (option == CourseMoveScope.THIS_WEEK) "仅本周这次课程" else "本周及以后的这个上课时段")
                    }
                }
                Text("其他上课时段保持不变；保存后可在课表顶部撤销。", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (conflictDetails.isNotEmpty()) {
                    Text("目标时间与以下课程重叠：", color = MaterialTheme.colorScheme.error)
                    conflictDetails.forEach { Text(it) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { save(conflictDetails.isNotEmpty()) }, enabled = !saving) {
            Text(if (saving) "正在保存…" else if (conflictDetails.isNotEmpty()) "仍然调课" else "确认调课")
        } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text("取消") } }
    )
}
