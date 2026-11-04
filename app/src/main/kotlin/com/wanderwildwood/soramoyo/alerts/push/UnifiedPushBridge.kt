package com.wanderwildwood.soramoyo.alerts.push

import android.content.Context
import android.util.Log
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/** The UnifiedPush connector, always pointed at Sky's own built-in distributor. */
internal object UnifiedPushBridge {
    fun register(context: Context, instance: String, vapid: String) {
        UnifiedPush.saveDistributor(context, context.packageName)
        UnifiedPush.register(context, instance, null, vapid)
    }

    fun unregister(context: Context, instance: String) {
        runCatching { UnifiedPush.unregister(context, instance) }
    }
}

/** Where the connector hands over the push address and the opened messages. */
class SkyPushService : PushService() {
    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        if (instance != InstantAlerts.INSTANCE) return
        InstantAlerts.onEndpoint(this, endpoint.url, endpoint.pubKeySet?.pubKey, endpoint.pubKeySet?.auth)
    }

    override fun onMessage(message: PushMessage, instance: String) {
        if (instance != InstantAlerts.INSTANCE) return
        if (!InstantAlerts.on(this)) return
        InstantAlerts.onMessage(this, message.content)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.w("SkyPush", "Push registration failed: $reason")
    }

    override fun onUnregistered(instance: String) {
        if (instance == InstantAlerts.INSTANCE) InstantAlerts.onUnregistered(this)
    }
}
