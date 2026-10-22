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
    fun moonPhase(angle: Double): Float = (((angle % 360.0) + 360.0) % 360.0 / 360.0).toFloat()
}
