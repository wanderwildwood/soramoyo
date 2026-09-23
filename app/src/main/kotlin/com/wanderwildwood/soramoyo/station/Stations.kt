package com.wanderwildwood.soramoyo.station

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Reads whichever station is set, into the one shape the screen draws. */
object Stations {
    suspend fun read(context: Context, source: Source): StationReading = when (source) {
        is Source.Ecowitt -> StationClient.read(source.address)
        is Source.Davis -> Davis.read(source.address)
        Source.Tempest -> Tempest.listen(context)
        is Source.Underground -> Underground.read(source.stationId, source.apiKey)
        Source.None -> throw IOException("no station")
    }

    internal val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    internal fun JSONObject.num(name: String): Double? =
        if (!has(name) || isNull(name)) null else optDouble(name).takeIf { !it.isNaN() }
}

/**
 * A Davis WeatherLink Live, over its local API: `GET /v1/current_conditions`.
 *
 * Written from Davis's published description of that API; nobody here has one. Everything it
 * reports is imperial, and rain comes as a count of bucket tips with the bucket's size beside
 * it, so both are converted here.
 */
object Davis {
    suspend fun read(address: String): StationReading = withContext(Dispatchers.IO) {
        val host = address.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        val request = Request.Builder().url("http://$host/v1/current_conditions").get().build()
        Stations.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("WeatherLink Live answered ${response.code}")
            parse(response.body?.string().orEmpty(), Units.metricHere())
        }
    }

    internal fun parse(json: String, metric: Boolean): StationReading {
        val conditions = JSONObject(json).getJSONObject("data").getJSONArray("conditions")
        val records = (0 until conditions.length()).mapNotNull { conditions.optJSONObject(it) }
        // 1 is the outdoor sensor suite, 3 the barometer. A console can carry more than one
        // suite; the first is taken.
        val iss = records.firstOrNull { it.optInt("data_structure_type") == 1 }
            ?: throw IOException("no outdoor sensors in the WeatherLink Live's answer")
        val bar = records.firstOrNull { it.optInt("data_structure_type") == 3 }

        with(Stations) {
            // What one tip of the bucket is worth: 1 is 0.01", 2 is 0.2 mm, 3 is 0.1 mm, 4 is 0.001".
            val tip: (Double?) -> Measure? = { count ->
                count?.let {
                    when (iss.optInt("rain_size")) {
                        1 -> Units.inches(it * 0.01, metric)
                        2 -> Units.millimetres(it * 0.2, metric)
                        3 -> Units.millimetres(it * 0.1, metric)
                        4 -> Units.inches(it * 0.001, metric)
                        else -> null
                    }
                }
            }
            val rate = tip(iss.num("rain_rate_last"))?.let { m ->
                // The same conversion, labelled as the hourly rate it is.
                Measure(m.number, m.unit + "/Hr", m.text)
            }
            return StationReading(
                temperature = Units.fahrenheit(iss.num("temp"), metric),
                feelsLike = Units.fahrenheit(iss.num("thw_index") ?: iss.num("heat_index"), metric),
                dewPoint = Units.fahrenheit(iss.num("dew_point"), metric),
                humidity = iss.num("hum")?.roundToInt(),
                windSpeed = Units.mph(iss.num("wind_speed_avg_last_1_min") ?: iss.num("wind_speed_last"), metric),
                windGust = Units.mph(iss.num("wind_speed_hi_last_10_min"), metric),
                windFrom = (iss.num("wind_dir_scalar_avg_last_1_min") ?: iss.num("wind_dir_last"))?.roundToInt(),
                rainToday = tip(iss.num("rainfall_daily")),
                rainRate = rate,
                raining = null,
                pressure = Units.inchesOfMercury(bar?.num("bar_sea_level"), metric),
                uvIndex = iss.num("uv_index"),
                soilMoisture = null,
            )
        }
    }
}

/**
 * A WeatherFlow Tempest, heard rather than asked: its hub broadcasts on UDP port 50222, and
 * an `obs_st` message arrives about once a minute.
 *
 * Written from WeatherFlow's published description; nobody here has one. Two things the
 * Tempest does not send, and so this cannot show: a total of the day's rain (it reports only
 * the last interval's, and adding a day up would need the app running all day), and a
 * feels-like temperature. The dew point is worked out from temperature and humidity.
 */
object Tempest {
    private const val PORT = 50222
    private const val WAIT_MS = 75_000

