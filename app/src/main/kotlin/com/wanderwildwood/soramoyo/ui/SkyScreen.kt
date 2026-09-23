package com.wanderwildwood.soramoyo.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.tabs.TabMMD
import com.mudita.mmd.components.tabs.TabRowMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.soramoyo.R
import com.wanderwildwood.soramoyo.forecast.Day
import com.wanderwildwood.soramoyo.station.Source
import com.wanderwildwood.soramoyo.station.StationReading
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** The three tabs, in the order they sit. */
enum class Tab { TODAY, FORECAST, RADAR }

/**
 * Sky's one screen: a top bar, three tabs, and whichever tab is open beneath them.
 *
 * Today opens, because it is what the app is opened for. The days ahead and the radar are a
 * press away, each with the whole panel to itself rather than sharing a long list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyTabs(
    tab: Tab,
    onTab: (Tab) -> Unit,
    state: SkyState,
    radar: RadarViewModel,
    onSettings: () -> Unit,
    onAllowLocation: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                TopAppBarMMD(
                    title = { TextMMD(text = stringResource(R.string.app_name)) },
                    actions = { BarButton(Icons.Settings, stringResource(R.string.sky_cd_settings), onSettings) },
                )
                // MMD's plain tab row, which does not slide between tabs. Its own underline is
                // left out: in this row it measures to the row's full height and draws a black
                // block over the tabs beside the chosen one. The chosen tab is its label in
                // bold with a rule under it instead, drawn by the tab itself.
                TabRowMMD(
                    selectedTabIndex = tab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    indicator = {},
                ) {
                    Tab.entries.forEach { t ->
                        val chosen = t == tab
                        val rule = MaterialTheme.colorScheme.onSurface
                        TabMMD(
                            selected = chosen,
                            onClick = { onTab(t) },
                            text = {
                                TextMMD(
                                    text = stringResource(
                                        when (t) {
                                            Tab.TODAY -> R.string.tab_today
                                            Tab.FORECAST -> R.string.tab_forecast
                                            Tab.RADAR -> R.string.tab_radar
                                        },
                                    ),
                                    fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier
                                        .padding(vertical = 10.dp)
                                        .drawBehind {
                                            if (chosen) {
                                                val y = size.height + 4.dp.toPx()
                                                drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 3.dp.toPx())
                                            }
                                        },
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { contentPadding ->
        val inside = Modifier.fillMaxSize().padding(contentPadding)
        when (tab) {
            Tab.TODAY -> TodayTab(state, onAllowLocation, inside)
            Tab.FORECAST -> ForecastTab(state, onAllowLocation, inside)
            Tab.RADAR -> RadarTab(radar, onAllowLocation, inside)
        }
    }
}

/**
 * What the station reads now, and what today is forecast to do.
 *
 * The temperature is the large figure because it is the one read at a glance; the rest are
 * rows because they are read deliberately or not at all.
 */
