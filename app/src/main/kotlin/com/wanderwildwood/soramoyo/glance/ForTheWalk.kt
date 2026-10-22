package com.wanderwildwood.soramoyo.glance

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.wanderwildwood.soramoyo.alerts.AlertNotices

/**
 * Today's weather in plain words for one other app of ours ([READER]): clear, fog, rain or snow, and
 * whether rain or snow starts or stops in the next hours. After those: the wind and gusts now in m/s,
 * the moon's phase now (0 new, 0.5 full) and today's moonrise and moonset (-1 for none), the kind of
 * the most significant Weather Service alert in force (none, heat, winter_storm, smoke or other; never
 * its text), whether heavy snow is falling or due within a few hours, the snow over the next day in
 * centimetres, and the hemisphere (north, south or tropic; never where).
 *
 * All of it comes from what Sky fetches anyway, for its lock-screen lines and its own alerts; nothing
 * is fetched for this alone. The alerts are only as new as the last look at them, by Sky's screen or
 * its alert check; older than [STALE_MS], the kind is none.
 *
 * It answers that app alone, from the last fetch, at once; a fetch more than half an hour old is renewed
 * for next time. An answer older than [STALE_MS] is not handed over, and the asking app does without it.
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
                Weather.walk(context)?.let { (sky, change, minutes) ->
                    // Whatever goes wrong with the rest, the first three are still handed over.
                    val more = runCatching { Weather.walkMore(context) }.getOrDefault(UNKNOWN)
                    val alert = runCatching { AlertNotices.kindInForce(context, STALE_MS) }.getOrDefault(WalkFacts.NONE)
                    cursor.addRow(arrayOf<Any?>(
                        sky, change, minutes,
                        more.wind, more.gust,
                        more.moonPhase, more.moonrise, more.moonset,
                        alert,
                        more.heavy, more.snowfall,
                        more.hemisphere,
                    ))
                }
            }
        } catch (_: Exception) {
            // No answer: the asking app does without it.
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
        /** The first three as they always were; the rest only ever added after them. */
        private val COLUMNS = arrayOf(
            "sky", "change", "minutes",
            "wind", "gust",
            "moon_phase", "moonrise", "moonset",
            "alert",
            "heavy", "snowfall",
            "hemisphere",
        )
        private const val STALE_MS = 3 * 60 * 60 * 1000L
        private val UNKNOWN = Weather.More(-1f, -1f, -1f, -1L, -1L, 0, 0f, null)
    }
}
