package com.wanderwildwood.soramoyo.location

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** A place chosen by name, and where it is. [label] is what the settings row shows. */
data class Place(val label: String, val lat: Double, val lon: Double) {
    val at: LatLon get() = LatLon(lat, lon)
}

/**
 * The place the forecast and the radar are for, when one has been chosen.
 *
 * A Kompakt has no network location of its own, only the GPS, and a GPS indoors on a phone
 * that has never had a fix may answer in minutes or not at all. So a place can be chosen once
 * by name instead. While one is set it is used for both and the phone's position is not read
 * at all; with none set, it is the phone's position as before.
 */
object Places {
    private const val PREFS = "sky"
    private const val KEY_LABEL = "place_label"
    private const val KEY_LAT = "place_lat"
    private const val KEY_LON = "place_lon"

    fun chosen(context: Context): Place? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val label = prefs.getString(KEY_LABEL, null) ?: return null
        val lat = prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return null
        val lon = prefs.getString(KEY_LON, null)?.toDoubleOrNull() ?: return null
        return Place(label, lat, lon)
    }

    /** Null goes back to the phone's own position. */
    fun choose(context: Context, place: Place?) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (place == null) {
            edit.remove(KEY_LABEL).remove(KEY_LAT).remove(KEY_LON)
        } else {
            edit.putString(KEY_LABEL, place.label)
                .putString(KEY_LAT, place.lat.toString())
                .putString(KEY_LON, place.lon.toString())
        }
        edit.apply()
    }

    /** Whether the phone's position is needed at all, and so its permission. */
    fun needsPosition(context: Context): Boolean = chosen(context) == null

    /** The chosen place if there is one, else where the phone is. */
    suspend fun where(context: Context): LatLon? = chosen(context)?.at ?: LocationProvider.here(context)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /**
     * Places matching [name], from Open-Meteo's place search — the same service the forecast
     * comes from, so choosing a place sends the words typed to nobody new.
     */
    suspend fun search(typed: String): List<Place> = withContext(Dispatchers.IO) {
        val (name, within) = split(typed)
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", name)
            .addQueryParameter("count", "20")
            .addQueryParameter("language", Locale.getDefault().language.ifEmpty { "en" })
            .addQueryParameter("format", "json")
            .build()
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("place search answered ${response.code}")
            narrow(parse(response.body?.string().orEmpty()), within)
        }
    }

    /**
     * "Portland, Maine" is a name and where it is. The search takes only names and ranks by
     * population, so a small town sharing a name with larger ones can come fifth or not at
     * all; what follows the comma is used here to pick it out of the answer instead.
     */
    internal fun split(typed: String): Pair<String, String> {
        val parts = typed.split(",", limit = 2)
        return parts[0].trim() to parts.getOrElse(1) { "" }.trim()
    }

    /** Only the places whose region or country mentions [within]; all of them if none do. */
    internal fun narrow(places: List<Place>, within: String): List<Place> {
        if (within.isEmpty()) return places
        val region = { p: Place -> p.label.substringAfter(",", "") }
        return places.filter { region(it).contains(within, ignoreCase = true) }.ifEmpty { places }
    }

    /**
     * An answer with nothing in it carries no "results" at all, rather than an empty list.
     * The label names the place and then the largest area around it, since "Portland" alone
     * is five different towns.
     */
    internal fun parse(json: String): List<Place> {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val r = results.optJSONObject(i) ?: return@mapNotNull null
            val name = r.optString("name").ifEmpty { return@mapNotNull null }
            if (!r.has("latitude") || !r.has("longitude")) return@mapNotNull null
            val label = listOf(name, r.optString("admin1"), r.optString("country"))
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(", ")
            // Rounded as the phone's own position is: the town is the point, not a street.
            Place(label, round2(r.getDouble("latitude")), round2(r.getDouble("longitude")))
        }
    }

    private fun round2(degrees: Double): Double = Math.round(degrees * 100.0) / 100.0
}
