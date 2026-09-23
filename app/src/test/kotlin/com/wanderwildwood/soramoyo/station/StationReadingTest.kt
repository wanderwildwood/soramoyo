package com.wanderwildwood.soramoyo.station

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StationReadingTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    /** A GW3000 with a WS90, exactly as it answered: Fahrenheit, mph, inches. */
    @Test
    fun readsARealGw3000() {
        val r = StationReading.parse(fixture("gw3000-livedata.json"))

        assertEquals(Measure(69.4, "F", "69.4"), r.temperature)
        assertEquals(Measure(69.4, "F", "69.4"), r.feelsLike)
        assertEquals(Measure(67.1, "F", "67.1"), r.dewPoint)
        assertEquals(92, r.humidity)
        assertEquals(Measure(0.0, "mph", "0.00"), r.windSpeed)
        assertEquals(Measure(0.0, "mph", "0.00"), r.windGust)
        // The ten-minute average, not the instant vane reading beside it (230).
        assertEquals(224, r.windFrom)
        assertEquals(Measure(0.51, "in", "0.51"), r.rainToday)
        assertEquals(Measure(0.0, "in/Hr", "0.00"), r.rainRate)
        assertEquals(false, r.raining)
        assertEquals(Measure(28.61, "inHg", "28.61"), r.pressure)
        assertEquals(0.0, r.uvIndex!!, 0.0)
        assertFalse(r.metric!!)
    }

    @Test
    fun aSensorBetweenReadingsIsNull() {
        val r = StationReading.parse(
            """{"common_list":[{"id":"0x02","val":"--","unit":"C"},{"id":"0x07","val":"--%"}]}""",
        )
        assertNull(r.temperature)
        assertNull(r.humidity)
        assertNull(r.metric)
    }

    @Test
    fun aMissingSensorIsNullAndNothingElseBreaks() {
        val r = StationReading.parse("""{"common_list":[{"id":"0x02","val":"21.4","unit":"C"}]}""")
        assertEquals(Measure(21.4, "C", "21.4"), r.temperature)
        assertTrue(r.metric!!)
        assertNull(r.rainToday)
        assertNull(r.pressure)
        assertNull(r.soilMoisture)
        assertNull(r.raining)
    }

    /** A tipping-bucket station reports rain under "rain", with the same ids. */
    @Test
    fun readsATippingBucket() {
        val r = StationReading.parse(
            """{"rain":[{"id":"0x0E","val":"1.2 mm/Hr"},{"id":"0x10","val":"12.9 mm"}]}""",
        )
        assertEquals(Measure(12.9, "mm", "12.9"), r.rainToday)
        assertEquals(Measure(1.2, "mm/Hr", "1.2"), r.rainRate)
        // A bucket cannot say whether it is raining this moment, only how much has fallen.
        assertNull(r.raining)
    }

    @Test
    fun readsASoilProbe() {
        val r = StationReading.parse(
            """{"ch_soil":[{"channel":"1","name":"","battery":"5","humidity":"42%"}]}""",
        )
        assertEquals(42, r.soilMoisture)
    }

    @Test
    fun anAddressIsTakenHoweverItWasCopied() {
        val want = "http://192.168.1.50/get_livedata_info"
        assertEquals(want, StationClient.urlFor("192.168.1.50"))
        assertEquals(want, StationClient.urlFor(" http://192.168.1.50/ "))
        assertEquals(want, StationClient.urlFor("https://192.168.1.50"))
    }
}
