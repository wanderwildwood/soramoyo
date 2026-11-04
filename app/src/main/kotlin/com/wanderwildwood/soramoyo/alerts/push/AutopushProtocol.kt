package com.wanderwildwood.soramoyo.alerts.push

import org.json.JSONArray
import org.json.JSONObject

/**
 * The WebSocket protocol spoken by Mozilla's push server (autopush).
 *
 * Adapted from Sunup (https://codeberg.org/Sunup/android, Apache-2.0), commit fe768d0
 * "1.3.3": api/data/ClientMessage.kt and api/data/ServerMessage.kt, by way of Whereabouts'
 * copy. Sunup uses kotlinx.serialization; this uses org.json, which Android carries. The
 * field names and the "{}" ping are Sunup's, which took them from autopush-rs.
 */
internal object AutopushProtocol {

    /** Messages this app sends. */
    object Client {
        fun hello(uaid: String?): String = JSONObject().apply {
            put("messageType", "hello")
            if (uaid != null) put("uaid", uaid)
        }.toString()

        fun register(channelId: String, vapid: String?): String = JSONObject().apply {
            put("messageType", "register")
            put("channelID", channelId)
            if (vapid != null) put("key", vapid)
        }.toString()

        fun unregister(channelId: String): String = JSONObject().apply {
            put("messageType", "unregister")
            put("channelID", channelId)
        }.toString()

        fun ack(channelId: String, version: String): String = JSONObject().apply {
            put("messageType", "ack")
            put("updates", JSONArray().put(JSONObject().put("channelID", channelId).put("version", version)))
        }.toString()

        /** autopush treats an empty object as a ping, and answers one with one. */
        const val PING = "{}"
    }

    /** Messages the server sends. */
    sealed class Server {
        data class Hello(val uaid: String, val status: Int) : Server()
        data class Register(val channelId: String, val status: Int, val endpoint: String?) : Server()
        data class Unregister(val channelId: String) : Server()

        /** [data] is base64url, and absent when the push had no body. */
        data class Notification(val channelId: String, val version: String, val data: String?) : Server()
        data object Ping : Server()

        /** Broadcasts, urgency replies and anything newer: nothing to do. */
        data class Other(val type: String) : Server()

        companion object {
            /** Returns null for anything that is not a JSON object. */
            fun parse(text: String): Server? {
                val obj = try {
                    JSONObject(text)
                } catch (_: Exception) {
                    null
                } ?: return null

                if (obj.length() == 0) return Ping

                fun str(name: String): String? =
                    if (obj.has(name) && !obj.isNull(name)) obj.optString(name) else null

                fun int(name: String): Int = obj.optInt(name, 0)

                return when (val type = str("messageType")) {
                    "hello" -> Hello(str("uaid") ?: return null, int("status"))
                    "register" -> Register(str("channelID") ?: return null, int("status"), str("pushEndpoint"))
                    "unregister" -> Unregister(str("channelID") ?: return null)
                    "notification" -> Notification(
                        str("channelID") ?: return null,
                        str("version") ?: return null,
                        str("data"),
                    )
                    "ping" -> Ping
                    null -> null
                    else -> Other(type)
                }
            }
        }
    }

    /**
     * How long to wait before reconnecting after [failures] failures in a row.
     *
     * The first six steps are Sunup's (from the UnifiedPush distributor library,
     * https://codeberg.org/UnifiedPush/android-distributor, Apache-2.0, commit 6daaf14
     * "0.7.6", SourceManager.getTimeout); after that, every 15 minutes.
     */
    fun retryDelayMillis(failures: Int): Long = when {
        failures <= 1 -> 1_000L
        failures == 2 -> 5_000L
        failures == 3 -> 20_000L
        failures == 4 -> 60_000L
        failures == 5 -> 300_000L
        failures == 6 -> 600_000L
        else -> 900_000L
    }
}
