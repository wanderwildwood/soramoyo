package com.wanderwildwood.soramoyo.glance

import android.content.Context
import android.text.format.DateFormat
import com.wanderwildwood.soramoyo.R
import com.wanderwildwood.soramoyo.forecast.Change
import com.wanderwildwood.soramoyo.forecast.Forecast
import com.wanderwildwood.soramoyo.forecast.upcoming
import com.wanderwildwood.soramoyo.location.Places
import com.wanderwildwood.soramoyo.station.Source
import com.wanderwildwood.soramoyo.station.Stations
import com.wanderwildwood.soramoyo.station.Units
import com.wanderwildwood.soramoyo.ui.conditionFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * The weather for the lock screen: what it is now, the day's high and low, and when rain or
 * snow is likely to start or stop.
 *
 * Nothing runs on a timer. When the lock screen asks, it gets the last answer at once; if that
 * is more than half an hour old, a new one is fetched in the background and Glance is told when
 * it is ready. So the weather is only ever fetched when someone looks at the phone.
 */
class NowOnLockScreen : GlanceProvider() {

    override fun enabled(context: Context): Boolean = LockScreen.on(context)

    override fun lines(context: Context): List<Line> {
        val saved = cache(context)
        val at = saved.getLong(AT, 0L)
        if (System.currentTimeMillis() - at > FRESH_MS) refresh(context.applicationContext)
        val rows = JSONArray(saved.getString(LINES, "[]") ?: "[]")
        return (0 until rows.length()).map { i ->
            val o = rows.getJSONObject(i)
            Line(text = o.getString("text"), lead = o.optString("lead").takeIf { it.isNotEmpty() })
        }
    }

    private fun refresh(context: Context) {
        if (!busy.compareAndSet(false, true)) return
        scope.launch {
            try {
                val lines = fetch(context) ?: return@launch
                val rows = JSONArray()
                lines.forEach { rows.put(JSONObject().put("text", it.text).put("lead", it.lead ?: "")) }
                cache(context).edit()
                    .putString(LINES, rows.toString())
                    .putLong(AT, System.currentTimeMillis())
                    .apply()
                changed(context)
            } catch (_: Exception) {
                // No answer this time: the last lines stay, and the next look asks again.
            } finally {
                busy.set(false)
            }
        }
    }

    private suspend fun fetch(context: Context): List<Line>? {
        val here = Places.where(context) ?: return null
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
        val lines = mutableListOf(Line(text = first.ifEmpty { " " }, lead = "${now.roundToInt()}°"))
        val hours = upcoming(predicted.hours, predicted.offset)
        val clock = DateTimeFormatter.ofPattern(
            DateFormat.getBestDateTimePattern(Locale.getDefault(), if (DateFormat.is24HourFormat(context)) "Hm" else "hm"),
        )
        when (val change = Change.of(hours)) {
            Change.Dry -> Unit
            is Change.From -> lines += Line(context.getString(
                if (change.snow) R.string.sky_hours_snow_from else R.string.sky_hours_rain_from, clock.format(change.at)))
            is Change.Until -> lines += Line(context.getString(
                if (change.snow) R.string.sky_hours_snow_until else R.string.sky_hours_rain_until, clock.format(change.at)))
            is Change.Throughout -> lines += Line(context.getString(
                if (change.snow) R.string.sky_hours_snow_throughout else R.string.sky_hours_rain_throughout, hours.size))
        }
        return lines
    }

    private fun cache(context: Context) = context.getSharedPreferences("lock_screen", Context.MODE_PRIVATE)

    companion object {
        private const val LINES = "lines"
        private const val AT = "at"
        private const val FRESH_MS = 30 * 60 * 1000L
        private val busy = AtomicBoolean(false)
        private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    }
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
