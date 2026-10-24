package com.wanderwildwood.soramoyo.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanderwildwood.soramoyo.alerts.Alert
import com.wanderwildwood.soramoyo.alerts.AlertAnswer
import com.wanderwildwood.soramoyo.alerts.AlertNotices
import com.wanderwildwood.soramoyo.alerts.Alerts
import com.wanderwildwood.soramoyo.forecast.Air
import com.wanderwildwood.soramoyo.forecast.AirQuality
import com.wanderwildwood.soramoyo.forecast.Current
import com.wanderwildwood.soramoyo.forecast.Day
import com.wanderwildwood.soramoyo.forecast.Forecast
import com.wanderwildwood.soramoyo.forecast.Hour
import com.wanderwildwood.soramoyo.forecast.Quarter
import com.wanderwildwood.soramoyo.location.LatLon
import com.wanderwildwood.soramoyo.location.LocationProvider
import com.wanderwildwood.soramoyo.location.Place
import com.wanderwildwood.soramoyo.location.Places
import com.wanderwildwood.soramoyo.station.Source
import com.wanderwildwood.soramoyo.station.Stations
import com.wanderwildwood.soramoyo.station.StationReading
import com.wanderwildwood.soramoyo.station.Units
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** Why the station has nothing new to show. */
enum class StationTrouble { NONE, NOT_SET, NO_ANSWER }

/** Why there is no forecast. */
enum class ForecastTrouble { NONE, NO_PERMISSION, NO_LOCATION, NO_ANSWER }

/** What a pull down from the top of a list is doing, for the one line of words that says so. */
enum class Pull { IDLE, UPDATING, UP_TO_DATE, NO_ANSWER }

data class SkyState(
    val source: Source = Source.None,
    val reading: StationReading? = null,
    /** Wall-clock time of the last reading that arrived, in milliseconds. */
    val readAt: Long = 0L,
    val stationTrouble: StationTrouble = StationTrouble.NONE,
    val asking: Boolean = false,
    /** The forecast's estimate of now, shown when there is no station to measure it. */
    val current: Current? = null,
    val days: List<Day> = emptyList(),
    /** The forecast's hours, from yesterday's first; [upcoming] picks out those still to come. */
    val hours: List<Hour> = emptyList(),
    /** The next hours in quarter-hours; empty where the model gave none. */
    val quarters: List<Quarter> = emptyList(),
    /** Whether the forecast's rain amounts are in inches rather than millimetres. */
    val inches: Boolean = false,
    /** The forecast place's offset from UTC, which the hours' times are in. */
    val offset: ZoneOffset = ZoneOffset.UTC,
    /** The forecast place's time zone, where the forecast named it. */
    val zone: ZoneId? = null,
    /**
     * Where the forecast is for, or was asked for: the almanac works from it, with or without
     * a forecast to go with it.
     */
    val at: LatLon? = null,
    /** Whether the forecast is in metric units, which also picks the air-quality scale. */
    val metric: Boolean = true,
    /** The air now; null until it arrives, and left null if it never does. */
    val air: Air? = null,
    /** The Weather Service's alerts in effect where the forecast is for; empty outside the US. */
    val alerts: List<Alert> = emptyList(),
    val units: Units.Choice = Units.Choice.AUTOMATIC,
    val forecastTrouble: ForecastTrouble = ForecastTrouble.NONE,
    /** Where the forecast and radar are for, when a place has been chosen rather than the phone's position. */
    val place: Place? = null,
    /** A second place to switch to, when one has been set. */
    val second: Place? = null,
    /** Whether the second place is the one shown; meaningless while there is none. */
    val onSecond: Boolean = false,
    /** A pull down at the top of a list: asking, and then what came of it for a few seconds. */
    val pull: Pull = Pull.IDLE,
) {
    /** Whether what is on the screen is for the second place. */
    val showingSecond: Boolean get() = onSecond && second != null

    /** The place on the screen, or null for the phone's own position. */
    val shown: Place? get() = Places.showing(place, second, onSecond)

    /**
     * This state as the screen shows it. The station is at the first place, so while the
     * second is shown its reading is left out: a temperature from home under a forecast for
     * work would be read as work's.
     */
    fun forScreen(): SkyState = if (!showingSecond) this else copy(
        source = Source.None,
        reading = null,
        readAt = 0L,
        stationTrouble = StationTrouble.NOT_SET,
        asking = false,
    )

    /**
     * Today where the forecast is for, which is not always today on the phone: a place three
     * zones west is still on yesterday until three in the morning here, and matching its days
     * against the phone's date left Today waiting for a day that never came.
     */
    fun placeToday(): LocalDate = LocalDate.now(offset)

    /**
     * The zone the almanac's times are in: the forecast's, else the offset it gave, else the
     * phone's own, which is right for the phone's own position and the best guess for a place
     * chosen while there was no forecast to say.
     */
    fun almanacZone(): ZoneId = zone ?: offset.takeIf { days.isNotEmpty() } ?: ZoneId.systemDefault()
}

