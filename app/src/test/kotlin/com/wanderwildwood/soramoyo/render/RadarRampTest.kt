package com.wanderwildwood.soramoyo.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarRampTest {
    // Colours from RainViewer's Universal Blue table, as unpremultiplied ARGB.
    private val drizzleTan = 0x14636159        // rain, -10 dBZ, alpha 20
    private val faintTan = 0xBEDED097.toInt()  // rain, 14 dBZ, alpha 190
    private val firstBlue = 0xFF88DDEE.toInt() // rain, 15 dBZ (where the old ramp went black)
    private val deepBlue = 0xFF004768.toInt()  // rain, 34 dBZ
    private val yellow = 0xFFFFEE00.toInt()    // rain, 35 dBZ
    private val orange = 0xFFFF8100.toInt()    // rain, 44 dBZ
    private val red = 0xFFFF4400.toInt()       // rain, 45 dBZ
    private val darkRed = 0xFFC10000.toInt()   // rain, 50 dBZ
    private val magenta = 0xFFFFAAFF.toInt()   // rain, 55 dBZ
    private val snowBlue = 0xFF7FBFFF.toInt()  // snow, 20 dBZ

    private fun grey(argb: Int): Int {
        val px = RadarRamp.pixelForIntensity(RadarRamp.intensityOf(RadarRamp.dbzOf(argb)))
        return 255 - (px ushr 24) // black at alpha a over white
    }

    @Test fun colours_read_back_to_their_dbz() {
        assertEquals(-10, RadarRamp.dbzOf(drizzleTan))
        assertEquals(14, RadarRamp.dbzOf(faintTan))
        assertEquals(15, RadarRamp.dbzOf(firstBlue))
        assertEquals(34, RadarRamp.dbzOf(deepBlue))
        assertEquals(35, RadarRamp.dbzOf(yellow))
        assertEquals(45, RadarRamp.dbzOf(red))
        assertEquals(55, RadarRamp.dbzOf(magenta))
        assertEquals(20, RadarRamp.dbzOf(snowBlue))
    }

    @Test fun transparent_is_dry() {
        assertNull(RadarRamp.dbzOf(0))
        assertNull(RadarRamp.dbzOf(0x00FFFFFF))
        assertEquals(0, RadarRamp.pixelForIntensity(RadarRamp.intensityOf(null)))
    }

    @Test fun light_rain_is_light_and_only_the_cores_are_black() {
        assertEquals(0xDD, grey(drizzleTan))
        assertEquals(0xDD, grey(faintTan))
        assertEquals(0xAA, grey(firstBlue))
        assertEquals(0x77, grey(yellow))
        assertEquals(0x44, grey(orange))
        assertEquals(0x44, grey(red))
        assertEquals(0x00, grey(darkRed))
        assertEquals(0x00, grey(magenta))
        for (dbz in -10 until RadarRamp.BLACK_FROM_DBZ) {
            assertTrue("$dbz dBZ drawn black", RadarRamp.levelOf(dbz) < RadarRamp.NUM_LEVELS - 1)
        }
    }

    @Test fun greys_are_far_enough_apart_not_to_dither_together() {
        val g = RadarRamp.BAND_GREY
        assertTrue(RadarRamp.NUM_LEVELS in 4..6)
        for (v in g) assertEquals("$v is off the panel's 16-grey grid", 0, v % 17)
        assertTrue("lightest band too close to the white map", 255 - g.first() >= 34)
        for (i in 1 until g.size) assertTrue("bands $i-1 and $i too close", g[i - 1] - g[i] >= 51)
    }

    @Test fun levels_rise_with_dbz() {
        var last = 0
        for (dbz in -10..80) {
            val l = RadarRamp.levelOf(dbz)
            assertTrue(l >= last)
            last = l
        }
        assertEquals(RadarRamp.NUM_LEVELS - 1, last)
    }

    @Test fun intensity_round_trips_every_dbz() {
        for (dbz in -10..74) {
            val v = RadarRamp.intensityOf(dbz)
            assertTrue(v in 1..255)
            assertEquals(dbz, RadarRamp.dbzOfIntensity(v))
        }
    }

    @Test fun an_unknown_colour_takes_the_nearest_entry() {
        // One off the 34 dBZ blue in each channel, as a retuned scheme might be.
        assertEquals(34, RadarRamp.dbzOf(0xFF014869.toInt()))
    }
}
