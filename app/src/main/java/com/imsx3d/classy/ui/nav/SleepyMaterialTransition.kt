package com.imsx3d.classy.ui.nav

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.graphics.TransformOrigin

/**
 * Navigation3 owns seek, cancellation and commit of predictive back. This file supplies
 * the finite transforms only; no second animation or delayed navigation callback runs here.
 * The existing 220ms / 0.92 scale baseline is retained pending an on-device comparison.
 * Predictive callback input is swipe edge, not a progress value to animate ourselves.
 */

internal const val ForwardDuration: Int = 220
internal const val ForwardFadeIn: Int = 110
internal const val PredictiveDuration: Int = 200

private val forwardTween: TweenSpec<Float> = tween(
    durationMillis = ForwardDuration,
    easing = FastOutSlowInEasing,
)

private val forwardFadeInTween: TweenSpec<Float> = tween(
    durationMillis = ForwardFadeIn,
    easing = FastOutSlowInEasing,
)

private val predictiveCommitTween: TweenSpec<Float> = tween(
    durationMillis = PredictiveDuration,
    easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
)
internal val PredictiveTweenRef: TweenSpec<Float> = predictiveCommitTween

/**
 * forward (push): 入页 fadeIn + 从中心 scaleIn 0.92→1。
 */
internal fun sleepyForwardEnter(): EnterTransition {
    return fadeIn(forwardFadeInTween) + scaleIn(
        animationSpec = forwardTween,
        initialScale = 0.92f,
        transformOrigin = TransformOrigin.Center,
    )
}

/**
 * forward (push): 出页 fadeOut + 向中心 scaleOut 1→0.92。
 */
internal fun sleepyForwardExit(): ExitTransition {
    return fadeOut(forwardTween) + scaleOut(
        animationSpec = forwardTween,
        targetScale = 0.92f,
        transformOrigin = TransformOrigin.Center,
    )
}

/**
 * pop (programmatic, 无手势): 目标页 EnterTransition.None — 用户要求的"目标页原地不动"。
 * 退出页走 scaleOut(0.92, Center) + 220ms tween — 与 forward 对称(都走 0.92 缩放 + 220ms),
 * 与 forward 唯一区别是 forward 入页是 fadeIn+scaleIn,pop 出页是 fadeOut+scaleOut。
 */
internal fun sleepyPopEnter(): EnterTransition {
    return EnterTransition.None
}

internal fun sleepyPopExit(): ExitTransition {
    return fadeOut(forwardTween) + scaleOut(
        animationSpec = forwardTween,
        targetScale = 0.92f,
        transformOrigin = TransformOrigin.Center,
    )
}

/**
 * NavDisplay transitionSpec 入参 — 推进时新页 enter + 旧页 exit 同时启动。
 */
internal val sleepyForwardTransform: ContentTransform = ContentTransform(
    targetContentEnter = sleepyForwardEnter(),
    initialContentExit = sleepyForwardExit(),
    targetContentZIndex = 1f,
)

/**
 * NavDisplay popTransitionSpec 入参 — 程序化 pop。
 */
internal val sleepyPopTransform: ContentTransform = ContentTransform(
    targetContentEnter = sleepyPopEnter(),
    initialContentExit = sleepyPopExit(),
    targetContentZIndex = 1f,
)

/** Seekable template; NavDisplay controls progress and cancellation. */
internal val sleepyPredictivePopTransform: ContentTransform = ContentTransform(
    targetContentEnter = scaleIn(
        animationSpec = predictiveCommitTween,
        initialScale = 0.92f,
        transformOrigin = TransformOrigin.Center,
    ) + fadeIn(predictiveCommitTween),
    initialContentExit = scaleOut(
        animationSpec = predictiveCommitTween,
        targetScale = 0.92f,
        transformOrigin = TransformOrigin.Center,
    ) + fadeOut(predictiveCommitTween),
    targetContentZIndex = 1f,
)
