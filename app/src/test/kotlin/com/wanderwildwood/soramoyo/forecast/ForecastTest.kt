package com.wanderwildwood.soramoyo.forecast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class ForecastTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    @Test
    fun readsThreeDays() {
        val days = Forecast.parse(fixture("open-meteo-prague.json")).days

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
        val days = Forecast.parse(json).days
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

    /** A morning in Prague, fetched at 03:32 there, with showers coming. */
    private val prague = Forecast.parse(
        fixture("open-meteo-prague-hourly.json"),
        now = Instant.parse("2026-09-24T01:32:00Z"),
    )

    @Test
    fun readsTwelveHoursFromTheOneUnderWay() {
        val hours = upcoming(prague.hours, prague.offset, Instant.parse("2026-09-24T01:32:00Z"))
        assertEquals(12, hours.size)
        // Prague's clock, not the phone's and not UTC.
        assertEquals(LocalDateTime.of(2026, 9, 24, 3, 0), hours.first().time)
        assertEquals(LocalDateTime.of(2026, 9, 24, 14, 0), hours.last().time)
        assertEquals(Hour(LocalDateTime.of(2026, 9, 24, 10, 0), 80, 13, 88, 1.4), hours[7])
        assertFalse(prague.inches)
        assertEquals(2.2, prague.days.first().rain!!, 0.0)
    }

    @Test
    fun hoursAlreadyGoneAreLeftOut() {
        val later = Forecast.parse(
            fixture("open-meteo-prague-hourly.json"),
            now = Instant.parse("2026-09-24T05:10:00Z"),
        )
        // 07:10 in Prague: from 07:00, and the fixture runs out at 16:00.
        assertEquals(LocalDateTime.of(2026, 9, 24, 7, 0), later.hours.first().time)
        assertEquals(10, later.hours.size)
        // The same, from a forecast fetched earlier and kept.
        assertEquals(later.hours, upcoming(prague.hours, prague.offset, Instant.parse("2026-09-24T05:10:00Z")))
    }

    @Test
    fun theFirstTurnIsTheOneTold() {
        // 88% at eight, after a dry night. Later hours go back and forth; only the first counts.
        val hours = prague.hours.take(HOURS)
        assertEquals(Change.From(LocalDateTime.of(2026, 9, 24, 8, 0), snow = false), Change.of(hours))
        assertEquals(1.9, Change.total(hours), 1e-9)
    }

    private fun hour(h: Int, chance: Int?, rain: Double? = 0.0, code: Int = 3) =
        Hour(LocalDateTime.of(2026, 1, 10, h, 0), code, 0, chance, rain)

    @Test
    fun changes() {
        assertEquals(Change.Dry, Change.of(listOf(hour(1, 10), hour(2, 49))))
        assertEquals(Change.Dry, Change.of(emptyList()))
        assertEquals(
            Change.Until(LocalDateTime.of(2026, 1, 10, 3, 0), snow = true),
            Change.of(listOf(hour(1, 90, code = 73), hour(2, 60, code = 71), hour(3, 20))),
        )
        assertEquals(Change.Throughout(snow = false), Change.of(listOf(hour(1, 50), hour(2, 70, code = 61))))
        // No chance given: an amount is what says it is wet.
        assertEquals(
            Change.From(LocalDateTime.of(2026, 1, 10, 2, 0), snow = false),
            Change.of(listOf(hour(1, null, 0.0), hour(2, null, 0.4))),
        )
    }

    @Test
    fun inchesAreAskedForAndNoticed() {
        assertTrue(Forecast.urlFor(50.0, 14.0, metric = false).contains("precipitation_unit=inch"))
        val json = """{"daily_units":{"precipitation_sum":"inch"},"daily":{"time":[],"weather_code":[],
            "temperature_2m_max":[],"temperature_2m_min":[]}}"""
        assertTrue(Forecast.parse(json).inches)
    }

    /** Now, as the forecast has it, for a phone with no station; and asked for in km/h or mph. */
    @Test
    fun readsTheCurrentEstimate() {
        assertEquals(
            Current(LocalDateTime.of(2026, 9, 24, 3, 30), 3, 11.7, 9.8, 67, 5.8, 12.6, 202),
            prague.current,
        )
        assertTrue(Forecast.urlFor(50.0, 14.0, metric = false).contains("wind_speed_unit=mph"))
        assertEquals(null, Forecast.parse(fixture("open-meteo-prague.json")).current)
    }
}
