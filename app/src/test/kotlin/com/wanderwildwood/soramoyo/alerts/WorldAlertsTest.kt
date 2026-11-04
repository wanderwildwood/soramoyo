package com.wanderwildwood.soramoyo.alerts

import com.wanderwildwood.soramoyo.alerts.WorldAlerts.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime

/** Made-up services and alerts, around a made-up place at 10.00, 20.00. */
class WorldAlertsTest {

    private val now = OffsetDateTime.parse("2026-11-04T12:00:00+01:00")
    private val lat = 10.00
    private val lon = 20.00

    private fun cap(
        id: String,
        event: String = "Heavy Rain",
        severity: String = "Moderate",
        category: String = "Met",
        expires: String = "2026-11-04T18:00:00+01:00",
        area: String = "<area><areaDesc>Example Valley</areaDesc></area>",
        msgType: String = "Alert",
        status: String = "Actual",
        references: String? = null,
        infos: String? = null,
    ) = """<?xml version="1.0" encoding="UTF-8"?>
<alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
  <identifier>$id</identifier>
  <sender>alerts@example.org</sender>
  <sent>2026-11-04T11:00:00+01:00</sent>
  <status>$status</status>
  <msgType>$msgType</msgType>
  <scope>Public</scope>
  ${references?.let { "<references>$it</references>" } ?: ""}
  ${infos ?: """<info>
    <language>en</language>
    <category>$category</category>
    <event>$event</event>
    <urgency>Immediate</urgency>
    <severity>$severity</severity>
    <certainty>Observed</certainty>
    <effective>2026-11-04T11:00:00+01:00</effective>
    <expires>$expires</expires>
    <senderName>Example Met Service</senderName>
    <headline>$event in Example Valley</headline>
    <description>First line
 of the first paragraph.</description>
    <instruction>Stay indoors.</instruction>
    $area
  </info>"""}
</alert>"""

    private fun choose(vararg m: Message, language: String = "en") =
        WorldAlerts.choose(m.toList(), lat, lon, now, language)

    @Test
    fun readsAnAlert() {
        val a = choose(Message("ex-met-en", cap("one"))).single()
        assertEquals("one", a.id)
        assertEquals("Heavy Rain", a.event)
        assertEquals("Heavy Rain in Example Valley", a.headline)
        assertEquals("Example Valley", a.areas)
        assertEquals("Moderate", a.severity)
        assertEquals(OffsetDateTime.parse("2026-11-04T11:00:00+01:00"), a.onset)
        assertEquals(OffsetDateTime.parse("2026-11-04T18:00:00+01:00"), a.until)
        assertEquals("Stay indoors.", a.instruction)
        assertEquals("Example Met Service", a.sender)
        assertFalse(a.serious)
    }

    @Test
    fun earthquakesAndTsunamisAreSerious() {
        val quake = choose(Message("int-quake-en", cap("q", event = "Earthquake M5.1", severity = "Minor", category = "Geo"))).single()
        assertTrue(quake.serious)
        val wave = choose(Message("ex-tsu-en", cap("t", event = "Tsunami Watch", severity = "Unknown", category = "Safety"))).single()
        assertTrue(wave.serious)
    }

    @Test
    fun leavesOutWhatIsOverTestsAndCancellations() {
        val shown = choose(
            Message("ex-met-en", cap("over", expires = "2026-11-04T10:00:00+01:00")),
            Message("ex-met-en", cap("drill", status = "Exercise")),
            Message("ex-met-en", cap("called-off")),
            Message("ex-met-en", cap("cancel", msgType = "Cancel", references = "alerts@example.org,called-off,2026-11-04T11:00:00+01:00")),
            Message("ex-met-en", cap("kept")),
        )
        assertEquals(listOf("kept"), shown.map { it.id })
    }

    @Test
    fun anUpdateReplacesWhatItUpdates() {
        val shown = choose(
            Message("ex-met-en", cap("first")),
            Message("ex-met-en", cap("second", msgType = "Update", references = "alerts@example.org,first,2026-11-04T11:00:00+01:00")),
        )
        assertEquals(listOf("second"), shown.map { it.id })
        assertEquals(listOf("first"), shown.single().references)
    }