    suspend fun listen(context: Context): StationReading = withContext(Dispatchers.IO) {
        // Android drops broadcast packets to save battery unless an app holds this lock.
        val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        val lock = wifi?.createMulticastLock("sky-tempest")?.apply { setReferenceCounted(false); acquire() }
        try {
            DatagramSocket(null).use { socket ->
                socket.reuseAddress = true
                socket.broadcast = true
                socket.bind(InetSocketAddress(PORT))
                socket.soTimeout = WAIT_MS
                val buffer = ByteArray(4096)
                val until = System.currentTimeMillis() + WAIT_MS
                while (System.currentTimeMillis() < until) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (e: SocketTimeoutException) {
                        break
                    }
                    val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    parse(text, Units.metricHere())?.let { return@withContext it }
                }
                throw IOException("no Tempest heard")
            }
        } finally {
            lock?.release()
        }
    }

    /** An `obs_st` message as a reading; any other message the hub sends is null. */
    internal fun parse(json: String, metric: Boolean): StationReading? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        if (root.optString("type") != "obs_st") return null
        val obs: JSONArray = root.optJSONArray("obs")?.optJSONArray(0) ?: return null
        fun at(i: Int): Double? = if (i >= obs.length() || obs.isNull(i)) null else obs.optDouble(i).takeIf { !it.isNaN() }

        val tempC = at(7)
        val humidity = at(8)
        val interval = at(17)?.takeIf { it > 0 } ?: 1.0
        val lastRain = at(12)
        return StationReading(
            temperature = Units.celsius(tempC, metric),
            feelsLike = null,
            dewPoint = Units.celsius(Units.dewPointC(tempC, humidity), metric),
            humidity = humidity?.roundToInt(),
            windSpeed = Units.metresPerSecond(at(2), metric),
            windGust = Units.metresPerSecond(at(3), metric),
            windFrom = at(4)?.roundToInt(),
            rainToday = null,
            // Index 12 is the rain over the report interval, index 17 minutes long.
            rainRate = Units.millimetresPerHour(lastRain?.let { it * 60.0 / interval }, metric),
            raining = at(13)?.let { it > 0 } ?: lastRain?.let { it > 0 },
            // The station's own pressure, not adjusted to sea level.
            pressure = Units.hectopascals(at(6), metric),
            uvIndex = at(10),
            soilMoisture = null,
        )
    }
}

/**
 * Any station that uploads to Weather Underground, read back through its PWS API with the
 * owner's own key.
 *
 * Asked in the phone's units, which Weather Underground converts; its "metric" gives pressure
 * in millibars, which are hectopascals by another name. A station that has not reported for
 * an hour comes back with nothing, and so does a wrong station ID.
 */
object Underground {
    suspend fun read(stationId: String, apiKey: String): StationReading = withContext(Dispatchers.IO) {
        val metric = Units.metricHere()
        val url = "https://api.weather.com/v2/pws/observations/current".toHttpUrl().newBuilder()
            .addQueryParameter("stationId", stationId)
            .addQueryParameter("format", "json")
            .addQueryParameter("units", if (metric) "m" else "e")
            .addQueryParameter("numericPrecision", "decimal")
            .addQueryParameter("apiKey", apiKey)
            .build()
        Stations.http.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            if (response.code == 204) throw IOException("no recent data for $stationId")
            if (!response.isSuccessful) throw IOException("Weather Underground answered ${response.code}")
            parse(response.body?.string().orEmpty(), metric)
        }
    }

    internal fun parse(json: String, metric: Boolean): StationReading {
        val obs = JSONObject(json).getJSONArray("observations").getJSONObject(0)
        val u = obs.optJSONObject(if (metric) "metric" else "imperial")
            ?: throw IOException("no readings in Weather Underground's answer")
        with(Stations) {
            val t = { name: String -> if (metric) Units.celsius(u.num(name), true) else Units.fahrenheit(u.num(name), false) }
            val speed = { name: String -> if (metric) Units.metresPerSecond(u.num(name)?.div(3.6), true) else Units.mph(u.num(name), false) }
            return StationReading(
                temperature = t("temp"),
                // Heat index when it is warm, wind chill when it is cold; they agree otherwise.
                feelsLike = run {
                    val temp = u.num("temp")
                    val hi = u.num("heatIndex")
                    val wc = u.num("windChill")
                    val pick = when {
                        temp == null -> null
                        hi != null && hi > temp -> "heatIndex"
                        wc != null && wc < temp -> "windChill"
                        else -> "temp"
                    }
                    pick?.let(t)
                },
                dewPoint = t("dewpt"),
                humidity = obs.num("humidity")?.roundToInt(),
                windSpeed = speed("windSpeed"),
                windGust = speed("windGust"),
                windFrom = obs.num("winddir")?.roundToInt(),
                rainToday = if (metric) Units.millimetres(u.num("precipTotal"), true) else Units.inches(u.num("precipTotal"), false),
                rainRate = if (metric) Units.millimetresPerHour(u.num("precipRate"), true) else Units.inchesPerHour(u.num("precipRate"), false),
                raining = null,
                pressure = if (metric) Units.hectopascals(u.num("pressure"), true) else Units.inchesOfMercury(u.num("pressure"), false),
                uvIndex = obs.num("uv"),
                soilMoisture = null,
            )
        }
    }
}
