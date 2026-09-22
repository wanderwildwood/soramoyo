package org.ok1cdj.kradar.motion

import org.ok1cdj.kradar.map.MapProjection
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Local "nowcast" by radar extrapolation (advection): estimate a single global
 * cloud-motion vector from the last few past frames, then push the latest frame
 * forward along it to synthesize forecast frames.
 *
 * This is deliberately the simplest useful model — one translation vector for the
 * whole frame. It only moves what is already on screen; it cannot predict rain
 * forming, decaying, or turning, and it degrades toward the upwind edge (inflow
 * from outside the tile is unknown). Good for ~30 min; label it an estimate.
 *
 * We use it only because RainViewer's free/keyless API serves no `nowcast` frames.
 */
object CloudMotion {

    /** Resolution the correlation search runs at (downsampled from the 512² tile). */
    private const val CORR = 128

    /** Search half-window in [CORR]-space px; ×(tile/CORR) is the max px/step. */
    private const val SEARCH_R = 12

    /** Below this peak correlation the motion is untrustworthy — emit nothing. */
    private const val MIN_CONFIDENCE = 0.35f

    /** Skip when the latest frame is nearly dry (nothing to advect). */
    private const val MIN_WET = 0.004f

    /** RainViewer past-frame cadence (seconds); also the spacing of synthesized frames. */
    const val STEP_SECONDS = 600L
    private const val STEP_TOL = 180L

    /** Cap how many consecutive pairs feed the median (recency-weighted). */
    private const val MAX_PAIRS = 3

    /** A per-step motion vector, in FULL-RES (tile) pixels per 10-minute step. */
    data class Vector(val dx: Float, val dy: Float, val confidence: Float) {

        /** Ground speed in km/h at the given map center. */
        fun speedKmh(lat: Double, zoom: Int): Double {
            val distM = hypot(dx.toDouble(), dy.toDouble()) * MapProjection.metersPerPixel(lat, zoom)
            return distM / STEP_SECONDS * 3.6
        }

        /** Compass bearing (deg, clockwise from north) the rain is moving TOWARD. */
        fun bearingDeg(): Double {
            // Image space: +x = east, +y = south.
            val east = dx.toDouble()
            val north = -dy.toDouble()
            var deg = Math.toDegrees(atan2(east, north))
            if (deg < 0) deg += 360.0
            return deg
        }
    }

    /**
     * Estimate the motion vector from [fields]/[times] (oldest → newest, past
     * frames only). Returns null when there's too little rain, too few usable
     * pairs, or the correlation is too weak to trust.
     */
    fun estimate(fields: List<IntensityField>, times: List<Long>): Vector? {
        val n = fields.size
        if (n < 2 || times.size != n) return null
        if (fields[n - 1].wetFraction() < MIN_WET) return null

        val scale = fields[n - 1].w.toFloat() / CORR // 512/128 = 4
        val cache = HashMap<Int, FloatArray>()
        fun ds(i: Int) = cache.getOrPut(i) { fields[i].downsample(CORR) }

        val dxs = ArrayList<Float>()
        val dys = ArrayList<Float>()
        val confs = ArrayList<Float>()
        var i = n - 1
        while (i >= 1 && dxs.size < MAX_PAIRS) {
            val dt = times[i] - times[i - 1]
            if (dt in (STEP_SECONDS - STEP_TOL)..(STEP_SECONDS + STEP_TOL)) {
                correlate(ds(i - 1), ds(i), CORR)?.let { (sx, sy, peak) ->
                    // Gate PER PAIR: a weak/degenerate correlation (e.g. an all-dry
                    // earlier frame) must not enter the median, or with an even pair
                    // count the average would drag the vector toward garbage while
                    // the median confidence still cleared the bar.
                    if (peak >= MIN_CONFIDENCE) {
                        dxs.add(sx * scale)
                        dys.add(sy * scale)
                        confs.add(peak)
                    }
                }
            }
            i--
        }
        if (dxs.isEmpty()) return null
        return Vector(median(dxs), median(dys), median(confs))
    }

