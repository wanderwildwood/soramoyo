package com.wanderwildwood.soramoyo.alerts

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.IOException
import java.io.StringReader
import java.time.OffsetDateTime
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Alerts for places outside the United States, from KDE's public alert server
 * (alerts.kde.org), the one FOSS Warn uses. It gathers the warnings that national weather,
 * civil protection, tsunami and earthquake services publish in the common alert format (CAP)
 * — about a hundred and fifty countries, and a worldwide earthquake feed — and answers which
 * of them cover a box on the map.
 *
 * Two steps: the ids of the alerts whose areas meet a small box around the place, then each
 * alert as its service wrote it. An alert never changes once issued (an update is a new one),
 * so each is fetched once and kept while the app runs.
 *
 * The server has no way to say a country is not covered; an empty answer is the same whether
 * all is quiet or nobody publishes there.
 */
internal object WorldAlerts {

    private const val SERVER = "https://alerts.kde.org"

    /** A box this far each side of the rounded place: about a kilometer, the place's own rounding. */
    private const val HALF_BOX = 0.01

    /** More than this at once is not a place's warnings but a fault; the rest are left. */
    private const val MOST = 40

    private val kept = object : LinkedHashMap<String, Message>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Message>?) = size > 200
    }

    /** One alert as fetched: the service it came from (its id on the server) and its CAP text. */
    internal data class Message(val source: String, val xml: String)

    fun fetch(client: OkHttpClient, userAgent: String, lat: Double, lon: Double): List<Alert> {
        val ids = get(client, userAgent, areaUrl(lat, lon)).second.let(::ids).take(MOST)
        val messages = ids.mapNotNull { id ->
            synchronized(kept) { kept[id] } ?: runCatching {
                val (url, body) = get(client, userAgent, "$SERVER/alert/$id")
                Message(sourceOf(url), body).also { synchronized(kept) { kept[id] = it } }
            }.getOrNull()
        }
        return choose(messages, round(lat), round(lon))
    }

    /** The place rounded as everything else that leaves the phone is, and a small box around it. */
    internal fun areaUrl(lat: Double, lon: Double): String {
        val la = round(lat)
        val lo = round(lon)
        fun f(d: Double) = String.format(Locale.US, "%.2f", d)
        return "$SERVER/alert/area".toHttpUrl().newBuilder()
            .addQueryParameter("min_lat", f(la - HALF_BOX))
            .addQueryParameter("max_lat", f(la + HALF_BOX))
            .addQueryParameter("min_lon", f(lo - HALF_BOX))
            .addQueryParameter("max_lon", f(lo + HALF_BOX))
            .build()
            .toString()
    }

    private fun round(d: Double) = Math.round(d * 100) / 100.0

    private fun get(client: OkHttpClient, userAgent: String, url: String): Pair<String, String> {
        val request = Request.Builder().url(url).header("User-Agent", userAgent).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("alert server answered ${response.code}")
            // The server sends an alert on to /cap/alerts/<source>/<file>.xml; the final address names the source.
            return response.request.url.toString() to response.body?.string().orEmpty()
        }
    }

    internal fun ids(json: String): List<String> {
        val array = org.json.JSONArray(json.ifBlank { "[]" })
        return (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
    }

    /** "…/cap/alerts/id-bmkg-en/2.49….xml" is from "id-bmkg-en"; anything else from "". */
    internal fun sourceOf(url: String): String {
        val parts = url.substringBefore('?').split('/')
        val i = parts.indexOf("alerts")
        return if (i >= 0 && i + 2 < parts.size) parts[i + 1] else ""
    }

    /**
     * The alerts to show from what the server sent, soonest-ending first.
     *
     * Some services publish every warning twice, once in each of their languages, as two sources
     * ("id-bmkg-id" and "id-bmkg-en"). Of those only one is kept: the phone's language, else
     * English, else the one feed that carries all its languages ("-xx"), else the first.
     * Within one alert, the part in the phone's language is read the same way.
     *
     * An alert drawn on the map (a polygon or circle) is kept only if the place is inside it;
     * one named only by region is kept as the server matched it. Tests, exercises, cancellations,
     * anything over, and anything a later message here replaces are left out.
     */
    internal fun choose(
        messages: List<Message>,
        lat: Double,
        lon: Double,
        now: OffsetDateTime = OffsetDateTime.now(),
        language: String = Locale.getDefault().language,
    ): List<Alert> {
        val sources = messages.map { it.source }.distinct()
        val wanted = sources.groupBy(::family).values.map { pickSource(it, language) }.toSet()
        val read = messages.filter { it.source in wanted }.mapNotNull { read(it.xml, lat, lon, now, language) }
        val replaced = read.flatMap { it.replaces }.toSet()
        return read
            .filter { !it.cancel && it.alert.id !in replaced }
            .map { it.alert }
            .distinctBy { it.id }
            .sortedBy { it.until ?: OffsetDateTime.MAX }
    }

    /** "id-bmkg-en" and "id-bmkg-id" are one service: the source id less its language. */
    private fun family(source: String) = source.substringBeforeLast('-', source)

    private fun pickSource(sources: List<String>, language: String): String {
        if (sources.size == 1) return sources[0]
        val sorted = sources.sorted()
        fun lang(s: String) = s.substringAfterLast('-', "")
        return sorted.firstOrNull { lang(it) == language }
            ?: sorted.firstOrNull { lang(it) == "en" }
            ?: sorted.firstOrNull { lang(it) == "xx" }
            ?: sorted[0]
    }

    /** One CAP message read: the alert, and what it replaces or calls off. */
    internal class Read(val alert: Alert, val replaces: List<String>, val cancel: Boolean)

    internal fun read(
        xml: String,
        lat: Double,
        lon: Double,
        now: OffsetDateTime,
        language: String,
    ): Read? {
        val root = runCatching { parseXml(xml) }.getOrNull() ?: return null
        if (root.text("status").let { it.isNotEmpty() && it != "Actual" }) return null
        val id = root.text("identifier").ifEmpty { return null }
        val replaces = references(root.text("references"))
        val cancel = root.text("msgType") == "Cancel"
        val info = pickInfo(root.children("info"), language) ?: return null
        val event = info.text("event").ifEmpty { info.text("headline") }.ifEmpty { return null }
        val until = time(info.text("expires"))
        if (!cancel && until != null && until.isBefore(now)) return null
        val areas = info.children("area")
        if (!cancel && !covers(areas, lat, lon)) return null
        val alert = Alert(
            id = id,
            track = null,
            event = event,
            headline = info.text("headline").takeIf { it != event }.orEmpty(),
            areas = areas.map { it.text("areaDesc") }.filter { it.isNotEmpty() }.distinct().joinToString("; "),
            severity = info.text("severity"),
            onset = time(info.text("onset")) ?: time(info.text("effective")),
            until = until,
            description = info.text("description"),
            instruction = info.text("instruction"),
            sender = info.text("senderName").ifEmpty { root.text("sender") },
            references = replaces,
            category = info.children("category").joinToString(" ") { it.textContent.trim() },
        )
        return Read(alert, replaces, cancel)
    }

    private fun pickInfo(infos: List<Element>, language: String): Element? {
        fun lang(e: Element) = e.text("language").lowercase(Locale.ROOT).substringBefore('-')
        return infos.firstOrNull { lang(it) == language }
            ?: infos.firstOrNull { lang(it) == "en" }
            ?: infos.firstOrNull()
    }

    /** CAP references are "sender,identifier,sent" triples, separated by spaces. */
    internal fun references(text: String): List<String> =
        text.split(Regex("\\s+")).mapNotNull { it.split(',').getOrNull(1)?.takeIf { s -> s.isNotEmpty() } }

    /**
     * Whether the place lies in an alert's area. An area with no shape on the map (only a
     * region's name or code) is taken as covering it: the server already matched it to the box.
     */
    internal fun covers(areas: List<Element>, lat: Double, lon: Double): Boolean {
        var anyShape = false
        for (area in areas) {
            for (p in area.children("polygon")) {
                val points = points(p.textContent)
                if (points.size < 3) continue
                anyShape = true
                if (inside(points, lat, lon)) return true
            }
            for (c in area.children("circle")) {
                val (centre, radius) = c.textContent.trim().split(Regex("\\s+")).let {
                    if (it.size < 2) return@let null to 0.0
                    points(it[0]).firstOrNull() to (it[1].toDoubleOrNull() ?: 0.0)
                }
                if (centre == null) continue
                anyShape = true
                if (km(lat, lon, centre.first, centre.second) <= radius + 1.5) return true
            }
        }
        return !anyShape
    }

    private fun points(text: String): List<Pair<Double, Double>> =
        text.trim().split(Regex("\\s+")).mapNotNull { pair ->
            val ll = pair.split(',')
            val a = ll.getOrNull(0)?.toDoubleOrNull()
            val b = ll.getOrNull(1)?.toDoubleOrNull()
            if (a == null || b == null) null else a to b
        }

    /** Ray casting, on plain latitude and longitude: near enough at the size of a warning area. */
    private fun inside(poly: List<Pair<Double, Double>>, lat: Double, lon: Double): Boolean {
        var inside = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val (yi, xi) = poly[i]
            val (yj, xj) = poly[j]
            if ((yi > lat) != (yj > lat) && lon < (xj - xi) * (lat - yi) / (yj - yi) + xi) inside = !inside
            j = i
        }
        return inside
    }

    private fun km(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(a))
    }

    private fun parseXml(xml: String): Element {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // A CAP message has no doctype; refuse one rather than follow what it points at.
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        return factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
    }

    private fun Element.children(name: String): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
            .filter { (it.localName ?: it.nodeName.substringAfter(':')) == name }
    }

    private fun Element.text(name: String): String = children(name).firstOrNull()?.textContent?.trim().orEmpty()

    private fun time(text: String): OffsetDateTime? =
        if (text.isEmpty()) null else runCatching { OffsetDateTime.parse(text) }.getOrNull()
}
