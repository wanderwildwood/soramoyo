package com.wanderwildwood.soramoyo.alerts.push

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.wanderwildwood.soramoyo.alerts.AlertNotices
import com.wanderwildwood.soramoyo.alerts.Alerts
import com.wanderwildwood.soramoyo.location.LatLon
import com.wanderwildwood.soramoyo.location.LocationProvider
import com.wanderwildwood.soramoyo.location.Places
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Instant alerts: KDE's public alert server tells Sky the moment an alert is issued for the
 * place, instead of Sky finding it at its next quarter-hourly look.
 *
 * Sky subscribes an area around the place with the server, giving it a push address from
 * Mozilla's push server, reached through Sky's own built-in distributor ([EmbeddedPush]) so
 * no other app is needed. The server encrypts every message to keys only this phone holds
 * (the UnifiedPush connector makes them and opens the messages). A message says no more than
 * that an alert was added or changed; Sky then looks, exactly as the quarter-hourly check does,
 * and notifies as that does. The quarter-hourly check stays on underneath, in case a push is
 * lost.
 *
 * The server forgets a subscription not renewed in a year; Sky renews it monthly. When the
 * place moves out of the middle of its area, the old subscription is dropped and a new one made.
 * Turned off, the subscription is deleted from the server and the connection closed.
 */
object InstantAlerts {
    private const val TAG = "SkyPush"
    private const val SERVER = "https://alerts.kde.org"
    const val INSTANCE = "alerts"

    /** The area subscribed: this far each side of the rounded place, about five kilometers. */
    private const val HALF_BOX = 0.05

    /** Moved this far from the middle of the area, the place gets a new one. */
    private const val MOVE = 0.025

    private const val RENEW_MS = 30L * 24 * 60 * 60 * 1000

    private const val PREFS = "instant_alerts"
    private const val ON = "on"
    private const val ENDPOINT = "endpoint"
    private const val P256DH = "p256dh"
    private const val AUTH = "auth"
    private const val VAPID = "vapid"
    private const val SUBSCRIPTION = "subscription"
    private const val SUB_LAT = "sub_lat"
    private const val SUB_LON = "sub_lon"
    private const val RENEWED_AT = "renewed_at"

    /** The push address and key the subscription was made for: another, and it is made again. */
    private const val SUB_FOR = "sub_for"

    /** Subscriptions the server has not yet confirmed deleting; tried again on every [keep]. */
    private const val TO_DELETE = "to_delete"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val json = "application/json".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The switch as set, whether or not alert notifications are on under it. */
    fun chosen(context: Context): Boolean = prefs(context).getBoolean(ON, false)

    /** In effect: chosen, and alert notifications on. */
    fun on(context: Context): Boolean = chosen(context) && AlertNotices.on(context)

    /** Switched from settings or the notification's Turn off. */
    fun setAsync(context: Context, on: Boolean, done: () -> Unit = {}) {
        val app = context.applicationContext
        prefs(app).edit().putBoolean(ON, on).commit()
        scope.launch {
            try {
                keep(app)
            } finally {
                done()
            }
        }
    }

