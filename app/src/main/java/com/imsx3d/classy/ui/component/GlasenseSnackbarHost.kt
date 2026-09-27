package com.imsx3d.classy.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.ui.theme.noRippleClickable
import com.nevoit.glasense.core.component.HGap
import com.nevoit.glasense.core.component.Text
import com.nevoit.glasense.theme.GlasenseTheme

/**
 * 应用内通知（Snackbar）—— Glasense 版式（UI-3f，2026-09-26）。
 *
 * 两处修正：
 * 1. **不再被底部导航 Dock 压住**：底部内边距取 `LocalNavExtraBottomPadding` ——
 *    这是 `SleepyNavHost` 为悬浮 Dock 提供给滚动内容的"底部占用量"。
 *    在带 Dock 的 tab 页里它 = 胶囊高 + 悬浮高 + 手势条内边距；在 Dock 之外的
 *    全屏页（如「关于」「导出」）它是 0.dp，通知自然落到屏幕底部 —— 一个组件两种场景自适应，
 *    调用方无需判断自己有没有 Dock。
 * 2. **样式与 Glasense 同源**：M3 Snackbar 的圆角/容器色/字号与全应用新视觉割裂，
 *    这里改成 iOS 式**反色浮层**：浅色模式 = 深底浅字，深色模式 = 浅灰底深字，
 *    12dp 圆角（Glasense 卡片规范）+ 中等阴影 + Glasense 字号阶梯，动作文字用强调色。
 */
@Composable
fun GlasenseSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val g = GlasenseTheme.colors
    val shape = RoundedCornerShape(12.dp)
    // 反色浮层：浅色 → 深底浅字；深色 → 抬升卡面色（#2C2C2E）深字，避免夜里一块纯白刺眼
    val dark = GlasenseTheme.darkTheme
    val container = if (dark) g.elevatedCardBackground else g.content
    val contentColor = if (dark) g.content else g.pageBackground
    // Dock 占用量（无 Dock 的页面为 0.dp），再加 8dp 呼吸位
    val dockExtra = LocalNavExtraBottomPadding.current

    SnackbarHost(
        hostState = hostState,
        modifier = modifier.padding(bottom = dockExtra + 8.dp)
    ) { data: SnackbarData ->
        Row(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .shadow(8.dp, shape)
                .clip(shape)
                .background(container)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = data.visuals.message,
                style = GlasenseTheme.type.footnoteEmphasized,
                color = contentColor,
                modifier = Modifier.weight(1f, fill = false)
            )
            val action = data.visuals.actionLabel
            if (action != null) {
                HGap(16.dp)
                Text(
                    text = action,
                    style = GlasenseTheme.type.footnoteEmphasized,
                    color = g.primary,
                    modifier = Modifier
                        .noRippleClickable { data.performAction() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}
