package com.wanderwildwood.soramoyo.alerts.push

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * The built-in UnifiedPush distributor: Sky is its own push app, as Whereabouts is.
 *
 * The UnifiedPush connector counts any app with a receiver for the distributor's REGISTER
 * action as a distributor, and that includes this app's own unexported
 * [EmbeddedDistributorReceiver]. So the connector registers with this one exactly as it
 * would with Sunup, and gets its endpoint and its messages back through the same broadcasts.
 *
 * Behind it is one WebSocket to Mozilla's push server, held by [PushConnectionService],
 * the way Sunup (https://codeberg.org/Sunup/android) holds it.
 */
internal object EmbeddedPush {
    const val TAG = "SkyPush"

    // The UnifiedPush Android spec's actions and extras, as the connector uses them.
    // https://unifiedpush.org/developers/spec/android/
    const val ACTION_REGISTER = "org.unifiedpush.android.distributor.REGISTER"
    const val ACTION_UNREGISTER = "org.unifiedpush.android.distributor.UNREGISTER"
    private const val ACTION_NEW_ENDPOINT = "org.unifiedpush.android.connector.NEW_ENDPOINT"
    private const val ACTION_REGISTRATION_FAILED = "org.unifiedpush.android.connector.REGISTRATION_FAILED"
    private const val ACTION_MESSAGE = "org.unifiedpush.android.connector.MESSAGE"
    const val EXTRA_TOKEN = "token"
    const val EXTRA_APPLICATION = "application"
    const val EXTRA_VAPID = "vapid"
    private const val EXTRA_ENDPOINT = "endpoint"
    private const val EXTRA_BYTES_MESSAGE = "bytesMessage"
    private const val EXTRA_REASON = "reason"

    /** Where the WebSocket goes. Sunup's default, and Mozilla's public push server. */
    const val PUSH_SERVER_URL = "https://push.services.mozilla.com"

    /** The connection is held only while instant alerts are on and something is registered. */
    fun shouldRun(context: Context): Boolean =
        InstantAlerts.on(context) && EmbeddedPushStore(context).hasChannels()

    private val handler = Handler(Looper.getMainLooper())

    /** Starts or stops the connection to match [shouldRun]. Safe to call from anywhere. */
    fun sync(context: Context) {
        val appContext = context.applicationContext
        if (shouldRun(appContext)) {
            PushConnectionService.start(appContext)
        } else {
            // Unregistering and registering again come in one breath: stopping at once would
            // restart the service, and redraw its notification, for nothing. Look again shortly.
            handler.postDelayed({
                if (!shouldRun(appContext)) {
                    PushRestartWorker.cancel(appContext)
                    PushConnectionService.stop(appContext)
                }
            }, 1_000L)
        }
    }

    /** From the quarter-hourly check: start a connection that died, reconnect one that is down, ping one that is up. */
    fun keepAlive(context: Context) {
        if (!shouldRun(context)) return
        val service = PushConnectionService.instance
        when {
            service == null -> PushConnectionService.start(context)
            service.isOpen -> service.ping()
            else -> service.connect()
        }
    }

    fun onRegister(context: Context, token: String, vapid: String?) {
        val channel = EmbeddedPushStore(context).channelFor(token, vapid)
        Log.i(TAG, "Registration request")
        if (channel.endpoint != null) {
            // Known already: answer at once, as the connector re-registers on every start.
            deliverEndpoint(context, channel.token, channel.endpoint)
        }
        PushConnectionService.instance?.registerPending()
        sync(context)
    }

    fun onUnregister(context: Context, token: String) {
        val channel = EmbeddedPushStore(context).remove(token)
        if (channel != null) PushConnectionService.instance?.unregisterChannel(channel.channelId)
        sync(context)
    }

    fun deliverEndpoint(context: Context, token: String, endpoint: String) {
        context.sendBroadcast(toConnector(context, ACTION_NEW_ENDPOINT, token).putExtra(EXTRA_ENDPOINT, endpoint))
    }

    fun deliverRegistrationFailed(context: Context, token: String) {
        context.sendBroadcast(toConnector(context, ACTION_REGISTRATION_FAILED, token).putExtra(EXTRA_REASON, "INTERNAL_ERROR"))
    }

    fun deliverMessage(context: Context, token: String, message: ByteArray) {
        context.sendBroadcast(toConnector(context, ACTION_MESSAGE, token).putExtra(EXTRA_BYTES_MESSAGE, message))
    }

    private fun toConnector(context: Context, action: String, token: String) =
        Intent(action).setPackage(context.packageName).putExtra(EXTRA_TOKEN, token)
}
