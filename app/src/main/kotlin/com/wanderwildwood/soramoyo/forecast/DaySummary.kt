package com.wanderwildwood.soramoyo.forecast

import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * The day in a few plain words, worked out from the forecast already fetched and from nothing
 * else: the sky, what falls and when, the high and low, and the wind where it is worth
 * mentioning. This holds what is to be said; the words themselves are strings.xml's. A part the
 * forecast did not give is left out, never guessed.
 */
data class DaySummary(
    val sky: Sky?,
    val wet: Wet?,
    val high: Int?,
    val low: Int?,
    val wind: WindNote?,
) {
    enum class Sky { CLEAR, MOSTLY_CLEAR, PARTLY_CLOUDY, MOSTLY_CLOUDY, OVERCAST, FOG }

    enum class Kind { RAIN, SHOWERS, DRIZZLE, SNOW, SNOW_SHOWERS, FREEZING_RAIN, SLEET, THUNDERSTORMS }

    /** What falls, and when. */
    data class Wet(val kind: Kind, val timing: When)

    sealed interface When {
        /** From the start of the day to the end of it. */
        data object AllDay : When

        /** From now to the end of the day. */
        data object RestOfDay : When

        /** Starting at [at], going on to the end of the day. */
        data class After(val at: LocalTime) : When

        /** Falling at the start of the day, clearing by [by], in the morning. */
        data class Early(val by: LocalTime) : When

        /** Falling at the start, clearing by [by]. */
        data class Until(val by: LocalTime) : When

        /** One spell, from [from] to [to]. */
        data class Between(val from: LocalTime, val to: LocalTime) : When

        /** More than one spell. */
        data object OnAndOff : When

        /** Likely, with no hours to say when. */
        data object Likely : When
    }

    enum class Strength { BREEZY, GUSTY, WINDY }

    /** [from] is the compass point in eighths, 0 for north and 2 for east, or null where not given. */
    data class WindNote(val strength: Strength, val from: Int?)

    /** Whether there is anything to say at all. */
    val isEmpty: Boolean get() = sky == null && wet == null && high == null && low == null && wind == null

    companion object {
        private val SNOW = setOf(71, 73, 75, 77, 85, 86)
        private val FOG = setOf(45, 48)
        private val FREEZING = setOf(56, 57, 66, 67)
        private val SHOWERS = setOf(80, 81, 82)
        private val DRIZZLE = setOf(51, 53, 55)
        private val RAIN = setOf(61, 63, 65)
        private val THUNDER = setOf(95, 96, 99)
        private val PRECIPITATION = SNOW + FREEZING + SHOWERS + DRIZZLE + RAIN + THUNDER

        /** A day is "all day" when what is left of it starts this early. */
        private const val DAY_BEGINS = 6

        /** "Early" is a spell that is under way by this hour. */
        private const val EARLY_BEFORE = 9

        /** Kilometres an hour; miles an hour are converted to these. */
        private const val BREEZY_KMH = 25.0
        private const val WINDY_KMH = 40.0
        private const val GUSTY_KMH = 55.0
        private const val MPH_TO_KMH = 1.609344

        /**
         * [day] is the day to describe and [hours] the forecast's hours (those of other days
         * are ignored). It speaks of what is left of the day from the hour under way at [now];
         * a day with none of its hours left is described by its own weather code alone. The
         * temperatures and the wind are in the units the forecast was asked for, [metric] or not.
         */
        fun of(day: Day?, hours: List<Hour>, metric: Boolean, offset: ZoneOffset, now: Instant = Instant.now()): DaySummary? {
            day ?: return null
            val thisHour = LocalDateTime.ofInstant(now, offset).truncatedTo(ChronoUnit.HOURS)
            val left = hours.filter { it.time.toLocalDate() == day.date && !it.time.isBefore(thisHour) }
                .sortedBy { it.time }
            val summary = DaySummary(
                sky = if (left.isEmpty()) skyOfCode(day.code) else skyOf(left),
                wet = wetOf(day, left),
                high = day.high,
                low = day.low,
                wind = windOf(day, metric),
            )
            return summary.takeUnless { it.isEmpty }
        }

        private fun skyOfCode(code: Int): Sky? = when (code) {
            0 -> Sky.CLEAR
            1 -> Sky.MOSTLY_CLEAR
            2 -> Sky.PARTLY_CLOUDY
            3 -> Sky.OVERCAST
            in FOG -> Sky.FOG
            // A day of rain says it fell, not how much cloud there was around it.
            else -> null
        }

        /** How much of the sky an hour's weather code covers, 0 to 1; null for a code that is not known. */
        private fun cover(code: Int): Double? = when (code) {
            0 -> 0.0
            1 -> 0.2
            2 -> 0.5
            3 -> 1.0
            in FOG -> 1.0
            in SHOWERS -> 0.7
            in PRECIPITATION -> 0.9
            else -> null
        }

        private fun skyOf(left: List<Hour>): Sky? {
            val known = left.filter { cover(it.code) != null }
            if (known.isEmpty()) return null
            if (known.count { it.code in FOG } * 2 >= known.size) return Sky.FOG
            val average = known.sumOf { cover(it.code)!! } / known.size
            return when {
                average < 0.1 -> Sky.CLEAR
                average < 0.35 -> Sky.MOSTLY_CLEAR
                average < 0.65 -> Sky.PARTLY_CLOUDY
                average < 0.85 -> Sky.MOSTLY_CLOUDY
                else -> Sky.OVERCAST
            }
        }

        /**
         * Likely to rain or snow: even odds, and an amount to go with it where one is given. An
         * hour with no chance given goes by its amount alone.
         */
        private fun wet(hour: Hour): Boolean {
            val chance = hour.rainChance
            val amount = (hour.rain ?: 0.0) + (hour.snow ?: 0.0)
            return when {
                chance == null -> amount > 0.0
                chance < Change.LIKELY -> false
                else -> (hour.rain == null && hour.snow == null) || amount > 0.0 || hour.code in PRECIPITATION
            }
        }

        private fun wetOf(day: Day, left: List<Hour>): Wet? {
            if (left.isEmpty()) {
                // Nothing to say when, only that it is likely and of what kind.
                val kind = kindOfCode(day.code) ?: return null
                val chance = day.rainChance ?: return null
                return if (chance >= Change.LIKELY) Wet(kind, When.Likely) else null
            }
            val wets = left.filter(::wet)
            if (wets.isEmpty()) return null
            val kind = kindOf(wets, day)
            val first = left.indexOfFirst(::wet)
            val clearAt = left.indexOfFirst { it.time > left[first].time && !wet(it) }
            val again = if (clearAt < 0) false else left.drop(clearAt).any(::wet)
            val startsAtOnce = first == 0
            val to = if (clearAt < 0) null else left[clearAt].time.toLocalTime()
            val from = left[first].time.toLocalTime()
            val timing = when {
                again -> When.OnAndOff
                startsAtOnce && to == null ->
                    if (from.hour <= DAY_BEGINS) When.AllDay else When.RestOfDay
                startsAtOnce && from.hour < EARLY_BEFORE && to!!.hour <= 12 -> When.Early(to)
                startsAtOnce -> When.Until(to!!)
                to == null -> When.After(from)
                else -> When.Between(from, to)
            }
            return Wet(kind, timing)
        }

        private fun kindOfCode(code: Int): Kind? = when (code) {
            in THUNDER -> Kind.THUNDERSTORMS
            in FREEZING -> Kind.FREEZING_RAIN
            in SNOW -> if (code >= 85) Kind.SNOW_SHOWERS else Kind.SNOW
            in SHOWERS -> Kind.SHOWERS
            in DRIZZLE -> Kind.DRIZZLE
            in RAIN -> Kind.RAIN
            else -> null
        }

        /** The kind of what falls: a storm if any of it is, else freezing, else what most of it is. */
        private fun kindOf(wets: List<Hour>, day: Day): Kind {
            val kinds = wets.mapNotNull { kindOfCode(it.code) }
            return when {
                kinds.isEmpty() -> if (day.snowy) Kind.SNOW else Kind.RAIN
                Kind.THUNDERSTORMS in kinds -> Kind.THUNDERSTORMS
                Kind.FREEZING_RAIN in kinds -> Kind.FREEZING_RAIN
                else -> {
                    val snowy = kinds.count { it == Kind.SNOW || it == Kind.SNOW_SHOWERS }
                    val wetter = kinds.size - snowy
                    when {
                        // Both: neither word is the right one.
                        snowy > 0 && wetter > 0 && snowy * 3 >= kinds.size && wetter * 3 >= kinds.size -> Kind.SLEET
                        snowy > wetter -> if (kinds.count { it == Kind.SNOW_SHOWERS } * 2 > snowy) Kind.SNOW_SHOWERS else Kind.SNOW
                        else -> kinds.filter { it != Kind.SNOW && it != Kind.SNOW_SHOWERS }
                            .groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key
                    }
                }
            }
        }

        private fun windOf(day: Day, metric: Boolean): WindNote? {
            val factor = if (metric) 1.0 else MPH_TO_KMH
            val wind = day.windMax?.times(factor)
            val gust = day.gustMax?.times(factor)
            val strength = when {
                wind != null && wind >= WINDY_KMH -> Strength.WINDY
                gust != null && gust >= GUSTY_KMH -> Strength.GUSTY
                wind != null && wind >= BREEZY_KMH -> Strength.BREEZY
                else -> return null
            }
            val from = day.windFrom?.let { (((it % 360 + 360) % 360) / 45.0).roundToInt() % 8 }
            return WindNote(strength, from)
        }
    }
}
