package com.imsx3d.classy.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.nevoit.glasense.theme.GlasenseTheme
import com.nevoit.glasense.theme.tokens.Gray200
import com.nevoit.glasense.theme.tokens.Gray800

/**
 * Glasense 令牌 → M3 `ColorScheme` 槽位映射（UI-2，2026-09-26）。
 *
 * 用途：课表网格是 1400+ 行的自绘代码，直接改写成 Glasense 组件成本过高、风险过大；
 * 但它只从 `MaterialTheme.colorScheme` 取了 10 个字段。把这些取值点换成本函数后，
 * **配色一次到位、布局一行不动**，后续再逐步把局部组件换成 Glasense 原生组件。
 *
 * 映射原则（与 Glasense 的分层语义一致）：
 *   · 页面底       → pageBackground
 *   · 卡片/面板    → cardBackground；浮起层 → elevatedCardBackground
 *   · 文字         → content / contentVariant
 *   · 中性面       → Tailwind Gray200（浅）/ Gray800（深）：用于"无色模式底"、
 *                    非本周等需要"低对比中性块"的位置（Glasense 自身没有 surfaceVariant 槽位）
 *   · 容器色        → scrim 系列（Glasense 用低透明度黑/白做层次，不用满色容器）
 *   · 分隔线        → scrimBold
 *
 * UI-4s（2026-09-26 审计发现）：**secondary / tertiary 这两族原先漏映射** ——
 * M3 的 `darkColorScheme()/lightColorScheme()` 默认值会把基线主题的紫/玫瑰粉漏进来
 * （深色 tertiary = #EFB8C8 粉），代码里谁用了 `colorScheme.tertiary` 谁就"跑调"。
 * 作息表编辑器正是踩了这个坑。现全部并到主色族：这两族在本 App 里只用于
 * "另有强调"，不承担独立语义，跟随主题色即可（需要次级强调时用 alpha 降权）。
 */
@Composable
fun glasenseM3Scheme(
    // 允许显式传入调色板：主题注入处会在 Glasense 中性色板基础上叠加"用户所选主题色"作为强调色
    g: com.nevoit.glasense.theme.GlasenseColors = GlasenseTheme.colors,
    dark: Boolean = GlasenseTheme.darkTheme
): ColorScheme {
    val neutralSurface = if (dark) Gray800 else Gray200
    return if (dark) {
        darkColorScheme(
            primary = g.primary,
            onPrimary = g.onPrimary,
            primaryContainer = g.primary.copy(alpha = 0.16f),
            onPrimaryContainer = g.primary,
            // 补齐 M3 其余容器/描边槽位：不补会掉回 M3 默认色（紫系），与 Glasense 中性系打架
            // （SegmentedSwitcher 的选中块就踩过这个坑）
            secondary = g.primary,
            onSecondary = g.onPrimary,
            secondaryContainer = g.primary.copy(alpha = 0.14f),
            onSecondaryContainer = g.primary,
            tertiary = g.primary,
            onTertiary = g.onPrimary,
            tertiaryContainer = g.primary.copy(alpha = 0.14f),
            onTertiaryContainer = g.primary,
            outline = g.scrimBold,
            surfaceContainerLowest = g.pageBackground,
            surfaceTint = g.primary,
            inverseSurface = g.content,
            inverseOnSurface = g.pageBackground,
            inversePrimary = g.primary,
            background = g.pageBackground,
            onBackground = g.content,
            surface = g.cardBackground,
            onSurface = g.content,
            surfaceVariant = neutralSurface,
            onSurfaceVariant = g.contentVariant,
            surfaceContainer = g.cardBackground,
            surfaceContainerLow = g.cardBackground,
            surfaceContainerHigh = g.elevatedCardBackground,
            surfaceContainerHighest = g.elevatedCardBackground,
            outlineVariant = g.scrimBold,
            error = g.error,
            onError = g.onError
        )
    } else {
        lightColorScheme(
            primary = g.primary,
            onPrimary = g.onPrimary,
            primaryContainer = g.primary.copy(alpha = 0.16f),
            onPrimaryContainer = g.primary,
            // 补齐 M3 其余容器/描边槽位：不补会掉回 M3 默认色（紫系），与 Glasense 中性系打架
            // （SegmentedSwitcher 的选中块就踩过这个坑）
            secondary = g.primary,
            onSecondary = g.onPrimary,
            secondaryContainer = g.primary.copy(alpha = 0.14f),
            onSecondaryContainer = g.primary,
            tertiary = g.primary,
            onTertiary = g.onPrimary,
            tertiaryContainer = g.primary.copy(alpha = 0.14f),
            onTertiaryContainer = g.primary,
            outline = g.scrimBold,
            surfaceContainerLowest = g.pageBackground,
            surfaceTint = g.primary,
            inverseSurface = g.content,
            inverseOnSurface = g.pageBackground,
            inversePrimary = g.primary,
            background = g.pageBackground,
            onBackground = g.content,
            surface = g.cardBackground,
            onSurface = g.content,
            surfaceVariant = neutralSurface,
            onSurfaceVariant = g.contentVariant,
            surfaceContainer = g.cardBackground,
            surfaceContainerLow = g.cardBackground,
            surfaceContainerHigh = g.elevatedCardBackground,
            surfaceContainerHighest = g.elevatedCardBackground,
            outlineVariant = g.scrimBold,
            error = g.error,
            onError = g.onError
        )
    }
}
