package com.wanderwildwood.soramoyo.forecast

import com.wanderwildwood.soramoyo.forecast.DaySummary.Kind
import com.wanderwildwood.soramoyo.forecast.DaySummary.Sky
import com.wanderwildwood.soramoyo.forecast.DaySummary.Strength
import com.wanderwildwood.soramoyo.forecast.DaySummary.When
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

class DaySummaryTest {
    private val date = LocalDate.of(2026, 5, 6)
    private val utc = ZoneOffset.UTC

    /** Half past five in the morning, so the whole day is still to come. */
    private val dawn = Instant.parse("2026-05-06T05:30:00Z")

    private fun day(
        code: Int = 3,
        high: Int = 61,
        low: Int = 44,
        chance: Int? = null,
        wind: Double? = null,
        gust: Double? = null,
        from: Int? = null,
    ) = Day(
        date, code, high, low, chance, sunrise = null, sunset = null,
        windMax = wind, gustMax = gust, windFrom = from,
    )

    private fun hour(h: Int, code: Int, chance: Int? = 0, rain: Double? = 0.0) =
        Hour(date.atTime(h, 0), code, 50, chance, rain)

    /** Hours 6 to 22, each as [of] says. */
    private fun hours(of: (Int) -> Hour) = (6..22).map(of)

    private fun summary(day: Day, hours: List<Hour>, metric: Boolean = false, now: Instant = dawn) =
        DaySummary.of(day, hours, metric, utc, now)!!

    @Test
    fun aClearDayIsJustClearWithItsHighAndLow() {
        val s = summary(day(code = 0), hours { hour(it, 0) })
        assertEquals(Sky.CLEAR, s.sky)
        assertNull(s.wet)
        assertNull(s.wind)
        assertEquals(61, s.high)
        assertEquals(44, s.low)
    }

    @Test
    fun theSkyIsTheAverageOfWhatIsLeftOfTheDay() {
        assertEquals(Sky.MOSTLY_CLEAR, summary(day(), hours { hour(it, 1) }).sky)
        assertEquals(Sky.PARTLY_CLOUDY, summary(day(), hours { hour(it, 2) }).sky)
        assertEquals(Sky.OVERCAST, summary(day(), hours { hour(it, 3) }).sky)
        // Half partly cloudy, half overcast: between the two.
        assertEquals(Sky.MOSTLY_CLOUDY, summary(day(), hours { hour(it, if (it < 14) 2 else 3) }).sky)
    }

    @Test
    fun showersAfterThreeAreToldWithTheirHour() {
        val h = hours { if (it >= 15) hour(it, 80, 70, 0.4) else hour(it, if (it < 12) 3 else 2) }
        val s = summary(day(code = 80), h)
        assertEquals(Sky.MOSTLY_CLOUDY, s.sky)
        assertEquals(DaySummary.Wet(Kind.SHOWERS, When.After(LocalTime.of(15, 0))), s.wet)
    }

    @Test
    fun rainEarlyClearsByNoon() {
        val h = hours { if (it < 12) hour(it, 61, 80, 0.5) else hour(it, 2) }
        val s = summary(day(code = 61), h)
        assertEquals(DaySummary.Wet(Kind.RAIN, When.Early(LocalTime.of(12, 0))), s.wet)
    }

    @Test
    fun rainThatClearsInTheAfternoonIsUntilNotEarly() {
        val h = hours { if (it < 16) hour(it, 63, 80, 0.5) else hour(it, 2) }
        assertEquals(When.Until(LocalTime.of(16, 0)), summary(day(code = 63), h).wet!!.timing)
    }

    @Test
    fun rainInTheMiddleIsFromAndTo() {
        val h = hours { if (it in 10..13) hour(it, 61, 80, 0.5) else hour(it, 3) }
        assertEquals(When.Between(LocalTime.of(10, 0), LocalTime.of(14, 0)), summary(day(code = 61), h).wet!!.timing)
    }

    @Test
    fun rainAllDayIsAllDayFromTheMorningAndTheRestOfTheDayFromTheAfternoon() {
        val h = hours { hour(it, 63, 90, 1.0) }
        assertEquals(When.AllDay, summary(day(code = 63), h).wet!!.timing)
        val afternoon = Instant.parse("2026-05-06T14:10:00Z")
        assertEquals(When.RestOfDay, summary(day(code = 63), h, now = afternoon).wet!!.timing)
    }

    @Test
    fun aMorningsRainIsOverByTheAfternoon() {
        val h = hours { if (it < 11) hour(it, 61, 90, 1.0) else hour(it, 2) }
        val s = summary(day(code = 61), h, now = Instant.parse("2026-05-06T14:00:00Z"))
        assertNull(s.wet)
        assertEquals(Sky.PARTLY_CLOUDY, s.sky)
    }

    @Test
    fun twoSpellsAreOnAndOffNotJustTheFirst() {
        val h = hours { if (it < 8 || it in 17..19) hour(it, 80, 70, 0.3) else hour(it, 2) }
        assertEquals(When.OnAndOff, summary(day(code = 80), h).wet!!.timing)
    }

