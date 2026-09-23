package com.wanderwildwood.soramoyo.net

import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

/**
 * One radar timestep. [baseUrl] is `{host}{path}` — the full tile URL is built by
 * [RainViewerClient.tileUrl]. [timeSec] is the frame's unix time (seconds);
 * [nowcast] marks forecast frames (drawn/labelled differently).
 */
data class RadarFrame(val baseUrl: String, val timeSec: Long, val nowcast: Boolean)

/**
 * Minimal RainViewer Weather Maps API client (no key required, free for personal
 * use). Attribution "Weather data by RainViewer" is shown in the UI.
 *
 * See https://www.rainviewer.com/api/weather-maps-api.html
 */
object RainViewerClient {
    private const val META_URL = "https://api.rainviewer.com/public/weather-maps.json"

    /**
     * Fetch the ~2 h of past radar frames (10 min step) plus any nowcast frames.
     * `nowcast` is parsed defensively — the simplified docs don't guarantee it.
     * Returned oldest → newest, past before nowcast.
     */
    suspend fun fetchFrames(): List<RadarFrame> {
        val req = Request.Builder().url(META_URL).get().build()
        return Net.call(req) { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IOException("weather-maps ${resp.code}")
            val root = JSONObject(text)
            val host = root.getString("host")
            val radar = root.optJSONObject("radar") ?: JSONObject()
            val frames = ArrayList<RadarFrame>()
            fun collect(key: String, nowcast: Boolean) {
                val arr = radar.optJSONArray(key) ?: return
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val path = o.optString("path", "")
                    if (path.isEmpty()) continue
                    frames.add(RadarFrame(host + path, o.optLong("time", 0L), nowcast))
                }
            }
            collect("past", nowcast = false)
            collect("nowcast", nowcast = true)
            frames
        }
    }

    /**
     * Widget tile URL centered on lat/lon:
     * `{host}{path}/{size}/{z}/{lat}/{lon}/{color}/{smooth}_{snow}.png`.
     * color=2 (Universal Blue — greyscaled on e-ink), smooth=1, snow=1.
     */
    fun tileUrl(frame: RadarFrame, lat: Double, lon: Double, zoom: Int, size: Int): String =
        String.format(
            Locale.US,
            "%s/%d/%d/%.4f/%.4f/2/1_1.png",
            frame.baseUrl, size, zoom, lat, lon,
        )

    /** Download one radar tile as raw PNG bytes. */
    suspend fun fetchTile(frame: RadarFrame, lat: Double, lon: Double, zoom: Int, size: Int): ByteArray {
        val url = tileUrl(frame, lat, lon, zoom, size)
        val req = Request.Builder().url(url).get().build()
        return Net.call(req) { resp ->
            if (!resp.isSuccessful) throw IOException("tile ${resp.code}")
            resp.body?.bytes() ?: throw IOException("empty tile")
        }
    }
}
