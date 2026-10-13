package com.imsx3d.classy.data

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext
import kotlin.math.roundToInt

/** Cached, bounded static blur. Slider changes blend layers without decoding/blur per frame. */
class BackgroundSoftening : Transformation {
    override val cacheKey = "classy-background-softening-v1"
    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val scale = minOf(1f, 512f / maxOf(input.width, input.height))
        val w = (input.width * scale).roundToInt().coerceAtLeast(1)
        val h = (input.height * scale).roundToInt().coerceAtLeast(1)
        val small = Bitmap.createScaledBitmap(input, w, h, true)
        var pixels = IntArray(w * h)
        small.getPixels(pixels, 0, w, 0, 0, w, h)
        if (small !== input) small.recycle()
        repeat(3) {
            coroutineContext.ensureActive()
            pixels = pass(pixels, w, h, horizontal = true)
            pixels = pass(pixels, w, h, horizontal = false)
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    private suspend fun pass(src: IntArray, w: Int, h: Int, horizontal: Boolean): IntArray {
        val out = IntArray(src.size)
        val radius = 12
        val count = radius * 2 + 1
        val length = if (horizontal) w else h
        val lines = if (horizontal) h else w
        for (line in 0 until lines) {
            coroutineContext.ensureActive()
            fun index(pos: Int) = if (horizontal) line * w + pos.coerceIn(0, length - 1)
                else pos.coerceIn(0, length - 1) * w + line
            var r = 0; var g = 0; var b = 0
            for (i in -radius..radius) {
                val p = src[index(i)]; r += p ushr 16 and 255; g += p ushr 8 and 255; b += p and 255
            }
            for (i in 0 until length) {
                out[index(i)] = (255 shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                val old = src[index(i - radius)]; val next = src[index(i + radius + 1)]
                r += (next ushr 16 and 255) - (old ushr 16 and 255)
                g += (next ushr 8 and 255) - (old ushr 8 and 255)
                b += (next and 255) - (old and 255)
            }
        }
        return out
    }
}
