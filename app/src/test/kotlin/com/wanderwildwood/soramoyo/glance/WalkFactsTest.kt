package com.wanderwildwood.soramoyo.glance

import com.wanderwildwood.soramoyo.glance.WalkFacts.InForce
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkFactsTest {

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
}
