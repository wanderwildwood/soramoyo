package com.wanderwildwood.soramoyo.alerts

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.wanderwildwood.soramoyo.MainActivity
import com.wanderwildwood.soramoyo.R
import com.wanderwildwood.soramoyo.glance.WalkFacts
import com.wanderwildwood.soramoyo.location.LocationProvider
import com.wanderwildwood.soramoyo.location.Places
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * A notification when a serious alert ([Alert.serious]) is issued for the first place: the
 * chosen one, else wherever the app last found the phone. Out of sight the app is not told
 * where the phone is, so it does not try to find out.
 *
 * Nothing in Sky ran on a timer before this. The check is the one exception, and only while the
 * setting is on: once every half hour, when the phone has a network, at the time Android finds
 * convenient. One small request to the Weather Service each time.
 *
 * Each alert is noticed once. An alert already seen on the screen, or one the Weather Service
 * has merely updated or extended, does not notify again.
 */
object AlertNotices {
    private const val PREFS = "alerts"
    private const val ON = "notify"
    private const val KNOWN = "known"
    private const val IN_FORCE = "in_force"
    private const val IN_FORCE_AT = "in_force_at"
    private const val CHANNEL = "weather_alerts"
    private const val WORK = "alerts"
    private const val NOTIFICATION = 1
    const val OPEN_EXTRA = "open_alerts"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Whether to notify. On by default only where the phone already lets Sky post
     * notifications; the default is settled the first time it is asked, so it does not change
     * under someone later.
     */
    fun on(context: Context): Boolean {
        val p = prefs(context)
        if (!p.contains(ON)) p.edit().putBoolean(ON, allowed(context)).apply()
        return p.getBoolean(ON, false)
    }

    /**
     * Switched from settings. Making the channel is what asks for permission to notify on
     * Android 13 and later, for an app built as this one is; so it is made here, while the
     * person who switched it on is looking, and not by a check in the background.
     */
    fun set(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(ON, on).apply()
        if (on) channel(context)
        schedule(context)
    }

