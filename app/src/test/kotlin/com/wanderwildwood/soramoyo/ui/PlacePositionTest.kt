package com.wanderwildwood.soramoyo.ui

import com.wanderwildwood.soramoyo.location.LatLon
import org.junit.Assert.assertEquals
import org.junit.Test

class PlacePositionTest {
    @Test fun southAndEast() =
        assertEquals("33.87° S, 151.21° E", position(LatLon(-33.87, 151.21), "N", "S", "E", "W"))

    @Test fun northAndWest() =
        assertEquals("40.70° N, 74.00° W", position(LatLon(40.7, -74.0), "N", "S", "E", "W"))
}
