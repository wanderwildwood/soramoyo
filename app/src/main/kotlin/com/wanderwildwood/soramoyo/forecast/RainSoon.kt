package com.wanderwildwood.soramoyo.forecast

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * One quarter-hour of the forecast. Open-Meteo gives each as the total for the quarter-hour
 * that ends at [time], in the place's own time. [precipitation] is null where the model has
 * nothing for it, which is not the same as none.
 */
data class Quarter(val time: LocalDateTime, val precipitation: Double?)

/** What the next two hours hold, in the one line that is asked for before going out. */
sealed interface RainSoon {
    /** Nothing in any of the next two hours. */
    data object Dry : RainSoon

    /** Dry now; falling in about [minutes]. */
    data class Starts(val minutes: Int, val snow: Boolean) : RainSoon

    /** Falling now; easing in about [minutes]. */
    data class Easing(val minutes: Int, val snow: Boolean) : RainSoon

    /** Falling now and still falling two hours on. */
    data class Lasting(val snow: Boolean) : RainSoon

    companion object {
        /** How many quarter-hours make two hours. */
        const val WINDOW = 8

        /** Under this much in a quarter-hour it is not rain: 0.1 mm, or the 0.01 in that is the nearest an inch can say. */
        private const val WET_MM = 0.1
        private const val WET_IN = 0.004

        /**
         * The next two hours from [now], or null where they cannot be told: no quarter-hours
         * at all, too few left in a forecast gone stale, or a gap in them before the answer is
         * known. A line that says "dry" says it of every one of the eight quarter-hours, never of
         * one the model left out. [hours] say whether what falls is snow.
         */
        fun of(
            quarters: List<Quarter>,
            hours: List<Hour>,
            inches: Boolean,
            offset: ZoneOffset,
            now: Instant = Instant.now(),
        ): RainSoon? {
            val at = LocalDateTime.ofInstant(now, offset)
            // The quarter-hour that ends after now is the one under way.
            val ahead = quarters.filter { it.time.isAfter(at) }.sortedBy { it.time }.take(WINDOW)
            val limit = if (inches) WET_IN else WET_MM
            fun wet(q: Quarter) = q.precipitation!! >= limit

            // The first quarter-hour the model has nothing for ends what is known.
            val known = ahead.takeWhile { it.precipitation != null }
            val firstWet = known.indexOfFirst(::wet)
            val full = ahead.size == WINDOW && known.size == WINDOW

            fun snowAt(time: LocalDateTime): Boolean {
                val hour = hours.firstOrNull { it.time == time.truncatedTo(ChronoUnit.HOURS) } ?: return false
                return Change.snow(hour.code) || (hour.snow ?: 0.0) > 0.0
            }
            // A quarter-hour's minutes are of the stretch before its time: it begins 15 earlier.
            fun minutesTo(from: Quarter): Int {
                val begins = from.time.minusMinutes(15)
                return ChronoUnit.MINUTES.between(at, begins).toInt().coerceAtLeast(0)
            }

            return when {
                firstWet < 0 -> if (full) Dry else null
                firstWet == 0 -> {
                    val dry = known.indexOfFirst { !wet(it) }
                    val snow = snowAt(known[0].time.minusMinutes(1))
                    when {
                        dry >= 0 -> Easing(rounded(minutesTo(known[dry])), snow)
                        full -> Lasting(snow)
                        else -> null
                    }
                }
                else -> Starts(rounded(minutesTo(known[firstWet])), snowAt(known[firstWet].time.minusMinutes(1)))
            }
        }

        /** Said to the nearest five minutes, to the nearest ten from an hour on, and never none. */
        fun rounded(minutes: Int): Int {
            val to = if (minutes >= 60) 10 else 5
            return ((minutes + to / 2) / to * to).coerceAtLeast(5)
        }
    }
}
