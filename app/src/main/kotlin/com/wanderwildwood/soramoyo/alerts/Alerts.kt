package com.wanderwildwood.soramoyo.alerts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.OffsetDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * One warning, watch, advisory or statement, as it was issued: by the US National Weather
 * Service in the United States, and elsewhere by the national service whose alert KDE's public
 * alert server passed on ([WorldAlerts]). The times keep the offset they were written with,
 * which is the place's own.
 */
data class Alert(
    val id: String,
    /**
     * What stays the same while the Weather Service updates and extends the one warning: its
     * office, kind and number. Each update is a new message with a new [id], and a warning
     * extended three times is still one warning to the person under it.
     */
    val track: String?,
    val event: String,
    val headline: String,
    val areas: String,
    val severity: String,
    val onset: OffsetDateTime?,
    /** When it ends, else when this message runs out; null when neither is given. */
    val until: OffsetDateTime?,
    val description: String,
    val instruction: String,
    val sender: String,
    /** The earlier messages this one updates. */
    val references: List<String> = emptyList(),
    /** The alert's CAP categories, such as "Met" or "Geo"; empty from the Weather Service. */
    val category: String = "",
) {
    /**
     * Worth a notification: what the Weather Service itself rates severe or extreme, and the
     * fire and evacuation warnings whatever their rating. A Red Flag Warning is only
     * "Moderate" by the book, and is the one someone in a dry valley most wants to hear of.
     * Earthquakes, tsunamis and the rest of the ground's own ("Geo") are serious too: a
     * service may rate a felt quake only "Minor", and the person under it still wants to know.
     */
    val serious: Boolean
        get() = severity == "Severe" || severity == "Extreme" ||
            FIRE.any { event.contains(it, ignoreCase = true) } ||
            category.split(' ').any { it == "Geo" } ||
            GROUND.any { event.contains(it, ignoreCase = true) }

    /** Every name this warning has gone by: a message is not new if any of them was seen. */
    val names: List<String> get() = listOfNotNull(id, track) + references

    companion object {
        private val FIRE = listOf("Red Flag", "Fire Weather Watch", "Fire Warning", "Evacuation")
        private val GROUND = listOf("Earthquake", "Tsunami")
    }
}

/** What the Weather Service had for a place: its alerts, or that the place is not one it covers. */
sealed interface AlertAnswer {
    data class Found(val alerts: List<Alert>) : AlertAnswer
    data object NotCovered : AlertAnswer
}

/**
 * Alerts in effect for a place. First from the US National Weather Service's own API
 * (api.weather.gov). It asks for no key; it asks only that callers say who they are, which is
 * the app's name and where its code is.
 *
 * The Weather Service covers only the United States and its territories. Anywhere else it
 * answers that the point is out of its bounds, and the alerts come instead from KDE's public
 * alert server ([WorldAlerts]), which gathers the national services of most of the world.
 */
object Alerts {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** Set by the debug build only, to see the screens with alerts on a quiet day. */
    @Volatile
    internal var fake: (() -> String?)? = null

    suspend fun fetch(lat: Double, lon: Double): AlertAnswer = withContext(Dispatchers.IO) {
        fake?.invoke()?.let { return@withContext AlertAnswer.Found(parse(it)) }
        val request = Request.Builder()
            .url(urlFor(lat, lon))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/geo+json")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            // 400 is "out of bounds" for a point outside the US; 404 is a point it has no zone for.
            if (response.code != 400 && response.code != 404) {
                if (!response.isSuccessful) throw IOException("alerts answered ${response.code}")
                return@withContext AlertAnswer.Found(parse(response.body?.string().orEmpty()))
            }
        }
        AlertAnswer.Found(WorldAlerts.fetch(client, USER_AGENT, lat, lon))
    }

    /** Rounded as everything else that leaves the phone is: to two places, about a kilometre. */
    internal fun urlFor(lat: Double, lon: Double): String =
        "https://api.weather.gov/alerts/active".toHttpUrl().newBuilder()
            .addQueryParameter("point", String.format(Locale.US, "%.2f,%.2f", lat, lon))
            .addQueryParameter("status", "actual")
            .build()
            .toString()

    /**
     * The alerts in an answer, soonest-ending first. Tests, exercises and cancellations are
     * left out; so is anything already over, which the Weather Service can still list for a
     * few minutes after it ends.
     */
    internal fun parse(json: String, now: OffsetDateTime = OffsetDateTime.now()): List<Alert> {
        val features = JSONObject(json).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val p = features.optJSONObject(i)?.optJSONObject("properties") ?: return@mapNotNull null
            if (p.text("status").let { it.isNotEmpty() && it != "Actual" }) return@mapNotNull null
            if (p.text("messageType") == "Cancel") return@mapNotNull null
            val id = p.text("id").ifEmpty { return@mapNotNull null }
            val event = p.text("event").ifEmpty { return@mapNotNull null }
            val until = time(p.text("ends")) ?: time(p.text("expires"))
            if (until != null && until.isBefore(now)) return@mapNotNull null
            val params = p.optJSONObject("parameters")
            Alert(
                id = id,
                track = params?.optJSONArray("VTEC")?.optString(0)?.let(::track),
                event = event,
                headline = p.text("headline"),
                areas = p.text("areaDesc"),
                severity = p.text("severity"),
                onset = time(p.text("onset")) ?: time(p.text("effective")),
                until = until,
                description = p.text("description"),
                instruction = p.text("instruction"),
                sender = p.text("senderName"),
                references = p.optJSONArray("references").identifiers(),
            )
        }.sortedBy { it.until ?: OffsetDateTime.MAX }
    }

    /**
     * "/O.EXT.KSGX.BH.S.0015.000000T0000Z-261021T0600Z/" is office KSGX's beach hazards
     * statement number 15, extended: the office, kind, level and number are the warning, the
     * rest is what this message did to it.
     */
    internal fun track(vtec: String): String? {
        val parts = vtec.trim('/').split('.')
        if (parts.size < 6) return null
        return parts.subList(2, 6).joinToString(".")
    }

    /**
     * The Weather Service's text is wrapped at about seventy characters, with a blank line
     * between paragraphs. Each paragraph comes back as one line, for the panel to wrap.
     */
    fun paragraphs(text: String): List<String> =
        text.replace("\r", "")
            .split(Regex("\n\\s*\n"))
            .map { it.replace(Regex("\\s*\n\\s*"), " ").replace(Regex(" {2,}"), " ").trim() }
            .filter { it.isNotEmpty() }

    private fun JSONObject.text(name: String): String =
        if (isNull(name)) "" else optString(name).trim()

    private fun JSONArray?.identifiers(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it)?.optString("identifier")?.takeIf { s -> s.isNotEmpty() } }
    }

    private fun time(text: String): OffsetDateTime? =
        if (text.isEmpty()) null else runCatching { OffsetDateTime.parse(text) }.getOrNull()

    private const val USER_AGENT = "Sky (https://github.com/wanderwildwood/soramoyo)"
}
