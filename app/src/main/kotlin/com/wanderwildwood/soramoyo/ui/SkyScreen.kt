package com.wanderwildwood.soramoyo.ui

import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.divider.HorizontalDividerMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.soramoyo.R
import com.wanderwildwood.soramoyo.almanac.Almanac
import com.wanderwildwood.soramoyo.forecast.DaySummary
import com.wanderwildwood.soramoyo.forecast.RainSoon
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
import com.wanderwildwood.soramoyo.location.Place
import com.wanderwildwood.soramoyo.location.Places
import com.wanderwildwood.soramoyo.station.Measure
import com.wanderwildwood.soramoyo.station.Source
import com.wanderwildwood.soramoyo.station.StationReading
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** The four tabs, in the order they sit. */
enum class Tab { TODAY, FORECAST, RADAR, ALMANAC }

/**
 * Sky's one screen: a top bar, four tabs, and whichever tab is open beneath them.
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
    onSwitchPlace: () -> Unit,
    onSettings: () -> Unit,
    onAllowLocation: () -> Unit,
    onDay: (LocalDate) -> Unit,
    onAlerts: () -> Unit,
    onPull: () -> Unit,
) {
    val shown = state.forScreen()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column {
                // With a second place the two names take the title's place rather than a line
                // of their own under it: that line pushed the rows of Today down a whole row,
                // and the wind off the first screen.
                TopAppBarMMD(
                    title = {
                        val second = state.second
                        if (second == null) {
                            TextMMD(text = stringResource(R.string.app_name))
                        } else {
                            PlaceSwitch(state.place, second, state.showingSecond, onSwitchPlace)
                        }
                    },
                    actions = { BarButton(Icons.Settings, stringResource(R.string.sky_cd_settings), onSettings) },
                )
                StoneTabs(
                    labels = Tab.entries.map { t ->
                        stringResource(
                            when (t) {
                                Tab.TODAY -> R.string.tab_today
                                Tab.FORECAST -> R.string.tab_forecast
                                Tab.RADAR -> R.string.tab_radar
                                Tab.ALMANAC -> R.string.tab_almanac
                            },
                        )
                    },
                    selected = tab.ordinal,
                    onSelect = { onTab(Tab.entries[it]) },
                )
            }
        },
    ) { contentPadding ->
        val inside = Modifier.fillMaxSize().padding(contentPadding)
        when (tab) {
            Tab.TODAY -> TodayTab(shown, onAllowLocation, onAlerts, onPull, inside)
            Tab.FORECAST -> ForecastTab(shown, onAllowLocation, onDay, onPull, inside)
            Tab.RADAR -> RadarTab(radar, onAllowLocation, state.metric, inside)
            Tab.ALMANAC -> AlmanacTab(shown, onAllowLocation, inside)
        }
    }
}

/**
 * The two places, in the top bar in place of the title once a second one is set: the one shown
 * in bold, the other plain, and a press anywhere on them goes over to the other. It is not a fourth tab because it is
 * not a fourth thing to look at — it is the same three for somewhere else.
 */
