package com.wanderwildwood.soramoyo.forecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class RainSoonTest {
    private val utc = ZoneOffset.UTC

    /** Ten past ten in the morning in a place at UTC. */
    private val now = Instant.parse("2026-05-06T10:10:00Z")
    private val first = LocalDateTime.of(2026, 5, 6, 10, 15)

    /** Sixteen quarter-hours, the first ending at 10:15, with these amounts and the rest dry. */
    private fun quarters(vararg amounts: Double?, rest: Double? = 0.0): List<Quarter> =
        (0 until 16).map { i ->
            Quarter(first.plusMinutes(15L * i), if (i < amounts.size) amounts[i] else rest)
        }

    private fun soon(q: List<Quarter>, hours: List<Hour> = emptyList(), inches: Boolean = false, at: Instant = now) =
        RainSoon.of(q, hours, inches, utc, at)

    @Test
    fun eightDryQuartersAreDryForTwoHours() {
        assertEquals(RainSoon.Dry, soon(quarters()))
    }

    @Test
    fun rainInTheFourthQuarterIsAboutThirtyFiveMinutes() {
        // The quarter ending 11:00 is the fourth; it begins at 10:45, 35 minutes from 10:10.
        val q = quarters(0.0, 0.0, 0.0, 0.6)
        assertEquals(RainSoon.Starts(35, snow = false), soon(q))
    }

    @Test
    fun minutesAreToTheNearestFive() {
        assertEquals(5, RainSoon.rounded(1))
        assertEquals(5, RainSoon.rounded(7))
        assertEquals(10, RainSoon.rounded(8))
        assertEquals(40, RainSoon.rounded(38))
        // From an hour on, the nearest ten.
        assertEquals(80, RainSoon.rounded(84))
        assertEquals(90, RainSoon.rounded(85))
    }

    @Test
    fun fallingNowThatEasesIsToldWithWhen() {
        // Wet for the quarters ending 10:15 and 10:30; the one ending 10:45 is dry and begins at 10:30.
        val q = quarters(0.5, 0.3)
        assertEquals(RainSoon.Easing(20, snow = false), soon(q))
    }

    @Test
    fun fallingForTheWholeTwoHoursIsLasting() {
        assertEquals(RainSoon.Lasting(snow = false), soon(quarters(rest = 0.4)))
    }

    @Test
    fun theQuarterAlreadyOverIsLeftOut() {
        // Wet in the quarter ending 10:00, which is behind us at 10:10.
        val q = listOf(Quarter(first.minusMinutes(15), 3.0)) + quarters()
        assertEquals(RainSoon.Dry, soon(q))
    }

    @Test
    fun aTraceBelowATenthOfAMillimetreIsNotRain() {
        assertEquals(RainSoon.Dry, soon(quarters(0.0, 0.05, 0.09, 0.0)))
        assertEquals(RainSoon.Starts(5, false), soon(quarters(0.0, 0.1)))
    }

    @Test
    fun inchesAreJudgedInInches() {
        // 0.01 in is light rain; the same number in millimetres would be dry.
        assertEquals(RainSoon.Starts(5, false), soon(quarters(0.0, 0.01), inches = true))
        assertEquals(RainSoon.Dry, soon(quarters(0.0, 0.01), inches = false))
    }

    @Test
    fun snowIsToldAsSnowFromTheHourItFallsIn() {
        val hours = listOf(Hour(LocalDateTime.of(2026, 5, 6, 10, 0), 73, 30, 90, 0.5, snow = 0.4))
        assertEquals(RainSoon.Easing(20, snow = true), soon(quarters(0.5, 0.3), hours))
    }

    @Test
    fun noQuartersAtAllIsNoLine() {
        assertNull(soon(emptyList()))
    }

    @Test
    fun missingDataIsNeverDry() {
        // All empty: outside the model's coverage.
        assertNull(soon(quarters(rest = null)))
        // Dry as far as it goes, then empty: not known to be dry for two hours.
        assertNull(soon(quarters(0.0, 0.0, 0.0, rest = null)))
        // A gap in the middle is a gap.
        assertNull(soon(quarters(0.0, null, 0.0)))
    }

    @Test
    fun rainBeforeAGapIsStillRain() {
        assertEquals(RainSoon.Starts(5, false), soon(quarters(0.0, 0.4, null, rest = null)))
    }

    @Test
    fun fallingNowWithAGapBeforeItEasesIsNotTold() {
        assertNull(soon(quarters(0.5, 0.5, rest = null)))
    }

    @Test
    fun aForecastGoneStaleHasTooFewQuartersToCallItDry() {
        val stale = quarters().take(5)
        assertNull(soon(stale))
        // An hour on, the same list has run out altogether.
        assertNull(soon(quarters(), at = Instant.parse("2026-05-06T13:00:00Z")))
        // But rain that is in the few still there is rain.
        assertEquals(RainSoon.Starts(35, false), soon(quarters(0.0, 0.0, 0.0, 0.5).take(5)))
    }
}
