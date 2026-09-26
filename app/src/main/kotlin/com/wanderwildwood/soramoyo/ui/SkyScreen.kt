package com.wanderwildwood.soramoyo.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
import com.wanderwildwood.soramoyo.forecast.Air
import com.wanderwildwood.soramoyo.forecast.AirBand
import com.wanderwildwood.soramoyo.forecast.AirQuality
import com.wanderwildwood.soramoyo.forecast.Change
import com.wanderwildwood.soramoyo.forecast.Current
import com.wanderwildwood.soramoyo.forecast.Day
import com.wanderwildwood.soramoyo.forecast.HOURS
import com.wanderwildwood.soramoyo.forecast.Hour
import com.wanderwildwood.soramoyo.forecast.Pollen
import com.wanderwildwood.soramoyo.forecast.restOf
import com.wanderwildwood.soramoyo.forecast.upcoming
import com.wanderwildwood.soramoyo.station.Measure
import com.wanderwildwood.soramoyo.station.Source
import com.wanderwildwood.soramoyo.station.StationReading
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    onDay: (LocalDate) -> Unit,
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
            Tab.FORECAST -> ForecastTab(state, onAllowLocation, onDay, inside)
            Tab.RADAR -> RadarTab(radar, onAllowLocation, state.metric, inside)
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
    // The rest of today, not the whole of it: its morning's rain is over by the afternoon.
    val today = state.days.firstOrNull { it.date == state.placeToday() }?.restOf(state.hours, state.offset)
    // Most people who install this have no station, and for them today's forecast is the
    // headline rather than a line under a reading they will never have. The station lives
    // in settings; nothing here asks for one.
    val noStation = state.stationTrouble == StationTrouble.NOT_SET
    // No rail here: it took a strip off the right of every row, and Today is read top down
    // with a swipe, which still moves it four rows at a time.
    LazyColumnMMD(modifier = modifier.padding(horizontal = 20.dp), isScrollbarVisible = false) {
        val hours = upcoming(state.hours, state.offset)
        val current = state.current
        if (noStation) {
            if (current != null) {
                item { Estimate(current, state.metric) }
                if (today != null) {
                    item {
                        HorizontalDividerMMD()
                        DayRow(today, state.inches, today.date)
                    }
                }
            } else {
                item { Outlook(today, state, onAllowLocation) }
            }
            if (hours.isNotEmpty()) {
                item {
                    HorizontalDividerMMD()
                    Hours(hours, state.inches, rainingNow(state))
                }
            }
        } else {
            item { Now(state) }
            item {
                HorizontalDividerMMD()
                if (today != null) {
                    DayRow(today, state.inches, today.date)
                } else {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        ForecastTrouble(state.forecastTrouble, true, onAllowLocation)
                    }
                }
            }
            if (hours.isNotEmpty()) {
                item {
                    HorizontalDividerMMD()
                    Hours(hours, state.inches, rainingNow(state))
                }
            }
        }

        // Measured if there is a station, else the forecast's estimate of the same things.
        val rows = state.reading ?: current?.takeIf { noStation }?.let { asReading(it, state.metric) }
        rows?.let { reading ->
            readingRows(reading).forEach { row ->
                item {
                    HorizontalDividerMMD()
                    row()
                }
            }
        }
        state.air?.let { air ->
            airRows(air, state.metric).forEach { row ->
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

/** The days after today, as many as the forecast gives, which is five. Each opens its own page. */
@Composable
private fun ForecastTab(state: SkyState, onAllowLocation: () -> Unit, onDay: (LocalDate) -> Unit, modifier: Modifier) {
    val placeToday = state.placeToday()
    val ahead = state.days.filter { it.date.isAfter(placeToday) }
    LazyColumnMMD(modifier = modifier.padding(horizontal = 20.dp)) {
        item {
            Spacer(Modifier.height(12.dp))
            ForecastTrouble(state.forecastTrouble, ahead.isEmpty(), onAllowLocation)
        }
        ahead.forEachIndexed { i, day ->
            item {
                if (i > 0) HorizontalDividerMMD()
                DayRow(day, state.inches, placeToday, Modifier.clickable { onDay(day.date) })
            }
        }
        if (ahead.isNotEmpty()) {
            item { Credit(state, air = false) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * Whose forecast, and for where when a place was chosen rather than read from the phone. The
 * air's source is named only where the air is shown.
 */
@Composable
private fun Credit(state: SkyState, air: Boolean = state.air != null) {
    Column {
        Spacer(Modifier.height(10.dp))
        state.place?.let {
            TextMMD(
                text = stringResource(R.string.sky_forecast_for, it.label.substringBefore(",")),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        TextMMD(text = stringResource(R.string.sky_forecast_credit), style = MaterialTheme.typography.labelSmall)
        if (air) {
            TextMMD(text = stringResource(R.string.sky_air_credit), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * The forecast's estimate of now, for a phone with no station: the same large figure a
 * station's reading gets, and a line under it saying it is an estimate and for when.
 */
@Composable
private fun Estimate(current: Current, metric: Boolean) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            TextMMD(text = "${current.temperature.roundToInt()}°", fontSize = 72.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
            TextMMD(
                text = if (metric) "C" else "F",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
        current.feelsLike?.let {
            TextMMD(
                text = stringResource(R.string.sky_feels_like, it.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(4.dp))
        TextMMD(
            text = stringResource(R.string.sky_estimate, stringResource(conditionFor(current.code)), clock(current.time)),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

/** The forecast's estimate, in the shape a station's reading has, so it gets the same rows. */
private fun asReading(c: Current, metric: Boolean): StationReading {
    fun m(v: Double?, unit: String) = v?.let { Measure(it, unit, String.format(Locale.US, "%.1f", it)) }
    val speed = if (metric) "km/h" else "mph"
    return StationReading(
        temperature = m(c.temperature, if (metric) "C" else "F"),
        feelsLike = m(c.feelsLike, if (metric) "C" else "F"),
        dewPoint = null,
        humidity = c.humidity,
        windSpeed = m(c.windSpeed, speed),
        windGust = m(c.windGust, speed),
        windFrom = c.windFrom,
        rainToday = null,
        rainRate = null,
        raining = null,
        pressure = null,
        uvIndex = null,
        soilMoisture = null,
    )
}

/**
 * The air: its quality on the scale that goes with the units (the US index with imperial,
 * the European one with metric), and the two heaviest pollens where there are any.
 */
private fun airRows(air: Air, metric: Boolean): List<@Composable () -> Unit> {
    val rows = ArrayList<@Composable () -> Unit>()
    val us = !metric || air.europeanAqi == null
    val aqi = if (us) air.usAqi else air.europeanAqi
    if (aqi != null) {
        rows += {
            val band = if (us) AirQuality.usBand(aqi) else AirQuality.europeanBand(aqi)
            Reading(
                label = stringResource(R.string.sky_air),
                value = aqi.toString(),
                unit = stringResource(if (us) R.string.sky_air_us else R.string.sky_air_eu),
                note = stringResource(bandName(band, us)),
            )
        }
    }
    air.pollen.take(2).forEach { (kind, grains) ->
        rows += {
            Reading(
                label = stringResource(pollenName(kind)),
                value = grains.roundToInt().toString(),
                unit = stringResource(R.string.sky_pollen_unit),
            )
        }
    }
    return rows
}

/** Each scale's own words for its bands: the EPA's six and the EEA's six are not the same. */
private fun bandName(band: AirBand, us: Boolean): Int = when (band) {
    AirBand.GOOD -> R.string.air_good
    AirBand.FAIR -> R.string.air_fair
    AirBand.MODERATE -> R.string.air_moderate
    AirBand.SENSITIVE -> R.string.air_sensitive
    AirBand.POOR -> if (us) R.string.air_unhealthy else R.string.air_poor
    AirBand.VERY_POOR -> if (us) R.string.air_very_unhealthy else R.string.air_very_poor
    AirBand.EXTREME -> if (us) R.string.air_hazardous else R.string.air_extremely_poor
}

private fun pollenName(kind: Pollen): Int = when (kind) {
    Pollen.ALDER -> R.string.pollen_alder
    Pollen.BIRCH -> R.string.pollen_birch
    Pollen.GRASS -> R.string.pollen_grass
    Pollen.MUGWORT -> R.string.pollen_mugwort
    Pollen.OLIVE -> R.string.pollen_olive
    Pollen.RAGWEED -> R.string.pollen_ragweed
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
                ?.let {
                    stringResource(
                        if (today.snowy) R.string.sky_condition_with_snow else R.string.sky_condition_with_rain,
                        condition,
                        it,
                    )
                }
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
                        // An empty key is not a wrong one: the key is sealed with this phone's
                        // keystore, so after a reinstall or on a new phone it cannot be opened and
                        // reads back empty. Said as what it is, so nobody hunts for a typo.
                        is Source.Underground -> if (source.apiKey.isEmpty()) {
                            stringResource(R.string.sky_wu_key_gone, source.stationId)
                        } else {
                            stringResource(R.string.sky_wu_silent, source.stationId)
                        }
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
private fun DayRow(day: Day, inches: Boolean, today: LocalDate, modifier: Modifier = Modifier) {
    val name = dayName(day.date, today)
    val condition = stringResource(conditionFor(day.code))

    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            TextMMD(text = name, style = MaterialTheme.typography.bodyMedium)
            val chance = day.rainChance?.takeIf { it > 0 }
            // A snowy day gives its snow, as deep as it lies; its rain amount would be the
            // snow melted to water, which is not how anyone measures a snowfall.
            val amount = if (day.snowy) snowDepth(day.snow, inches) else amount(day.rain, inches)
            val amountUnit = if (day.snowy) snowUnit(inches) else unit(inches)
            TextMMD(
                text = when {
                    day.snowy && chance != null && amount != null ->
                        stringResource(R.string.sky_condition_with_snow_amount, condition, chance, amount, amountUnit)
                    day.snowy && chance != null -> stringResource(R.string.sky_condition_with_snow, condition, chance)
                    chance != null && amount != null ->
                        stringResource(R.string.sky_condition_with_rain_amount, condition, chance, amount, amountUnit)
                    chance != null -> stringResource(R.string.sky_condition_with_rain, condition, chance)
                    else -> condition
                },
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            TextMMD(
                text = stringResource(R.string.sky_high_low, day.high, day.low),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
            )
            // Only where it differs: a day of -5° in a hard wind is not a still -5°.
            if (day.feelsDifferent(metric = !inches)) {
                TextMMD(
                    text = stringResource(R.string.sky_feels_like_high_low, day.feelsHigh!!, day.feelsLow!!),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Composable
private fun dayName(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.sky_today)
    today.plusDays(1) -> stringResource(R.string.sky_tomorrow)
    else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
}

/**
 * One day of the forecast on a page of its own, opened from the Forecast tab: the high as the
 * large figure, what the day feels like, its twenty-four hours as two strips of twelve, and
 * rows for the rain, snow, wind, UV and the sun. Everything the list has room only to hint at.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(date: LocalDate, state: SkyState, onClose: () -> Unit) {
    val day = state.days.firstOrNull { it.date == date }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = dayName(date, state.placeToday())) },
                navigationIcon = { BarButton(Icons.Back, stringResource(R.string.sky_cd_back), onClose) },
            )
        },
    ) { contentPadding ->
        LazyColumnMMD(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 20.dp),
            isScrollbarVisible = false,
        ) {
            // A forecast refreshed past midnight no longer has yesterday's tomorrow in it.
            if (day == null) {
                item {
                    Spacer(Modifier.height(12.dp))
                    TextMMD(text = stringResource(R.string.sky_day_gone), style = MaterialTheme.typography.bodySmall)
                }
                return@LazyColumnMMD
            }
            item { DayHeadline(day) }

            val hours = state.hours.filter { it.time.toLocalDate() == date }
            if (hours.isNotEmpty()) {
                item {
                    HorizontalDividerMMD()
                    DayHours(hours)
                }
            }

            dayRows(day, state.inches, state.metric).forEach { row ->
                item {
                    HorizontalDividerMMD()
                    row()
                }
            }
            if (day.sunrise != null && day.sunset != null) {
                item {
                    HorizontalDividerMMD()
                    Reading(stringResource(R.string.sky_sunrise), clock(day.sunrise), "", small = true)
                }
                item {
                    HorizontalDividerMMD()
                    Reading(stringResource(R.string.sky_sunset), clock(day.sunset), "", small = true)
                }
            }
            item { Credit(state, air = false) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** The date, the high and low, what they feel like, and what the day is expected to do. */
@Composable
private fun DayHeadline(day: Day) {
    val locale = Locale.getDefault()
    val written = DateTimeFormatter.ofPattern(
        android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"),
        locale,
    ).format(day.date)
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp)) {
        TextMMD(text = written, style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.Bottom) {
            TextMMD(text = "${day.high}°", fontSize = 72.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(10.dp))
            TextMMD(
                text = stringResource(R.string.sky_low, day.low),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
        // Here always, where there is room for it; in the list only where it differs.
        if (day.feelsHigh != null && day.feelsLow != null) {
            TextMMD(
                text = stringResource(R.string.sky_feels_like_high_low, day.feelsHigh, day.feelsLow),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(4.dp))
        TextMMD(text = stringResource(conditionFor(day.code)), style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * The day's hours: a line saying when rain is likely, over the hours before noon and those
 * after, each a strip like Today's. Twelve to a strip because that is what fits the panel.
 */
@Composable
private fun DayHours(hours: List<Hour>) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        TextMMD(
            text = when (val change = Change.of(hours)) {
                Change.Dry -> stringResource(R.string.sky_day_dry)
                // A whole day is read to plan it, so where the wet spell ends inside the day
                // it is said too; Today's next twelve hours tell only the first turn.
                is Change.From -> {
                    val end = hours.firstOrNull { it.time.isAfter(change.at) && !Change.wet(it) }
                    if (end != null) {
                        stringResource(
                            if (change.snow) R.string.sky_day_snow_between else R.string.sky_day_rain_between,
                            clock(change.at),
                            clock(end.time),
                        )
                    } else {
                        stringResource(
                            if (change.snow) R.string.sky_hours_snow_from else R.string.sky_hours_rain_from,
                            clock(change.at),
                        )
                    }
                }
                is Change.Until -> stringResource(
                    if (change.snow) R.string.sky_hours_snow_until else R.string.sky_hours_rain_until,
                    clock(change.at),
                )
                is Change.Throughout -> stringResource(
                    if (change.snow) R.string.sky_day_snow_all else R.string.sky_day_rain_all,
                )
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        // Split at noon rather than every twelve, so a day the clocks change on (23 or 25
        // hours) does not leave an hour on a strip of its own.
        hours.partition { it.time.hour < 12 }.toList().filter { it.isNotEmpty() }.forEach { half ->
            Spacer(Modifier.height(14.dp))
            Strip(half)
        }
    }
}

/** The day's rows, each only where the forecast gave it. */
private fun dayRows(day: Day, inches: Boolean, metric: Boolean): List<@Composable () -> Unit> {
    val rows = ArrayList<@Composable () -> Unit>()
    val chance = day.rainChance
    // The chance and the hours are of anything falling; they go under the snow on a snowy
    // day, and under the rain otherwise.
    val odds: @Composable () -> String? = {
        val hours = day.rainHours?.roundToInt()?.takeIf { it > 0 }
        when {
            chance != null && hours != null -> pluralStringResource(R.plurals.sky_rain_chance_hours, hours, chance, hours)
            chance != null -> stringResource(R.string.sky_rain_chance, chance)
            else -> null
        }
    }
    val snow = day.snow?.takeIf { it > 0.0 }
    if (snow != null) {
        rows += {
            Reading(
                label = stringResource(R.string.sky_snow),
                value = snowDepth(snow, inches) ?: "0",
                unit = snowUnit(inches),
                note = odds(),
            )
        }
        // The rain alone, not the snow again as water.
        amount(day.rainOnly, inches)?.let { rain ->
            rows += { Reading(stringResource(R.string.sky_rain), rain, unit(inches)) }
        }
    } else if (chance != null || day.rain != null) {
        rows += {
            Reading(
                label = stringResource(R.string.sky_rain),
                value = amount(day.rain, inches) ?: "0",
                unit = unit(inches),
                note = odds(),
            )
        }
    }
    val speed = if (metric) "km/h" else "mph"
    day.windMax?.let { wind ->
        rows += {
            val compass = stringArrayResource(R.array.compass_16)
            val strength = wind.roundToInt()
            val from = day.windFrom?.takeIf { strength > 0 }?.let { compass[((it / 22.5).roundToInt()) % 16] }
            Reading(
                label = stringResource(R.string.sky_wind),
                value = listOfNotNull(from, strength.toString()).joinToString(" "),
                unit = speed,
                note = stringResource(R.string.sky_wind_strongest),
            )
        }
    }
    day.gustMax?.let { gust ->
        rows += { Reading(stringResource(R.string.sky_gusts), gust.roundToInt().toString(), speed) }
    }
    day.uvMax?.let { uv ->
        rows += { Reading(stringResource(R.string.sky_uv), uv.roundToInt().toString(), "") }
    }
    return rows
}

/**
 * The next hours: a line saying when rain is likely to start or stop, and under it a strip,
 * an hour to a column, of the chance of rain drawn as a bar, with a dotted line where it
 * becomes likely. The line is what is read; the strip is there to see how sure it is. The
 * temperature and the hour are written every third column: every column was tried, and on
 * the Kompakt's panel the figures ran together into one string.
 */
@Composable
private fun Hours(hours: List<Hour>, inches: Boolean, raining: Boolean) {
    val now = stringResource(R.string.sky_now)
    val count = hours.size
    val change = Change.of(hours)
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        TextMMD(
            text = when (change) {
                // The station knows the present better than a model does: a drizzle the
                // forecast missed should not sit under a line saying none is likely.
                Change.Dry -> if (raining) {
                    stringResource(R.string.sky_hours_raining_none_forecast)
                } else {
                    stringResource(R.string.sky_hours_dry, count)
                }
                is Change.From -> if (raining) {
                    stringResource(R.string.sky_hours_raining_forecast_from, clock(change.at))
                } else {
                    stringResource(
                        if (change.snow) R.string.sky_hours_snow_from else R.string.sky_hours_rain_from,
                        clock(change.at),
                    )
                }
                is Change.Until -> stringResource(
                    if (change.snow) R.string.sky_hours_snow_until else R.string.sky_hours_rain_until,
                    clock(change.at),
                )
                is Change.Throughout -> stringResource(
                    if (change.snow) R.string.sky_hours_snow_throughout else R.string.sky_hours_rain_throughout,
                    count,
                )
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        // Under "no rain likely", a trace spread over unlikely hours would contradict the
        // line above it rather than add to it.
        amount(Change.total(hours), inches)?.takeIf { change != Change.Dry }?.let {
            TextMMD(
                text = stringResource(R.string.sky_hours_amount, it, unit(inches), count),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Spacer(Modifier.height(10.dp))
        Strip(hours, first = now)
    }
}

/**
 * The strip itself: an hour to a column, the chance of rain as a bar standing on a rule, a
 * dotted line where it becomes likely, and the temperature and hour every third column. The
 * first column's label is [first] where given ("Now"), else its hour.
 */
@Composable
private fun Strip(hours: List<Hour>, first: String? = null) {
    val ink = MaterialTheme.colorScheme.onSurface
    val twentyFour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val described = stringResource(R.string.sky_cd_hours)
    Row(modifier = Modifier.fillMaxWidth().semantics { contentDescription = described }) {
        hours.forEachIndexed { i, hour ->
            val written = i % 3 == 0
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Written past the column's edges if it needs to: its neighbours are empty.
                TextMMD(
                    text = if (written) "${hour.temperature}°" else "",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                )
                Spacer(Modifier.height(4.dp))
                // The bar stands on a rule drawn across the whole strip, so an hour with
                // no chance at all still shows as an hour.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BAR_HEIGHT)
                        .drawBehind {
                            drawLine(ink, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                            val likely = size.height * (1f - Change.LIKELY / 100f)
                            drawLine(
                                ink,
                                Offset(0f, likely),
                                Offset(size.width, likely),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                            )
                        },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    val chance = (hour.rainChance ?: 0).coerceIn(0, 100)
                    if (chance > 0) {
                        Box(
                            Modifier
                                .width(10.dp)
                                .height(BAR_HEIGHT * chance / 100)
                                .background(ink),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                TextMMD(
                    text = when {
                        i == 0 && first != null -> first
                        written -> hourLabel(hour.time, twentyFour)
                        else -> ""
                    },
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.wrapContentWidth(unbounded = true),
                )
            }
        }
    }
}

private val BAR_HEIGHT = 40.dp

private val hour24: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val hour12: DateTimeFormatter = DateTimeFormatter.ofPattern("h a")

/** Whether the station says it is raining now, by its sensor or by a rate above nothing. */
private fun rainingNow(state: SkyState): Boolean {
    val reading = state.reading ?: return false
    // A reading from a station that has stopped answering is not "now".
    if (state.stationTrouble == StationTrouble.NO_ANSWER) return false
    val rate = reading.rainRate
    return reading.raining == true || (rate != null && rate.number > 0.0)
}

/** The hour, as the phone's clock would write it: "15:00" or "3 PM". */
private fun hourLabel(time: java.time.LocalDateTime, twentyFour: Boolean): String =
    (if (twentyFour) hour24 else hour12).withLocale(Locale.getDefault()).format(time)

/** An amount of rain as it is written, or null where it would round to nothing. */
private fun amount(value: Double?, inches: Boolean): String? {
    value ?: return null
    return when {
        inches && value >= 0.005 -> String.format(Locale.getDefault(), "%.2f", value)
        !inches && value >= 10 -> String.format(Locale.getDefault(), "%.0f", value)
        !inches && value >= 0.05 -> String.format(Locale.getDefault(), "%.1f", value)
        else -> null
    }
}

private fun unit(inches: Boolean): String = if (inches) "in" else "mm"

/** A depth of snow as it is written, or null where it would round to nothing. */
private fun snowDepth(value: Double?, inches: Boolean): String? {
    value ?: return null
    return when {
        inches && value >= 0.05 -> String.format(Locale.getDefault(), "%.1f", value)
        !inches && value >= 0.5 -> String.format(Locale.getDefault(), "%.0f", value)
        else -> null
    }
}

/** Open-Meteo gives snow in centimetres beside millimetres of rain, and inches beside inches. */
private fun snowUnit(inches: Boolean): String = if (inches) "in" else "cm"

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

/**
 * A time as the phone's own clock writes it. The locale alone would say 9:47 PM on a phone
 * set to the 24-hour clock, beside a status bar saying 21:47.
 */
@Composable
private fun clockFormat(): DateTimeFormatter {
    val locale = Locale.getDefault()
    val skeleton = if (android.text.format.DateFormat.is24HourFormat(LocalContext.current)) "Hm" else "hm"
    return DateTimeFormatter.ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale)
}

@Composable
private fun clock(millis: Long): String =
    clockFormat().format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
private fun clock(time: java.time.LocalDateTime): String = clockFormat().format(time)

/** WMO weather interpretation codes, as Open-Meteo uses them, in a few plain words. */
internal fun conditionFor(code: Int): Int = when (code) {
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
