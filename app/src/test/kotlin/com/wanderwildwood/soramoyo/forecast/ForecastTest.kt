package com.wanderwildwood.soramoyo.forecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class ForecastTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun readsThreeDays() {
        val days = Forecast.parse(fixture("open-meteo-prague.json"))

        assertEquals(3, days.size)
        assertEquals(
            Day(
                date = LocalDate.of(2026, 9, 24),
                code = 80,
                high = 15,
                low = 12,
                rainChance = 78,
                sunrise = LocalDateTime.of(2026, 9, 24, 6, 51),
                sunset = LocalDateTime.of(2026, 9, 24, 18, 56),
            ),
            days[1],
        )
        // 16.6 is 17, not 16: rounded, not cut off.
        assertEquals(17, days[2].high)
    }

    @Test
    fun aDayWithNoTemperatureIsLeftOut() {
        val json = """
            {"daily":{"time":["2026-09-23","2026-09-24"],"weather_code":[3,null],
            "temperature_2m_max":[18.0,null],"temperature_2m_min":[8.8,null],
            "precipitation_probability_max":[null,null]}}
        """.trimIndent()
        val days = Forecast.parse(json)
        assertEquals(1, days.size)
        assertEquals(null, days[0].rainChance)
        assertEquals(null, days[0].sunrise)
    }

    /** The position goes out to about a kilometre and no finer. */
    @Test
    fun thePositionIsRoundedBeforeItLeaves() {
        val url = Forecast.urlFor(51.507351, -0.127758, metric = false)
        assertTrue(url, url.contains("latitude=51.51&"))
        assertTrue(url, url.contains("longitude=-0.13&"))
        assertTrue(url, url.contains("temperature_unit=fahrenheit"))
    }
}
