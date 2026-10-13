package com.imsx3d.classy.ui.component

/** Bounded, analytic bursts. Input cadence controls density; animation time controls lifetime.
 * At saturation retire the oldest burst rather than queueing delayed input or allocating forever.
 * No Android clock dependency: the owner provides its single frame clock and stops it when empty.
 */
internal class BrandFireworks {
    data class Burst(val started: Long, val count: Int, val energy: Float, val variant: Int)
    val bursts = mutableListOf<Burst>()
    fun emit(now: Long, gapMs: Long, initial: Boolean, variant: Int) {
        expire(now)
        val energy = ((850f - gapMs.coerceAtLeast(0)) / 730f).coerceIn(0f, 1f)
        val count = if (initial) 160 else (40 + energy * 88).toInt()
        while (bursts.sumOf { it.count } + count > MAX_PARTICLES) bursts.removeAt(0)
        bursts.add(Burst(now, count, energy, variant))
    }
    fun expire(now: Long) { bursts.removeAll { now - it.started >= LIFETIME_MS } }
    companion object {
        const val LIFETIME_MS = 1800L
        const val MAX_PARTICLES = 1024
    }
}
