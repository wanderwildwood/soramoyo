package org.ok1cdj.kradar.map

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Web Mercator (EPSG:3857 / slippy-map) projection matching RainViewer's widget
 * tile `{host}{path}/{size}/{z}/{lat}/{lon}/...png`.
 *
 * The tile is a [sizePx] × [sizePx] image, centered on ([centerLat], [centerLon]),
 * rendered in Web Mercator at slippy zoom [zoom]. Projecting the vector map with
 * the SAME math is what keeps borders/cities locked to the radar raster — this is
 * deliberately NOT the azimuthal projection MeteoPlaneRadar uses on the ESP32.
 *
 * Pixel coordinates returned by [project] are in image space: (0,0) top-left,
 * ([sizePx],[sizePx]) bottom-right. The UI scales image space to the on-screen
 * canvas, and applies the same scale to the raster, so both stay aligned.
 */
class MapProjection(
    val centerLat: Double,
    val centerLon: Double,
    val zoom: Int,
    val sizePx: Int,
) {
    // Width of the whole world in pixels at this zoom (256 px per tile).
    private val worldPx: Double = 256.0 * (1 shl zoom)
    private val half: Double = sizePx / 2.0
    private val centerNx = normX(centerLon)
    private val centerNy = normY(centerLat)

    /** Project geographic lat/lon to image-space pixels (may fall outside 0..sizePx). */
    fun project(lat: Double, lon: Double): FloatArray {
        val px = half + (normX(lon) - centerNx) * worldPx
        val py = half + (normY(lat) - centerNy) * worldPx
        return floatArrayOf(px.toFloat(), py.toFloat())
    }

    /**
     * Geographic center after panning the view by ([dxPx], [dyPx]) IMAGE-space
     * pixels (dragging the map right/down moves the center left/up). Returns
     * [lat, lon]; used by the debug drag-to-pan. Reuses the same Mercator math so
     * the pan is exact at any latitude.
     */
    fun centerAfterPan(dxPx: Double, dyPx: Double): DoubleArray {
        val nx = centerNx - dxPx / worldPx
        val ny = centerNy - dyPx / worldPx
        return doubleArrayOf(invLat(ny), invLon(nx))
    }

    /** Geographic bounding box currently visible in the image, for cheap culling. */
    fun visibleBounds(): Bounds {
        val latN = invLat(centerNy - half / worldPx) // top edge (py = 0)
        val latS = invLat(centerNy + half / worldPx) // bottom edge (py = sizePx)
        val lonW = invLon(centerNx - half / worldPx) // left edge  (px = 0)
        val lonE = invLon(centerNx + half / worldPx) // right edge (px = sizePx)
        return Bounds(latS = latS, latN = latN, lonW = lonW, lonE = lonE)
    }

    private fun normX(lon: Double): Double = (lon + 180.0) / 360.0

    private fun normY(lat: Double): Double {
        val r = lat * PI / 180.0
        return (1.0 - ln(tan(r) + 1.0 / kotlin.math.cos(r)) / PI) / 2.0
    }

    private fun invLon(nx: Double): Double = nx * 360.0 - 180.0

    private fun invLat(ny: Double): Double {
        val r = atan(sinh(PI * (1.0 - 2.0 * ny)))
        return r * 180.0 / PI
    }
}

/** Simple lat/lon bounding box (a small margin is added by callers when culling). */
data class Bounds(val latS: Double, val latN: Double, val lonW: Double, val lonE: Double) {
    fun contains(lat: Double, lon: Double, margin: Double = 0.0): Boolean =
        lat in (latS - margin)..(latN + margin) && lon in (lonW - margin)..(lonE + margin)
}
