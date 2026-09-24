package com.wanderwildwood.soramoyo.forecast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * The air now: its quality on the two common scales, and pollen where there is any to report.
 *
 * [pollen] is grains per cubic metre by kind, highest first, only the kinds above a grain.
 * Open-Meteo has pollen for Europe alone, so elsewhere it is always empty.
 */
data class Air(
    val usAqi: Int?,
    val europeanAqi: Int?,
    val pollen: List<Pair<Pollen, Double>> = emptyList(),
)

enum class Pollen(val key: String) {
    ALDER("alder_pollen"),
    BIRCH("birch_pollen"),
    GRASS("grass_pollen"),
    MUGWORT("mugwort_pollen"),
    OLIVE("olive_pollen"),
    RAGWEED("ragweed_pollen"),
}

/** How bad a reading is, in the words each scale uses for its own bands. */
enum class AirBand { GOOD, FAIR, MODERATE, SENSITIVE, POOR, VERY_POOR, EXTREME }

/**
 * Open-Meteo's air-quality service, which like its forecast asks for no key. CAMS data, the
 * European Union's Copernicus atmosphere service, modelled rather than measured.
 */
object AirQuality {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun fetch(lat: Double, lon: Double): Air = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(urlFor(lat, lon)).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("air quality answered ${response.code}")
            parse(response.body?.string().orEmpty())
        }
    }

    internal fun urlFor(lat: Double, lon: Double): String =
        "https://air-quality-api.open-meteo.com/v1/air-quality".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", String.format(Locale.US, "%.2f", lat))
            .addQueryParameter("longitude", String.format(Locale.US, "%.2f", lon))
            .addQueryParameter("current", "us_aqi,european_aqi," + Pollen.entries.joinToString(",") { it.key })
            .addQueryParameter("timezone", "auto")
            .build()
            .toString()

    internal fun parse(json: String): Air {
        val c = JSONObject(json).optJSONObject("current") ?: return Air(null, null)
        fun num(name: String): Double? =
            if (!c.has(name) || c.isNull(name)) null else c.optDouble(name).takeIf { !it.isNaN() }
        return Air(
            usAqi = num("us_aqi")?.roundToInt(),
            europeanAqi = num("european_aqi")?.roundToInt(),
            pollen = Pollen.entries
                .mapNotNull { kind -> num(kind.key)?.takeIf { it >= 1.0 }?.let { kind to it } }
                .sortedByDescending { it.second },
        )
    }

    /** The US EPA's bands. */
    fun usBand(aqi: Int): AirBand = when {
        aqi <= 50 -> AirBand.GOOD
        aqi <= 100 -> AirBand.MODERATE
        aqi <= 150 -> AirBand.SENSITIVE
        aqi <= 200 -> AirBand.POOR
        aqi <= 300 -> AirBand.VERY_POOR
        else -> AirBand.EXTREME
    }

    /** The European Environment Agency's bands. */
    fun europeanBand(aqi: Int): AirBand = when {
        aqi <= 20 -> AirBand.GOOD
        aqi <= 40 -> AirBand.FAIR
        aqi <= 60 -> AirBand.MODERATE
        aqi <= 80 -> AirBand.POOR
        aqi <= 100 -> AirBand.VERY_POOR
        else -> AirBand.EXTREME
    }
}
