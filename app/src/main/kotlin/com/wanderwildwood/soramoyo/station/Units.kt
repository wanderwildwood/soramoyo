package com.wanderwildwood.soramoyo.station

import android.content.Context
import java.util.Locale

/**
 * Readings into the units they are shown in, for the sources that do not say.
 *
 * An Ecowitt gateway reports in whatever its owner set, and is shown that way. A Tempest
 * always sends metric and a Davis always sends imperial, so those follow the phone's country
 * instead — the same rule the forecast falls back on — and are written the way an Ecowitt
 * writes them, so the screen reads the same whichever station is behind it.
 *
 * Unless metric or imperial is chosen in settings, which then goes for everything: the
 * forecast, and the station too, an Ecowitt's readings converted from its gateway's units.
 */
object Units {
    /** The three countries that still count in Fahrenheit. */
    fun metricHere(): Boolean = Locale.getDefault().country !in setOf("US", "LR", "MM")

    /** What the settings say: follow the station (else the country), or always one or the other. */
    enum class Choice { AUTOMATIC, METRIC, IMPERIAL }

    private const val PREFS = "sky"
    private const val KEY = "units"

    fun choice(context: Context): Choice =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { Choice.valueOf(it) }.getOrNull() } ?: Choice.AUTOMATIC

    fun choose(context: Context, choice: Choice) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, choice.name).apply()
    }

    /** True for metric, false for imperial, null to let the station or the country decide. */
    fun chosen(context: Context): Boolean? = when (choice(context)) {
        Choice.AUTOMATIC -> null
        Choice.METRIC -> true
        Choice.IMPERIAL -> false
    }

    /**
     * An Ecowitt reading, arriving in whatever units its gateway is set to, in the units
     * chosen instead. A unit this does not know (Beaufort, say) is left as the gateway wrote it.
     */
    fun convert(reading: StationReading, metric: Boolean): StationReading = reading.copy(
        temperature = reading.temperature?.let { to(it, metric) },
        feelsLike = reading.feelsLike?.let { to(it, metric) },
        dewPoint = reading.dewPoint?.let { to(it, metric) },
        windSpeed = reading.windSpeed?.let { to(it, metric) },
        windGust = reading.windGust?.let { to(it, metric) },
        rainToday = reading.rainToday?.let { to(it, metric) },
        rainRate = reading.rainRate?.let { to(it, metric) },
        pressure = reading.pressure?.let { to(it, metric) },
    )

    private fun to(m: Measure, metric: Boolean): Measure {
        val v = m.number
        return when (m.unit.lowercase(Locale.US).removePrefix("°").removePrefix("℃")) {
            "c" -> celsius(v, metric)
            "f" -> fahrenheit(v, metric)
            "mph" -> mph(v, metric)
            "km/h" -> mph(v / 1.609344, metric)
            "m/s" -> metresPerSecond(v, metric)
            "knots", "kn", "kt" -> mph(v * 1.150779, metric)
            "ft/s" -> mph(v * 0.681818, metric)
            "in" -> inches(v, metric)
            "mm" -> millimetres(v, metric)
            "in/hr" -> inchesPerHour(v, metric)
            "mm/hr" -> millimetresPerHour(v, metric)
            "inhg" -> inchesOfMercury(v, metric)
            "hpa", "mbar" -> hectopascals(v, metric)
            "mmhg" -> hectopascals(v * 1.333224, metric)
            else -> m
        }!!.let { converted ->
            // Already in the wanted unit: keep the gateway's own figure, and its places.
            if (converted.unit.equals(m.unit, ignoreCase = true)) m else converted
        }
    }

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
