package com.wanderwildwood.soramoyo.glance

import com.wanderwildwood.soramoyo.glance.WalkFacts.InForce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkFactsTest {

    @Test
    fun minutesToARainChangeCountFromNow() {
        val at = 10_000_000L
        assertEquals(Triple("clear", "starts", 30L), WalkFacts.changeNow("clear", "starts", at, at - 30 * 60_000L))
        assertEquals(Triple("rain", "stops", 0L), WalkFacts.changeNow("rain", "stops", at, at))
        // Past: no change is handed over any more.
        assertEquals(Triple("clear", "", -1L), WalkFacts.changeNow("rain", "stops", at, at + 1))
    }

    @Test
    fun aChangeAlreadyPastTurnsTheSky() {
        val at = 10_000_000L
        assertEquals(Triple("rain", "", -1L), WalkFacts.changeNow("clear", "starts", at, at + 1))
        assertEquals(Triple("snow", "", -1L), WalkFacts.changeNow("clear", "starts", at, at + 1, snow = true))
        assertEquals(Triple("clear", "", -1L), WalkFacts.changeNow("snow", "stops", at, at + 1))
        assertEquals(Triple("fog", "", -1L), WalkFacts.changeNow("fog", "stops", at, at + 1))
    }

    @Test
    fun alertKindsFromEventNames() {
        assertEquals("heat", WalkFacts.alertKind("Heat Advisory"))
        assertEquals("heat", WalkFacts.alertKind("Excessive Heat Warning"))
        assertEquals("heat", WalkFacts.alertKind("Extreme Heat Watch"))
        assertEquals("winter_storm", WalkFacts.alertKind("Winter Storm Warning"))
        assertEquals("winter_storm", WalkFacts.alertKind("Blizzard Warning"))
        assertEquals("winter_storm", WalkFacts.alertKind("Ice Storm Warning"))
        assertEquals("winter_storm", WalkFacts.alertKind("Winter Weather Advisory"))
        assertEquals("smoke", WalkFacts.alertKind("Air Quality Alert"))
        assertEquals("smoke", WalkFacts.alertKind("Dense Smoke Advisory"))
        assertEquals("other", WalkFacts.alertKind("Wind Advisory"))
        assertEquals("other", WalkFacts.alertKind("Red Flag Warning"))
    }

    @Test
    fun mostSignificantInForce() {
        val now = 1_000_000L
        assertEquals("none", WalkFacts.mostSignificant(emptyList(), now))
        // Over, and not yet begun: neither is in force.
        assertEquals("none", WalkFacts.mostSignificant(listOf(
            InForce("heat", 3, from = null, until = now - 1),
            InForce("smoke", 3, from = now + 1, until = null),
        ), now))
        // The more severe wins, whatever its kind.
        assertEquals("other", WalkFacts.mostSignificant(listOf(
            InForce("heat", 2, null, null),
            InForce("other", 4, null, null),
        ), now))
        // Among equals, a named kind before other.
        assertEquals("winter_storm", WalkFacts.mostSignificant(listOf(
            InForce("other", 3, null, null),
            InForce("winter_storm", 3, now - 10, now + 10),
        ), now))
    }

    @Test
    fun hemispheres() {
        assertEquals("north", WalkFacts.hemisphere(35.6))
        assertEquals("south", WalkFacts.hemisphere(-41.3))
        assertEquals("tropic", WalkFacts.hemisphere(0.0))
        assertEquals("tropic", WalkFacts.hemisphere(-23.43))
        assertEquals("north", WalkFacts.hemisphere(23.44))
    }

    @Test
    fun heavySnow() {
        assertFalse(WalkFacts.heavySnow(listOf(0.5, 1.9, null), listOf(71, 73)))
        assertTrue(WalkFacts.heavySnow(listOf(0.0, 2.0), listOf(3)))
        assertTrue(WalkFacts.heavySnow(emptyList(), listOf(75)))
        assertTrue(WalkFacts.heavySnow(emptyList(), listOf(2, 86)))
    }

    @Test
    fun units() {
        assertEquals(10f, WalkFacts.metresPerSecond(36.0, metric = true), 1e-4f)
        assertEquals(4.4704f, WalkFacts.metresPerSecond(10.0, metric = false), 1e-4f)
        assertEquals(-1f, WalkFacts.metresPerSecond(null, metric = true), 0f)
        assertEquals(2.54, WalkFacts.centimetres(1.0, inches = true), 1e-9)
        assertEquals(3.0, WalkFacts.centimetres(3.0, inches = false), 1e-9)
        assertEquals(0.0, WalkFacts.centimetres(null, inches = false), 1e-9)
    }

    @Test
    fun moonPhaseFraction() {
        assertEquals(0f, WalkFacts.moonPhase(0.0), 1e-6f)
        assertEquals(0.5f, WalkFacts.moonPhase(180.0), 1e-6f)
        assertEquals(0.75f, WalkFacts.moonPhase(270.0), 1e-6f)
    }

    private fun hoursFrom(start: String, zone: String, vararg hpa: Double?): List<Pair<java.time.Instant, Double?>> {
        val first = java.time.LocalDateTime.parse(start).atZone(java.time.ZoneId.of(zone)).toInstant()
        return hpa.mapIndexed { i, p -> first.plusSeconds(i * 3600L) to p }
    }

    @Test
    fun pressureFallingIsPositive() {
        val utc = java.time.ZoneId.of("UTC")
        val h = hoursFrom("2026-11-23T06:00", "UTC", 1015.0, 1014.0, 1013.0, 1012.5, 1012.0)
        // 09:30: this hour is 09:00 (1012.5), three back 06:00 (1015.0)
        assertEquals(2.5f, WalkFacts.pressureFall(h, java.time.Instant.parse("2026-11-23T09:30:00Z"), utc)!!, 0.001f)
    }

    @Test
    fun pressureRisingIsNegative() {
        val utc = java.time.ZoneId.of("UTC")
        val h = hoursFrom("2026-11-23T06:00", "UTC", 1010.0, 1011.0, 1012.0, 1013.2)
        assertEquals(-3.2f, WalkFacts.pressureFall(h, java.time.Instant.parse("2026-11-23T09:00:00Z"), utc)!!, 0.001f)
    }

    @Test
    fun pressureFallMissingHoursIsNull() {
        val utc = java.time.ZoneId.of("UTC")
        val now = java.time.Instant.parse("2026-11-23T09:10:00Z")
        assertNull(WalkFacts.pressureFall(emptyList(), now, utc))
        // the hour now unknown, then the hour three back unknown, then the hour three back not in the list
        assertNull(WalkFacts.pressureFall(hoursFrom("2026-11-23T06:00", "UTC", 1015.0, 1014.0, 1013.0, null), now, utc))
        assertNull(WalkFacts.pressureFall(hoursFrom("2026-11-23T06:00", "UTC", null, 1014.0, 1013.0, 1012.0), now, utc))
        assertNull(WalkFacts.pressureFall(hoursFrom("2026-11-23T07:00", "UTC", 1014.0, 1013.0, 1012.0), now, utc))
    }

    @Test
    fun pressureFallAcrossTheClocksGoingForwardIsStillThreeHours() {
        // New York, 2026-03-08: 02:00 became 03:00, so local 00 01 03 04 05 are five real hours in a row.
        val zone = "America/New_York"
        val z = java.time.ZoneId.of(zone)
        val h = hoursFrom("2026-03-08T00:00", zone, 1020.0, 1019.0, 1018.0, 1017.0, 1016.0)
        val now = java.time.LocalDateTime.parse("2026-03-08T04:20").atZone(z).toInstant()
        // 04:20: this hour is 04:00 EDT (1017); three real hours back is 00:00 EST (1020), not 01:00 on the wall clock
        assertEquals(3.0f, WalkFacts.pressureFall(h, now, z)!!, 0.001f)
        val next = java.time.LocalDateTime.parse("2026-03-08T05:20").atZone(z).toInstant()
        // 05:20: 05:00 EDT (1016) back to 01:00 EST (1019)
        assertEquals(3.0f, WalkFacts.pressureFall(h, next, z)!!, 0.001f)
    }

    @Test
    fun pressureFallInAHalfHourZone() {
        // India: the hours start on the half hour in UTC; "this hour" is the place's own.
        val zone = "Asia/Kolkata"
        val z = java.time.ZoneId.of(zone)
        val h = hoursFrom("2026-11-23T00:00", zone, 1010.0, 1009.5, 1009.0, 1008.0)
        val now = java.time.LocalDateTime.parse("2026-11-23T03:40").atZone(z).toInstant()
        assertEquals(2.0f, WalkFacts.pressureFall(h, now, z)!!, 0.001f)
    }

    @Test
    fun pressureKeptSurvivesTheCacheAndLeavesOutWhatIsUnknown() {
        val now = java.time.Instant.parse("2026-11-23T09:10:00Z")
        val h = hoursFrom("2026-11-23T00:00", "UTC", 1000.0, 1001.0, 1002.0, null, 1004.0, 1005.0, 1006.0, 1007.0, 1008.0, 1009.0, 1010.0)
        val back = WalkFacts.pressureRead(WalkFacts.pressureKept(h, now))
        // 03:00 (six back) to 10:00 (one ahead) are kept; the unknown 03:00 is not
        assertEquals(listOf(1004.0, 1005.0, 1006.0, 1007.0, 1008.0, 1009.0, 1010.0), back.map { it.second })
        assertEquals(emptyList<Any>(), WalkFacts.pressureRead(null))
        assertEquals(emptyList<Any>(), WalkFacts.pressureRead("junk,1=,=2,3=x"))
    }
}
