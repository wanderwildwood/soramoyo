package com.wanderwildwood.soramoyo.station

import java.util.Locale

/**
 * Readings into the units they are shown in, for the sources that do not say.
 *
 * An Ecowitt gateway reports in whatever its owner set, and is shown that way. A Tempest
 * always sends metric and a Davis always sends imperial, so those follow the phone's country
 * instead — the same rule the forecast falls back on — and are written the way an Ecowitt
 * writes them, so the screen reads the same whichever station is behind it.
 */
object Units {
    /** The three countries that still count in Fahrenheit. */
    fun metricHere(): Boolean = Locale.getDefault().country !in setOf("US", "LR", "MM")

    fun celsius(c: Double?, metric: Boolean): Measure? = c?.let {
        if (metric) m(it, "C", 1) else m(it * 9 / 5 + 32, "F", 1)
    }

    fun fahrenheit(f: Double?, metric: Boolean): Measure? = f?.let {
        if (metric) m((it - 32) * 5 / 9, "C", 1) else m(it, "F", 1)
    }

    fun metresPerSecond(v: Double?, metric: Boolean): Measure? = v?.let {
        if (metric) m(it * 3.6, "km/h", 1) else m(it * 2.236936, "mph", 1)
    }

    fun mph(v: Double?, metric: Boolean): Measure? = v?.let {
        if (metric) m(it * 1.609344, "km/h", 1) else m(it, "mph", 1)
    }

    fun millimetres(v: Double?, metric: Boolean): Measure? = v?.let {
        if (metric) m(it, "mm", 1) else m(it / 25.4, "in", 2)
    }

    fun inches(v: Double?, metric: Boolean): Measure? = v?.let {
        if (metric) m(it * 25.4, "mm", 1) else m(it, "in", 2)
    }

    fun millimetresPerHour(v: Double?, metric: Boolean): Measure? = v?.let {
        if (metric) m(it, "mm/Hr", 1) else m(it / 25.4, "in/Hr", 2)
    }

    fun inchesPerHour(v: Double?, metric: Boolean): Measure? = v?.let {
        if (metric) m(it * 25.4, "mm/Hr", 1) else m(it, "in/Hr", 2)
    }

    fun hectopascals(v: Double?, metric: Boolean): Measure? = v?.let {
        if (metric) m(it, "hPa", 1) else m(it / 33.8639, "inHg", 2)
    }

    fun inchesOfMercury(v: Double?, metric: Boolean): Measure? = v?.let {
        if (metric) m(it * 33.8639, "hPa", 1) else m(it, "inHg", 2)
    }

    /** Dew point from temperature and humidity (Magnus), for a source that does not send it. */
    fun dewPointC(tempC: Double?, humidity: Double?): Double? {
        if (tempC == null || humidity == null || humidity <= 0) return null
        val a = 17.62
        val b = 243.12
        val g = Math.log(humidity / 100.0) + a * tempC / (b + tempC)
        return b * g / (a - g)
    }

    private fun m(value: Double, unit: String, places: Int): Measure =
        Measure(value, unit, String.format(Locale.US, "%.${places}f", value))
}
