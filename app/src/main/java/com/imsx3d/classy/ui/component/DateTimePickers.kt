package com.imsx3d.classy.ui.component

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.theme.SleepyTheme
import com.imsx3d.classy.ui.theme.noRippleClickable
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.imsx3d.classy.ui.theme.primaryFilledButtonColors
import com.imsx3d.classy.ui.component.GlasenseIconButton

/**
 * 日期输入字段 — 手动输入 + 点击图标弹出原生日期选择器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = SleepyTheme.fieldShape,
    isError: Boolean = false,
    enabled: Boolean = true
) {
    val colors = MaterialTheme.colorScheme
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val fieldColors = SleepyTheme.fieldColors()

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = value,
            enabled = enabled,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f),
            shape = shape,
            isError = isError,
            colors = fieldColors
        )
        GlasenseIconButton(
            icon = Icons.Outlined.CalendarMonth,
            enabled = enabled,
            contentDescription = stringResource(R.string.select_date),
            onClick = { showPicker = true },
            modifier = Modifier.padding(start = 4.dp),
            tint = colors.primary)
    }

    if (showPicker) {
        // Session-local draft: cancel disposes it; recreation while open restores it.
        val initialDate = PickerInitialValues.dateMillis(value)
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate,
            initialDisplayedMonthMillis = initialDate,
            yearRange = PickerInitialValues.dateYears
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                // 2026-09-16 用户: 裸 TextButton 无边界无色块 — 色块按钮
                Button(
                    enabled = datePickerState.selectedDateMillis != null,
                    colors = primaryFilledButtonColors(),
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneOffset.UTC)
                                .toLocalDate()
                            onValueChange(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        }
                        showPicker = false
                    },
                    shape = SleepyTheme.shapes.medium
                ) { Text(stringResource(R.string.ok), maxLines = 1) }
            },
            dismissButton = {
                Button(
                    onClick = { showPicker = false },
                    shape = SleepyTheme.shapes.medium,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) { Text(stringResource(R.string.cancel), maxLines = 1) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * 时间输入字段 — 点击输入框直接弹时间选择器，无 clock 图标。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = SleepyTheme.fieldShape
) {
    val colors = MaterialTheme.colorScheme
    var showPicker by rememberSaveable { mutableStateOf(false) }
    val fieldColors = SleepyTheme.fieldColors()

    // Box + clickable 包装：OutlinedTextField 内部设 enabled=false
    // 让 click 事件穿透到外层 Box 的 clickable。
    // Box 必须先 clip(shape) 再 clickable — 否则涟漪是方角、且能溢出字段圆角。
    Box(
        modifier = modifier
            .clip(shape)
            .noRippleClickable { showPicker = true }
    ) {
        TextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            enabled = false,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = shape,
            colors = fieldColors
        )
    }

    if (showPicker) {
        val initialTime = PickerInitialValues.time(value)
        val timePickerState = rememberTimePickerState(
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.select_time), color = colors.onSurface) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 默认 TimePicker 配色 — 与 ReminderScreen 时间弹窗一致, 不再单独覆写表盘色
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                DialogActionButtons(
                    confirmText = stringResource(R.string.ok),
                    onConfirm = {
                        val h = timePickerState.hour.toString().padStart(2, '0')
                        val m = timePickerState.minute.toString().padStart(2, '0')
                        onValueChange("$h:$m")
                        showPicker = false
                    },
                    dismissText = stringResource(R.string.cancel),
                    onDismiss = { showPicker = false }
                )
            },
            dismissButton = {}
        )
    }
}
