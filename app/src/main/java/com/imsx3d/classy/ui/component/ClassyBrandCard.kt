package com.imsx3d.classy.ui.component

import android.animation.ValueAnimator
import androidx.compose.material.icons.outlined.Email
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.theme.SleepyTheme

/** A brand object, not a navigation button. Observes presses without taking list scroll gestures. */
@Composable
fun ClassyBrandCard(modifier: Modifier = Modifier, onOpenLetter: () -> Unit = {}) {
    // Brand-specific inks echo the cyan/green app mark; kept local to this artwork.
    val ink = Color(0xFF090D24)
    val mint = Color(0xFF90FFE2)
    val cyan = Color(0xFF96B6FF)
    val scope = rememberCoroutineScope()
    val sweep = remember { Animatable(1.4f) }
    var sweepJob by remember { mutableStateOf<Job?>(null) }
    var pressed by remember { mutableStateOf(false) }
    var light by remember { mutableStateOf(Offset(.7f, .2f)) }
    val lightX by animateFloatAsState(if (pressed) light.x else .7f, sleepyIndicatorSpec(), label = "brand light x")
    val lightY by animateFloatAsState(if (pressed) light.y else .2f, sleepyIndicatorSpec(), label = "brand light y")
    val glow by animateFloatAsState(if (pressed) .32f else .10f, sleepyIndicatorSpec(), label = "brand light")
    val scale by animateFloatAsState(if (pressed) .978f else 1f, sleepyIndicatorSpec(), label = "brand depth")
    val sparkle = {
        if (ValueAnimator.areAnimatorsEnabled()) {
            sweepJob?.cancel()
            sweepJob = scope.launch { sweep.snapTo(-.5f); sweep.animateTo(1.4f, tween(700)) }
        }
    }
    var discovered by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    var celebrating by remember { mutableStateOf(false) }
    var variant by remember { mutableIntStateOf(0) }
    val constellation = remember { Animatable(0f) }
    LaunchedEffect(lastTap) {
        kotlinx.coroutines.delay(850)
        if (!celebrating) tapCount = 0
    }
    val tap by rememberUpdatedState<(Long) -> Unit>({ now ->
        if (!celebrating) {
            tapCount = if (now - lastTap in 0..850) tapCount + 1 else 1
            lastTap = now
            if (tapCount >= 5) {
                tapCount = 0
                celebrating = true
                variant = (variant + kotlin.random.Random.nextInt(1, 3)) % 3
                scope.launch {
                    try {
                        sweepJob?.cancel()
                        if (ValueAnimator.areAnimatorsEnabled()) {
                            sweepJob = launch { sweep.snapTo(-.5f); sweep.animateTo(1.4f, tween(1050)) }
                            constellation.snapTo(0f)
                            constellation.animateTo(1f, tween(1100))
                        }
                        discovered = true
                        kotlinx.coroutines.delay(900)
                    } finally {
                        celebrating = false
                        tapCount = 0
                    }
                }
            } else sparkle()
        }
    })
    val gathering by animateFloatAsState(
        if (celebrating) 1f else if (tapCount >= 3) (tapCount - 2) / 3f else 0f,
        tween(180), label = "brand stars gathering")
    var target by remember { mutableStateOf(Offset.Zero) }
    val x by animateFloatAsState(target.x, sleepyIndicatorSpec(), label = "brand tilt x")
    val y by animateFloatAsState(target.y, sleepyIndicatorSpec(), label = "brand tilt y")
    val description = stringResource(R.string.about_brand_description)
    val actionLabel = stringResource(R.string.about_brand_action)
    Box(modifier.fillMaxWidth().testTag("classy-brand-card")
        .semantics(mergeDescendants = true) {
            contentDescription = description
            customActions = listOf(CustomAccessibilityAction(actionLabel) { tap(android.os.SystemClock.uptimeMillis()); true })
        }
        .onKeyEvent {
            if ((it.key == Key.Enter || it.key == Key.Spacebar) && it.type == KeyEventType.KeyUp) {
                tap(android.os.SystemClock.uptimeMillis()); true
            } else false
        }.focusable()
        .pointerInput(Unit) {
            try {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val enabled = ValueAnimator.areAnimatorsEnabled()
                    pressed = enabled
                    fun tilt(position: Offset) {
                        light = Offset(position.x / size.width.coerceAtLeast(1), position.y / size.height.coerceAtLeast(1))
                        target = if (enabled && size.width > 0 && size.height > 0) Offset(
                            -(position.y / size.height * 2 - 1).coerceIn(-1f, 1f) * 10f,
                            (position.x / size.width * 2 - 1).coerceIn(-1f, 1f) * 10f) else Offset.Zero
                    }
                    tilt(down.position)
                    var moved = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        if (change != null && !change.pressed && !change.isConsumed && !moved &&
                            change.uptimeMillis - down.uptimeMillis < 500 && event.changes.size == 1 &&
                            change.position.x in 0f..size.width.toFloat() && change.position.y in 0f..size.height.toFloat()) tap(change.uptimeMillis)
                        if (change == null || !change.pressed || change.isConsumed ||
                            event.changes.count { it.pressed } > 1 ||
                            change.position.x !in 0f..size.width.toFloat() ||
                            change.position.y !in 0f..size.height.toFloat()) break
                        tilt(change.position)
                    } while (true)
                    if (moved) tapCount = 0
                    pressed = false
                    target = Offset.Zero
                }
            } finally { pressed = false; target = Offset.Zero }
        }
        .graphicsLayer { rotationX = x; rotationY = y; scaleX = scale; scaleY = scale; cameraDistance = 24 * density }
        .clip(SleepyTheme.shapes.large)
        .background(Brush.linearGradient(listOf(ink, Color(0xFF19143C), Color(0xFF082D38))))
        .border(1.dp, Brush.linearGradient(listOf(cyan.copy(alpha = .65f), mint.copy(alpha = .12f))), SleepyTheme.shapes.large)) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width
            val h = size.height
            val shiftX = y * density * 1.5f
            val shiftY = -x * density
            drawRect(Brush.radialGradient(listOf(Color(0xFF855CF8).copy(alpha = .42f), Color.Transparent),
                center = Offset(w * .95f + shiftX, h * .08f + shiftY), radius = w * .85f))
            drawRect(Brush.radialGradient(listOf(Color(0xFF00D1B2).copy(alpha = .32f), Color.Transparent),
                center = Offset(w * .08f - shiftX, h * .9f), radius = w * .7f))
            // Layered contour ribbons create depth without a bitmap, blur pass or idle loop.
            translate(left = shiftX, top = shiftY) {
                for (i in 0..25) {
                    val t = i / 25f
                    val ribbon = Path().apply {
                        moveTo(-w * .15f, h * (.92f + t * .3f))
                        cubicTo(w * .15f, h * (.20f + t * .48f), w * .5f, h * (1.08f - t * .4f), w * 1.15f, h * (-.12f + t * .6f))
                    }
                    drawPath(ribbon, Brush.linearGradient(listOf(
                        Color(0xFF49F2D2).copy(alpha = .10f + t * .16f),
                        Color(0xFFB694FF).copy(alpha = .12f + t * .22f),
                        Color(0xFF79CFFF).copy(alpha = .28f))), style = Stroke((.6f + t * .7f).dp.toPx()))
                }
            }
            drawRect(Brush.radialGradient(listOf(mint.copy(alpha = glow), Color.Transparent),
                center = Offset(w * lightX, h * lightY), radius = size.maxDimension * .8f))
            // Finite celebration only: no idle animation or extra bitmap/blur layer.
            val burst = kotlin.math.sin(constellation.value * kotlin.math.PI).toFloat().coerceAtLeast(0f)
            val clue = if (tapCount >= 3) (tapCount - 2) * .18f else 0f
            val stars = when (variant) {
                1 -> listOf(Offset(.12f,.28f), Offset(.30f,.14f), Offset(.53f,.24f), Offset(.73f,.12f), Offset(.88f,.30f))
                2 -> listOf(Offset(.12f,.25f), Offset(.29f,.12f), Offset(.48f,.25f), Offset(.67f,.14f), Offset(.89f,.23f))
                else -> listOf(Offset(.10f,.22f), Offset(.30f,.28f), Offset(.48f,.12f), Offset(.70f,.25f), Offset(.90f,.16f))
            }
            val points = stars.map {
                Offset((.5f + (it.x - .5f) * (1.25f - .25f * gathering)) * w, it.y * h)
            }
            if (burst > 0f) {
                points.zipWithNext().forEach { (a, b) -> drawLine(mint.copy(alpha = burst * .65f), a, b, 1.dp.toPx()) }
                drawRect(Brush.radialGradient(listOf(cyan.copy(alpha = burst * .28f), Color.Transparent),
                    center = Offset(w * .5f, h * .25f), radius = w * .65f))
            }
            points.forEach { point ->
                val alpha = maxOf(clue, burst)
                if (alpha > 0f) {
                    drawCircle(mint.copy(alpha = alpha * .16f), 8.dp.toPx(), point)
                    drawCircle(Color.White.copy(alpha = alpha), 2.dp.toPx(), point)
                }
            }
            val beam = w * sweep.value
            if (sweep.value < 1.35f) drawRect(Brush.linearGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = .20f), Color.Transparent),
                start = Offset(beam - w * .25f, 0f), end = Offset(beam + w * .25f, h)))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(12.dp))
            BrandWordmark(Modifier.fillMaxWidth().aspectRatio(2.8f)
                .graphicsLayer { translationX = y * .6f; translationY = -x * .6f }, shimmer = { constellation.value })
            Text(stringResource(R.string.about_brand_tagline), style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD8E2F4), textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
            if (discovered) {
                androidx.compose.material3.TextButton(onClick = onOpenLetter,
                    modifier = Modifier.testTag("brand-secret-letter")) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Email,
                        null, tint = mint, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.about_brand_discovery), color = mint,
                        textAlign = TextAlign.Center)
                }
            } else {
                Text(stringResource(R.string.about_brand_hint), style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFFB2C5D8), textAlign = TextAlign.Center)
            }
            }
        }
    }
}

