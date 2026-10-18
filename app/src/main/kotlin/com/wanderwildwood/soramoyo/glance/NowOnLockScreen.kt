package com.wanderwildwood.soramoyo.glance

import android.content.Context
import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import com.wanderwildwood.soramoyo.R
import com.wanderwildwood.soramoyo.forecast.Change
import com.wanderwildwood.soramoyo.forecast.Forecast
import com.wanderwildwood.soramoyo.forecast.upcoming
import com.wanderwildwood.soramoyo.location.LocationProvider
import com.wanderwildwood.soramoyo.location.Places
import com.wanderwildwood.soramoyo.station.Source
import com.wanderwildwood.soramoyo.station.Stations
import com.wanderwildwood.soramoyo.station.Units
import com.wanderwildwood.soramoyo.ui.conditionFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The weather for the lock screen: what it is now, the day's high and low, and when rain or
 * snow is likely to start or stop.
 *
 * Nothing runs on a timer. When the lock screen asks, it gets the last answer at once; if that
 * is more than half an hour old, a new one is fetched and Glance is told when it is ready. So
 * the weather is only ever fetched when someone looks at the phone.
 *
 * Glance waits for the new one through [call] ("refresh"), and the wait is what makes it
 * work: an app nobody is talking to loses the network within seconds of the lock screen's
 * question being answered, before a forecast can arrive, so a fetch started from [lines]
 * alone ended with the old lines kept and nothing said.
 */
class NowOnLockScreen : GlanceProvider() {

    override fun enabled(context: Context): Boolean = LockScreen.on(context)

    override fun lines(context: Context): List<Line> = Weather.lines(context)

    /** Glance asking for the weather to be brought up to date, and waiting until it is. */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        if (method != Weather.REFRESH || callingPackage != READER) return null
        val context = context?.applicationContext ?: return null
        if (enabled(context)) Weather.refreshAndWait(context)
        return null
    }
}

/**
 * The weather lines, kept between looks, and the fetch that brings them up to date: shared by
 * the lock screen ([NowOnLockScreen]) and Clock's ringing screen ([ForTheAlarm]).
 */
internal object Weather {
    const val REFRESH = "refresh"

    /** The last lines, at once; a new fetch is started if they are more than half an hour old. */
    fun lines(context: Context): List<GlanceProvider.Line> {
        val saved = cache(context)
        if (System.currentTimeMillis() - saved.getLong(AT, 0L) > FRESH_MS) refresh(context.applicationContext)
        val rows = JSONArray(saved.getString(LINES, "[]") ?: "[]")
        return (0 until rows.length()).map { i ->
            val o = rows.getJSONObject(i)
            GlanceProvider.Line(text = o.getString("text"), lead = o.optString("lead").takeIf { it.isNotEmpty() })
        }
    }

    /** When the lines were made, in milliseconds since 1970; 0 for never. */
    fun madeAt(context: Context): Long = cache(context).getLong(AT, 0L)

    /** A fetch if the lines are more than half an hour old, waited for up to twenty seconds. */
    fun refreshAndWait(context: Context) {
        if (System.currentTimeMillis() - madeAt(context) <= FRESH_MS) return
        val job = refresh(context)
        runBlocking { withTimeoutOrNull(WAIT_MS) { job.join() } }
    }

    /** The fetch under way, or a new one; one at a time. */
    @Synchronized
    private fun refresh(context: Context): Job {
        running?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            try {
                val lines = fetch(context) ?: return@launch
                val rows = JSONArray()
                lines.forEach { rows.put(JSONObject().put("text", it.text).put("lead", it.lead ?: "")) }
                cache(context).edit()
                    .putString(LINES, rows.toString())
                    .putLong(AT, System.currentTimeMillis())
                    .apply()
                GlanceProvider.changed(context)
            } catch (e: Exception) {
                // No answer this time: the last lines stay, and the next look asks again.
                Log.i("soramoyo", "weather lines not refreshed: ${e.javaClass.simpleName}")
            }
        }.also { running = it }
    }

    private suspend fun fetch(context: Context): List<GlanceProvider.Line>? {
        // Never a new fix: out of sight, the phone would not give one (see LocationProvider.lastFound).
        val here = Places.chosen(context)?.at ?: LocationProvider.lastFound(context) ?: run {
            Log.i("soramoyo", "weather lines not refreshed: no place known yet")
            return null
        }
        val source = Source.load(context)
        val reading = if (source == Source.None) null else runCatching { Stations.read(context, source) }.getOrNull()
        val metric = Units.chosen(context) ?: reading?.metric ?: Units.metricHere()
        val predicted = Forecast.fetch(here.lat, here.lon, metric)
        val now = reading?.temperature?.number ?: predicted.current?.temperature ?: return null
        val today = predicted.days.firstOrNull { it.date == LocalDateTime.now(predicted.offset).toLocalDate() }
            ?: predicted.days.firstOrNull()
        val code = predicted.current?.code ?: today?.code
        val condition = code?.let { context.getString(conditionFor(it)) }
        val first = listOfNotNull(
            condition,
            today?.let { context.getString(R.string.lock_high_low, it.high, it.low) },
        ).joinToString(" · ")
        val lines = mutableListOf(GlanceProvider.Line(text = first.ifEmpty { " " }, lead = "${now.roundToInt()}°"))
        val hours = upcoming(predicted.hours, predicted.offset)
        val clock = DateTimeFormatter.ofPattern(
            DateFormat.getBestDateTimePattern(Locale.getDefault(), if (DateFormat.is24HourFormat(context)) "Hm" else "hm"),
        )
        when (val change = Change.of(hours)) {
            Change.Dry -> Unit
            is Change.From -> lines += GlanceProvider.Line(context.getString(
                if (change.snow) R.string.sky_hours_snow_from else R.string.sky_hours_rain_from, clock.format(change.at)))
            is Change.Until -> lines += GlanceProvider.Line(context.getString(
                if (change.snow) R.string.sky_hours_snow_until else R.string.sky_hours_rain_until, clock.format(change.at)))
            is Change.Throughout -> lines += GlanceProvider.Line(context.getString(
                if (change.snow) R.string.sky_hours_snow_throughout else R.string.sky_hours_rain_throughout, hours.size))
        }
        return lines
    }

    private fun cache(context: Context) = context.getSharedPreferences("lock_screen", Context.MODE_PRIVATE)

    private const val LINES = "lines"
    private const val AT = "at"
    private const val FRESH_MS = 30 * 60 * 1000L
    private const val WAIT_MS = 20_000L
    private var running: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
}

/** Sky's own switch for the lock screen. */
object LockScreen {
    private const val ON = "on"
    fun on(context: Context): Boolean =
        context.getSharedPreferences("lock_screen", Context.MODE_PRIVATE).getBoolean(ON, true)

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences("lock_screen", Context.MODE_PRIVATE).edit().putBoolean(ON, on).apply()
        GlanceProvider.changed(context)
    }
}
