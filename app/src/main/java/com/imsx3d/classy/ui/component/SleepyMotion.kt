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
// 动效门面（UI-30a · 规范 `设计规范_动效体系.md`）
//
// 规则：**自研组件不许自己写时长与曲线** —— 一律从这里取。于是"改一处 = 全 app 同步"，
// 红线脚本 `tools/scan_motion.py` 也能查（禁裸 tween/spring、禁不带 spec 的 expand/fade）。
// 取值来源是 M3E 的 `MaterialTheme.motionScheme`（Theme.kt 全局注入 Expressive），
// 所以自研组件与官方组件（按钮 / Switch / 弹窗 / 底部弹层）用的是**同一套曲线** ——
// 这就是"动效统一"的落地方式：不是抄一份参数，而是取同一个源头。
// ─────────────────────────────────────────────────────────────────────────────

/** 折叠 / 展开：尺寸走 spatial 档（空间曲线），透明度走 effects 档（不回弹） */
@Composable
fun sleepyExpandEnter(): EnterTransition =
    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())

@Composable
fun sleepyExpandExit(): ExitTransition =
    shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
        fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())

/** 展开指示（折叠箭头旋转等）—— fast spatial：比内容展开本身更快收住，不抢戏 */
@Composable
fun sleepyIndicatorSpec(): FiniteAnimationSpec<Float> =
    MaterialTheme.motionScheme.fastSpatialSpec()

/**
 * 加载圈的自转周期。**全库唯一写死的时长**，理由：它是无限循环的匀速转动，
 * 没有起点终点、不属于"状态过渡"，motionScheme 的有限 spec 不适用。
 * 1100ms 是试出来的：再快像卡住，再慢像卡顿。
 */
const val SleepySpinMillis = 1100
