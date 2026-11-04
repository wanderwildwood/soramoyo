package com.wanderwildwood.soramoyo.alerts.push

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings

/**
 * DuraSpeed, MediaTek's background manager on the Kompakt, force-stops installed apps a few
 * minutes after the screen goes dark. For instant alerts that cuts the connection to the push
 * server, and a stopped app is not started again until it is opened. Apps switched on in its
 * list are left alone, but no other app can read that list, and Settings has no way into it.
 * Its App info page can be opened, and has an Open button, so that is where the button goes;
 * whether it was done is the person's word.
 *
 * A stop by the system takes that word back: DuraSpeed does not stop apps on its list.
 * Android records such a stop as "stop <package> due to from pid N". A Force stop pressed by
 * hand in Settings reads the same, and is the one case this gets wrong. As in Practice.
 */
internal object DuraSpeed {

    private const val PACKAGE = "com.mediatek.duraspeed"
    private const val PREFS = "instant_alerts"
    private const val ALLOWED = "duraspeed_allowed"
    private const val STOP_SEEN = "duraspeed_stop_seen"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** A Kompakt with DuraSpeed on it; the manifest names it in <queries> to see it. */
    private fun isKompakt(context: Context): Boolean =
        Build.MANUFACTURER.equals("Mudita", ignoreCase = true) &&
            runCatching { context.packageManager.getApplicationInfo(PACKAGE, 0) }.isSuccess

    /** DuraSpeed's own switch; null where the phone does not say. */
    private fun isOn(context: Context): Boolean? = runCatching {
        val cr = context.contentResolver
        (AndroidSettings.Global.getString(cr, "setting.duraspeed.enabled")
            ?: AndroidSettings.System.getString(cr, "setting.duraspeed.enabled"))?.let { it != "0" }
    }.getOrNull()

    /** A stop by the system since the last look, while DuraSpeed is on, means it is not done after all. */
    private fun noteStops(context: Context) {
        val p = prefs(context)
        val am = context.getSystemService(ActivityManager::class.java)
        val stop = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 0) }
            .getOrDefault(emptyList())
            .filter { it.reason == ApplicationExitInfo.REASON_USER_REQUESTED && it.description?.contains("due to from pid") == true }
            .maxOfOrNull { it.timestamp } ?: return
        if (stop <= p.getLong(STOP_SEEN, 0L)) return
        p.edit().putLong(STOP_SEEN, stop).apply()
        if (isKompakt(context) && isOn(context) != false) p.edit().putBoolean(ALLOWED, false).apply()
    }

    /** Whether the one step is still to do: shown under the switch while it is. */
    fun atRisk(context: Context): Boolean {
        noteStops(context)
        return isKompakt(context) && isOn(context) != false && !prefs(context).getBoolean(ALLOWED, false)
    }

    fun done(context: Context) = prefs(context).edit().putBoolean(ALLOWED, true).apply()

    fun open(context: Context) {
        runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:$PACKAGE"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
