package com.imsx3d.classy.ui.component

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/** One bounded burst, driven by the card's existing animation. No timer, jobs or idle loop.
 * Deterministic trajectories avoid allocating random particles on every frame.
 */
internal fun DrawScope.drawBrandConfetti(progress: Float, variant: Int, count: Int = 160, energy: Float = 0f) {
    if (progress <= 0f || progress >= 1f) return
    val palette = listOf(Color(0xFFFF6BB5), Color(0xFF8FFFF0), Color(0xFFFFFF80),
        Color(0xFF9D8BFF), Color(0xFFFFA56B), Color.White)
    // Two staggered fans travel across the visible card as well as outside its edges.
    // Fixed particle count and analytic trajectories prevent queues or per-frame random state.
    repeat(count) { index ->
        val delay = (index % 4) * .045f
        val t = ((progress - delay) / (1f - delay)).coerceIn(0f, 1f)
        if (t <= 0f) return@repeat
        val side = if (index % 2 == 0) -1f else 1f
        val spread = ((index * 37 + variant * 13) % 101) / 100f
        val angle = (-155f + spread * 130f) * kotlin.math.PI.toFloat() / 180f
        val reach = size.width.coerceAtMost(620.dp.toPx()) * (.48f + (index % 7) * .07f + energy * .22f)
        val origin = Offset(size.width * if (side < 0) .22f else .78f, size.height * .68f)
        val point = origin + Offset(kotlin.math.cos(angle) * reach * t * side,
            sin(angle) * reach * t + size.height * 1.2f * t * t)
        val alpha = ((1f - t) * 3f).coerceIn(0f, 1f)
        val color = palette[(index + variant) % palette.size].copy(alpha = alpha)
        rotate(side * (index * 31f + t * 620f), point) {
            if (index % 5 == 0) {
                drawCircle(color, (2.5f + spread * 2f).dp.toPx(), point)
            } else {
                drawRoundRect(color, point,
                    Size((3f + spread * 4f).dp.toPx(), (7f + spread * 6f).dp.toPx()),
                    androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
            }
        }
    }
}
