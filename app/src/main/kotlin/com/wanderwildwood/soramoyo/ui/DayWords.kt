package com.wanderwildwood.soramoyo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.soramoyo.R
import com.wanderwildwood.soramoyo.forecast.DaySummary
import com.wanderwildwood.soramoyo.forecast.RainSoon
import java.time.LocalTime
import java.util.Locale

/**
 * The line a pull down from the top of a list is answered with: what is happening, in words,
 * and gone when it is done. Never a spinner. A failure to fetch says what the screen already
 * says of one.
 */
internal fun LazyListScope.pullLine(pull: Pull) {
    if (pull == Pull.IDLE) return
    item {
        Column {
            Spacer(Modifier.height(10.dp))
            TextMMD(
                text = stringResource(
                    when (pull) {
                        Pull.UPDATING -> R.string.sky_pull_updating
                        Pull.UP_TO_DATE -> R.string.sky_pull_up_to_date
                        else -> R.string.sky_forecast_no_answer
                    },
                ),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDividerMMD()
        }
    }
}

/**
 * The day in a few plain words, and under it the next two hours' rain when any is near, with
 * [levels] drawn as a strip of quarter-hours; any of them may be absent.
 */
@Composable
internal fun DayWords(summary: DaySummary?, soon: RainSoon?, levels: List<RainSoon.Level?> = emptyList()) {
    val words = summary?.let { daySentence(it) }
    val rain = soon?.let { soonLine(it) }
    if (words == null && rain == null) return
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        if (words != null) TextMMD(text = words, style = MaterialTheme.typography.bodySmall)
        if (words != null && rain != null) Spacer(Modifier.height(6.dp))
        if (rain != null) TextMMD(text = rain, style = MaterialTheme.typography.labelSmall)
        if (rain != null && levels.size == RainSoon.WINDOW) {
            Spacer(Modifier.height(8.dp))
            RainStrip(levels)
        }
    }
}

/**
 * The next two hours as eight bars, one a quarter-hour: none, a short one for light, taller for
 * moderate, full for heavy; a quarter the model left out is a dotted outline. Solid ink only,
 * for e-ink. Now, an hour and two hours are marked under the line.
 */
