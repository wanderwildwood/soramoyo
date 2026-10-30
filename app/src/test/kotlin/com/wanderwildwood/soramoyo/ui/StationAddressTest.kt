package com.wanderwildwood.soramoyo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StationAddressTest {
    @Test fun `an internet number is caught`() {
        assertTrue(isPublicIpAddress("8.8.8.8"))
        assertTrue(isPublicIpAddress("http://203.0.113.5:80/data"))
    }

    @Test fun `home numbers and names are not`() {
        assertFalse(isPublicIpAddress("192.168.1.20"))
        assertFalse(isPublicIpAddress("10.0.0.7:8080"))
        assertFalse(isPublicIpAddress("172.20.1.1"))
        assertFalse(isPublicIpAddress("ecowitt.local"))
        assertFalse(isPublicIpAddress(""))
    }
}
