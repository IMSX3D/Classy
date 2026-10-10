package com.imsx3d.classy.ui.component

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * [intentional custom] 官方 MotionScheme.expressive() 已是全局默认(MD3E), 但其空间类
 * spring 带回弹(MediumBouncy) — thumb 类「跟手不弹」交互是用户实测定参
 * (高硬度+无回弹, MediumLow 拖沓不跟手), 官方 motionScheme 无对应档位。
 * 使用处: SegmentedSwitcher thumb / PillNavigationBar 贴底+Dock thumb(均为薄层组件)。
 * 注释里的「同款」即指此常量; 改参数三处同步生效。
 */
val SleepyThumbSpring = spring<Float>(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessHigh
)

// ─────────────────────────────────────────────────────────────────────────────
// 动效门面（设计原则见 docs/design/DESIGN_SYSTEM.md）
//
// 规则：**自研组件不许自己写时长与曲线** —— 一律从这里取。于是"改一处 = 全 app 同步"，
// 导航的预测性返回另由 SleepyMaterialTransition 维护，不在业务页新增独立曲线。
// 透明度继续取 M3E effects；内容尺寸与指示器明确采用无回弹 spring，
// 官方按钮 / Switch / 弹窗 / 底部弹层保留 Material 自己的交互与曲线，
// 统一的是交互职责与同类组件入口，不强制所有动画使用同一条曲线。
// ─────────────────────────────────────────────────────────────────────────────

/** Content size must settle without bouncing text; keep effects on the shared Material scheme. */
fun <T> sleepyContentSizeSpec(): FiniteAnimationSpec<T> = spring(
    dampingRatio = Spring.DampingRatioNoBouncy,
    stiffness = Spring.StiffnessMedium
)

/** 折叠 / 展开：尺寸不回弹，透明度继续使用主题 effects。 */
@Composable
fun sleepyExpandEnter(): EnterTransition =
    expandVertically(sleepyContentSizeSpec()) +
        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())

@Composable
fun sleepyExpandExit(): ExitTransition =
    shrinkVertically(sleepyContentSizeSpec()) +
        fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())

/** 展开指示与选择滑块共用快速无回弹响应。 */
@Composable
fun sleepyIndicatorSpec(): FiniteAnimationSpec<Float> =
    SleepyThumbSpring

/**
 * 加载圈的自转周期。它是无限循环的匀速转动，
 * 没有起点终点、不属于"状态过渡"，motionScheme 的有限 spec 不适用。
 * 1100ms 是试出来的：再快像卡住，再慢像卡顿。
 */
const val SleepySpinMillis = 1100