@Composable
private fun TodayTab(state: SkyState, onAllowLocation: () -> Unit, modifier: Modifier) {
    val today = state.days.firstOrNull()?.takeIf { it.date == LocalDate.now() }
    // Most people who install this have no station, and for them today's forecast is the
    // headline rather than a line under a reading they will never have. The station lives
    // in settings; nothing here asks for one.
    val noStation = state.stationTrouble == StationTrouble.NOT_SET
    LazyColumnMMD(modifier = modifier.padding(horizontal = 20.dp)) {
        if (noStation) {
            item { Outlook(today, state, onAllowLocation) }
        } else {
            item { Now(state) }
            item {
                HorizontalDividerMMD()
                if (today != null) {
                    DayRow(today)
                } else {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        ForecastTrouble(state.forecastTrouble, true, onAllowLocation)
                    }
                }
            }
        }

        state.reading?.let { reading ->
            readingRows(reading).forEach { row ->
                item {
                    HorizontalDividerMMD()
                    row()
                }
            }
        }

        if (today?.sunrise != null && today.sunset != null) {
            item {
                HorizontalDividerMMD()
                Reading(stringResource(R.string.sky_sunrise), clock(today.sunrise), "", small = true)
            }
            item {
                HorizontalDividerMMD()
                Reading(stringResource(R.string.sky_sunset), clock(today.sunset), "", small = true)
            }
        }
        if (today != null) {
            item { Credit(state) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** The days after today, as many as the forecast gives, which is five. */
@Composable
private fun ForecastTab(state: SkyState, onAllowLocation: () -> Unit, modifier: Modifier) {
    val ahead = state.days.filter { it.date.isAfter(LocalDate.now()) }
    LazyColumnMMD(modifier = modifier.padding(horizontal = 20.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            ForecastTrouble(state.forecastTrouble, ahead.isEmpty(), onAllowLocation)
        }
        ahead.forEachIndexed { i, day ->
            item {
                if (i > 0) HorizontalDividerMMD()
                DayRow(day)
            }
        }
        if (ahead.isNotEmpty()) {
            item { Credit(state) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Whose forecast, and for where when a place was chosen rather than read from the phone. */
@Composable
private fun Credit(state: SkyState) {
    Column {
        Spacer(Modifier.height(10.dp))
        state.place?.let {
            TextMMD(
                text = stringResource(R.string.sky_forecast_for, it.label.substringBefore(",")),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        TextMMD(text = stringResource(R.string.sky_forecast_credit), style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Today's forecast as the headline, for a phone with no station: the high and low where the
 * station's temperature would be, and what the day is expected to do beneath.
 */
@Composable
private fun Outlook(today: Day?, state: SkyState, onAllowLocation: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp)) {
        if (today == null) {
            if (state.forecastTrouble == ForecastTrouble.NONE) {
                TextMMD(text = stringResource(R.string.sky_forecast_asking), style = MaterialTheme.typography.bodySmall)
            } else {
                ForecastTrouble(state.forecastTrouble, true, onAllowLocation)
            }
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom) {
            TextMMD(text = "${today.high}°", fontSize = 72.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(10.dp))
            TextMMD(
                text = stringResource(R.string.sky_low, today.low),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
        val condition = stringResource(conditionFor(today.code))
        TextMMD(
            text = today.rainChance?.takeIf { it > 0 }
                ?.let { stringResource(R.string.sky_condition_with_rain, condition, it) }
                ?: condition,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** The temperature, what it feels like, and how old it is — or why there is none. */
@Composable
private fun Now(state: SkyState) {
    val reading = state.reading
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp)) {
        when {
            reading == null && state.stationTrouble == StationTrouble.NO_ANSWER -> {
                TextMMD(
                    text = when (val source = state.source) {
                        is Source.Ecowitt -> stringResource(R.string.sky_not_answering, source.address)
                        is Source.Davis -> stringResource(R.string.sky_not_answering, source.address)
                        Source.Tempest -> stringResource(R.string.sky_tempest_silent)
                        is Source.Underground -> stringResource(R.string.sky_wu_silent, source.stationId)
                        Source.None -> ""
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                return@Column
            }

            reading == null -> {
                TextMMD(text = stringResource(R.string.sky_asking), style = MaterialTheme.typography.bodySmall)
                return@Column
            }
        }
        reading ?: return@Column

        Row(verticalAlignment = Alignment.Bottom) {
            // A figure the instrument reads, like a speedometer's, rather than type.
            TextMMD(
                text = reading.temperature?.let { "${it.number.roundToInt()}°" } ?: "–",
                fontSize = 72.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(6.dp))
            TextMMD(
                text = reading.temperature?.unit.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
        reading.feelsLike?.let { feels ->
            TextMMD(
                text = stringResource(R.string.sky_feels_like, feels.number.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(4.dp))
        TextMMD(
            text = if (state.stationTrouble == StationTrouble.NO_ANSWER) {
                stringResource(R.string.sky_last_read, clock(state.readAt))
            } else {
                stringResource(R.string.sky_read_at, clock(state.readAt))
            },
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/**
 * The rows under the temperature, each only if the station reported it. A station without a
 * soil probe has no soil row, rather than a dash beside one.
 */
private fun readingRows(reading: StationReading): List<@Composable () -> Unit> {
    val rows = ArrayList<@Composable () -> Unit>()

    reading.windSpeed?.let { wind ->
        rows += {
            val compass = stringArrayResource(R.array.compass_16)
            val speed = wind.number.roundToInt()
            // A direction on a still day is the vane's last resting place, not a wind.
            val from = reading.windFrom?.takeIf { speed > 0 }?.let { compass[((it / 22.5).roundToInt()) % 16] }
            Reading(
                label = stringResource(R.string.sky_wind),
                value = listOfNotNull(from, speed.toString()).joinToString(" "),
                unit = wind.unit,
            )
        }
    }
    reading.windGust?.let { gust ->
        rows += { Reading(stringResource(R.string.sky_gusts), gust.number.roundToInt().toString(), gust.unit) }
    }
    reading.rainToday?.let { rain ->
        val rate = reading.rainRate
        val falling = reading.raining == true || (rate != null && rate.number > 0.0)
        rows += {
            Reading(
                label = stringResource(R.string.sky_rain_today),
                value = rain.text,
                unit = rain.unit,
                note = when {
                    !falling -> null
                    rate != null && rate.number > 0.0 ->
                        stringResource(R.string.sky_rain_falling_at, rate.text, rate.unit)
                    else -> stringResource(R.string.sky_rain_falling)
                },
            )
        }
    }
    reading.humidity?.let { rows += { Reading(stringResource(R.string.sky_humidity), it.toString(), "%") } }
    reading.dewPoint?.let { dew ->
        rows += { Reading(stringResource(R.string.sky_dew_point), "${dew.number.roundToInt()}°", dew.unit) }
    }
    reading.pressure?.let { p -> rows += { Reading(stringResource(R.string.sky_pressure), p.text, p.unit) } }
    reading.uvIndex?.let { uv -> rows += { Reading(stringResource(R.string.sky_uv), uv.roundToInt().toString(), "") } }
    reading.soilMoisture?.let { rows += { Reading(stringResource(R.string.sky_soil), it.toString(), "%") } }
    return rows
}

@Composable
private fun ForecastTrouble(trouble: ForecastTrouble, nothingYet: Boolean, onAllow: () -> Unit) {
    val words = when (trouble) {
        ForecastTrouble.NONE -> return
        ForecastTrouble.NO_PERMISSION -> stringResource(R.string.sky_forecast_no_permission)
        ForecastTrouble.NO_LOCATION -> stringResource(R.string.sky_forecast_no_location)
        // With an older forecast still on the screen, the failure to refresh it is not
        // worth a line; it will be asked again next time.
        ForecastTrouble.NO_ANSWER -> if (nothingYet) stringResource(R.string.sky_forecast_no_answer) else return
    }
    TextMMD(text = words, style = MaterialTheme.typography.labelSmall)
    if (trouble == ForecastTrouble.NO_PERMISSION) {
        Spacer(Modifier.height(10.dp))
        OutlinedButtonMMD(
            onClick = onAllow,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { TextMMD(text = stringResource(R.string.allow_location), style = MaterialTheme.typography.bodySmall) }
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun DayRow(day: Day) {
    val today = LocalDate.now()
    val name = when (day.date) {
        today -> stringResource(R.string.sky_today)
        today.plusDays(1) -> stringResource(R.string.sky_tomorrow)
        else -> day.date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
    val condition = stringResource(conditionFor(day.code))

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TextMMD(text = name, style = MaterialTheme.typography.bodyMedium)
            TextMMD(
                text = day.rainChance?.takeIf { it > 0 }
                    ?.let { stringResource(R.string.sky_condition_with_rain, condition, it) }
                    ?: condition,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        TextMMD(
            text = stringResource(R.string.sky_high_low, day.high, day.low),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
        )
    }
}

/** A label, a number with its unit, and a note only where a label cannot carry it. */
@Composable
private fun Reading(label: String, value: String, unit: String, note: String? = null, small: Boolean = false) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            TextMMD(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                TextMMD(
                    text = value,
                    style = if (small) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.size(6.dp))
                    TextMMD(text = unit, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        if (note != null) {
            Spacer(Modifier.height(2.dp))
            TextMMD(text = note, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private val clockFormat: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

private fun clock(millis: Long): String =
    clockFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

private fun clock(time: java.time.LocalDateTime): String = clockFormat.format(time)

/** WMO weather interpretation codes, as Open-Meteo uses them, in a few plain words. */
private fun conditionFor(code: Int): Int = when (code) {
    0 -> R.string.wmo_clear
    1 -> R.string.wmo_mostly_clear
    2 -> R.string.wmo_partly_cloudy
    3 -> R.string.wmo_overcast
    45, 48 -> R.string.wmo_fog
    51, 53, 55 -> R.string.wmo_drizzle
    56, 57 -> R.string.wmo_freezing_drizzle
    61 -> R.string.wmo_light_rain
    63 -> R.string.wmo_rain
    65 -> R.string.wmo_heavy_rain
    66, 67 -> R.string.wmo_freezing_rain
    71 -> R.string.wmo_light_snow
    73 -> R.string.wmo_snow
    75 -> R.string.wmo_heavy_snow
    77 -> R.string.wmo_snow_grains
    80, 81 -> R.string.wmo_showers
    82 -> R.string.wmo_heavy_showers
    85, 86 -> R.string.wmo_snow_showers
    95 -> R.string.wmo_thunderstorm
    96, 99 -> R.string.wmo_thunderstorm_hail
    else -> R.string.wmo_unknown
}

@Composable
internal fun BarButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}
