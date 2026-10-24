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

    @Test
    fun readsWhatTheDayFeelsLike() {
        val json = """
            {"daily":{"time":["2026-01-14","2026-01-15"],"weather_code":[3,71],
            "temperature_2m_max":[-4.6,-2.0],"temperature_2m_min":[-11.2,-6.0],
            "apparent_temperature_max":[-10.4,null],"apparent_temperature_min":[-18.7,null]}}
        """.trimIndent()
        val days = Forecast.parse(json).days
        assertEquals(-10, days[0].feelsHigh)
        assertEquals(-19, days[0].feelsLow)
        assertTrue(days[0].feelsDifferent(metric = true))
        // None given, none shown.
        assertEquals(null, days[1].feelsHigh)
        assertFalse(days[1].feelsDifferent(metric = true))
    }

    /** A wet morning is over by the afternoon: what is left of the day is dry. */
    @Test
    fun theRestOfTodayLeavesTheMorningBehind() {
        val date = LocalDate.of(2026, 9, 24)
        fun hour(h: Int, code: Int, chance: Int, rain: Double) =
            Hour(date.atTime(h, 0), code, 10, chance, rain)
        val hours = listOf(hour(7, 63, 100, 2.0), hour(8, 61, 90, 0.5), hour(14, 2, 10, 0.0), hour(15, 3, 20, 0.1)) +
            Hour(date.plusDays(1).atTime(0, 0), 65, 8, 95, 4.0)
        val day = Day(date, 63, high = 15, low = 8, rainChance = 100, rain = 2.6, sunrise = null, sunset = null)
        // 14:20 UTC in a place at UTC.
        val rest = day.restOf(hours, java.time.ZoneOffset.UTC, Instant.parse("2026-09-24T14:20:00Z"))
        assertEquals(20, rest.rainChance)
        assertEquals(0.1, rest.rain!!, 1e-9)
        // Overcast, the worst of the two hours left; tomorrow's heavy rain is not today's.
        assertEquals(3, rest.code)
        assertEquals(15, rest.high)
        // Past the last hour there is nothing to narrow to.
        assertEquals(day, day.restOf(hours, java.time.ZoneOffset.UTC, Instant.parse("2026-09-24T23:30:00Z")))
    }

    @Test
    fun readsWhatTheDayPageShows() {
        val json = """
            {"daily":{"time":["2026-01-14"],"weather_code":[73],
            "temperature_2m_max":[-4.6],"temperature_2m_min":[-11.2],
            "precipitation_hours":[7.0],"snowfall_sum":[4.2],"wind_speed_10m_max":[31.4],
            "wind_gusts_10m_max":[58.0],"wind_direction_10m_dominant":[292],"uv_index_max":[1.35]}}
        """.trimIndent()
        val day = Forecast.parse(json).days.single()
        assertEquals(7.0, day.rainHours!!, 0.0)
        assertEquals(4.2, day.snow!!, 0.0)
        assertEquals(31.4, day.windMax!!, 0.0)
        assertEquals(58.0, day.gustMax!!, 0.0)
        assertEquals(292, day.windFrom)
        assertEquals(1.35, day.uvMax!!, 0.0)
    }

    /** On a snowy day the snow is its own depth, and the rain is the rain without it. */
    @Test
    fun aSnowyDayKeepsItsSnowAndItsRainApart() {
        val json = """
            {"daily":{"time":["2026-01-14","2026-01-15"],"weather_code":[73,61],
            "temperature_2m_max":[-1.0,4.0],"temperature_2m_min":[-6.0,1.0],
            "precipitation_sum":[14.2,6.0],"snowfall_sum":[9.1,0.0],
            "rain_sum":[1.2,5.0],"showers_sum":[0.8,1.0]}}
        """.trimIndent()
        val (snowy, wet) = Forecast.parse(json).days
        assertTrue(snowy.snowy)
        assertEquals(2.0, snowy.rainOnly!!, 1e-9)
        // Rain with no snow to show is a rainy day, whatever else it is called.
        assertFalse(wet.snowy)
    }

    /** Every hour of all six days comes back, for the days' own pages, not only Today's twelve. */
    @Test
    fun asksForTheHoursOfEveryDay() {
        val url = Forecast.urlFor(51.5, -0.13, metric = true)
        assertFalse(url, url.contains("forecast_hours"))
        assertTrue(url, url.contains("forecast_days=6"))
    }

    @Test
    fun aDayThatFeelsAsItIsSaysNothingMore() {
        val day = Day(LocalDate.of(2026, 7, 1), 0, high = 75, low = 58, rainChance = null,
            sunrise = null, sunset = null, feelsHigh = 78, feelsLow = 56)
        assertFalse(day.feelsDifferent(metric = false))
        // Three degrees Fahrenheit apart is under the five a Fahrenheit day asks for; five is not.
        assertTrue(day.copy(feelsHigh = 80).feelsDifferent(metric = false))
        assertTrue(day.copy(high = 20, low = 12, feelsHigh = 23, feelsLow = 12).feelsDifferent(metric = true))
    }

    /** The position goes out to about a kilometre and no finer. */
    @Test
    fun thePositionIsRoundedBeforeItLeaves() {
        val url = Forecast.urlFor(51.507351, -0.127758, metric = false)
        assertTrue(url, url.contains("latitude=51.51&"))
        assertTrue(url, url.contains("longitude=-0.13&"))
        assertTrue(url, url.contains("temperature_unit=fahrenheit"))
        assertTrue(url, url.contains("apparent_temperature_max"))
    }

    /** A morning in Prague, fetched at 03:32 there, with showers coming. */
    private val prague = Forecast.parse(fixture("open-meteo-prague-hourly.json"))

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

    /** The gone hours are kept, for the day's own page; what is to come starts at the hour under way. */
    @Test
    fun hoursAlreadyGoneAreKeptButNotUpcoming() {
        assertEquals(LocalDateTime.of(2026, 9, 24, 3, 0), prague.hours.first().time)
        // 07:10 in Prague: from 07:00, and the fixture runs out at 16:00.
        val later = upcoming(prague.hours, prague.offset, Instant.parse("2026-09-24T05:10:00Z"))
        assertEquals(LocalDateTime.of(2026, 9, 24, 7, 0), later.first().time)
        assertEquals(10, later.size)
        assertEquals(java.time.ZoneId.of("Europe/Prague"), prague.zone)
    }

    /** Yesterday, for Today to compare against, and the sun's hours for each day's page. */
    @Test
    fun asksForYesterdayAndTheSunshine() {
        val url = Forecast.urlFor(51.5, -0.13, metric = true)
        assertTrue(url, url.contains("past_days=1"))
        assertTrue(url, url.contains("sunshine_duration"))
        val json = """
            {"daily":{"time":["2026-10-15"],"weather_code":[2],
            "temperature_2m_max":[18.0],"temperature_2m_min":[8.0],
            "sunshine_duration":[25560.0],"daylight_duration":[40320.0]}}
        """.trimIndent()
        val day = Forecast.parse(json).days.single()
        assertEquals(7.1, day.sunshine!!, 1e-9)
        assertEquals(11.2, day.daylight!!, 1e-9)
    }

    @Test
    fun theFirstTurnIsTheOneTold() {
        // 88% at eight, after a dry night. Later hours go back and forth; only the first counts.
        val hours = upcoming(prague.hours, prague.offset, Instant.parse("2026-09-24T01:32:00Z"))
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

    /**
     * A real answer (Berlin, 2026-10-17) to the request as it is made since yesterday came
     * into it: seven days from yesterday, each with its wind, its gusts and where the wind
     * comes from, at the same index as its date.
     */
    @Test
    fun windAndGustsLineUpWithTheirDaysFromYesterday() {
        val predicted = Forecast.parse(fixture("open-meteo-berlin-past-day.json"))
        val days = predicted.days

        assertEquals(7, days.size)
        assertEquals(LocalDate.of(2026, 10, 16), days[0].date)
        assertEquals(LocalDate.of(2026, 10, 17), days[1].date)
        assertEquals(9.8, days[0].windMax!!, 0.0)
        assertEquals(23.8, days[0].gustMax!!, 0.0)
        assertEquals(158, days[0].windFrom)
        assertEquals(13.4, days[1].windMax!!, 0.0)
        assertEquals(31.3, days[1].gustMax!!, 0.0)
        assertEquals(318, days[1].windFrom)
        assertEquals(LocalDate.of(2026, 10, 22), days[6].date)
        assertEquals(15.9, days[6].windMax!!, 0.0)
        assertEquals(41.4, days[6].gustMax!!, 0.0)
        assertEquals(255, days[6].windFrom)
        assertTrue(days.all { it.windMax != null && it.gustMax != null && it.windFrom != null })

        val now = predicted.current!!
        assertEquals(9.0, now.windSpeed!!, 0.0)
        assertEquals(22.0, now.windGust!!, 0.0)
        assertEquals(323, now.windFrom)
    }

    @Test
    fun readsTheQuarterHoursAndKeepsTheEmptyOnesEmpty() {
        val json = """
            {"minutely_15":{"time":["2026-05-06T10:15","2026-05-06T10:30","2026-05-06T10:45"],
            "precipitation":[0.0,null,0.4]}}
        """.trimIndent()
        val quarters = Forecast.parse(json.replace("{\"minutely", "{\"daily\":{\"time\":[],\"weather_code\":[],\"temperature_2m_max\":[],\"temperature_2m_min\":[]},\"minutely")).quarters
        assertEquals(3, quarters.size)
        assertEquals(Quarter(LocalDateTime.of(2026, 5, 6, 10, 15), 0.0), quarters[0])
        assertEquals(null, quarters[1].precipitation)
        assertEquals(0.4, quarters[2].precipitation!!, 1e-9)
    }

    @Test
    fun noQuarterHoursIsNone() {
        assertTrue(Forecast.parse(fixture("open-meteo-prague.json")).quarters.isEmpty())
    }

    @Test
    fun asksForTheQuarterHoursTooAndNothingElseNew() {
        val url = Forecast.urlFor(51.5, -0.13, metric = true)
        assertTrue(url.contains("minutely_15=precipitation"))
        assertTrue(url.contains("forecast_minutely_15=16"))
        assertTrue(url.startsWith("https://api.open-meteo.com/v1/forecast?"))
    }
}