@Composable
private fun PlaceSwitch(first: Place?, second: Place, onSecond: Boolean, onSwitch: () -> Unit) {
    val firstName = first?.let { Places.short(it.label) } ?: stringResource(R.string.sky_place_here)
    val secondName = Places.short(second.label)
    val switchTo = stringResource(R.string.sky_cd_switch_place, if (onSecond) firstName else secondName)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onSwitch)
            .semantics { contentDescription = switchTo }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextMMD(
            text = firstName,
            fontWeight = if (onSecond) FontWeight.Normal else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        TextMMD(
            text = stringResource(R.string.sky_place_between),
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        TextMMD(
            text = secondName,
            fontWeight = if (onSecond) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/**
 * What the station reads now, and what today is forecast to do.
 *
 * The temperature is the large figure because it is the one read at a glance; the rest are
 * rows because they are read deliberately or not at all.
 */
@Composable
private fun TodayTab(state: SkyState, onAllowLocation: () -> Unit, onAlerts: () -> Unit, onPull: () -> Unit, modifier: Modifier) {
    // The rest of today, not the whole of it: its morning's rain is over by the afternoon.
    val today = state.days.firstOrNull { it.date == state.placeToday() }?.restOf(state.hours, state.offset)
    val yesterday = state.days.firstOrNull { it.date == state.placeToday().minusDays(1) }
    // Most people who install this have no station, and for them today's forecast is the
    // headline rather than a line under a reading they will never have. The station lives
    // in settings; nothing here asks for one.
    val noStation = state.stationTrouble == StationTrouble.NOT_SET
    // A station that is set but cannot be reached, with nothing recent from it to show.
    val fallback = !noStation && stationGone(state)
    // No rail here: it took a strip off the right of every row, and Today is read top down
    // with a swipe, a page of whole rows at a time.
    // The day in a few words, and the next two hours' rain; each is left out when the forecast
    // did not give what it needs.
    val summary = DaySummary.of(
        state.days.firstOrNull { it.date == state.placeToday() }, state.hours, state.metric, state.offset,
    )
    val soon = RainSoon.of(state.quarters, state.hours, state.inches, state.offset)
    PagedColumn(modifier = modifier.padding(horizontal = 20.dp), onPullAtTop = onPull) {
        pullLine(state.pull)
        // An official warning comes before anything else, and only while there is one.
        if (state.alerts.isNotEmpty()) {
            item {
                Column {
                    AlertLine(state.alerts, onAlerts)
                    HorizontalDividerMMD()
                }
            }
        }
        if (summary != null || soon != null) {
            item {
                Column {
                    DayWords(summary, soon)
                    HorizontalDividerMMD()
                }
            }
        }
        // The next twelve hours, and the twelve after them a swipe away: going out in the
        // morning, the evening you come back in is past the first twelve.
        val next = upcoming(state.hours, state.offset, count = HOURS * 2)
        val hours = next.take(HOURS)
        val after = next.drop(HOURS)
        val current = state.current
        if (noStation) {
            if (current != null) {
                item { Estimate(current, state.metric) }
                if (today != null) {
                    item {
                        HorizontalDividerMMD()
                        DayRow(today, state.inches, today.date, yesterday = yesterday, sayCondition = summary?.sky == null && summary?.wet == null)
                    }
                }
            } else {
                item { Outlook(today, yesterday, state, onAllowLocation) }
            }
            if (hours.isNotEmpty()) {
                item {
                    HorizontalDividerMMD()
                    Hours(hours, after, state.inches, rainingNow(state))
                }
            }
        } else {
            if (fallback && current != null) {
                // The station cannot be reached -- away from home, or the home network down --
                // so the forecast's estimate for the chosen place stands in, saying so, rather
                // than a message and nothing.
                item {
                    Column {
                        Estimate(current, state.metric)
                        TextMMD(
                            text = stringResource(R.string.sky_station_fallback),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                }
            } else {
                item { Now(state) }
            }
            item {
                HorizontalDividerMMD()
                if (today != null) {
                    DayRow(today, state.inches, today.date, yesterday = yesterday, sayCondition = summary?.sky == null && summary?.wet == null)
                } else {
                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                        ForecastTrouble(state.forecastTrouble, true, onAllowLocation)
                    }
                }
            }
            if (hours.isNotEmpty()) {
                item {
                    HorizontalDividerMMD()
                    Hours(hours, after, state.inches, rainingNow(state))
                }
            }
        }

        // Measured if there is a station, else the forecast's estimate of the same things.
        val rows = if (fallback && current != null) {
            asReading(current, state.metric)
        } else {
            state.reading ?: current?.takeIf { noStation }?.let { asReading(it, state.metric) }
        }
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

        val (sunrise, sunset) = today?.let { sunTimes(it, state) } ?: (null to null)
        if (sunrise != null && sunset != null) {
            item {
                HorizontalDividerMMD()
                Reading(stringResource(R.string.sky_sunrise), clock(sunrise), "", small = true)
            }
            item {
                HorizontalDividerMMD()
                Reading(stringResource(R.string.sky_sunset), clock(sunset), "", small = true)
            }
        }
        if (today != null) {
            item { Credit(state) }
        }
    }
}

/** The days after today, as many as the forecast gives, which is five. Each opens its own page. */
@Composable
private fun ForecastTab(state: SkyState, onAllowLocation: () -> Unit, onDay: (LocalDate) -> Unit, onPull: () -> Unit, modifier: Modifier) {
    val placeToday = state.placeToday()
    val ahead = state.days.filter { it.date.isAfter(placeToday) }
    PagedColumn(modifier = modifier.padding(horizontal = 20.dp), onPullAtTop = onPull) {
        pullLine(state.pull)
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
        state.shown?.let {
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

/**
 * Whether the station is set but gone: not answering, and with no reading, or only one older
 * than [STALE_MS]. A reading from a few minutes ago is still worth more than an estimate; one
 * from hours ago, labelled "last read", is not.
 */
private fun stationGone(state: SkyState): Boolean =
    state.stationTrouble == StationTrouble.NO_ANSWER &&
        (state.reading == null || System.currentTimeMillis() - state.readAt > STALE_MS)

private const val STALE_MS = 60 * 60 * 1000L

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
private fun Outlook(today: Day?, yesterday: Day?, state: SkyState, onAllowLocation: () -> Unit) {
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
        yesterday?.let {
            TextMMD(text = againstYesterday(today, it, metric = !state.inches), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Today's high set against yesterday's: "6° warmer than yesterday". A degree or two either way
 * (2 °C, or 3 °F) is within what a forecast can tell apart, and is said as about the same.
 */
@Composable
private fun againstYesterday(today: Day, yesterday: Day, metric: Boolean): String {
    val by = today.high - yesterday.high
    val same = if (metric) 2 else 3
    return when {
        by >= same -> stringResource(R.string.sky_warmer_than_yesterday, by)
        by <= -same -> stringResource(R.string.sky_cooler_than_yesterday, -by)
        else -> stringResource(R.string.sky_as_yesterday)
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

        // What it feels like and how old the reading is sit beside the figure, not under it, so
        // the block is no taller than the figure and leaves no blank to its right.
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
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
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(bottom = 14.dp)) {
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
    }
}

/**
 * The rows under the temperature, each only if the station reported it. A station without a
 * soil probe has no soil row, rather than a dash beside one.
 */
private fun readingRows(reading: StationReading): List<@Composable () -> Unit> {
    val rows = ArrayList<@Composable () -> Unit>()

    // The gusts go in the wind's own row: as two rows, a scroll could leave the wind under
    // the top bar and only the gusts on the screen.
    val gust = reading.windGust
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
                note = gust?.let { stringResource(R.string.sky_gusts_to, it.number.roundToInt(), it.unit) },
            )
        }
    } ?: gust?.let {
        rows += { Reading(stringResource(R.string.sky_gusts), it.number.roundToInt().toString(), it.unit) }
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
internal fun ForecastTrouble(trouble: ForecastTrouble, nothingYet: Boolean, onAllow: () -> Unit) {
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
private fun DayRow(
    day: Day,
    inches: Boolean,
    today: LocalDate,
    modifier: Modifier = Modifier,
    yesterday: Day? = null,
    sayCondition: Boolean = true,
) {
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
            // On Today the sentence above already says what the sky does, and the row's word for
            // it (the worst of the hours left) could disagree with it, so only the chance is given.
            val line = if (!sayCondition) {
                when {
                    day.snowy && chance != null && amount != null ->
                        stringResource(R.string.sky_chance_snow_amount, chance, amount, amountUnit)
                    day.snowy && chance != null -> stringResource(R.string.sky_chance_snow, chance)
                    chance != null && amount != null -> stringResource(R.string.sky_chance_rain_amount, chance, amount, amountUnit)
                    chance != null -> stringResource(R.string.sky_chance_rain, chance)
                    else -> null
                }
            } else {
                when {
                    day.snowy && chance != null && amount != null ->
                        stringResource(R.string.sky_condition_with_snow_amount, condition, chance, amount, amountUnit)
                    day.snowy && chance != null -> stringResource(R.string.sky_condition_with_snow, condition, chance)
                    chance != null && amount != null ->
                        stringResource(R.string.sky_condition_with_rain_amount, condition, chance, amount, amountUnit)
                    chance != null -> stringResource(R.string.sky_condition_with_rain, condition, chance)
                    else -> condition
                }
            }
            line?.let { TextMMD(text = it, style = MaterialTheme.typography.labelSmall) }
            // Today's row only: how it compares is what yesterday is for.
            yesterday?.let {
                TextMMD(text = againstYesterday(day, it, metric = !inches), style = MaterialTheme.typography.labelSmall)
            }
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

/**
 * A day's sunrise and sunset as the Almanac works them out, so Today, the day's page and the
 * Almanac give the same minute: the forecast's own were rounded differently and could be one
 * off. The forecast's stand in where there is no place to work from, or the sun does not rise.
 */
private fun sunTimes(day: Day, state: SkyState): Pair<LocalDateTime?, LocalDateTime?> {
    val at = state.at ?: return day.sunrise to day.sunset
    val sun = Almanac.sun(day.date, state.almanacZone(), at.lat, at.lon)
    return (sun.rise ?: day.sunrise) to (sun.set ?: day.sunset)
}

@Composable
internal fun dayName(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.sky_today)
    today.plusDays(1) -> stringResource(R.string.sky_tomorrow)
    else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
}

/**
 * One day of the forecast on a page of its own, opened from the Forecast tab: the high as the
 * large figure, what the day feels like, its twenty-four hours as two strips of twelve, and
 * rows for the rain, snow, wind, UV, the sun and the moon. Everything the list has room only to
 * hint at.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(date: LocalDate, state: SkyState, onClose: () -> Unit, onPull: () -> Unit) {
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
        PagedColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 20.dp),
            onPullAtTop = onPull,
        ) {
            pullLine(state.pull)
            // A forecast refreshed past midnight no longer has yesterday's tomorrow in it.
            if (day == null) {
                item {
                    Spacer(Modifier.height(12.dp))
                    TextMMD(text = stringResource(R.string.sky_day_gone), style = MaterialTheme.typography.bodySmall)
                }
                return@PagedColumn
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
            val (sunrise, sunset) = sunTimes(day, state)
            if (sunrise != null && sunset != null) {
                item {
                    HorizontalDividerMMD()
                    Reading(stringResource(R.string.sky_sunrise), clock(sunrise), "", small = true)
                }
                item {
                    HorizontalDividerMMD()
                    Reading(stringResource(R.string.sky_sunset), clock(sunset), "", small = true)
                }
            }
            almanacDayRows(day, state.at, state.almanacZone()).forEach { row ->
                item {
                    HorizontalDividerMMD()
                    row()
                }
            }
            item { Credit(state, air = false) }
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
            text = spellLine(hours),
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

/**
 * What a run of hours read to plan by says about rain: when a wet spell starts, and where it
 * ends inside them, or that there is none. A day's page says so of the whole day, Today's
 * later hours of just those.
 */
@Composable
private fun spellLine(hours: List<Hour>, wholeDay: Boolean = true): String = when (val change = Change.of(hours)) {
    Change.Dry -> stringResource(R.string.sky_day_dry)
    // Hours read to plan by, a day or Today's later twelve, say where the wet spell ends
    // too; Today's next twelve hours tell only the first turn.
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
        when {
            wholeDay -> if (change.snow) R.string.sky_day_snow_all else R.string.sky_day_rain_all
            else -> if (change.snow) R.string.sky_later_snow_all else R.string.sky_later_rain_all
        },
    )
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
    // One row for the wind and its gusts, as on Today: the sun and moon rows under them made
    // the page long enough for a scroll to stop with the wind under the top bar and only the
    // gusts showing.
    val gust = day.gustMax
    day.windMax?.let { wind ->
        rows += {
            val compass = stringArrayResource(R.array.compass_16)
            val strength = wind.roundToInt()
            val from = day.windFrom?.takeIf { strength > 0 }?.let { compass[((it / 22.5).roundToInt()) % 16] }
            Reading(
                label = stringResource(R.string.sky_wind),
                value = listOfNotNull(from, strength.toString()).joinToString(" "),
                unit = speed,
                note = if (gust != null) {
                    stringResource(R.string.sky_wind_strongest_gusts, gust.roundToInt(), speed)
                } else {
                    stringResource(R.string.sky_wind_strongest)
                },
            )
        }
    } ?: gust?.let {
        rows += { Reading(stringResource(R.string.sky_gusts), it.roundToInt().toString(), speed) }
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
private fun Hours(hours: List<Hour>, after: List<Hour>, inches: Boolean, raining: Boolean) {
    if (after.isEmpty()) {
        NextHours(hours, inches, raining)
        return
    }
    // The twelve hours after these, a swipe across the strip away. The word under it jumps
    // there too, without a slide, for anyone who does not think to swipe: on this panel a
    // half-drawn page is only smear.
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxWidth()) {
        // As tall as the taller of the two, whichever is showing: the later hours take a line
        // more, and the block growing under Later moved every row above it on the page.
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().alpha(0f).clearAndSetSemantics {}) {
                NextHours(hours, inches, raining)
                LaterHours(after)
            }
            HorizontalPager(state = pager, verticalAlignment = Alignment.Top, modifier = Modifier.matchParentSize()) { page ->
                if (page == 0) NextHours(hours, inches, raining) else LaterHours(after)
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { scope.launch { pager.scrollToPage(1 - pager.currentPage) } },
            // In the same place either way, so the finger that pressed it can press it back.
            contentAlignment = Alignment.CenterEnd,
        ) {
            TextMMD(
                text = stringResource(if (pager.currentPage == 0) R.string.sky_hours_later else R.string.sky_hours_sooner),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.End,
            )
        }
    }
}

/** The next twelve hours, as Today first shows them. */
@Composable
private fun NextHours(hours: List<Hour>, inches: Boolean, raining: Boolean) {
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
        // Always a second line, as the later hours have, so Later and Now swap only the words
        // and figures and the strip stays where it is: the amount where there is one, else the
        // hours these are.
        TextMMD(
            text = amount(Change.total(hours), inches)?.takeIf { change != Change.Dry }
                ?.let { stringResource(R.string.sky_hours_amount, it, unit(inches), count) }
                ?: stringResource(R.string.sky_hours_span, clock(hours.first().time), clock(hours.last().time.plusHours(1))),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Spacer(Modifier.height(10.dp))
        Strip(hours, first = now)
    }
}

/**
 * The twelve hours after the next twelve: the line as a day's page words it, with where
 * these hours start and end under it, since "no rain likely" alone would not say when.
 */
@Composable
private fun LaterHours(hours: List<Hour>) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        TextMMD(text = spellLine(hours, wholeDay = false), style = MaterialTheme.typography.bodyMedium)
        TextMMD(
            text = stringResource(R.string.sky_hours_span, clock(hours.first().time), clock(hours.last().time.plusHours(1))),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        Spacer(Modifier.height(10.dp))
        Strip(hours)
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
            // Written past the column's edges if it needs to: its neighbours are empty. The
            // first column's run right from its left edge instead, since left of it is the
            // edge of the screen, where "8 AM" lost its 8.
            val label = if (i == 0) {
                Modifier.fillMaxWidth().wrapContentWidth(Alignment.Start, unbounded = true)
            } else {
                Modifier.wrapContentWidth(unbounded = true)
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TextMMD(
                    text = if (written) "${hour.temperature}°" else "",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = label,
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
                    modifier = label,
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
internal fun Reading(label: String, value: String, unit: String, note: String? = null, small: Boolean = false) {
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
internal fun clock(time: java.time.LocalDateTime): String = clockFormat().format(time)

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