    /** Brings everything in line with the switch: from the app's start and from the alert-notification switch. */
    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        scope.launch { keep(app) }
    }

    /**
     * Brings everything in line with the switch, the place and the calendar: from the
     * quarter-hourly check, the app's start and the switches. Only ever one at a time.
     */
    suspend fun keep(context: Context) = mutex.withLock {
        val p = prefs(context)
        retryDeletes(context)
        if (!on(context)) {
            if (p.contains(SUBSCRIPTION) || p.contains(ENDPOINT) || EmbeddedPushStore(context).hasChannels()) stop(context)
            return@withLock
        }
        if (p.getString(ENDPOINT, null) == null || p.getString(VAPID, null) == null) {
            start(context)
            return@withLock // the endpoint comes back through [onEndpoint], which subscribes
        }
        EmbeddedPush.keepAlive(context)
        val here = here(context) ?: return@withLock
        val id = p.getString(SUBSCRIPTION, null)
        val moved = abs(p.getFloat(SUB_LAT, 999f) - here.lat) > MOVE || abs(p.getFloat(SUB_LON, 999f) - here.lon) > MOVE
        val stale = p.getString(SUB_FOR, null) != subscribedFor(p.getString(ENDPOINT, null), p.getString(P256DH, null))
        when {
            id == null || moved || stale -> subscribe(context, here)
            System.currentTimeMillis() - p.getLong(RENEWED_AT, 0L) > RENEW_MS -> renew(context, id, here)
        }
    }

    private suspend fun start(context: Context) {
        val vapid = runCatching { fetchVapid() }.getOrNull() ?: return
        prefs(context).edit().putString(VAPID, vapid).commit()
        UnifiedPushBridge.register(context, INSTANCE, vapid)
    }

    private fun stop(context: Context) {
        val p = prefs(context)
        p.getString(SUBSCRIPTION, null)?.let { id -> deleteOrKeep(context, id) }
        UnifiedPushBridge.unregister(context, INSTANCE)
        p.edit().remove(ENDPOINT).remove(P256DH).remove(AUTH).remove(SUBSCRIPTION)
            .remove(SUB_LAT).remove(SUB_LON).remove(RENEWED_AT).remove(SUB_FOR).commit()
        EmbeddedPush.sync(context)
    }

    /** The push address and the keys, from the connector. */
    fun onEndpoint(context: Context, url: String, p256dh: String?, auth: String?) {
        if (p256dh == null || auth == null) {
            Log.w(TAG, "A push address without keys; the alert server only sends encrypted")
            return
        }
        val p = prefs(context)
        val same = p.getString(ENDPOINT, null) == url && p.getString(P256DH, null) == p256dh
        p.edit().putString(ENDPOINT, url).putString(P256DH, p256dh).putString(AUTH, auth).commit()
        val app = context.applicationContext
        scope.launch {
            mutex.withLock {
                if (!on(app)) return@withLock
                val here = here(app) ?: return@withLock
                val id = p.getString(SUBSCRIPTION, null)
                // A new address for an old subscription: a new subscription, the old one dropped.
                if (id == null || !same) subscribe(app, here)
            }
        }
    }

    fun onUnregistered(context: Context) {
        prefs(context).edit().remove(ENDPOINT).remove(P256DH).remove(AUTH).commit()
    }

    /**
     * A message from the alert server. "added" or "update" carry an alert's id; the alert itself
     * is fetched the ordinary way. "subscribe" confirms a new subscription. "unsubscribe" says
     * the server has dropped it, so a new one is made.
     */
    fun onMessage(context: Context, content: ByteArray) {
        val app = context.applicationContext
        val type = runCatching { JSONObject(String(content, Charsets.UTF_8)).optString("type") }.getOrDefault("")
        when (type) {
            "subscribe" -> {
                Log.i(TAG, "The alert server confirmed the subscription")
                return
            }
            "unsubscribe" -> {
                prefs(app).edit().remove(SUBSCRIPTION).commit()
                refreshAsync(app)
                return
            }
        }
        // Anything else, or something unreadable, is taken as "look now": a look costs little.
        Log.i(TAG, "Push from the alert server ($type), looking now")
        val wake = (app.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "soramoyo:instant-alert")
        wake.acquire(60_000L)
        scope.launch {
            try {
                AlertNotices.checkNow(app)
            } finally {
                if (wake.isHeld) wake.release()
            }
        }
    }

    private fun here(context: Context): LatLon? = Places.chosen(context)?.at ?: LocationProvider.lastFound(context)

    private fun subscribe(context: Context, here: LatLon) {
        val p = prefs(context)
        val endpoint = p.getString(ENDPOINT, null) ?: return
        val p256dh = p.getString(P256DH, null) ?: return
        val auth = p.getString(AUTH, null) ?: return
        val vapid = p.getString(VAPID, null) ?: return
        // The old subscription is let go only once the new one is made, so a failed attempt
        // leaves the place with the one it had, and the next check tries again.
        val old = p.getString(SUBSCRIPTION, null)
        val lat = round(here.lat)
        val lon = round(here.lon)
        val body = JSONObject()
            .put("token", endpoint)
            .put("push_service", "UNIFIED_PUSH_ENCRYPTED")
            .put("min_lat", f(lat - HALF_BOX)).put("max_lat", f(lat + HALF_BOX))
            .put("min_lon", f(lon - HALF_BOX)).put("max_lon", f(lon + HALF_BOX))
            .put("p256dh_key", p256dh).put("auth_key", auth).put("vapid_public_key", vapid)
        val answer = runCatching {
            call(Request.Builder().url("$SERVER/subscription/").post(body.toString().toRequestBody(json)))
        }.getOrElse {
            Log.w(TAG, "Could not subscribe: ${it.javaClass.simpleName}")
            return
        }
        if (answer.first != 200) {
            Log.w(TAG, "The alert server refused the subscription (${answer.first})")
            when {
                // Refused for the key: the server's has changed since it was fetched.
                answer.first == 401 -> rekey(context, onlyIfChanged = true)
                // Refused outright, perhaps for the one already there: that one is let go
                // first, and the next check subscribes afresh.
                answer.first in 400..499 && old != null -> {
                    deleteOrKeep(context, old)
                    p.edit().remove(SUBSCRIPTION).commit()
                }
            }
            return
        }
        val id = runCatching { JSONObject(answer.second).get("subscription_id").toString() }.getOrNull() ?: return
        p.edit().putString(SUBSCRIPTION, id).putFloat(SUB_LAT, lat.toFloat()).putFloat(SUB_LON, lon.toFloat())
            .putString(SUB_FOR, subscribedFor(endpoint, p256dh))
            .putLong(RENEWED_AT, System.currentTimeMillis()).commit()
        Log.i(TAG, "Subscribed with the alert server")
        if (old != null && old != id) deleteOrKeep(context, old)
    }

    private fun subscribedFor(endpoint: String?, p256dh: String?) = "$endpoint $p256dh"

    /**
     * The server's key has changed. Registered again with the new key, without unregistering
     * first: the connection stays up, so this works from the quarter-hourly check in the
     * background, where Android would refuse to start the connection again. The new address
     * comes back through [onEndpoint], which subscribes anew. With [onlyIfChanged], a key that
     * has not changed is left alone: the refusal was not the key's, and registering again would
     * only bring it back.
     */
    private fun rekey(context: Context, onlyIfChanged: Boolean) {
        val p = prefs(context)
        val vapid = runCatching { fetchVapid() }.getOrNull() ?: return
        if (onlyIfChanged && vapid == p.getString(VAPID, null)) return
        p.edit().putString(VAPID, vapid).remove(ENDPOINT).commit()
        UnifiedPushBridge.register(context, INSTANCE, vapid)
    }

    private fun renew(context: Context, id: String, here: LatLon) {
        val p = prefs(context)
        val body = JSONObject()
            .put("token", p.getString(ENDPOINT, null))
            .put("p256dh_key", p.getString(P256DH, null)).put("auth_key", p.getString(AUTH, null))
            .put("vapid_public_key", p.getString(VAPID, null))
        val code = runCatching {
            call(Request.Builder().url(subscriptionUrl(id)).put(body.toString().toRequestBody(json))).first
        }.getOrElse { return }
        when (code) {
            200 -> p.edit().putLong(RENEWED_AT, System.currentTimeMillis()).commit()
            // Forgotten by the server, or its key has changed: start again with a fresh key.
            404 -> subscribe(context, here)
            // The server's key has changed.
            401 -> rekey(context, onlyIfChanged = false)
        }
    }

    /**
     * True once the server has let the subscription go (or never knew it), or has said it never
     * will: any other refusal of the request itself is final. Only a busy or failing server, a
     * timeout or a lost connection is worth trying again.
     */
    private fun delete(id: String): Boolean =
        deleteSettled(call(Request.Builder().url(subscriptionUrl(id)).delete()).first)

    internal fun deleteSettled(code: Int): Boolean =
        code in 200..299 || (code in 400..499 && code != 408 && code != 429)

    /** Deletes [id] on the server, or keeps it to try again, so a failed delete is not forgotten. */
    private fun deleteOrKeep(context: Context, id: String) {
        if (runCatching { delete(id) }.getOrDefault(false)) return
        val p = prefs(context)
        val left = p.getStringSet(TO_DELETE, emptySet()).orEmpty() + id
        p.edit().putStringSet(TO_DELETE, left).commit()
    }

    private fun retryDeletes(context: Context) {
        val p = prefs(context)
        val waiting = p.getStringSet(TO_DELETE, emptySet()).orEmpty()
        if (waiting.isEmpty()) return
        val left = waiting.filterNot { runCatching { delete(it) }.getOrDefault(false) }.toSet()
        p.edit().putStringSet(TO_DELETE, left).commit()
    }

    private fun fetchVapid(): String? {
        val (code, body) = call(Request.Builder().url("$SERVER/subscription/?type=webpush").get())
        if (code != 200) return null
        return JSONObject(body).optString("vapid-key").takeIf { it.isNotEmpty() }
    }

    private fun subscriptionUrl(id: String) =
        "$SERVER/subscription/".toHttpUrl().newBuilder().addQueryParameter("subscription_id", id).build()

    private fun call(builder: Request.Builder): Pair<Int, String> =
        client.newCall(builder.header("User-Agent", Alerts.USER_AGENT).build()).execute().use {
            it.code to it.body?.string().orEmpty()
        }

    private fun round(d: Double) = Math.round(d * 100) / 100.0
    private fun f(d: Double) = String.format(Locale.US, "%.2f", d)
}