    /**
     * Push [last] forward [steps] time-steps along [v], producing forecast fields
     * (+10, +20, … min). Pixels advected in from outside the tile are left dry.
     */
    fun extrapolate(last: IntensityField, v: Vector, steps: Int): List<IntensityField> {
        val w = last.w
        val h = last.h
        val out = ArrayList<IntensityField>(steps)
        for (k in 1..steps) {
            val dx = Math.round(v.dx * k)
            val dy = Math.round(v.dy * k)
            val a = ByteArray(w * h) // 0 = no rain (exposed edges stay dry)
            // pred[x, y] = last[x - dx, y - dy]
            val xStart = maxOf(0, dx)
            val xEnd = minOf(w, w + dx)
            val yStart = maxOf(0, dy)
            val yEnd = minOf(h, h + dy)
            var y = yStart
            while (y < yEnd) {
                val dst = y * w
                val src = (y - dy) * w
                var x = xStart
                while (x < xEnd) {
                    a[dst + x] = last.alpha[src + (x - dx)]
                    x++
                }
                y++
            }
            out.add(IntensityField(w, h, a))
        }
        return out
    }

    /**
     * Best integer shift (sx, sy) such that B ≈ A shifted by (sx, sy), refined to
     * sub-pixel by a parabolic fit on the correlation surface. Returns the shift
     * plus the peak normalized cross-correlation (0..1), or null.
     */
    private fun correlate(a: FloatArray, b: FloatArray, w: Int): Triple<Float, Float, Float>? {
        val r = SEARCH_R
        val span = 2 * r + 1
        val surf = FloatArray(span * span) { -2f }
        var bestVal = -2f
        var bestI = 0
        var bestJ = 0
        for (sy in -r..r) {
            for (sx in -r..r) {
                val ncc = nccAt(a, b, w, sx, sy)
                surf[(sy + r) * span + (sx + r)] = ncc
                if (ncc > bestVal) {
                    bestVal = ncc
                    bestI = sx
                    bestJ = sy
                }
            }
        }
        // Every shift returned the -1f "no signal" sentinel (uniform/dry overlap):
        // reject rather than emit the search-window corner as a bogus shift.
        if (bestVal <= -1f + 1e-4f) return null

        var fx = bestI.toFloat()
        var fy = bestJ.toFloat()
        if (bestI > -r && bestI < r) {
            val l = surf[(bestJ + r) * span + (bestI - 1 + r)]
            val rr = surf[(bestJ + r) * span + (bestI + 1 + r)]
            val denom = l - 2 * bestVal + rr
            if (denom < 0f) fx = bestI + 0.5f * (l - rr) / denom
        }
        if (bestJ > -r && bestJ < r) {
            val u = surf[(bestJ - 1 + r) * span + (bestI + r)]
            val d = surf[(bestJ + 1 + r) * span + (bestI + r)]
            val denom = u - 2 * bestVal + d
            if (denom < 0f) fy = bestJ + 0.5f * (u - d) / denom
        }
        return Triple(fx, fy, bestVal.coerceIn(0f, 1f))
    }

    /** Normalized cross-correlation of A shifted by (sx, sy) against B, over overlap. */
    private fun nccAt(a: FloatArray, b: FloatArray, w: Int, sx: Int, sy: Int): Float {
        var sa = 0.0
        var sb = 0.0
        var saa = 0.0
        var sbb = 0.0
        var sab = 0.0
        var nn = 0
        // x - sx in [0, w) → x in [sx, w + sx); intersect with [0, w).
        val x0 = maxOf(0, sx)
        val x1 = minOf(w, w + sx)
        val y0 = maxOf(0, sy)
        val y1 = minOf(w, w + sy)
        var y = y0
        while (y < y1) {
            val by = y * w
            val ay = (y - sy) * w
            var x = x0
            while (x < x1) {
                val av = a[ay + (x - sx)].toDouble()
                val bv = b[by + x].toDouble()
                sa += av; sb += bv
                saa += av * av; sbb += bv * bv
                sab += av * bv
                nn++
                x++
            }
            y++
        }
        if (nn < w) return -1f // require a minimum overlap
        val num = nn * sab - sa * sb
        val den = sqrt((nn * saa - sa * sa) * (nn * sbb - sb * sb))
        if (den <= 1e-6) return -1f // uniform (dry) region: no signal
        return (num / den).toFloat()
    }

    private fun median(v: List<Float>): Float {
        val s = v.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 1) s[m] else (s[m - 1] + s[m]) / 2f
    }
}
