package com.wanderwildwood.soramoyo.alerts.push

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstantAlertsTest {

    @Test
    fun aDeleteTheServerWillNeverTakeIsNotTriedForever() {
        assertTrue(InstantAlerts.deleteSettled(200))
        assertTrue(InstantAlerts.deleteSettled(204))
        assertTrue(InstantAlerts.deleteSettled(404))
        assertTrue(InstantAlerts.deleteSettled(400))
        assertTrue(InstantAlerts.deleteSettled(401))
        assertTrue(InstantAlerts.deleteSettled(403))
        // A timeout, a server asking for less, or one busy or down: tried again.
        assertFalse(InstantAlerts.deleteSettled(408))
        assertFalse(InstantAlerts.deleteSettled(429))
        assertFalse(InstantAlerts.deleteSettled(500))
        assertFalse(InstantAlerts.deleteSettled(503))
    }
}
