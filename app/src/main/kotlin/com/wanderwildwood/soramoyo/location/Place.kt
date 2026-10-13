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
    private const val KEY_SECOND_LABEL = "second_label"
    private const val KEY_SECOND_LAT = "second_lat"
    private const val KEY_SECOND_LON = "second_lon"
    private const val KEY_ON_SECOND = "on_second"

    /** The first place: the one the station, the lock screen and the radar's default belong to. */
    fun chosen(context: Context): Place? = read(context, KEY_LABEL, KEY_LAT, KEY_LON)

    /** Null goes back to the phone's own position. */
    fun choose(context: Context, place: Place?) = write(context, place, KEY_LABEL, KEY_LAT, KEY_LON)

    /**
     * A second place, for someone who lives in one town and works in another. Off unless
     * set; the main screen offers a switch between the two only while it is.
     */
    fun second(context: Context): Place? = read(context, KEY_SECOND_LABEL, KEY_SECOND_LAT, KEY_SECOND_LON)

    /** Null removes the second place, and goes back to the first. */
    fun chooseSecond(context: Context, place: Place?) {
        write(context, place, KEY_SECOND_LABEL, KEY_SECOND_LAT, KEY_SECOND_LON)
        if (place == null) showSecond(context, false)
    }

    /** Whether the second place was the one last shown, kept so the app opens where it was left. */
    fun onSecond(context: Context): Boolean = prefs(context).getBoolean(KEY_ON_SECOND, false)

    fun showSecond(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(KEY_ON_SECOND, on).apply()
    }

    /** The place being shown: the second when it is set and chosen, else the first. */
    fun showing(context: Context): Place? = showing(chosen(context), second(context), onSecond(context))

    /** Null means the phone's own position. */
    internal fun showing(first: Place?, second: Place?, onSecond: Boolean): Place? =
        if (onSecond && second != null) second else first

    /** The town alone, for the switch: "Portland, Maine, United States" is "Portland". */
    internal fun short(label: String): String = label.substringBefore(",").trim()

    /** Whether the phone's position is needed for what is shown now, and so its permission. */
    fun needsPosition(context: Context): Boolean = showing(context) == null

    /** The place being shown if there is one, else where the phone is. */
    suspend fun where(context: Context): LatLon? = showing(context)?.at ?: LocationProvider.here(context)

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun read(context: Context, label: String, lat: String, lon: String): Place? {
        val p = prefs(context)
        return Place(
            p.getString(label, null) ?: return null,
            p.getString(lat, null)?.toDoubleOrNull() ?: return null,
            p.getString(lon, null)?.toDoubleOrNull() ?: return null,
        )
    }

    private fun write(context: Context, place: Place?, label: String, lat: String, lon: String) {
        val edit = prefs(context).edit()
        if (place == null) {
            edit.remove(label).remove(lat).remove(lon)
        } else {
            edit.putString(label, place.label)
                .putString(lat, place.lat.toString())
                .putString(lon, place.lon.toString())
        }
        edit.apply()
    }

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