    @Test
    fun aShapeOnTheMapMustHoldThePlace() {
        val around = "<area><areaDesc>Here</areaDesc><polygon>9.9,19.9 9.9,20.1 10.1,20.1 10.1,19.9 9.9,19.9</polygon></area>"
        val beside = "<area><areaDesc>Next door</areaDesc><polygon>10.02,19.9 10.02,20.1 10.1,20.1 10.1,19.9 10.02,19.9</polygon></area>"
        val ring = "<area><areaDesc>Ring</areaDesc><circle>10.05,20.0 10</circle></area>"
        val farRing = "<area><areaDesc>Far ring</areaDesc><circle>11.0,20.0 10</circle></area>"
        val shown = choose(
            Message("ex-met-en", cap("around", area = around)),
            Message("ex-met-en", cap("beside", area = beside)),
            Message("ex-met-en", cap("ring", area = ring)),
            Message("ex-met-en", cap("far", area = farRing)),
        )
        assertEquals(setOf("around", "ring"), shown.map { it.id }.toSet())
    }

    @Test
    fun oneLanguageOfAServiceThatWritesTwo() {
        val both = arrayOf(
            Message("ex-met-en", cap("in-english", event = "Heavy Rain")),
            Message("ex-met-de", cap("auf-deutsch", event = "Starkregen")),
            Message("other-geo-xx", cap("quake", event = "Earthquake")),
        )
        assertEquals(setOf("in-english", "quake"), choose(*both, language = "en").map { it.id }.toSet())
        assertEquals(setOf("auf-deutsch", "quake"), choose(*both, language = "de").map { it.id }.toSet())
        // Neither the phone's language nor English: the first, so never both.
        assertEquals(1, choose(*both, language = "fr").count { it.id != "quake" })
    }

    @Test
    fun theInfoInThePhonesLanguage() {
        fun info(lang: String, event: String) =
            "<info><language>$lang</language><category>Met</category><event>$event</event><severity>Severe</severity>" +
                "<expires>2026-11-04T18:00:00+01:00</expires><area><areaDesc>Example Valley</areaDesc></area></info>"
        val xml = cap("multi", infos = info("es-ES", "Lluvia intensa") + info("en-GB", "Heavy rain"))
        assertEquals("Lluvia intensa", choose(Message("ex-met-xx", xml), language = "es").single().event)
        assertEquals("Heavy rain", choose(Message("ex-met-xx", xml), language = "de").single().event)
    }

    @Test
    fun aBrokenMessageIsLeftOut() {
        assertEquals(listOf("good"), choose(Message("ex-met-en", "<alert><oops"), Message("ex-met-en", cap("good"))).map { it.id })
    }

    @Test
    fun aDoctypeIsRefused() {
        val xml = "<?xml version=\"1.0\"?><!DOCTYPE alert [<!ENTITY x SYSTEM \"file:///etc/hostname\">]>" +
            cap("ent", event = "&x;").substringAfter("?>")
        assertTrue(choose(Message("ex-met-en", xml)).isEmpty())
    }

    @Test
    fun asksForASmallBoxAroundTheRoundedPlace() {
        assertEquals(
            "https://alerts.kde.org/alert/area?min_lat=9.99&max_lat=10.01&min_lon=19.99&max_lon=20.01",
            WorldAlerts.areaUrl(10.0012, 19.9988),
        )
    }

    @Test
    fun theSourceIsInTheAddressItWasSentOnTo() {
        assertEquals("ex-met-en", WorldAlerts.sourceOf("https://alerts.kde.org/cap/alerts/ex-met-en/2.49.0.1.xml"))
        assertEquals("", WorldAlerts.sourceOf("https://alerts.kde.org/alert/abc"))
        assertEquals(listOf("a", "b"), WorldAlerts.ids("[\"a\", \"b\"]"))
    }
}
