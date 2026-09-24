package com.wanderwildwood.soramoyo.forecast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * One day of forecast. [rainChance] is null where the model gave none; [rain] is how much is
 * expected to fall, in millimetres or inches as the forecast was asked for.
 */
data class Day(
    val date: LocalDate,
    val code: Int,
    val high: Int,
    val low: Int,
    val rainChance: Int?,
    val rain: Double? = null,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
)

/** One hour of forecast, its time in the zone of the place it is for. */
data class Hour(
    val time: LocalDateTime,
    val code: Int,
    val temperature: Int,
    val rainChance: Int?,
    val rain: Double?,
)

/**
 * The forecast's estimate of right now, for a phone with no station to measure it. Speeds in
 * km/h or mph, temperatures in the unit asked for.
 */
data class Current(
    val time: LocalDateTime,
    val code: Int,
    val temperature: Double,
    val feelsLike: Double?,
    val humidity: Int?,
    val windSpeed: Double?,
    val windGust: Double?,
    /** Degrees clockwise from north that the wind is coming from. */
    val windFrom: Int?,
)

/**
 * What came back: the days, the hours from the one under way, whether the amounts are in
 * inches, and the place's offset from UTC, which the hours' times are in.
 */
data class Predicted(
    val current: Current? = null,
    val days: List<Day> = emptyList(),
    val hours: List<Hour> = emptyList(),
    val inches: Boolean = false,
    val offset: ZoneOffset = ZoneOffset.UTC,
)

/**
 * The next [HOURS] hours from [now]. A forecast is kept for half an hour, so by the time it
 * is looked at again its first hour may be over.
 */
fun upcoming(hours: List<Hour>, offset: ZoneOffset, now: Instant = Instant.now()): List<Hour> {
    val thisHour = LocalDateTime.ofInstant(now, offset).truncatedTo(ChronoUnit.HOURS)
    return hours.filterNot { it.time.isBefore(thisHour) }.take(HOURS)
}

/** How many hours ahead the Today tab shows. */
const val HOURS = 12

/**
 * Six days from Open-Meteo — today and the five after it — for the phone or the chosen place.
 *
 * Open-Meteo asks for no key and no account, which is the only kind of forecast service an
 * app with no server of its own can use. Its data is CC BY 4.0, and the screen says whose it
 * is beside it.
 */
