package com.wanderwildwood.soramoyo.station

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.wanderwildwood.soramoyo.net.LocalHttp

/**
 * Asks the gateway for what it is reading now.
 *
 * Plain HTTP, because that is all an Ecowitt gateway serves, sent by [LocalHttp] and only to an address on the home network. The
 * timeouts are short: a gateway on the same network answers in well under a second, and a
 * phone away from home should find out quickly that it cannot reach one rather than sit
 * waiting on a screen that looks like it is loading.
 */
object StationClient {
    suspend fun read(address: String): StationReading = withContext(Dispatchers.IO) {
        StationReading.parse(LocalHttp.get(address, PATH))
    }

    /** The live-data path of the gateway; the address itself is checked in [LocalHttp]. */
    internal const val PATH = "/get_livedata_info"
}
