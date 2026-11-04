package com.wanderwildwood.soramoyo.alerts.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Takes REGISTER and UNREGISTER from the UnifiedPush connector inside this same app.
 *
 * It is not exported, so no other app can register with it: the connector still lists it
 * as a distributor, because it counts this app's own receivers whether exported or not.
 */
class EmbeddedDistributorReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val token = intent.getStringExtra(EmbeddedPush.EXTRA_TOKEN) ?: return
        val application = intent.getStringExtra(EmbeddedPush.EXTRA_APPLICATION)
        if (application != null && application != context.packageName) {
            Log.w(EmbeddedPush.TAG, "Ignoring a registration for another app")
            return
        }
        when (intent.action) {
            EmbeddedPush.ACTION_REGISTER ->
                EmbeddedPush.onRegister(context, token, intent.getStringExtra(EmbeddedPush.EXTRA_VAPID))
            EmbeddedPush.ACTION_UNREGISTER -> EmbeddedPush.onUnregister(context, token)
        }
    }
}