/** A fitted typographic mark: large italic serif lettering with a pearl-like ink gradient. */
@Composable
private fun BrandWordmark(modifier: Modifier, shimmer: () -> Float) {
    val paint = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = android.graphics.Typeface.create("serif", android.graphics.Typeface.BOLD_ITALIC)
        }
    }
    Canvas(modifier) {
        paint.textSize = size.height * .84f
        val bounds = android.graphics.Rect()
        paint.getTextBounds("Classy", 0, 6, bounds)
        val factor = minOf(1f, size.width * .94f / bounds.width().coerceAtLeast(1))
        paint.textSize *= factor
        paint.getTextBounds("Classy", 0, 6, bounds)
        val flash = kotlin.math.sin(shimmer() * kotlin.math.PI).toFloat().coerceAtLeast(0f)
        paint.shader = android.graphics.LinearGradient(0f, 0f, size.width, size.height,
            intArrayOf(
                androidx.compose.ui.graphics.lerp(Color.White, Color(0xFFB5FFE8), flash).toArgb(),
                androidx.compose.ui.graphics.lerp(Color(0xFFE3D9FF), Color(0xFFFFB4EA), flash).toArgb(),
                androidx.compose.ui.graphics.lerp(Color(0xFFB5FFE8), Color(0xFF96B6FF), flash).toArgb()),
            null, android.graphics.Shader.TileMode.CLAMP)
        val left = (size.width - bounds.width()) / 2f - bounds.left
        val baseline = (size.height - bounds.height()) / 2f - bounds.top
        drawContext.canvas.nativeCanvas.drawText("Classy", left, baseline, paint)
    }
}
