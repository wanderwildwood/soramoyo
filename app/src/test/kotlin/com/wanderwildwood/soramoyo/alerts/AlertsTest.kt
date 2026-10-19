package com.wanderwildwood.soramoyo.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

class AlertsTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource(name)!!.readText()

    private val noon = OffsetDateTime.parse("2026-10-19T12:00:00-07:00")

    private fun alerts() = Alerts.parse(fixture("nws-alerts-made-up.json"), now = noon)

    @Test
    fun keepsWhatIsInEffectSoonestEndingFirst() {
        // The flood warning is over, the test is a test, the flash flood warning was cancelled.
        assertEquals(listOf("Wind Advisory", "Red Flag Warning"), alerts().map { it.event })
    }

    @Test
    fun readsAnAlert() {
        val red = alerts().single { it.event == "Red Flag Warning" }
        assertEquals("Lower Valley; Eastern Foothills", red.areas)
        assertEquals(OffsetDateTime.parse("2026-10-19T11:00:00-07:00"), red.onset)
        // The end of the warning, not the end of this one message.
        assertEquals(OffsetDateTime.parse("2026-10-19T20:00:00-07:00"), red.until)
        assertEquals("KXXX.FW.W.0042", red.track)
        assertEquals(listOf("urn:oid:2.49.0.1.840.0.0000.001.1"), red.references)
        assertEquals("NWS Example Office", red.sender)
    }

    @Test
    fun noEndMeansTheMessagesOwnExpiry() {
        val wind = alerts().single { it.event == "Wind Advisory" }
        assertEquals(OffsetDateTime.parse("2026-10-19T14:00:00-07:00"), wind.until)
        assertNull(wind.track)
        assertEquals("", wind.instruction)
    }

    @Test
    fun aRedFlagWarningIsSeriousThoughOnlyModerate() {
        val (wind, red) = alerts()
        assertTrue(red.serious)
        assertFalse(wind.serious)
        assertTrue(red.copy(event = "Fire Weather Watch").serious)
        assertTrue(wind.copy(event = "Evacuation Immediate").serious)
        assertTrue(wind.copy(severity = "Extreme").serious)
        assertTrue(wind.copy(severity = "Severe").serious)
    }

    @Test
    fun anAnswerWithNoFeaturesIsNoAlerts() {
        assertEquals(emptyList<Alert>(), Alerts.parse("""{"type":"FeatureCollection","features":[]}"""))
        assertEquals(emptyList<Alert>(), Alerts.parse("{}"))
    }

    @Test
    fun theTextIsUnwrappedIntoParagraphs() {
        val red = alerts().single { it.event == "Red Flag Warning" }
        assertEquals(
            listOf(
                "* AFFECTED AREA...Lower Valley and Eastern Foothills.",
                "* WINDS...Northeast 15 to 25 mph with gusts up to 45 mph.",
                "* RELATIVE HUMIDITY...As low as 8 percent.",
            ),
            Alerts.paragraphs(red.description),
        )
        assertEquals(emptyList<String>(), Alerts.paragraphs(""))
    }

    @Test
    fun trackIsTheWarningNotTheMessage() {
        assertEquals("KSGX.BH.S.0015", Alerts.track("/O.EXT.KSGX.BH.S.0015.000000T0000Z-261021T0600Z/"))
        assertEquals(
            Alerts.track("/O.NEW.KXXX.FW.W.0042.261019T1800Z-261020T0300Z/"),
            Alerts.track("/O.EXT.KXXX.FW.W.0042.000000T0000Z-261020T0500Z/"),
        )
        assertNull(Alerts.track("nonsense"))
    }

    @Test
    fun onlyNewSeriousAlertsNotify() {
        val (wind, red) = alerts()
        assertEquals(listOf(red), AlertNotices.fresh(listOf(wind, red), emptySet()))
        // Seen before under this message's name, its warning's, or the one it updates.
        assertEquals(emptyList<Alert>(), AlertNotices.fresh(listOf(red), setOf(red.id)))
        assertEquals(emptyList<Alert>(), AlertNotices.fresh(listOf(red.copy(id = "new")), setOf("KXXX.FW.W.0042")))
        assertEquals(emptyList<Alert>(), AlertNotices.fresh(listOf(red), setOf("urn:oid:2.49.0.1.840.0.0000.001.1")))
    }

    @Test
    fun thePositionGoesRounded() {
        assertEquals(
            "https://api.weather.gov/alerts/active?point=39.74%2C-104.99&status=actual",
            Alerts.urlFor(39.7392, -104.9903),
        )
    }
}
