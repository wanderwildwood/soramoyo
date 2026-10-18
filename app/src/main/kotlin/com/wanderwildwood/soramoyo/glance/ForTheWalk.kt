package com.wanderwildwood.soramoyo.glance

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri

/**
 * Today's weather in plain words for Wander (com.wanderwildwood.sanpo), the walking game, which lets
 * the sky in its wood follow the real one: clear, fog, rain or snow, and whether rain or snow starts or
 * stops in the next hours.
 *
 * It answers Wander alone, from the last fetch, at once; a fetch more than half an hour old is renewed
 * for next time. An answer older than [STALE_MS] is not handed over, and the game picks a likely day.
 */
class ForTheWalk : ContentProvider() {

    override fun onCreate(): Boolean = true

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
            Weather.lines(context)                                   // renews a stale fetch, for next time
            if (System.currentTimeMillis() - Weather.madeAt(context) <= STALE_MS) {
                Weather.walk(context)?.let { (sky, change, minutes) -> cursor.addRow(arrayOf<Any?>(sky, change, minutes)) }
            }
        } catch (_: Exception) {
            // No answer: the game picks a likely day.
        }
        return cursor
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        const val READER = "com.wanderwildwood.sanpo"
        private val COLUMNS = arrayOf("sky", "change", "minutes")
        private const val STALE_MS = 3 * 60 * 60 * 1000L
    }
}
