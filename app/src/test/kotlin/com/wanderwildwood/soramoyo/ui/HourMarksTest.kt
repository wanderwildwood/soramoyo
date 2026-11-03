package com.wanderwildwood.soramoyo.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HourMarksTest {
    @Test
    fun firstHourAndEachChangeOnly() {
        val skies = listOf(Weather.CLOUDY, Weather.CLOUDY, Weather.DRIZZLE, Weather.DRIZZLE, Weather.CLOUDY)
        assertEquals(listOf(Weather.CLOUDY, null, Weather.DRIZZLE, null, Weather.CLOUDY), changeMarks(skies))
    }

    @Test
    fun sunsetCountsAsAChange() {
        val skies = listOf(Weather.CLEAR, Weather.CLEAR, Weather.CLEAR_NIGHT)
        assertEquals(listOf(Weather.CLEAR, null, Weather.CLEAR_NIGHT), changeMarks(skies))
    }

    @Test
    fun nothingForNoHours() {
        assertEquals(emptyList<Weather?>(), changeMarks(emptyList()))
    }
}