@Composable
private fun RainStrip(levels: List<RainSoon.Level?>) {
    val ink = MaterialTheme.colorScheme.onSurface
    val said = levels.joinToString(", ") { level ->
        when (level) {
            RainSoon.Level.NONE -> "0"
            RainSoon.Level.LIGHT -> "1"
            RainSoon.Level.MODERATE -> "2"
            RainSoon.Level.HEAVY -> "3"
            null -> "?"
        }
    }
    val description = stringResource(R.string.sky_soon_strip_description, said)
    Column(Modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(32.dp)) {
            val line = 1.dp.toPx()
            val gap = 4.dp.toPx()
            val width = (size.width - gap * (levels.size - 1)) / levels.size
            levels.forEachIndexed { i, level ->
                val left = i * (width + gap)
                val tall = when (level) {
                    RainSoon.Level.NONE -> 0f
                    RainSoon.Level.LIGHT -> 1f / 3
                    RainSoon.Level.MODERATE -> 2f / 3
                    RainSoon.Level.HEAVY -> 1f
                    null -> 1f
                }
                val height = (size.height - line) * tall
                if (level == null) {
                    drawRect(
                        ink, Offset(left, 0f), Size(width, size.height - line),
                        style = Stroke(line, pathEffect = PathEffect.dashPathEffect(floatArrayOf(line * 2, line * 2))),
                    )
                } else if (height > 0f) {
                    drawRect(ink, Offset(left, size.height - line - height), Size(width, height))
                }
            }
            drawRect(ink, Offset(0f, size.height - line), Size(size.width, line))
        }
        Spacer(Modifier.height(2.dp))
        Row(Modifier.fillMaxWidth()) {
            val style = MaterialTheme.typography.labelSmall
            TextMMD(text = stringResource(R.string.sky_soon_strip_now), style = style, modifier = Modifier.weight(1f))
            TextMMD(text = stringResource(R.string.sky_soon_strip_hour), style = style, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            TextMMD(text = stringResource(R.string.sky_soon_strip_two_hours), style = style, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun soonLine(soon: RainSoon): String? = when (soon) {
    // The next twelve hours' line says so, and for longer.
    RainSoon.Dry -> null
    is RainSoon.Starts -> stringResource(
        if (soon.snow) R.string.sky_soon_snow_in else R.string.sky_soon_rain_in,
        soon.minutes,
    )
    is RainSoon.Easing -> stringResource(
        if (soon.snow) R.string.sky_soon_snowing_easing else R.string.sky_soon_raining_easing,
        soon.minutes,
    )
    is RainSoon.Lasting -> stringResource(
        if (soon.snow) R.string.sky_soon_snowing_lasting else R.string.sky_soon_raining_lasting,
    )
}

/**
 * The sentences, joined with a space; null where there is nothing to say. The high and low are
 * left to Today's row just under it, which gives them large: said twice, they were read twice.
 */
@Composable
internal fun daySentence(s: DaySummary): String? {
    val sky = s.sky?.let { stringResource(skyName(it)) }
    val wet = s.wet?.let { wetPhrase(it) }
    val first = when {
        sky != null && wet != null -> stringResource(R.string.sky_summary_sky_with, sky, wet)
        sky != null -> stringResource(R.string.sky_summary_sky_alone, sky)
        wet != null -> stringResource(
            R.string.sky_summary_wet_alone,
            wet.replaceFirstChar { it.titlecase(Locale.getDefault()) },
        )
        else -> null
    }
    val wind = s.wind?.let { w ->
        val strength = stringResource(
            when (w.strength) {
                DaySummary.Strength.BREEZY -> R.string.sky_wind_breezy
                DaySummary.Strength.GUSTY -> R.string.sky_wind_gusty
                DaySummary.Strength.WINDY -> R.string.sky_wind_windy
            },
        )
        val directions = stringArrayResource(R.array.sky_directions_8)
        if (w.from != null) stringResource(R.string.sky_summary_wind_from, strength, directions[w.from])
        else stringResource(R.string.sky_summary_wind, strength)
    }
    return listOfNotNull(first, wind).joinToString(" ").ifEmpty { null }
}

private fun skyName(sky: DaySummary.Sky): Int = when (sky) {
    DaySummary.Sky.CLEAR -> R.string.wmo_clear
    DaySummary.Sky.MOSTLY_CLEAR -> R.string.wmo_mostly_clear
    DaySummary.Sky.PARTLY_CLOUDY -> R.string.wmo_partly_cloudy
    DaySummary.Sky.MOSTLY_CLOUDY -> R.string.sky_summary_mostly_cloudy
    DaySummary.Sky.OVERCAST -> R.string.wmo_overcast
    DaySummary.Sky.FOG -> R.string.sky_summary_foggy
}

@Composable
private fun wetPhrase(wet: DaySummary.Wet): String {
    val kind = stringResource(
        when (wet.kind) {
            DaySummary.Kind.RAIN -> R.string.sky_kind_rain
            DaySummary.Kind.SHOWERS -> R.string.sky_kind_showers
            DaySummary.Kind.DRIZZLE -> R.string.sky_kind_drizzle
            DaySummary.Kind.SNOW -> R.string.sky_kind_snow
            DaySummary.Kind.SNOW_SHOWERS -> R.string.sky_kind_snow_showers
            DaySummary.Kind.FREEZING_RAIN -> R.string.sky_kind_freezing_rain
            DaySummary.Kind.SLEET -> R.string.sky_kind_sleet
            DaySummary.Kind.THUNDERSTORMS -> R.string.sky_kind_thunderstorms
        },
    )
    return when (val t = wet.timing) {
        DaySummary.When.AllDay -> stringResource(R.string.sky_when_all_day, kind)
        DaySummary.When.RestOfDay -> stringResource(R.string.sky_when_rest_of_day, kind)
        is DaySummary.When.After -> stringResource(R.string.sky_when_after, kind, hourWords(t.at))
        is DaySummary.When.Early -> stringResource(R.string.sky_when_early, kind, hourWords(t.by))
        is DaySummary.When.Until -> stringResource(R.string.sky_when_until, kind, hourWords(t.by))
        is DaySummary.When.Between ->
            stringResource(R.string.sky_when_between, kind, hourWords(t.from), hourWords(t.to))
        DaySummary.When.OnAndOff -> stringResource(R.string.sky_when_on_and_off, kind)
        DaySummary.When.Likely -> stringResource(R.string.sky_when_likely, kind)
    }
}

/** An hour as the phone's clock would write it, short: "3 pm" or "15:00", and "noon". */
@Composable
private fun hourWords(time: LocalTime): String {
    if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) {
        return stringResource(R.string.sky_time_24, time.hour, time.minute)
    }
    if (time.hour == 12 && time.minute == 0) return stringResource(R.string.sky_time_noon)
    val hour = time.hour % 12
    return stringResource(
        R.string.sky_time_12,
        if (hour == 0) 12 else hour,
        stringResource(if (time.hour < 12) R.string.sky_time_am else R.string.sky_time_pm),
    )
}
