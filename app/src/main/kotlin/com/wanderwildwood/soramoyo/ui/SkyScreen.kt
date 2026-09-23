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
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.soramoyo.R
import com.wanderwildwood.soramoyo.forecast.Day
import com.wanderwildwood.soramoyo.station.StationReading
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What the station reads now, and the next three days.
 *
 * The temperature is the large figure because it is the one read at a glance; the rest are
 * rows because they are read deliberately or not at all. The radar is a press away in the
 * top bar rather than on this screen, because it wants the whole panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkyScreen(
    state: SkyState,
    onRadar: () -> Unit,
    onSettings: () -> Unit,
    onAllowLocation: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.app_name)) },
                actions = {
                    BarButton(Icons.Radar, stringResource(R.string.sky_cd_radar), onRadar)
                    BarButton(Icons.Settings, stringResource(R.string.sky_cd_settings), onSettings)
                },
            )
        },
    ) { contentPadding ->
        LazyColumnMMD(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            item { Now(state, onSettings) }

            state.reading?.let { reading ->
                readingRows(reading).forEach { row ->
                    item {
                        HorizontalDividerMMD()
                        row()
                    }
                }
            }

            item {
                Spacer(Modifier.height(20.dp))
                TextMMD(
                    text = state.place?.let { stringResource(R.string.sky_forecast_for, it.label.substringBefore(",")) }
                        ?: stringResource(R.string.sky_forecast),
                    style = MaterialTheme.typography.titleSmall,
                )
                Spacer(Modifier.height(4.dp))
                ForecastTrouble(state.forecastTrouble, state.days.isEmpty(), onAllowLocation)
            }
            state.days.forEach { day ->
                item {
                    HorizontalDividerMMD()
                    DayRow(day)
                }
            }
            state.days.firstOrNull()?.let { today ->
                if (today.sunrise != null && today.sunset != null) {
                    item {
                        HorizontalDividerMMD()
                        Reading(stringResource(R.string.sky_sunrise), clock(today.sunrise), "", small = true)
                    }
                    item {
                        HorizontalDividerMMD()
                        Reading(stringResource(R.string.sky_sunset), clock(today.sunset), "", small = true)
                    }
                }
            }
            if (state.days.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(10.dp))
                    TextMMD(text = stringResource(R.string.sky_forecast_credit), style = MaterialTheme.typography.labelSmall)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** The temperature, what it feels like, and how old it is — or why there is none. */
@Composable
private fun Now(state: SkyState, onSettings: () -> Unit) {
    val reading = state.reading
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp)) {
        when {
            state.stationTrouble == StationTrouble.NOT_SET -> {
                TextMMD(text = stringResource(R.string.sky_no_station), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                OutlinedButtonMMD(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) { TextMMD(text = stringResource(R.string.sky_set_station), style = MaterialTheme.typography.bodySmall) }
                return@Column
            }

            reading == null && state.stationTrouble == StationTrouble.NO_ANSWER -> {
                TextMMD(
                    text = stringResource(R.string.sky_not_answering, state.address),
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
