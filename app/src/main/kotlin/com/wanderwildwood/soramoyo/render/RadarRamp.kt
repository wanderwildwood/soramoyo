package com.wanderwildwood.soramoyo.render

/**
 * How hard it is raining, read back out of a RainViewer tile, and the grey that is drawn for it.
 *
 * RainViewer's free API now serves one colour scheme only, Universal Blue, whatever scheme the
 * URL asks for (the raw-dBZ "Black and White" scheme comes back as the same blue tile). So the
 * reflectivity is recovered by looking each pixel up in RainViewer's published colour table
 * (rainviewer.com/files/rainviewer_api_colors_table.csv), one entry per dBZ from -10 to 74, rain
 * and snow alike. Reading alpha alone, as before, saw only the translucent drizzle band: every
 * colour from 15 dBZ up is fully opaque, so all rain heavier than drizzle came out solid black.
 *
 * The greys are spaced three of the panel's sixteen steps apart (or more) so neighbouring bands
 * never dither into each other, and only reflectivity of [BLACK_FROM_DBZ] and above (the red
 * cores, heavy rain and hail) is drawn black.
 *
 * No Android here, so it can be tested on the JVM.
 */
object RadarRamp {
    /** Lowest dBZ in the table; [intensityOf] maps it to 3, and 0 stays "dry". */
    const val MIN_DBZ = -10

    /**
     * Upper dBZ bound (exclusive) of each band, lightest first; past the last is black.
     * Drizzle, light, moderate, heavy, then very heavy.
     */
    private val BAND_TOP_DBZ = intArrayOf(15, 30, 40, 50)

    /** First dBZ drawn black. */
    val BLACK_FROM_DBZ = BAND_TOP_DBZ.last()

    /** The grey (0 black .. 255 white) of each band, on the panel's own 17-step grid. */
    val BAND_GREY = intArrayOf(0xDD, 0xAA, 0x77, 0x44, 0x00)

    /** Number of rain shades. */
    val NUM_LEVELS = BAND_GREY.size

    // Universal Blue as ARGB, index = dBZ - MIN_DBZ.
    private val RAIN = longArrayOf(
        0x14636159, 0x1966635A, 0x1E69665C, 0x246C685D, 0x296F6B5F, 0x2E726E61,
        0x34757062, 0x39787364, 0x3E7C7565, 0x447F7867, 0x49827B69, 0x4E857D6A,
        0x5488806C, 0x598B826D, 0x5E8E856F, 0x64928871, 0x6E9E9375, 0x78AA9E79,
        0x82B6A97E, 0x8CC2B482, 0x96CEC087, 0xA0D2C48B, 0xAAD6C88F, 0xB4DACC93,
        0xBEDED097, 0xFF88DDEE, 0xFF6CD1EB, 0xFF51C5E8, 0xFF36BAE5, 0xFF1BAEE2,
        0xFF00A3E0, 0xFF009AD5, 0xFF0091CA, 0xFF0088BF, 0xFF007FB4, 0xFF0077AA,
        0xFF0070A3, 0xFF00699C, 0xFF006295, 0xFF005B8E, 0xFF005588, 0xFF005180,
        0xFF004E78, 0xFF004A70, 0xFF004768, 0xFFFFEE00, 0xFFFFE000, 0xFFFFD200,
        0xFFFFC500, 0xFFFFB700, 0xFFFFAA00, 0xFFFF9F00, 0xFFFF9500, 0xFFFF8B00,
        0xFFFF8100, 0xFFFF4400, 0xFFF23600, 0xFFE62800, 0xFFD91B00, 0xFFCD0D00,
        0xFFC10000, 0xFFA80000, 0xFF8F0000, 0xFF760000, 0xFF5D0000, 0xFFFFAAFF,
        0xFFFF9FFF, 0xFFFF95FF, 0xFFFF8BFF, 0xFFFF81FF, 0xFFFF77FF, 0xFFFF6CFF,
        0xFFFF62FF, 0xFFFF58FF, 0xFFFF4EFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
        0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFFFFF,
        0xFFFFFFFF,    )
    private val SNOW = longArrayOf(
        0x00CFFFFF, 0x0CCEFFFF, 0x19CDFFFF, 0x26CCFFFF, 0x33CBFFFF, 0x3FCBFFFF,
        0x4CCAFFFF, 0x59C9FFFF, 0x66C8FFFF, 0x72C7FFFF, 0x7FC7FFFF, 0x8CC6FFFF,
        0x99C5FFFF, 0xA5C4FFFF, 0xB2C3FFFF, 0xBFC3FFFF, 0xCCC2FFFF, 0xD8C1FFFF,
        0xE5C0FFFF, 0xF2BFFFFF, 0xFFBFFFFF, 0xFFB8F8FF, 0xFFB2F2FF, 0xFFABEBFF,
        0xFFA5E5FF, 0xFF9FDFFF, 0xFF98D8FF, 0xFF92D2FF, 0xFF8BCBFF, 0xFF85C5FF,
        0xFF7FBFFF, 0xFF78B8FF, 0xFF72B2FF, 0xFF6BABFF, 0xFF65A5FF, 0xFF5F9FFF,
        0xFF5B9BFF, 0xFF5898FF, 0xFF5595FF, 0xFF5292FF, 0xFF4F8FFF, 0xFF4B8BFF,
        0xFF4888FF, 0xFF4585FF, 0xFF4282FF, 0xFF3F7FFF, 0xFF3B7BFF, 0xFF3878FF,
        0xFF3575FF, 0xFF3272FF, 0xFF2F6FFF, 0xFF2B6BFF, 0xFF2868FF, 0xFF2565FF,
        0xFF2262FF, 0xFF1F5FFF, 0xFF1B5BFF, 0xFF1858FF, 0xFF1555FF, 0xFF1252FF,
        0xFF0F4FFF, 0xFF0C4BFF, 0xFF0948FF, 0xFF0645FF, 0xFF0242FF, 0xFF003FFF,
        0xFF003BFF, 0xFF0038FF, 0xFF0035FF, 0xFF0032FF, 0xFF002FFF, 0xFF002BFF,
        0xFF0028FF, 0xFF0025FF, 0xFF0022FF, 0xFF001FFF, 0xFF001BFF, 0xFF0018FF,
        0xFF0015FF, 0xFF0012FF, 0xFF000FFF, 0xFF000CFF, 0xFF0009FF, 0xFF0006FF,
        0xFF0002FF,    )
    /** Universal Blue's top band (75 dBZ and over), past the end of the tables above. */
    private const val OFF_SCALE = 0xFF00FF00L