object Forecast {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(lat: Double, lon: Double, metric: Boolean): Predicted =
        withContext(Dispatchers.IO) {
            val request = Request.Builder().url(urlFor(lat, lon, metric)).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("forecast answered ${response.code}")
                parse(response.body?.string().orEmpty())
            }
        }

    internal fun urlFor(lat: Double, lon: Double, metric: Boolean): String =
        "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", String.format(Locale.US, "%.2f", lat))
            .addQueryParameter("longitude", String.format(Locale.US, "%.2f", lon))
            .addQueryParameter(
                "daily",
                "weather_code,temperature_2m_max,temperature_2m_min," +
                    "precipitation_probability_max,precipitation_sum,sunrise,sunset",
            )
            .addQueryParameter(
                "current",
                "weather_code,temperature_2m,apparent_temperature,relative_humidity_2m," +
                    "wind_speed_10m,wind_gusts_10m,wind_direction_10m",
            )
            .addQueryParameter("hourly", "weather_code,temperature_2m,precipitation_probability,precipitation")
            // Hours from the one now under way. Two spare, in case the hour turns between the
            // server answering and the screen being drawn.
            .addQueryParameter("forecast_hours", (HOURS + 2).toString())
            .addQueryParameter("temperature_unit", if (metric) "celsius" else "fahrenheit")
            .addQueryParameter("precipitation_unit", if (metric) "mm" else "inch")
            .addQueryParameter("wind_speed_unit", if (metric) "kmh" else "mph")
            // The phone's own time zone would be wrong for a forecast of somewhere else;
            // "auto" gives the days and the sunrise in the zone of the place itself.
            .addQueryParameter("timezone", "auto")
            // Today for the first tab, and five after it for the second.
            .addQueryParameter("forecast_days", "6")
            .build()
            .toString()

    internal fun parse(json: String, now: Instant = Instant.now()): Predicted {
        val root = JSONObject(json)
        val inches = root.optJSONObject("daily_units")?.optString("precipitation_sum") == "inch" ||
            root.optJSONObject("hourly_units")?.optString("precipitation") == "inch"
        val offset = ZoneOffset.ofTotalSeconds(root.optInt("utc_offset_seconds", 0))
        val thisHour = LocalDateTime.ofInstant(now, offset).truncatedTo(ChronoUnit.HOURS)
        return Predicted(current(root), days(root), hours(root).filterNot { it.time.isBefore(thisHour) }, inches, offset)
    }

    private fun days(root: JSONObject): List<Day> {
        val daily = root.getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        val rain = daily.optJSONArray("precipitation_probability_max")
        val amounts = daily.optJSONArray("precipitation_sum")
        val rises = daily.optJSONArray("sunrise")
        val sets = daily.optJSONArray("sunset")

        return (0 until dates.length()).mapNotNull { i ->
            // A day the model has no temperature for is not a day worth showing a dash for.
            if (highs.isNull(i) || lows.isNull(i)) return@mapNotNull null
            Day(
                date = LocalDate.parse(dates.getString(i)),
                code = if (codes.isNull(i)) -1 else codes.getInt(i),
                high = highs.getDouble(i).roundToInt(),
                low = lows.getDouble(i).roundToInt(),
                rainChance = rain?.takeUnless { it.isNull(i) }?.getInt(i),
                rain = amounts?.takeUnless { it.isNull(i) }?.getDouble(i),
                sunrise = rises?.optString(i)?.takeIf { it.isNotEmpty() && it != "null" }?.let(LocalDateTime::parse),
                sunset = sets?.optString(i)?.takeIf { it.isNotEmpty() && it != "null" }?.let(LocalDateTime::parse),
            )
        }
    }

    private fun current(root: JSONObject): Current? {
        val c = root.optJSONObject("current") ?: return null
        val temperature = c.num("temperature_2m") ?: return null
        return Current(
            time = LocalDateTime.parse(c.getString("time")),
            code = c.num("weather_code")?.toInt() ?: -1,
            temperature = temperature,
            feelsLike = c.num("apparent_temperature"),
            humidity = c.num("relative_humidity_2m")?.roundToInt(),
            windSpeed = c.num("wind_speed_10m"),
            windGust = c.num("wind_gusts_10m"),
            windFrom = c.num("wind_direction_10m")?.roundToInt(),
        )
    }

    private fun JSONObject.num(name: String): Double? =
        if (!has(name) || isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }

    /**
     * The hours, in the place's own time: Open-Meteo gives the times without a zone and says
     * the offset once, beside them.
     */
    private fun hours(root: JSONObject): List<Hour> {
        val hourly = root.optJSONObject("hourly") ?: return emptyList()
        val times = hourly.getJSONArray("time")
        val codes = hourly.optJSONArray("weather_code")
        val temps = hourly.getJSONArray("temperature_2m")
        val chances = hourly.optJSONArray("precipitation_probability")
        val amounts = hourly.optJSONArray("precipitation")
        return (0 until times.length()).mapNotNull { i ->
            if (temps.isNull(i)) return@mapNotNull null
            Hour(
                time = LocalDateTime.parse(times.getString(i)),
                code = codes?.takeUnless { it.isNull(i) }?.getInt(i) ?: -1,
                temperature = temps.getDouble(i).roundToInt(),
                rainChance = chances?.takeUnless { it.isNull(i) }?.getInt(i),
                rain = amounts?.takeUnless { it.isNull(i) }?.getDouble(i),
            )
        }
    }
}
