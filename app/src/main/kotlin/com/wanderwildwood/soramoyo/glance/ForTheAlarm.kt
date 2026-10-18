package com.wanderwildwood.soramoyo.glance

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle

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
        if (method != Weather.REFRESH || callingPackage != READER) return null
        val context = context?.applicationContext ?: return null
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
        if (callingPackage != READER) return null
        val context = context ?: return null
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

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        const val READER = "com.wanderwildwood.tokei"
        private val COLUMNS = arrayOf("lead", "text")
        private const val STALE_MS = 3 * 60 * 60 * 1000L
    }
}
