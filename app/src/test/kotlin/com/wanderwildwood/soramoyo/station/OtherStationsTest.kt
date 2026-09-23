package com.wanderwildwood.soramoyo.station

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The three sources nobody here owns, checked against the examples in their makers' own
 * documentation. That proves the fields are read from where the documents say they are; it
 * does not prove a real device sends what its document says. (Davis's example is not even
 * plausible weather — 1% humidity — but its fields are where they belong.)
 */
class OtherStationsTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun tempestInMetric() {
        val r = Tempest.parse(fixture("tempest-obs_st-doc.json"), metric = true)!!
        assertEquals("22.4", r.temperature!!.text)
        assertEquals("C", r.temperature!!.unit)
        assertEquals(50, r.humidity)
        assertEquals(144, r.windFrom)
        assertEquals("0.8", r.windSpeed!!.text) // 0.22 m/s
        assertEquals("km/h", r.windSpeed!!.unit)
        assertEquals("1017.6", r.pressure!!.text)
        assertEquals(false, r.raining)
        // The Tempest sends no day's total and no feels-like; neither is made up.
        assertNull(r.rainToday)
        assertNull(r.feelsLike)
        assertEquals(11.5, r.dewPoint!!.number, 0.2)
    }

    @Test
    fun tempestInImperial() {
        val r = Tempest.parse(fixture("tempest-obs_st-doc.json"), metric = false)!!
        assertEquals("72.3", r.temperature!!.text)
        assertEquals("F", r.temperature!!.unit)
        assertEquals("30.05", r.pressure!!.text)
        assertEquals("inHg", r.pressure!!.unit)
    }

    @Test
    fun tempestIgnoresEverythingButAnObservation() {
        assertNull(Tempest.parse("""{"type":"rapid_wind","ob":[1493322445,2.3,128]}""", metric = true))
        assertNull(Tempest.parse("not json", metric = true))
    }

    @Test
    fun davisInImperial() {
        val r = Davis.parse(fixture("davis-current-doc.json"), metric = false)
        assertEquals("62.7", r.temperature!!.text)
        assertEquals(1, r.humidity)
        assertEquals("4.0", r.windSpeed!!.text) // the one-minute average, not the last sample
        assertEquals("8.0", r.windGust!!.text)
        assertEquals(15, r.windFrom)
        // 63 tips of a 0.2 mm bucket.
        assertEquals(12.6 / 25.4, r.rainToday!!.number, 0.001)
        assertEquals("in", r.rainToday!!.unit)
        assertEquals("30.01", r.pressure!!.text)
        assertEquals(5.5, r.uvIndex!!, 0.0)
    }

    @Test
    fun davisInMetric() {
        val r = Davis.parse(fixture("davis-current-doc.json"), metric = true)
        assertEquals("17.1", r.temperature!!.text)
        assertEquals("12.6", r.rainToday!!.text)
        assertEquals("mm", r.rainToday!!.unit)
        assertEquals("1016.2", r.pressure!!.text)
    }

    @Test
    fun undergroundInImperial() {
        val r = Underground.parse(fixture("wu-pws-current-doc.json"), metric = false)
        assertEquals("53.0", r.temperature!!.text)
        assertEquals("53.0", r.feelsLike!!.text)
        assertEquals("44.0", r.dewPoint!!.text)
        assertEquals(71, r.humidity)
        assertEquals(329, r.windFrom)
        assertNull(r.windGust)
        assertEquals("30.09", r.pressure!!.text)
        assertEquals("0.00", r.rainToday!!.text)
        assertEquals(false, r.metric)
    }
}
