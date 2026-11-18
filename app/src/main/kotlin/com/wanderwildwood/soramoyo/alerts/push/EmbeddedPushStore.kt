package com.wanderwildwood.soramoyo.alerts.push

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * What the built-in push distributor remembers: the id Mozilla's push server knows this
 * phone by, and one channel per registration the connector has made with it.
 *
 * A registration is keyed by the connector's token. Each gets a channel id of its own,
 * which is what the push server knows it by, and the endpoint the server gave it once
 * the server has answered. From Whereabouts.
 */
internal class EmbeddedPushStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Channel(val token: String, val channelId: String, val vapid: String?, val endpoint: String?)

    /** The push server's id for this phone. When it changes, every endpoint is void. */
    var uaid: String?
        get() = prefs.getString(KEY_UAID, null)
        set(value) = prefs.edit().apply { if (value == null) remove(KEY_UAID) else putString(KEY_UAID, value) }.apply()

    fun channels(): List<Channel> = prefs.all.keys
        .filter { it.startsWith(PREFIX_CHANNEL) }
        .mapNotNull { key -> channelForToken(key.removePrefix(PREFIX_CHANNEL)) }

    fun hasChannels(): Boolean = prefs.all.keys.any { it.startsWith(PREFIX_CHANNEL) }

    fun channelForToken(token: String): Channel? {
        val channelId = prefs.getString(PREFIX_CHANNEL + token, null) ?: return null
        return Channel(
            token = token,
            channelId = channelId,
            vapid = prefs.getString(PREFIX_VAPID + channelId, null),
            endpoint = prefs.getString(PREFIX_ENDPOINT + channelId, null),
        )
    }

    fun channelForId(channelId: String): Channel? = channels().firstOrNull { it.channelId == channelId }

    /**
     * The channel for [token], made if there is none. A different VAPID key from the one
     * it was registered with gets a new channel id in place of the old one, because the server
     * binds a channel to its key; [replaced] is told the old channel, to unregister it. The
     * token keeps a channel the whole time, so the connection is never dropped for the change.
     */
    fun channelFor(token: String, vapid: String?, replaced: (Channel) -> Unit = {}): Channel {
        val existing = channelForToken(token)
        if (existing != null && existing.vapid == vapid) return existing
        val channelId = UUID.randomUUID().toString()
        prefs.edit().apply {
            if (existing != null) {
                remove(PREFIX_VAPID + existing.channelId)
                remove(PREFIX_ENDPOINT + existing.channelId)
            }
            putString(PREFIX_CHANNEL + token, channelId)
            if (vapid == null) remove(PREFIX_VAPID + channelId) else putString(PREFIX_VAPID + channelId, vapid)
        }.commit()
        if (existing != null) replaced(existing)
        return channelForToken(token)!!
    }

    fun setEndpoint(channelId: String, endpoint: String) =
        prefs.edit().putString(PREFIX_ENDPOINT + channelId, endpoint).apply()

    /** A new uaid means the server has forgotten every channel: all of them re-register. */
    fun forgetEndpoints() = prefs.edit().apply {
        prefs.all.keys.filter { it.startsWith(PREFIX_ENDPOINT) }.forEach { remove(it) }
    }.apply()

    fun remove(token: String): Channel? {
        val channel = channelForToken(token) ?: return null
        prefs.edit()
            .remove(PREFIX_CHANNEL + token)
            .remove(PREFIX_VAPID + channel.channelId)
            .remove(PREFIX_ENDPOINT + channel.channelId)
            .commit()
        return channel
    }

    companion object {
        private const val PREFS = "embedded_push"
        private const val KEY_UAID = "uaid"
        private const val PREFIX_CHANNEL = "channel."
        private const val PREFIX_VAPID = "vapid."
        private const val PREFIX_ENDPOINT = "endpoint."
    }
}
