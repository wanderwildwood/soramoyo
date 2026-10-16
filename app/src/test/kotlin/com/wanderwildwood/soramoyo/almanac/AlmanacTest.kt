package com.wanderwildwood.soramoyo.almanac

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Checked against PyEphem 4.2.1, with its standard refraction, to two minutes and a degree. */
class AlmanacTest {

    private val denver = ZoneId.of("America/Denver")
    private val prague = ZoneId.of("Europe/Prague")
    private val tromso = ZoneId.of("Europe/Oslo")

    private fun near(expected: String, actual: LocalDateTime?, minutes: Long = 2) {
        val want = LocalDateTime.parse(expected)
        val gap = Duration.between(want, actual!!).abs()
        assertTrue("$actual, wanted $want", gap <= Duration.ofMinutes(minutes))
    }

    private fun near(expected: String, actual: Instant, minutes: Long) {
        val gap = Duration.between(Instant.parse(expected), actual).abs()
        assertTrue("$actual, wanted $expected", gap <= Duration.ofMinutes(minutes))
    }

    @Test
    fun theSunInDenver() {
        val sun = Almanac.sun(LocalDate.of(2026, 10, 16), denver, 39.74, -104.99)
        near("2026-10-16T07:11", sun.rise)
        near("2026-10-16T18:19", sun.set)
        near("2026-10-16T05:42", sun.astronomicalDawn)
        near("2026-10-16T19:49", sun.astronomicalDusk)
        assertTrue(sun.nauticalDawn!! > sun.astronomicalDawn && sun.civilDawn!! < sun.rise)
    }

    /** The same day as Open-Meteo's sunrise and sunset in the forecast fixture. */
    @Test
    fun theSunInPrague() {
        val sun = Almanac.sun(LocalDate.of(2026, 9, 24), prague, 50.09, 14.42)
        near("2026-09-24T06:51", sun.rise)
        near("2026-09-24T18:57", sun.set)
        near("2026-09-24T05:02", sun.astronomicalDawn)
        near("2026-09-24T20:46", sun.astronomicalDusk)
    }

    @Test
    fun midsummerInTromsoHasNoSunsetAndNoDark() {
        val sun = Almanac.sun(LocalDate.of(2026, 6, 21), tromso, 69.65, 18.96)
        assertNull(sun.rise)
        assertNull(sun.set)
        assertNull(sun.astronomicalDusk)
        assertTrue(sun.upAtNoon)
        assertTrue(sun.twilightAtMidnight)
    }

    @Test
    fun theMoonInDenver() {
        val moon = Almanac.moon(LocalDate.of(2026, 10, 16), denver, 39.74, -104.99)
        near("2026-10-16T13:16", moon.rise)
        assertEquals(127.1, moon.riseAzimuth!!, 1.0)
        near("2026-10-16T22:10", moon.set)
        assertEquals(233.5, moon.setAzimuth!!, 1.0)
        assertTrue(moon.phase.waxing)
        // PyEphem: 32.3% lit at noon there.
        assertEquals(0.32, moon.phase.lit, 0.02)
        assertEquals(MoonName.WAXING_CRESCENT, moon.phase.name(moon.principal))
    }

    /**
     * Full moon at 04:12 UTC on the 26th, which in Denver is 22:12 on the 25th: the day it
     * falls on there is named for it, and the next day is not.
     */
    @Test
    fun theFullMoonsDayIsNamedForIt() {
        val moon = Almanac.moon(LocalDate.of(2026, 10, 25), denver, 39.74, -104.99)
        near("2026-10-25T06:49", moon.set)
        assertEquals(287.0, moon.setAzimuth!!, 1.0)
        near("2026-10-25T17:34", moon.rise)
        assertEquals(69.5, moon.riseAzimuth!!, 1.0)
        assertEquals(Principal.FULL, moon.principal)
        assertEquals(MoonName.FULL, moon.phase.name(moon.principal))
        val after = Almanac.moon(LocalDate.of(2026, 10, 26), denver, 39.74, -104.99)
        assertEquals(null, after.principal)
        assertEquals(MoonName.WANING_GIBBOUS, after.phase.name(after.principal))
    }

    /** The moonset falls after midnight in Tromsø: the day before has it, this day's comes past the next. */
    @Test
    fun aMoonsetPastMidnightBelongsToTheNextDay() {
        val moon = Almanac.moon(LocalDate.of(2026, 6, 21), tromso, 69.65, 18.96)
        near("2026-06-21T00:38", moon.set, minutes = 4)
        near("2026-06-21T12:17", moon.rise, minutes = 4)
    }

    @Test
    fun principalPhases() {
        val eclipse = Almanac.phases(Instant.parse("2024-04-01T00:00:00Z"), 4)
        assertEquals(
            listOf(Principal.LAST_QUARTER, Principal.NEW, Principal.FIRST_QUARTER, Principal.FULL),
            eclipse.map { it.principal },
        )
        near("2024-04-02T03:15:00Z", eclipse[0].at, 15)
        near("2024-04-08T18:21:00Z", eclipse[1].at, 15)
        near("2024-04-15T19:13:00Z", eclipse[2].at, 15)
        near("2024-04-23T23:49:00Z", eclipse[3].at, 15)

        val coming = Almanac.phases(Instant.parse("2026-10-16T00:00:00Z"), 4)
        near("2026-10-18T16:13:00Z", coming[0].at, 15)
        near("2026-10-26T04:12:00Z", coming[1].at, 15)
        near("2026-11-01T20:28:00Z", coming[2].at, 15)
        near("2026-11-09T07:02:00Z", coming[3].at, 15)
        near("2025-01-29T12:36:00Z", Almanac.phases(Instant.parse("2025-01-25T00:00:00Z"), 1)[0].at, 15)
    }

    @Test
    fun litFraction() {
        assertEquals(0.0, Almanac.phase(Instant.parse("2024-04-08T18:21:00Z")).lit, 0.01)
        assertEquals(1.0, Almanac.phase(Instant.parse("2024-09-18T02:34:00Z")).lit, 0.01)
        assertFalse(Almanac.phase(Instant.parse("2024-09-20T00:00:00Z")).waxing)
    }
}
