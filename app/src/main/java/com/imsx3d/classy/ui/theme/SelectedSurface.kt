package com.imsx3d.classy.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.imsx3d.classy.util.TodayHighlight
import com.nevoit.glasense.theme.GlasenseTheme

/**
 * 「选中面」取色 —— App 里所有"选中/当前"状态的大色块（周选择器的当前周、加课的星期、
 * 主题模式分段、作息表的位置卡、网格表头的今天…）统一走这里。
 *
 * ## 为什么不能一律用 `primary` 实心
 * 深色主题里 **`primary` 是浅色调**：M3 基线深色 primary 本身就是浅色（浅紫 #D0BCFF），
 * 而本 App 的 `CustomSchemeDeriver` 按"保留模板 S/V、只换色相"派生 → 用户的主题色在深色下
 * 变成**亮青**（实测 `#85D2E7`）；同时深色的 `onPrimary` 近黑。于是"实心主色 + onPrimary 字"
 * 在深色下＝**暗色页面上一块高亮**（刺眼），而且黑字压在亮块上会被高光吃掉笔画（发糊）。
 * 浅色主题恰好相反（primary 深、onPrimary 白），所以浅色一直没问题。
 *
 * ## 规则
 * · 浅色：实心 `primary` + `onPrimary` 字 —— 用户定稿"很有确认感、不刺眼"，不动。
 * · 深色：`primary` @[TodayHighlight.DARK_PILL_ALPHA] 当底（叠在卡片上 ≈ 暗青块，
 *   看得清"选中"但不刺眼）+ **`primary` 本身当字色**（亮青字压暗青底）——
 *   即 M3 的「container / onContainer」语义，也是网格组件表头胶囊用的同一套。
 *
 * 数值集中在 `util/TodayHighlight.kt`（与 RemoteViews 组件端共用，改一处两端一起变）。
 */
@Immutable
data class SelectedSurfaceColors(
    /** 选中块底色。 */
    val container: Color,
    /** 选中块上的文字/图标色。 */
    val content: Color,
    /** 选中块内的次级小块（角标、日期方章等）。深色下与 container 同色 = 不再叠一层，
     *  因为叠加会压低对比度（实测叠一层后只剩 3:1）；深色直接让次级元素浮在 container 上。 */
    val inner: Color,
    /** 是否处于深色分支（调用方需要额外判断时可读）。 */
    val isDark: Boolean
) {
    companion object {
        /** 深色分支标记：`inner` 等于容器色时不画背景。 */
        fun innerIsTransparent(inner: Color, container: Color): Boolean = inner == container
    }
}

/**
 * 当前的「选中面」取色。用法：
 * ```
 * val sel = selectedSurfaceColors()
 * Modifier.background(if (selected) sel.container else colors.surfaceContainerHigh)
 * Text(..., color = if (selected) sel.content else colors.onSurface)
 * ```
 */
@Composable
fun selectedSurfaceColors(): SelectedSurfaceColors {
    val colors = MaterialTheme.colorScheme
    return if (GlasenseTheme.darkTheme) {
        val container = colors.primary.copy(alpha = TodayHighlight.DARK_PILL_ALPHA)
        SelectedSurfaceColors(
            container = container,
            content = colors.primary,
            inner = container,
            isDark = true
        )
    } else {
        SelectedSurfaceColors(
            container = colors.primary,
            content = colors.onPrimary,
            inner = colors.onPrimary.copy(alpha = TodayHighlight.LIGHT_INNER_ALPHA),
            isDark = false
        )
    }
}

/**
 * **实心主行动按钮**的配色（M3 `Button` 的 `colors =` 参数）。UI-4t（2026-09-26 用户报障
 * 「黑夜模式下刷新组件的按钮有些刺眼」）。
 *
 * 背景：M3 的 filled button 在深色主题里本来就该用"浅色调容器 + 深色字"（基线深色 primary
 * 就是浅紫），但那在本 App 的配色下会变成暗色页面上一整条**亮青色**——用户明确嫌刺眼。
 * 且 Glasense 自己的深色板用的是 `primary = Blue500`（中间调）+ 白字，**本来就不亮**；
 * 是我们的 `CustomSchemeDeriver` 照抄 M3 模板明度、把深色 primary 提成了亮青，才把按钮也带亮了。
 *
 * 处理：与「选中面」共用同一条规则 —— 浅色实心主色（用户定稿的观感，不动），
 * 深色用容器底 + 主色字（观感≈ M3 的 tonal button，沉稳且依然明显是按钮）。
 * 想要回到 M3 那种"深色下亮块按钮"，把本函数改成返回 `ButtonDefaults.buttonColors()` 即可。
 */
@Composable
fun primaryFilledButtonColors(): androidx.compose.material3.ButtonColors =
    androidx.compose.material3.ButtonDefaults.buttonColors(
        containerColor = selectedSurfaceColors().container,
        contentColor = selectedSurfaceColors().content
    )

