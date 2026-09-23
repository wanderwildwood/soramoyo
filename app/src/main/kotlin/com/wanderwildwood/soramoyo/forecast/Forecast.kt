package com.wanderwildwood.soramoyo.forecast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** One day of forecast. [rainChance] is null where the model gave none. */
data class Day(
    val date: LocalDate,
    val code: Int,
    val high: Int,
    val low: Int,
    val rainChance: Int?,
    val sunrise: LocalDateTime?,
    val sunset: LocalDateTime?,
)

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

    suspend fun fetch(lat: Double, lon: Double, metric: Boolean): List<Day> =
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
                    "precipitation_probability_max,sunrise,sunset",
            )
            .addQueryParameter("temperature_unit", if (metric) "celsius" else "fahrenheit")
            // The phone's own time zone would be wrong for a forecast of somewhere else;
            // "auto" gives the days and the sunrise in the zone of the place itself.
            .addQueryParameter("timezone", "auto")
            // Today for the first tab, and five after it for the second.
            .addQueryParameter("forecast_days", "6")
            .build()
            .toString()

    internal fun parse(json: String): List<Day> {
        val daily = JSONObject(json).getJSONObject("daily")
        val dates = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val highs = daily.getJSONArray("temperature_2m_max")
        val lows = daily.getJSONArray("temperature_2m_min")
        val rain = daily.optJSONArray("precipitation_probability_max")
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
                sunrise = rises?.optString(i)?.takeIf { it.isNotEmpty() && it != "null" }?.let(LocalDateTime::parse),
                sunset = sets?.optString(i)?.takeIf { it.isNotEmpty() && it != "null" }?.let(LocalDateTime::parse),
            )
        }
    }
}
