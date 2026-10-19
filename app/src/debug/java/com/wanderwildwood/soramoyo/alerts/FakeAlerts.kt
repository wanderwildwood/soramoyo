package com.wanderwildwood.soramoyo.alerts

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import java.io.File

/**
 * Debug builds only. While the app's files hold `fake-alerts.json` (put there with
 * `adb shell run-as`), it is read in place of the Weather Service's answer. A provider only
 * because a provider is started with the app, before anything asks for alerts.
 */
class FakeAlerts : ContentProvider() {
    override fun onCreate(): Boolean {
        val dir = context?.filesDir ?: return true
        Alerts.fake = { File(dir, "fake-alerts.json").takeIf { it.exists() }?.readText() }
        return true
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?) = 0
}
