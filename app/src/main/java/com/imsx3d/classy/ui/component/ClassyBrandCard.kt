package com.imsx3d.classy.ui.component

import android.animation.ValueAnimator
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.input.key.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.imsx3d.classy.R
import com.imsx3d.classy.ui.theme.SleepyTheme

/** A brand object, not a navigation button. Observes presses without taking list scroll gestures. */
@Composable
fun ClassyBrandCard(modifier: Modifier = Modifier) {
    // Brand-specific inks echo the cyan/green app mark; kept local to this artwork.
    val ink = Color(0xFF082E39)
    val mint = Color(0xFF7BF7D3)
    val cyan = Color(0xFF65DFF6)
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
    var target by remember { mutableStateOf(Offset.Zero) }
    val x by animateFloatAsState(target.x, sleepyIndicatorSpec(), label = "brand tilt x")
    val y by animateFloatAsState(target.y, sleepyIndicatorSpec(), label = "brand tilt y")
    val description = stringResource(R.string.about_brand_description)
    val actionLabel = stringResource(R.string.about_brand_action)
    Box(modifier.fillMaxWidth().testTag("classy-brand-card")
        .semantics(mergeDescendants = true) {
            contentDescription = description
            customActions = listOf(CustomAccessibilityAction(actionLabel) { sparkle(); true })
        }
        .onKeyEvent {
            if ((it.key == Key.Enter || it.key == Key.Spacebar) && it.type == KeyEventType.KeyUp) {
                sparkle(); true
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
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null && !change.pressed && !change.isConsumed &&
                            change.position.x in 0f..size.width.toFloat() && change.position.y in 0f..size.height.toFloat()) sparkle()
                        if (change == null || !change.pressed || change.isConsumed ||
                            event.changes.count { it.pressed } > 1 ||
                            change.position.x !in 0f..size.width.toFloat() ||
                            change.position.y !in 0f..size.height.toFloat()) break
                        tilt(change.position)
                    } while (true)
                    pressed = false
                    target = Offset.Zero
                }
            } finally { pressed = false; target = Offset.Zero }
        }
        .graphicsLayer { rotationX = x; rotationY = y; scaleX = scale; scaleY = scale; cameraDistance = 24 * density }
        .clip(SleepyTheme.shapes.large)
        .background(Brush.linearGradient(listOf(ink, Color(0xFF125967), Color(0xFF177B73))))
        .border(1.dp, Brush.linearGradient(listOf(cyan.copy(alpha = .65f), mint.copy(alpha = .12f))), SleepyTheme.shapes.large)) {
        Canvas(Modifier.matchParentSize()) {
            drawCircle(Brush.radialGradient(listOf(mint.copy(alpha = glow), Color.Transparent),
                center = Offset(size.width * lightX, size.height * lightY), radius = size.maxDimension * .8f),
                radius = size.maxDimension)
            val beam = size.width * sweep.value
            if (sweep.value < 1.35f) drawRect(Brush.linearGradient(
                listOf(Color.Transparent, cyan.copy(alpha = .22f), Color.Transparent),
                start = Offset(beam - size.width * .25f, 0f), end = Offset(beam + size.width * .25f, size.height)))
            val cell = 28.dp.toPx()
            val gap = 9.dp.toPx()
            val origin = Offset(size.width - cell * 3 - gap * 2 - 22.dp.toPx(), 32.dp.toPx())
            for (row in 0..2) for (col in 0..2) {
                drawRoundRect(mint.copy(alpha = if (row == col) .38f else .16f),
                    origin + Offset(col * (cell + gap), row * (cell + gap)), Size(cell, cell), CornerRadius(8.dp.toPx()),
                    style = Stroke(1.2.dp.toPx()))
            }
        }
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Vector rendition of the app mark avoids the launcher's opaque white backing.
            Canvas(Modifier.size(88.dp).graphicsLayer { translationX = y * .8f; translationY = -x * .8f }) {
                val inset = 5.dp.toPx()
                val extent = Size(size.width - inset * 2, size.height - inset * 2)
                drawRoundRect(Brush.linearGradient(listOf(Color(0xFF08C5EA), Color(0xFF43DB87))),
                    Offset(inset, inset), extent, CornerRadius(22.dp.toPx()))
                drawRoundRect(Color.White.copy(alpha = .4f), Offset(inset, inset), extent,
                    CornerRadius(22.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                for (i in 0..1) drawCircle(Color.White.copy(alpha = .65f), 2.8.dp.toPx(),
                    Offset(size.width * (if (i == 0) .32f else .68f), size.height * .23f))
                val unit = size.width * .235f
                for (row in 0..1) for (col in 0..1) {
                    val origin = Offset(size.width * .23f + col * unit * 1.3f, size.height * .36f + row * unit * 1.3f)
                    drawRoundRect(Color.White.copy(alpha = if (row == 0 && col == 0) .94f else .18f),
                        origin, Size(unit, unit), CornerRadius(6.dp.toPx()))
                    if (row != 0 || col != 0) drawRoundRect(Color.White.copy(alpha = .32f), origin,
                        Size(unit, unit), CornerRadius(6.dp.toPx()), style = Stroke(1.dp.toPx()))
                }
            }
            Text("Classy", style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.SemiBold), color = Color.White)
            Text(stringResource(R.string.about_brand_tagline), style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFC2EDE7))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.about_brand_hint), style = MaterialTheme.typography.labelMedium,
                color = Color(0xFFC2EDE7))
        }
    }
}
