package com.wanderwildwood.soramoyo.glance

import kotlin.math.abs

/**
 * The words and numbers [ForTheWalk] hands over beyond the sky itself, worked out from what Sky
 * already has: kept apart from Android so they can be tested.
 */
internal object WalkFacts {
    const val NONE = "none"
    const val HEAT = "heat"
    const val WINTER_STORM = "winter_storm"
    const val SMOKE = "smoke"
    const val OTHER = "other"

    /** Snow falling at this many centimetres an hour or more is heavy. */
    const val HEAVY_CM_PER_HOUR = 2.0

    /** The weather codes for heavy snow, and heavy snow showers. */
    private val HEAVY_CODES = setOf(75, 86)

    /** The latitude of the tropics, either side of the equator. */
    const val TROPIC = 23.44

    /** The kind of a Weather Service alert, from its event name alone. */
    fun alertKind(event: String): String {
        val e = event.lowercase()
        return when {
            "heat" in e -> HEAT
            listOf("winter storm", "blizzard", "ice storm", "winter weather").any { it in e } -> WINTER_STORM
            "air quality" in e || "smoke" in e -> SMOKE
            else -> OTHER
        }
    }

    /** The Weather Service's own severity, as a number to compare: Extreme 4 down to Unknown 0. */
    fun severityRank(severity: String): Int = when (severity) {
        "Extreme" -> 4
        "Severe" -> 3
        "Moderate" -> 2
        "Minor" -> 1
        else -> 0
    }

    /** One alert as it is kept: its kind, its severity's rank, and when it starts and ends (null for not said). */
    data class InForce(val kind: String, val rank: Int, val from: Long?, val until: Long?)

    /**
     * The kind of the most severe alert in force at [now]; among equals, a named kind before
     * [OTHER]. [NONE] when nothing is.
     */
    fun mostSignificant(alerts: List<InForce>, now: Long): String =
        alerts.filter { (it.from == null || it.from <= now) && (it.until == null || now < it.until) }
            .maxWithOrNull(compareBy<InForce>({ it.rank }, { it.kind != OTHER }))
            ?.kind ?: NONE

    fun hemisphere(lat: Double): String = when {
        abs(lat) < TROPIC -> "tropic"
        lat > 0 -> "north"
        else -> "south"
    }

    /** Heavy snow in the hours given: [HEAVY_CM_PER_HOUR] or more in any one, or a heavy-snow code. */
    fun heavySnow(snowCm: List<Double?>, codes: List<Int>): Boolean =
        snowCm.any { (it ?: 0.0) >= HEAVY_CM_PER_HOUR } || codes.any { it in HEAVY_CODES }

    /** A speed from the forecast, in km/h or mph as it was asked for, in metres a second; -1 for none. */
    fun metresPerSecond(speed: Double?, metric: Boolean): Float = when {
        speed == null || speed.isNaN() -> -1f
        metric -> (speed / 3.6).toFloat()
        else -> (speed * 0.44704).toFloat()
    }

    /** Snow from the forecast, in centimetres or inches as it was asked for, in centimetres. */
    fun centimetres(snow: Double?, inches: Boolean): Double {
        val s = snow?.takeUnless { it.isNaN() } ?: return 0.0
        return if (inches) s * 2.54 else s
    }

    /** The moon's angle round from new, in degrees, as a fraction of its month: 0 new, 0.5 full. */
    /**
     * The sky, and whether rain or snow starts or stops and in how many minutes, counted from
     * [nowMs] to the change's time [atMs], not from when the forecast was fetched. A change
     * already past is no longer one, and the sky is as it said: rain or snow ([snow]) once it
     * has started, dry once it has stopped.
     */
    fun changeNow(sky: String, change: String, atMs: Long, nowMs: Long, snow: Boolean = false): Triple<String, String, Long> {
        if (atMs < nowMs) {
            val after = when (change) {
                "starts" -> if (snow) "snow" else "rain"
                "stops" -> if (sky == "rain" || sky == "snow") "clear" else sky
                else -> sky
            }
            return Triple(after, "", -1L)
        }
        return Triple(sky, change, (atMs - nowMs) / 60_000L)
    }

    fun moonPhase(angle: Double): Float = (((angle % 360.0) + 360.0) % 360.0 / 360.0).toFloat()

    /** The hours kept for the pressure's fall: this many back from now, which is enough to look three back. */
    const val PRESSURE_KEEP_HOURS = 6L

    /** How far back the fall looks, in hours. */
    const val PRESSURE_SPAN_HOURS = 3L

    /** Hours of pressure (hPa) as kept between fetches: "epoch seconds=hPa" pairs, comma apart; only the ones that are known. */
    fun pressureKept(hours: List<Pair<java.time.Instant, Double?>>, now: java.time.Instant): String =
        hours.filter { (at, hpa) ->
            hpa != null && !at.isBefore(now.minusSeconds(PRESSURE_KEEP_HOURS * 3600)) && !at.isAfter(now.plusSeconds(3600))
        }.joinToString(",") { (at, hpa) -> "${at.epochSecond}=$hpa" }

    fun pressureRead(kept: String?): List<Pair<java.time.Instant, Double?>> =
        kept.orEmpty().split(',').mapNotNull { pair ->
            val (t, p) = pair.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
            val at = t.toLongOrNull() ?: return@mapNotNull null
            val hpa = p.toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
            java.time.Instant.ofEpochSecond(at) to hpa
        }

    /**
     * How far the sea-level pressure has fallen over the three hours to the hour under way, in hPa: the pressure three
     * hours back minus now's, so a fall is positive and a rise negative. Hours are taken as instants, so a change of the
     * clocks inside the three does not make it two or four. Null if either hour is not known: not a fall of nothing.
     */
    fun pressureFall(hours: List<Pair<java.time.Instant, Double?>>, now: java.time.Instant, zone: java.time.ZoneId): Float? {
        val thisHour = now.atZone(zone).truncatedTo(java.time.temporal.ChronoUnit.HOURS).toInstant()
        val before = thisHour.minusSeconds(PRESSURE_SPAN_HOURS * 3600)
        val then = hours.firstOrNull { it.first == before }?.second ?: return null
        val nowP = hours.firstOrNull { it.first == thisHour }?.second ?: return null
        return (Math.round((then - nowP) * 10) / 10.0).toFloat()
    }
}
