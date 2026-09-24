package com.wanderwildwood.soramoyo.forecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AirQualityTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun pragueHasPollen() {
        val air = AirQuality.parse(fixture("air-prague.json"))
        assertEquals(51, air.usAqi)
        assertEquals(25, air.europeanAqi)
        // Grass at 0.1 grains is not worth a row; mugwort at 6.6 is.
        assertEquals(listOf(Pollen.MUGWORT to 6.6), air.pollen)
    }

    /** Open-Meteo has pollen for Europe only: elsewhere every kind is null, and there is none. */
    @Test
    fun portlandHasNone() {
        val air = AirQuality.parse(fixture("air-portland.json"))
        assertEquals(30, air.usAqi)
        assertTrue(air.pollen.isEmpty())
    }

    @Test
    fun bands() {
        assertEquals(AirBand.GOOD, AirQuality.usBand(50))
        assertEquals(AirBand.MODERATE, AirQuality.usBand(51))
        assertEquals(AirBand.SENSITIVE, AirQuality.usBand(150))
        assertEquals(AirBand.EXTREME, AirQuality.usBand(301))
        assertEquals(AirBand.FAIR, AirQuality.europeanBand(25))
        assertEquals(AirBand.EXTREME, AirQuality.europeanBand(101))
    }

    @Test
    fun thePositionIsRoundedBeforeItLeaves() {
        val url = AirQuality.urlFor(51.507351, -0.127758)
        assertTrue(url, url.contains("latitude=51.51&"))
        assertTrue(url, url.contains("longitude=-0.13&"))
    }
}