    /** Whether the phone lets Sky post notifications at all. */
    fun allowed(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** The half-hourly check while the setting is on, and none while it is off. */
    fun schedule(context: Context) {
        val work = WorkManager.getInstance(context)
        if (!on(context)) {
            work.cancelUniqueWork(WORK)
            return
        }
        val request = PeriodicWorkRequestBuilder<AlertCheck>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /**
     * Alerts fetched for the first place. The screen passes [notify] false: what is on the
     * screen has been seen, and needs no notification after it.
     */
    fun note(context: Context, alerts: List<Alert>, notify: Boolean) {
        val now = System.currentTimeMillis()
        val known = known(context, now)
        val fresh = fresh(alerts, known.keys)
        alerts.forEach { a ->
            val until = a.until?.toInstant()?.toEpochMilli() ?: (now + KEEP_MS)
            a.names.forEach { known[it] = maxOf(until, known[it] ?: 0L) }
        }
        prefs(context).edit()
            .putString(KNOWN, JSONObject(known as Map<*, *>).toString())
            .putString(IN_FORCE, inForce(alerts).toString())
            .putLong(IN_FORCE_AT, now)
            .apply()
        if (notify && fresh.isNotEmpty() && on(context) && allowed(context)) post(context, fresh)
    }

    /** The alerts as [WalkFacts] wants them: each one's kind, severity and times, and nothing of its text. */
    private fun inForce(alerts: List<Alert>): JSONArray {
        val rows = JSONArray()
        alerts.forEach { a ->
            rows.put(
                JSONObject()
                    .put("kind", WalkFacts.alertKind(a.event))
                    .put("rank", WalkFacts.severityRank(a.severity))
                    .put("from", a.onset?.toInstant()?.toEpochMilli() ?: -1L)
                    .put("until", a.until?.toInstant()?.toEpochMilli() ?: -1L),
            )
        }
        return rows
    }

    /**
     * The kind of the most significant alert in force at the first place now, from the last
     * alerts fetched there; [WalkFacts.NONE] when none is, or when they were fetched more than
     * [staleMs] ago.
     */
    internal fun kindInForce(context: Context, staleMs: Long, now: Long = System.currentTimeMillis()): String {
        val p = prefs(context)
        if (now - p.getLong(IN_FORCE_AT, 0L) > staleMs) return WalkFacts.NONE
        val rows = runCatching { JSONArray(p.getString(IN_FORCE, "[]") ?: "[]") }.getOrDefault(JSONArray())
        val alerts = (0 until rows.length()).mapNotNull { i ->
            val o = rows.optJSONObject(i) ?: return@mapNotNull null
            WalkFacts.InForce(
                kind = o.optString("kind", WalkFacts.OTHER),
                rank = o.optInt("rank", 0),
                from = o.optLong("from", -1L).takeIf { it >= 0 },
                until = o.optLong("until", -1L).takeIf { it >= 0 },
            )
        }
        return WalkFacts.mostSignificant(alerts, now)
    }

    /** The serious alerts none of whose names has been seen before. */
    internal fun fresh(alerts: List<Alert>, known: Set<String>): List<Alert> =
        alerts.filter { a -> a.serious && a.names.none { it in known } }

    /** Names seen, with when each runs out; those over for a day are forgotten. */
    private fun known(context: Context, now: Long): MutableMap<String, Long> {
        val o = runCatching { JSONObject(prefs(context).getString(KNOWN, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val map = mutableMapOf<String, Long>()
        o.keys().forEach { k -> o.optLong(k).takeIf { it > now - KEEP_MS }?.let { map[k] = it } }
        return map
    }

    private fun channel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.alerts_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.alerts_channel_about)
            },
        )
    }

    /** One notification for however many arrived together; a press opens the list. */
    private fun post(context: Context, fresh: List<Alert>) {
        channel(context)
        val first = fresh.first()
        val title = if (fresh.size == 1) first.event else {
            context.resources.getQuantityString(R.plurals.alerts_and_more, fresh.size - 1, first.event, fresh.size - 1)
        }
        val text = listOfNotNull(
            first.until?.let { context.getString(R.string.alerts_until, untilText(context, it)) },
            first.areas.takeIf { it.isNotEmpty() },
        ).joinToString(" · ")
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtra(OPEN_EXTRA, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOf(text, first.headline).filter { it.isNotEmpty() }.joinToString("\n")))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION, notification)
        } catch (_: SecurityException) {
            // Permission taken away between the check and the post: nothing to be done here.
        }
    }

    /** "8:00 PM" today where the alert is, else "Tue 8:00 PM". */
    fun untilText(context: Context, time: OffsetDateTime): String {
        val locale = Locale.getDefault()
        val clock = DateTimeFormatter.ofPattern(
            DateFormat.getBestDateTimePattern(locale, if (DateFormat.is24HourFormat(context)) "Hm" else "hm"),
            locale,
        ).format(time)
        if (time.toLocalDate() == LocalDate.now(time.offset)) return clock
        return time.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) + " " + clock
    }

    /** A day past its end, a name is no longer worth keeping. */
    private const val KEEP_MS = 24 * 60 * 60 * 1000L
}

/** The half-hourly look at the Weather Service, for [AlertNotices]. */
class AlertCheck(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!AlertNotices.on(context)) return Result.success()
        val here = Places.chosen(context)?.at ?: LocationProvider.lastFound(context) ?: return Result.success()
        return try {
            when (val answer = Alerts.fetch(here.lat, here.lon)) {
                is AlertAnswer.Found -> AlertNotices.note(context, answer.alerts, notify = true)
                // None to notify of, but that there are none is worth keeping too.
                AlertAnswer.NotCovered -> AlertNotices.note(context, emptyList(), notify = false)
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The next half hour tries again; no faster retry, for the battery's sake.
            Log.i("soramoyo", "alerts not checked: ${e.javaClass.simpleName}")
            Result.success()
        }
    }
}
