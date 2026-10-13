package com.wanderwildwood.soramoyo.render

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.wanderwildwood.soramoyo.motion.IntensityField

/**
 * Converts a RainViewer PNG tile into a banded greyscale overlay for e-ink.
 *
 * Each pixel's colour is read back to its reflectivity (dBZ) through [RadarRamp], then drawn
 * as one of [RadarRamp.NUM_LEVELS] greys: drizzle a light grey, heavier bands darker, only the
 * heaviest cores black. Discrete bands (not a smooth gradient) keep e-ink redraws minimal and
 * each one distinct on a sixteen-grey panel.
 */
object EinkConverter {
    // Unpremultiplied, so a translucent drizzle pixel keeps the exact colour in RainViewer's
    // table rather than a rounded one.
    private val decodeOptions = BitmapFactory.Options().apply { inPremultiplied = false }

    /**
     * Decode [png] once into both the banded e-ink overlay and the continuous
     * [IntensityField] (dBZ) the motion estimator needs. Null on decode failure.
     */
    fun decode(png: ByteArray): Pair<Bitmap, IntensityField>? {
        val src = BitmapFactory.decodeByteArray(png, 0, png.size, decodeOptions) ?: return null
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        src.recycle()

        val intensity = ByteArray(w * h)
        // A tile holds a few dozen colours; look each up once.
        val seen = HashMap<Int, Int>(128)
        for (i in pixels.indices) {
            val c = pixels[i]
            val v = if ((c ushr 24) == 0) 0 else seen.getOrPut(c) { RadarRamp.intensityOf(RadarRamp.dbzOf(c)) }
            intensity[i] = v.toByte()
            pixels[i] = RadarRamp.pixelForIntensity(v)
        }
        val bmp = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        return bmp to IntensityField(w, h, intensity)
    }

    /** Render a (possibly synthesized) [field] to an e-ink overlay with the same bands. */
    fun renderField(field: IntensityField): Bitmap {
        val pixels = IntArray(field.w * field.h)
        for (i in pixels.indices) pixels[i] = RadarRamp.pixelForIntensity(field.intensity[i].toInt() and 0xFF)
        return Bitmap.createBitmap(pixels, field.w, field.h, Bitmap.Config.ARGB_8888)
    }
}
