package com.wanderwildwood.soramoyo.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** Single shared OkHttp client for all RainViewer REST/tile calls (no Play Services). */
object Net {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Execute [request] and handle the response entirely on the IO dispatcher, so
     * body reads never touch the main thread. The response is auto-closed after
     * [handle] returns.
     */
    suspend fun <T> call(request: Request, handle: (Response) -> T): T =
        withContext(Dispatchers.IO) {
            client.newCall(request).execute().use { handle(it) }
        }
}
