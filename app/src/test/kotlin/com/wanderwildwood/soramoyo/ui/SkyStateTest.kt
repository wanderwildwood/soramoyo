package com.wanderwildwood.soramoyo.ui

import com.wanderwildwood.soramoyo.location.Place
import com.wanderwildwood.soramoyo.station.Measure
import com.wanderwildwood.soramoyo.station.Source
import com.wanderwildwood.soramoyo.station.StationReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyStateTest {

    private val home = Place("Portland, Maine, United States", 43.66, -70.26)
    private val work = Place("Bangor, Maine, United States", 44.80, -68.77)
    private val reading = StationReading(
        temperature = Measure(12.0, "C", "12.0"),
        feelsLike = null, dewPoint = null, humidity = 80, windSpeed = null, windGust = null,
        windFrom = null, rainToday = null, rainRate = null, raining = true, pressure = null,
        uvIndex = null, soilMoisture = null,
    )
    private val withStation = SkyState(
        source = Source.Ecowitt("192.0.2.10"),
        reading = reading,
        readAt = 1_000L,
        place = home,
        second = work,
    )

    @Test
    fun theFirstPlaceKeepsItsStation() {
        val s = withStation.copy(onSecond = false)
        assertFalse(s.showingSecond)
        assertEquals(home, s.shown)
        assertSame(s, s.forScreen())
    }

    /** The station is at the first place; its reading must not stand under the second's forecast. */
    @Test
    fun theSecondPlaceShowsNoStation() {
        val s = withStation.copy(onSecond = true).forScreen()
        assertEquals(work, s.shown)
        assertEquals(Source.None, s.source)
        assertNull(s.reading)
        assertEquals(StationTrouble.NOT_SET, s.stationTrouble)
    }

    @Test
    fun withNoSecondPlaceNothingChanges() {
        val s = withStation.copy(second = null, onSecond = true)
        assertFalse(s.showingSecond)
        assertEquals(home, s.shown)
        assertSame(s, s.forScreen())
    }

    /** The first place can be the phone's own position, which [SkyState.shown] gives as null. */
    @Test
    fun thePhoneAndASecondPlace() {
        val s = withStation.copy(place = null, onSecond = true)
        assertTrue(s.showingSecond)
        assertEquals(work, s.shown)
        assertNull(s.copy(onSecond = false).shown)
    }
}
