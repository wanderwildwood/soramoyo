package com.wanderwildwood.soramoyo.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanderwildwood.soramoyo.forecast.Air
import com.wanderwildwood.soramoyo.forecast.AirQuality
import com.wanderwildwood.soramoyo.forecast.Current
import com.wanderwildwood.soramoyo.forecast.Day
import com.wanderwildwood.soramoyo.forecast.Forecast
import com.wanderwildwood.soramoyo.forecast.Hour
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
import java.time.ZoneOffset
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** Why the station has nothing new to show. */
enum class StationTrouble { NONE, NOT_SET, NO_ANSWER }

/** Why there is no forecast. */
enum class ForecastTrouble { NONE, NO_PERMISSION, NO_LOCATION, NO_ANSWER }

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
    /** The next hours, from the one under way. */
    val hours: List<Hour> = emptyList(),
    /** Whether the forecast's rain amounts are in inches rather than millimetres. */
    val inches: Boolean = false,
    /** The forecast place's offset from UTC, which the hours' times are in. */
    val offset: ZoneOffset = ZoneOffset.UTC,
    /** Whether the forecast is in metric units, which also picks the air-quality scale. */
    val metric: Boolean = true,
    /** The air now; null until it arrives, and left null if it never does. */
    val air: Air? = null,
    val units: Units.Choice = Units.Choice.AUTOMATIC,
    val forecastTrouble: ForecastTrouble = ForecastTrouble.NONE,
    /** Where the forecast and radar are for, when a place has been chosen rather than the phone's position. */
    val place: Place? = null,
) {
    /**
     * Today where the forecast is for, which is not always today on the phone: a place three
     * zones west is still on yesterday until three in the morning here, and matching its days
     * against the phone's date left Today waiting for a day that never came.
     */
    fun placeToday(): LocalDate = LocalDate.now(offset)
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
        SkyState(source = Source.load(app), place = Places.chosen(app), units = Units.choice(app)),
    )
    val state = _state.asStateFlow()

    private var stationJob: Job? = null
    private var forecastJob: Job? = null
    private var forecastAtMs = 0L
    private var forecastFor: Pair<LatLon, Boolean>? = null
    private var asking: Boolean? = null

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
        // The old forecast is for somewhere else now.
        _state.update {
            it.copy(
                place = place,
                current = null,
                days = emptyList(),
                hours = emptyList(),
                air = null,
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
        readStation()
        readForecast(force = true)
    }

    fun onPermissionResult() = readForecast(force = true)

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
            try {
                val predicted = Forecast.fetch(here.lat, here.lon, metric)
                forecastAtMs = SystemClock.elapsedRealtime()
                forecastFor = here to metric
                _state.update {
                    it.copy(
                        current = predicted.current,
                        days = predicted.days,
                        hours = predicted.hours,
                        inches = predicted.inches,
                        offset = predicted.offset,
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

        /** The three countries that still count in Fahrenheit. */
        private fun localeIsMetric(): Boolean =
            Locale.getDefault().country !in setOf("US", "LR", "MM")
    }
}
