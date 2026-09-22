package org.ok1cdj.kradar.motion

/**
 * Continuous precipitation-intensity raster for one radar frame: the RainViewer
 * tile's alpha channel (0..255) at full tile resolution, before [org.ok1cdj.kradar.render.EinkConverter]
 * quantizes it to 5 e-ink levels. The motion search needs the continuous signal,
 * so we keep the raw alpha here and quantize only for display.
 *
 * Alpha is stored as a raw [ByteArray] (0..255, read unsigned) to keep ~13 frames
 * cheap in memory (256 KB each at 512²) versus a float grid.
 */
class IntensityField(val w: Int, val h: Int, val alpha: ByteArray) {

    /** Fraction of pixels carrying any rain (alpha at/above [threshold]). */
    fun wetFraction(threshold: Int = 12): Float {
        var wet = 0
        for (b in alpha) if ((b.toInt() and 0xFF) >= threshold) wet++
        return wet.toFloat() / alpha.size
    }

    /**
     * Box-averaged copy at [size]×[size], values normalized to 0..1. Downsampling
     * before correlation both bounds the search cost and suppresses per-pixel noise.
     */
    fun downsample(size: Int): FloatArray {
        val out = FloatArray(size * size)
        for (ty in 0 until size) {
            val y0 = ty * h / size
            val y1 = (((ty + 1) * h / size).coerceAtLeast(y0 + 1)).coerceAtMost(h)
            for (tx in 0 until size) {
                val x0 = tx * w / size
                val x1 = (((tx + 1) * w / size).coerceAtLeast(x0 + 1)).coerceAtMost(w)
                var sum = 0
                var cnt = 0
                var y = y0
                while (y < y1) {
                    val row = y * w
                    var x = x0
                    while (x < x1) {
                        sum += alpha[row + x].toInt() and 0xFF
                        cnt++
                        x++
                    }
                    y++
                }
                out[ty * size + tx] = if (cnt > 0) sum.toFloat() / cnt / 255f else 0f
            }
        }
        return out
    }
}
