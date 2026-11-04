package com.wanderwildwood.soramoyo.alerts.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * After the phone starts and after Sky is updated: the two moments Android lets an app start a
 * foreground service from the background, so the connection comes back without opening Sky.
 */
class StartAgain : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!InstantAlerts.on(context)) return
        EmbeddedPush.sync(context)
        InstantAlerts.refreshAsync(context)
    }
}
