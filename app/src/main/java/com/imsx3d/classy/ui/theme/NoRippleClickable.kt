package com.imsx3d.classy.ui.theme

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent

/**
 * 无涟漪点击 — 全 app 自定义可点面(Box/Row/Text 上的 clickable)唯一入口。
 * 视觉策略: 自定义表面用即时轻遮罩确认按压/键盘焦点，不缩放布局或排队播放动画；
 * 涟漪反馈只保留给 M3 标准组件(Button/Switch/IconButton/DropdownMenuItem)。
 */
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val focused by source.collectIsFocusedAsState()
    val feedback = MaterialTheme.colorScheme.onSurface.copy(alpha = if (pressed) 0.08f else 0.06f)
    drawWithContent {
        drawContent()
        if (pressed || focused) drawRect(feedback)
    }.clickable(
        interactionSource = source,
        indication = null,
        onClick = onClick
    )
}