    private val exact: HashMap<Int, Int> = HashMap<Int, Int>(256).apply {
        // Rain first, so a colour both tables share (none today) reads as rain.
        for (t in arrayOf(RAIN, SNOW)) {
            for (i in t.indices) {
                val c = t[i].toInt()
                if ((c ushr 24) != 0) putIfAbsent(c, MIN_DBZ + i)
            }
        }
        putIfAbsent(OFF_SCALE.toInt(), 75)
    }

    /**
     * The dBZ an unpremultiplied ARGB tile pixel stands for, or null for a dry (transparent)
     * pixel. A colour not in the table (if RainViewer retunes the scheme) takes the nearest
     * entry's value, so the radar degrades rather than going blank.
     */
    fun dbzOf(argb: Int): Int? {
        if ((argb ushr 24) == 0) return null
        exact[argb]?.let { return it }
        var best = MIN_DBZ
        var bestD = Int.MAX_VALUE
        for ((c, d) in exact) {
            val dist = sq((c ushr 24) - (argb ushr 24)) +
                sq(((c shr 16) and 0xFF) - ((argb shr 16) and 0xFF)) +
                sq(((c shr 8) and 0xFF) - ((argb shr 8) and 0xFF)) +
                sq((c and 0xFF) - (argb and 0xFF))
            if (dist < bestD) { bestD = dist; best = d }
        }
        return best
    }

    private fun sq(x: Int) = x * x

    /** Band 0 (drizzle) .. [NUM_LEVELS] - 1 (black) for a reflectivity. */
    fun levelOf(dbz: Int): Int {
        var l = 0
        while (l < BAND_TOP_DBZ.size && dbz >= BAND_TOP_DBZ[l]) l++
        return l
    }

    /**
     * dBZ packed into the byte the motion estimator keeps (0 = dry, then 3 per dBZ from
     * [MIN_DBZ]), so the forecast frames it shifts along can be drawn with the same greys.
     */
    fun intensityOf(dbz: Int?): Int =
        if (dbz == null) 0 else ((dbz - MIN_DBZ + 1) * 3).coerceIn(1, 255)

    /** Inverse of [intensityOf]; null for dry. */
    fun dbzOfIntensity(intensity: Int): Int? =
        if (intensity <= 0) null else intensity / 3 - 1 + MIN_DBZ

    // Black at the band's opacity: over the white map that is exactly the band's grey.
    private val levelPixel = IntArray(NUM_LEVELS) { (255 - BAND_GREY[it]) shl 24 }

    /** The overlay pixel (black with alpha; 0 = transparent) for a stored intensity. */
    fun pixelForIntensity(intensity: Int): Int {
        val dbz = dbzOfIntensity(intensity) ?: return 0
        return levelPixel[levelOf(dbz)]
    }
}
