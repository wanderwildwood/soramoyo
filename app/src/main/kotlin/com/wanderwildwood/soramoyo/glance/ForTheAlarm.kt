package com.wanderwildwood.soramoyo.glance

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import java.security.MessageDigest

/**
 * The weather in one line for Clock (com.wanderwildwood.tokei), which shows it on its ringing
 * screen when its own setting for that is on: the same line the lock screen opens with, the
 * temperature, the sky and the day's high and low.
 *
 * It answers Clock alone. Clock first asks for a fresh forecast with [call] ("refresh") and
 * waits, as Glance does, then reads the line. A line older than [STALE_MS] is not handed over:
 * the weather of last night is no use to someone waking up.
 */
class ForTheAlarm : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != Weather.REFRESH) return null
        val context = context?.applicationContext ?: return null
        if (!fromClock(context)) return null
        Weather.refreshAndWait(context)
        return null
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? {
        val context = context ?: return null
        if (!fromClock(context)) return null
        val cursor = MatrixCursor(COLUMNS)
        try {
            if (System.currentTimeMillis() - Weather.madeAt(context) <= STALE_MS) {
                Weather.lines(context).firstOrNull()?.let { cursor.addRow(arrayOf<Any?>(it.lead, it.text)) }
            }
        } catch (_: Exception) {
            // No line, rather than a ringing screen that cannot open.
        }
        return cursor
    }

    /**
     * Whether the caller is Clock: its package name, and signed with Clock's release certificate.
     * A name alone is not enough - an app can take Clock's name on a phone where Clock is not
     * installed. A debuggable build of this app answers any Clock, for testing.
     */
    private fun fromClock(context: Context): Boolean {
        if (callingPackage != READER) return false
        if (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) return true
        return runCatching {
            val signers = context.packageManager
                .getPackageInfo(READER, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
            !signers.isNullOrEmpty() && signers.all { cert ->
                MessageDigest.getInstance("SHA-256").digest(cert.toByteArray())
                    .joinToString("") { "%02x".format(it) } == READER_CERT_SHA256
            }
        }.getOrDefault(false)
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        const val READER = "com.wanderwildwood.tokei"

        /** SHA-256 of the certificate Clock's releases are signed with, lower-case hex. */
        const val READER_CERT_SHA256 = "9166ab4a621bab6aaa5c1e35303ac1a25216f2f885560dcb5918f12174337287"
        private val COLUMNS = arrayOf("lead", "text")
        private const val STALE_MS = 3 * 60 * 60 * 1000L
    }
}
