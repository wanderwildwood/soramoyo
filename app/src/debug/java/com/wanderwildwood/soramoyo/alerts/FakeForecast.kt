package com.wanderwildwood.soramoyo.alerts

import android.content.ContentProvider
import android.content.ContentValues
import android.net.Uri
import com.wanderwildwood.soramoyo.forecast.Forecast
import java.io.File

/**
 * Debug builds only. While the app's files hold `fake-forecast.json` (Open-Meteo's own format,
 * put there with `adb shell run-as`), it is read in place of the forecast from the network.
 */
class FakeForecast : ContentProvider() {
    override fun onCreate(): Boolean {
        val dir = context?.filesDir ?: return true
        Forecast.fake = {
            // A file called fake-forecast-slow makes the answer take its time, to see the wait.
            if (File(dir, "fake-forecast-slow").exists()) Thread.sleep(3000)
            File(dir, "fake-forecast.json").takeIf { it.exists() }?.readText()
        }
        return true
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?) = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, s: String?, a: Array<out String>?) = 0
}
