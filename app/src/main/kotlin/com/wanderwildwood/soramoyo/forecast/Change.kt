package com.wanderwildwood.soramoyo.forecast

import java.time.LocalDateTime

/**
 * When the next hours turn wet or dry, in one line: the question an hourly forecast is read
 * to answer. Only the first turn is told; past it the model is guessing more than knowing.
 */
sealed interface Change {
    /** Nothing likely in any of the hours. */
    data object Dry : Change

    /** Dry now, likely from [at]. */
    data class From(val at: LocalDateTime, val snow: Boolean) : Change

    /** Likely now, easing at [at]. */
    data class Until(val at: LocalDateTime, val snow: Boolean) : Change

    /** Likely in every one of the hours. */
    data class Throughout(val snow: Boolean) : Change

    companion object {
        /** Even odds or better. Below that, "likely" would be the wrong word. */
        const val LIKELY = 50

        private val SNOW = setOf(71, 73, 75, 77, 85, 86)

        /** Whether a WMO weather code is one of snow. */
        fun snow(code: Int): Boolean = code in SNOW

        /** Likely to rain or snow; an hour the model gave no chance for goes by its amount. */
        fun wet(hour: Hour): Boolean =
            hour.rainChance?.let { it >= LIKELY } ?: ((hour.rain ?: 0.0) > 0.0)

        fun of(hours: List<Hour>): Change {
            val first = hours.firstOrNull() ?: return Dry
            val turn = hours.indexOfFirst { wet(it) != wet(first) }
            // Snow if most of the wet hours say snow.
            val wetHours = hours.filter(::wet)
            val snow = wetHours.count { it.code in SNOW } * 2 > wetHours.size
            return when {
                !wet(first) && turn < 0 -> Dry
                !wet(first) -> From(hours[turn].time, snow)
                turn < 0 -> Throughout(snow)
                else -> Until(hours[turn].time, snow)
            }
        }

        /** How much the hours add up to, in the forecast's own unit. */
        fun total(hours: List<Hour>): Double = hours.sumOf { it.rain ?: 0.0 }
    }
}
