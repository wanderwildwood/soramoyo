package com.wanderwildwood.soramoyo.station

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Asks the gateway for what it is reading now.
 *
 * Plain HTTP on the local network, because that is all an Ecowitt gateway serves. The
 * timeouts are short: a gateway on the same network answers in well under a second, and a
 * phone away from home should find out quickly that it cannot reach one rather than sit
 * waiting on a screen that looks like it is loading.
 */
object StationClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun read(address: String): StationReading = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(urlFor(address)).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("gateway answered ${response.code}")
            StationReading.parse(response.body?.string().orEmpty())
        }
    }

    /**
     * Whatever was typed into settings, as the live-data address. People copy these from a
     * router page or the WS View app, so a scheme or a trailing slash is taken off rather
     * than refused.
     */
    internal fun urlFor(address: String): String {
        val host = address.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .trimEnd('/')
        return "http://$host/get_livedata_info"
    }
}
