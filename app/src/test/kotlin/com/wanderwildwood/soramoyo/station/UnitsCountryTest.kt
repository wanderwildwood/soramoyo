package com.wanderwildwood.soramoyo.station

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsCountryTest {
    @Test fun fahrenheitOnlyInTheThree() {
        assertFalse(Units.metricIn("US"))
        assertFalse(Units.metricIn("LR"))
        assertFalse(Units.metricIn("MM"))
        assertTrue(Units.metricIn("AU"))
        assertTrue(Units.metricIn("DE"))
        assertTrue(Units.metricIn(null))
    }
}