    @Test
    fun snowIsSnowAndSnowShowersAreSnowShowers() {
        val snow = hours { if (it >= 9) hour(it, 73, 90, 0.5) else hour(it, 3) }
        assertEquals(Kind.SNOW, summary(day(code = 73), snow).wet!!.kind)
        val showers = hours { if (it >= 9) hour(it, 85, 90, 0.5) else hour(it, 3) }
        assertEquals(Kind.SNOW_SHOWERS, summary(day(code = 85), showers).wet!!.kind)
    }

    @Test
    fun rainMixedWithSnowIsNeitherWord() {
        val h = hours { if (it % 2 == 0) hour(it, 61, 80, 0.4) else hour(it, 71, 80, 0.4) }
        assertEquals(Kind.SLEET, summary(day(code = 71), h).wet!!.kind)
    }

    @Test
    fun aStormOutranksTheRainAroundIt() {
        val h = hours { if (it == 16) hour(it, 95, 90, 2.0) else hour(it, 61, 80, 0.4) }
        assertEquals(Kind.THUNDERSTORMS, summary(day(code = 95), h).wet!!.kind)
    }

    @Test
    fun aChanceBelowEvenOddsIsNotRain() {
        val h = hours { hour(it, 3, 40, 0.2) }
        assertNull(summary(day(), h).wet)
    }

    @Test
    fun fogIsFogWhenMostOfTheDayIsIt() {
        val s = summary(day(code = 45), hours { hour(it, 45) })
        assertEquals(Sky.FOG, s.sky)
        // A little fog among clear hours is not a foggy day.
        assertEquals(Sky.MOSTLY_CLEAR, summary(day(code = 45), hours { hour(it, if (it < 8) 45 else 0) }).sky)
    }

    @Test
    fun theWindIsToldOnlyWhenItIsNotable() {
        assertNull(summary(day(wind = 15.0, gust = 25.0, from = 90), hours { hour(it, 0) }).wind)
        assertEquals(
            DaySummary.WindNote(Strength.BREEZY, 7),
            summary(day(wind = 18.0, gust = 28.0, from = 315), hours { hour(it, 0) }).wind,
        )
        assertEquals(
            DaySummary.WindNote(Strength.WINDY, 4),
            summary(day(wind = 28.0, gust = 40.0, from = 180), hours { hour(it, 0) }).wind,
        )
        // Light wind, strong gusts.
        assertEquals(
            Strength.GUSTY,
            summary(day(wind = 17.0, gust = 38.0, from = 0), hours { hour(it, 0) }).wind!!.strength,
        )
    }

    @Test
    fun theWindIsJudgedInTheUnitsItWasGivenIn() {
        // 25 km/h breezy, 25 mph windy: the same number in the other unit is another wind.
        val metric = summary(day(wind = 26.0), hours { hour(it, 0) }, metric = true)
        val imperial = summary(day(wind = 26.0), hours { hour(it, 0) }, metric = false)
        assertEquals(Strength.BREEZY, metric.wind!!.strength)
        assertEquals(Strength.WINDY, imperial.wind!!.strength)
    }

    @Test
    fun aDirectionNotGivenIsLeftOut() {
        assertNull(summary(day(wind = 30.0), hours { hour(it, 0) }).wind!!.from)
    }

    @Test
    fun theDirectionRoundsToTheNearestEighth() {
        fun from(deg: Int) = summary(day(wind = 30.0, from = deg), hours { hour(it, 0) }).wind!!.from
        assertEquals(0, from(359))
        assertEquals(0, from(20))
        assertEquals(1, from(30))
        assertEquals(6, from(270))
        assertEquals(7, from(300))
    }

    @Test
    fun noHoursLeftFallsBackOnTheDaysOwnCode() {
        val late = Instant.parse("2026-05-06T23:30:00Z")
        val s = summary(day(code = 2), hours { hour(it, 0) }, now = late)
        assertEquals(Sky.PARTLY_CLOUDY, s.sky)
        assertNull(s.wet)
        // Rain by the day's code alone: that it is likely, with no hours to say when.
        val wet = summary(day(code = 63, chance = 80), emptyList())
        assertNull(wet.sky)
        assertEquals(DaySummary.Wet(Kind.RAIN, When.Likely), wet.wet)
    }

    @Test
    fun aDayWithNoHoursAndNoLikelyRainSaysNoRain() {
        assertNull(summary(day(code = 63, chance = 30), emptyList()).wet)
    }

    @Test
    fun anUnknownCodeLeavesTheSkyOutButKeepsTheRest() {
        val s = summary(day(code = -1), emptyList())
        assertNull(s.sky)
        assertEquals(61, s.high)
    }

    @Test
    fun hoursOfOtherDaysAreIgnored() {
        val tomorrow = (6..22).map { Hour(date.plusDays(1).atTime(it, 0), 63, 50, 90, 1.0) }
        val s = summary(day(code = 0), hours { hour(it, 0) } + tomorrow)
        assertNull(s.wet)
    }

    @Test
    fun noDayIsNoSummary() {
        assertNull(DaySummary.of(null, emptyList(), true, utc, dawn))
    }
}