/**
 * The station and the forecast: what the first screen shows.
 *
 * Both are asked again every time the app comes to the front, the station always and the
 * forecast at most every half hour. The station is a few hundred bytes on the local
 * network and is the reason anyone opens this, so it is never held back; the forecast
 * changes a few times a day and costs a trip to a server someone else runs for free.
 */
class SkyViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(
        SkyState(
            source = Source.load(app),
            place = Places.chosen(app),
            second = Places.second(app),
            onSecond = Places.onSecond(app),
            units = Units.choice(app),
        ),
    )
    val state = _state.asStateFlow()

    private var stationJob: Job? = null
    private var forecastJob: Job? = null
    private var forecastAtMs = 0L
    private var forecastFor: Pair<LatLon, Boolean>? = null
    private var asking: Boolean? = null

    /**
     * The other place's forecast, kept when the switch is pressed so that pressing it back
     * shows it at once rather than fetching it again. It is refreshed on the same half-hour
     * as the one on the screen.
     */
    private var other: Held? = null

    private data class Held(
        val current: Current?,
        val days: List<Day>,
        val hours: List<Hour>,
        val quarters: List<Quarter>,
        val inches: Boolean,
        val offset: ZoneOffset,
        val zone: ZoneId?,
        val at: LatLon?,
        val metric: Boolean,
        val air: Air?,
        val alerts: List<Alert>,
        val atMs: Long,
        val forWhat: Pair<LatLon, Boolean>?,
    )

    fun onForeground() {
        readStation()
        readForecast(force = false)
    }

    fun setSource(source: Source) {
        Source.save(getApplication(), source)
        // A different station's last reading is not this one's.
        _state.update {
            it.copy(source = source, reading = null, readAt = 0L, stationTrouble = StationTrouble.NONE, asking = false)
        }
        readStation()
    }

    /** A place for the forecast and radar, or null to go back to the phone's position. */
    fun setPlace(place: Place?) {
        Places.choose(getApplication(), place)
        if (_state.value.showingSecond) {
            // Not the one on the screen: only what was kept for it is out of date.
            other = null
            _state.update { it.copy(place = place) }
        } else {
            _state.update { it.copy(place = place) }
            forget()
        }
    }

    /** A second place to switch to, or null for none. Taking it away goes back to the first. */
    fun setSecond(place: Place?) {
        Places.chooseSecond(getApplication(), place)
        val wasShowing = _state.value.showingSecond
        _state.update { it.copy(second = place, onSecond = place != null && it.onSecond) }
        when {
            place == null && wasShowing -> swap()
            place == null -> other = null
            wasShowing -> forget()
            else -> other = null
        }
    }

    /** Over to the other place: the switch at the top of the screen. */
    fun switchPlace() {
        val s = _state.value
        if (s.second == null) return
        val toSecond = !s.showingSecond
        Places.showSecond(getApplication(), toSecond)
        _state.update { it.copy(onSecond = toSecond) }
        swap()
    }

    /** Keep what is on the screen as the other place's, and show what was kept, if anything. */
    private fun swap() {
        forecastJob?.cancel()
        asking = null
        val s = _state.value
        val leaving = if (s.days.isEmpty()) null else Held(
            s.current, s.days, s.hours, s.quarters, s.inches, s.offset, s.zone, s.at, s.metric, s.air, s.alerts, forecastAtMs, forecastFor,
        )
        val coming = other
        other = leaving
        if (coming == null) {
            forget()
            return
        }
        forecastAtMs = coming.atMs
        forecastFor = coming.forWhat
        _state.update {
            it.copy(
                current = coming.current,
                days = coming.days,
                hours = coming.hours,
                quarters = coming.quarters,
                inches = coming.inches,
                offset = coming.offset,
                zone = coming.zone,
                at = coming.at,
                metric = coming.metric,
                air = coming.air,
                alerts = coming.alerts,
                forecastTrouble = ForecastTrouble.NONE,
            )
        }
        readForecast(force = false)
    }

    /** The forecast on the screen is for somewhere else now: clear it and ask for the new one. */
    private fun forget() {
        forecastAtMs = 0L
        forecastFor = null
        _state.update {
            it.copy(
                current = null,
                days = emptyList(),
                hours = emptyList(),
                quarters = emptyList(),
                zone = null,
                at = null,
                air = null,
                alerts = emptyList(),
                forecastTrouble = ForecastTrouble.NONE,
            )
        }
        readForecast(force = true)
    }

    /** Metric, imperial, or back to following the station and the country. */
    fun setUnits(choice: Units.Choice) {
        Units.choose(getApplication(), choice)
        // The station's last reading is in the old units; it is read again rather than shown so.
        _state.update { it.copy(units = choice, reading = null) }
        other = null
        readStation()
        readForecast(force = true)
    }

    fun onPermissionResult() = readForecast(force = true)

    private var pullJob: Job? = null

    /**
     * A pull down from the top of a list: the station and the forecast asked for again, as if the
     * app had just come to the front but without the half hour's wait. The state says "updating"
     * while they are on their way, then how it went for a few seconds, and then nothing. A
     * forecast that cannot be had for want of a place or a permission already says so on the
     * screen, so it is not said twice.
     */
    fun refresh() {
        if (_state.value.pull == Pull.UPDATING) return
        pullJob?.cancel()
        pullJob = viewModelScope.launch {
            val before = forecastAtMs
            _state.update { it.copy(pull = Pull.UPDATING) }
            readStation()
            readForecast(force = true)
            stationJob?.join()
            // The station's answer may start the forecast over (new units); wait for the last.
            forecastJob?.join()
            val outcome = when {
                forecastAtMs != before -> Pull.UP_TO_DATE
                _state.value.forecastTrouble == ForecastTrouble.NO_ANSWER -> Pull.NO_ANSWER
                else -> Pull.IDLE
            }
            _state.update { it.copy(pull = outcome) }
            if (outcome != Pull.IDLE) kotlinx.coroutines.delay(SAID_FOR_MS)
            _state.update { it.copy(pull = Pull.IDLE) }
        }
    }

    private fun readStation() {
        val source = _state.value.source
        if (source == Source.None) {
            _state.update { it.copy(stationTrouble = StationTrouble.NOT_SET, reading = null) }
            // The forecast's units follow the station, and with no station there is nothing
            // to follow; it falls back to the phone's country, which readForecast handles.
            return
        }
        stationJob?.cancel()
        stationJob = viewModelScope.launch {
            _state.update { it.copy(asking = true) }
            try {
                val reading = Stations.read(getApplication(), source)
                _state.update {
                    it.copy(
                        reading = reading,
                        readAt = System.currentTimeMillis(),
                        stationTrouble = StationTrouble.NONE,
                        asking = false,
                    )
                }
                // The first reading is what says whether the station counts in Celsius. If
                // the forecast was fetched in the other units before it arrived, fetch again.
                readForecast(force = false)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // The last reading stays on the screen with the time it was taken; the
                // screen says the station is not answering rather than blanking it.
                _state.update { it.copy(stationTrouble = StationTrouble.NO_ANSWER, asking = false) }
            }
        }
    }

    private fun readForecast(force: Boolean) {
        val context = getApplication<Application>()
        if (Places.needsPosition(context) && !LocationProvider.hasPermission(context)) {
            _state.update { it.copy(forecastTrouble = ForecastTrouble.NO_PERMISSION) }
            return
        }
        val metric = Units.chosen(context) ?: _state.value.reading?.metric ?: localeIsMetric()
        val now = SystemClock.elapsedRealtime()
        val fresh = forecastAtMs != 0L && now - forecastAtMs < FORECAST_EVERY_MS
        val sameUnits = forecastFor?.second == metric
        if (!force && fresh && sameUnits) return
        // Already on its way in these units: finding where the phone is can take half a
        // minute, and starting again would only start the wait again.
        if (!force && forecastJob?.isActive == true && asking == metric) return

        asking = metric
        forecastJob?.cancel()
        forecastJob = viewModelScope.launch {
            val here = Places.where(context)
            if (here == null) {
                if (_state.value.days.isEmpty()) {
                    _state.update { it.copy(forecastTrouble = ForecastTrouble.NO_LOCATION) }
                }
                return@launch
            }
            // The almanac needs only this, and works it out on the phone: it is there even when
            // the forecast that follows never arrives.
            _state.update { it.copy(at = here) }
            // The air is asked beside the forecast, for the same place. It is an extra row, not
            // the point: if it fails there is simply no row, and nothing on the screen says so.
            launch {
                try {
                    val air = AirQuality.fetch(here.lat, here.lon)
                    _state.update { it.copy(air = air) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
            // The alerts the same way, and for the same place. Outside the US there are none to
            // show; a failed fetch keeps whatever was shown before rather than blanking it.
            val first = !_state.value.showingSecond
            launch {
                try {
                    val alerts = when (val answer = Alerts.fetch(here.lat, here.lon)) {
                        is AlertAnswer.Found -> answer.alerts
                        AlertAnswer.NotCovered -> emptyList()
                    }
                    _state.update { it.copy(alerts = alerts) }
                    // Seen on the screen now, so the background check need not notify of them.
                    if (first) AlertNotices.note(context, alerts, notify = false)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                }
            }
            try {
                val predicted = Forecast.fetch(here.lat, here.lon, metric)
                forecastAtMs = SystemClock.elapsedRealtime()
                forecastFor = here to metric
                _state.update {
                    it.copy(
                        current = predicted.current,
                        days = predicted.days,
                        hours = predicted.hours,
                        quarters = predicted.quarters,
                        inches = predicted.inches,
                        offset = predicted.offset,
                        zone = predicted.zone,
                        metric = metric,
                        forecastTrouble = ForecastTrouble.NONE,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(forecastTrouble = ForecastTrouble.NO_ANSWER) }
            }
        }
    }

    companion object {
        private const val FORECAST_EVERY_MS = 30 * 60 * 1000L

        /** How long "Up to date" or the failure stays at the top of a list. */
        private const val SAID_FOR_MS = 3_000L

        /** The three countries that still count in Fahrenheit. */
        private fun localeIsMetric(): Boolean =
            Locale.getDefault().country !in setOf("US", "LR", "MM")
    }
}
