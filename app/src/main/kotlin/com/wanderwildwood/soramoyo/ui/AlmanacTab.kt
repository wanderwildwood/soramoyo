package com.wanderwildwood.soramoyo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.soramoyo.R
import com.wanderwildwood.soramoyo.almanac.Almanac
import com.wanderwildwood.soramoyo.almanac.MoonDay
import com.wanderwildwood.soramoyo.almanac.MoonName
import com.wanderwildwood.soramoyo.almanac.Phase
import com.wanderwildwood.soramoyo.almanac.Principal
import com.wanderwildwood.soramoyo.almanac.SunDay
import com.wanderwildwood.soramoyo.forecast.Day
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The sun and moon for today where the forecast is for: the moon as it looks and when it rises
 * and sets, the sun's rise and set and the dark either side, the coming phases, and the month's
 * moons. Worked out on the phone from the place alone, so it is here without a signal.
 */
@Composable
internal fun AlmanacTab(state: SkyState, onAllowLocation: () -> Unit, modifier: Modifier) {
    val at = state.at
    LazyColumnMMD(modifier = modifier.padding(horizontal = 20.dp), isScrollbarVisible = false) {
        if (at == null) {
            item {
                Spacer(Modifier.height(12.dp))
                if (state.forecastTrouble == ForecastTrouble.NO_PERMISSION ||
                    state.forecastTrouble == ForecastTrouble.NO_LOCATION
                ) {
                    ForecastTrouble(state.forecastTrouble, true, onAllowLocation)
                } else {
                    TextMMD(text = stringResource(R.string.sky_almanac_waiting), style = MaterialTheme.typography.bodySmall)
                }
            }
            return@LazyColumnMMD
        }
        val zone = state.almanacZone()
        val today = LocalDate.now(zone)
        val sun = Almanac.sun(today, zone, at.lat, at.lon)
        val moon = Almanac.moon(today, zone, at.lat, at.lon)
        val south = at.lat < 0

        item { MoonHeadline(moon, south, zone) }
        moonRows(moon).forEach { row ->
            item {
                HorizontalDividerMMD()
                row()
            }
        }
        sunRows(sun).forEach { row ->
            item {
                HorizontalDividerMMD()
                row()
            }
        }
        item {
            HorizontalDividerMMD()
            MoonMonth(today, zone, south)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** The moon drawn as it looks tonight, its name, how much is lit, and the next principal phase. */
@Composable
private fun MoonHeadline(moon: MoonDay, south: Boolean, zone: ZoneId) {
    val name = stringResource(moonName(moon.phase.name(moon.principal)))
    val next = remember(moon) {
        Almanac.phases(java.time.Instant.now(), 2).first { it.principal != moon.principal }
    }
    val locale = Locale.getDefault()
    val date = DateTimeFormatter.ofPattern(
        android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM"),
        locale,
    ).format(next.at.atZone(zone))
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MoonFace(moon.phase, south, 84.dp, Modifier.semantics { contentDescription = name })
        Spacer(Modifier.width(18.dp))
        Column {
            TextMMD(text = name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            TextMMD(
                text = stringResource(R.string.sky_moon_lit, (moon.phase.lit * 100).roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            TextMMD(
                text = stringResource(R.string.sky_next_phase, stringResource(principalName(next.principal)), date),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Moonrise and moonset in the order they come, each with the compass point it is at. */
private fun moonRows(moon: MoonDay): List<@Composable () -> Unit> {
    val rise: @Composable () -> Unit = {
        MoonTime(stringResource(R.string.sky_moonrise), moon.rise, moon.riseAzimuth)
    }
    val set: @Composable () -> Unit = {
        MoonTime(stringResource(R.string.sky_moonset), moon.set, moon.setAzimuth)
    }
    // A day with only one of them puts the one it has first.
    val setFirst = moon.set != null && (moon.rise == null || moon.set < moon.rise)
    return if (setFirst) listOf(set, rise) else listOf(rise, set)
}

@Composable
private fun MoonTime(label: String, time: LocalDateTime?, azimuth: Double?) {
    val compass = stringArrayResource(R.array.compass_16)
    if (time == null) {
        Reading(label, stringResource(R.string.sky_not_today), "", small = true)
    } else {
        Reading(label, clock(time), azimuth?.let { compass[((it / 22.5).roundToInt()) % 16] }.orEmpty(), small = true)
    }
}

/**
 * The sun: astronomical dawn, rise, set, astronomical dusk and the daylight between, each where
 * it happens. Far enough north, a summer's day has no set and a summer's night no full dark,
 * and those are said as one row rather than dashes.
 */
private fun sunRows(sun: SunDay): List<@Composable () -> Unit> {
    val rows = ArrayList<@Composable () -> Unit>()
    val dark = sun.astronomicalDawn != null || sun.astronomicalDusk != null
    if (dark) {
        rows += { SunTime(R.string.sky_astronomical_dawn, sun.astronomicalDawn) }
    }
    if (sun.rise == null && sun.set == null) {
        rows += {
            Reading(
                stringResource(R.string.sky_sun),
                stringResource(if (sun.upAtNoon) R.string.sky_sun_up_all_day else R.string.sky_sun_down_all_day),
                "",
                small = true,
            )
        }
    } else {
        rows += { SunTime(R.string.sky_sunrise, sun.rise) }
        rows += { SunTime(R.string.sky_sunset, sun.set) }
    }
    if (dark) {
        rows += { SunTime(R.string.sky_astronomical_dusk, sun.astronomicalDusk) }
    } else if (sun.twilightAtMidnight) {
        rows += {
            Reading(stringResource(R.string.sky_never_dark), stringResource(R.string.sky_never_dark_value), "", small = true)
        }
    }
    if (sun.rise != null && sun.set != null && sun.set > sun.rise) {
        rows += {
            val minutes = java.time.Duration.between(sun.rise, sun.set).toMinutes().toInt()
            Reading(
                stringResource(R.string.sky_daylight),
                stringResource(R.string.sky_duration, minutes / 60, minutes % 60),
                "",
                small = true,
            )
        }
    }
    return rows
}

@Composable
private fun SunTime(label: Int, time: LocalDateTime?) {
    Reading(stringResource(label), time?.let { clock(it) } ?: stringResource(R.string.sky_not_today), "", small = true)
}

/**
 * A month of moons, a week to a row, each day's moon drawn as it looks at noon and the days of
 * the principal phases in bold. ‹ and › step a month either way; today is underlined.
 */
@Composable
private fun MoonMonth(today: LocalDate, zone: ZoneId, south: Boolean) {
    var shown by rememberSaveable { mutableStateOf(0L) }
    val month = YearMonth.from(today).plusMonths(shown)
    val locale = Locale.getDefault()
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val days = remember(month, zone) {
        (1..month.lengthOfMonth()).map { d ->
            val date = month.atDay(d)
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            Triple(
                date,
                Almanac.phase(date.atTime(12, 0).atZone(zone).toInstant()),
                Almanac.phases(start, 1).first().takeIf { it.at.isBefore(end) }?.principal,
            )
        }
    }
    val ink = MaterialTheme.colorScheme.onSurface
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Arrow("‹", stringResource(R.string.sky_cd_month_before)) { shown-- }
            TextMMD(
                text = DateTimeFormatter.ofPattern(
                    android.text.format.DateFormat.getBestDateTimePattern(locale, "MMMMyyyy"),
                    locale,
                ).format(month),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            Arrow("›", stringResource(R.string.sky_cd_month_after)) { shown++ }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            (0 until 7).forEach { i ->
                TextMMD(
                    text = firstDay.plus(i.toLong()).getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        // Blank days before the first, so it falls under its own weekday.
        val lead = (month.atDay(1).dayOfWeek.value - firstDay.value + 7) % 7
        val cells: List<Triple<LocalDate, Phase, Principal?>?> = List(lead) { null } + days
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                (0 until 7).forEach { i ->
                    val cell = week.getOrNull(i)
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (cell != null) {
                            val (date, phase, principal) = cell
                            TextMMD(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (principal != null) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.drawBehind {
                                    if (date == today) {
                                        val y = size.height
                                        drawLine(ink, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.dp.toPx())
                                    }
                                },
                            )
                            Spacer(Modifier.height(2.dp))
                            MoonFace(phase, south, 22.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Arrow(glyph: String, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        TextMMD(text = glyph, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * The moon as it looks: its dark part in ink and its lit part left white, inside a ring so a
 * full moon is still a moon. South of the equator it is lit from the other side.
 */
@Composable
internal fun MoonFace(phase: Phase, south: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val paper = MaterialTheme.colorScheme.surface
    Canvas(modifier = modifier.size(size)) { drawMoon(phase, south, ink, paper) }
}

private fun DrawScope.drawMoon(
    phase: Phase,
    south: Boolean,
    ink: androidx.compose.ui.graphics.Color,
    paper: androidx.compose.ui.graphics.Color,
) {
    val r = size.minDimension / 2
    val c = center
    drawCircle(ink, r, c)
    val lit = phase.lit
    if (lit > 0.005) {
        // Lit on the right while it waxes, seen from the north.
        val right = phase.waxing != south
        val crescent = lit < 0.5
        // The terminator is half an ellipse, as wide as the lit part is short of a half.
        val tx = r * abs(1 - 2 * lit).toFloat()
        val path = Path().apply {
            arcTo(Rect(c, r), -90f, if (right) 180f else -180f, forceMoveTo = true)
            arcTo(
                Rect(c.x - tx, c.y - r, c.x + tx, c.y + r),
                90f,
                if (crescent == right) -180f else 180f,
                forceMoveTo = false,
            )
            close()
        }
        drawPath(path, paper)
    }
    drawCircle(ink, r - 0.75.dp.toPx(), c, style = Stroke(width = 1.5.dp.toPx()))
}

/** The day's own page: its sunshine, and the moon that day, from the place the forecast is for. */
internal fun almanacDayRows(day: Day, at: com.wanderwildwood.soramoyo.location.LatLon?, zone: ZoneId): List<@Composable () -> Unit> {
    val rows = ArrayList<@Composable () -> Unit>()
    day.sunshine?.let { sunshine ->
        rows += {
            val hours = if (sunshine > 0.0 && sunshine < 0.5) "<1" else sunshine.roundToInt().toString()
            Reading(
                label = stringResource(R.string.sky_sunshine),
                value = hours,
                unit = stringResource(R.string.sky_hours_short),
                note = day.daylight?.let { stringResource(R.string.sky_sunshine_of, it.roundToInt().toString()) },
            )
        }
    }
    at ?: return rows
    val moon = Almanac.moon(day.date, zone, at.lat, at.lon)
    rows += {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                TextMMD(text = stringResource(R.string.sky_moon), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(2.dp))
                TextMMD(
                    text = stringResource(moonName(moon.phase.name(moon.principal))) + ", " +
                        stringResource(R.string.sky_moon_lit, (moon.phase.lit * 100).roundToInt()),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            MoonFace(moon.phase, at.lat < 0, 32.dp)
        }
    }
    rows += moonRows(moon)
    return rows
}

private fun moonName(name: MoonName): Int = when (name) {
    MoonName.NEW -> R.string.moon_new
    MoonName.WAXING_CRESCENT -> R.string.moon_waxing_crescent
    MoonName.FIRST_QUARTER -> R.string.moon_first_quarter
    MoonName.WAXING_GIBBOUS -> R.string.moon_waxing_gibbous
    MoonName.FULL -> R.string.moon_full
    MoonName.WANING_GIBBOUS -> R.string.moon_waning_gibbous
    MoonName.LAST_QUARTER -> R.string.moon_last_quarter
    MoonName.WANING_CRESCENT -> R.string.moon_waning_crescent
}

private fun principalName(p: Principal): Int = when (p) {
    Principal.NEW -> R.string.moon_new
    Principal.FIRST_QUARTER -> R.string.moon_first_quarter
    Principal.FULL -> R.string.moon_full
    Principal.LAST_QUARTER -> R.string.moon_last_quarter
}
