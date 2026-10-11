package com.imsx3d.classy.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.ui.theme.AdaptiveContentPolicy

/** Reflow the same fields without replacing their composition or picker state. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdaptiveTimeRangeFields(
    start: String,
    end: String,
    startLabel: String,
    endLabel: String,
    onStartChange: (String) -> Unit,
    onEndChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AdaptiveEditorFields(modifier, minimumFieldWidth = 136.dp) {
        TimePickerField(start, onStartChange, startLabel, Modifier.weight(1f))
        TimePickerField(end, onEndChange, endLabel, Modifier.weight(1f))
    }
}

/** Two related fields retain their composition when width or font size changes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdaptiveEditorFields(
    modifier: Modifier = Modifier,
    minimumFieldWidth: androidx.compose.ui.unit.Dp = 168.dp,
    content: @Composable FlowRowScope.() -> Unit
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (AdaptiveContentPolicy.showTimeFieldsSideBySide(
                maxWidth.value, LocalDensity.current.fontScale, minimumFieldWidth.value)) 2 else 1
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            maxItemsInEachRow = columns,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}
