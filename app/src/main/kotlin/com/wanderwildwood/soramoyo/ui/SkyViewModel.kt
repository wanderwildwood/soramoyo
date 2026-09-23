package com.wanderwildwood.soramoyo.ui

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wanderwildwood.soramoyo.forecast.Day
import com.wanderwildwood.soramoyo.forecast.Forecast
import com.wanderwildwood.soramoyo.location.LatLon
import com.wanderwildwood.soramoyo.location.LocationProvider
import com.wanderwildwood.soramoyo.station.StationClient
import com.wanderwildwood.soramoyo.station.StationReading
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** Why the station has nothing new to show. */
enum class StationTrouble { NONE, NOT_SET, NO_ANSWER }

/** Why there is no forecast. */
enum class ForecastTrouble { NONE, NO_PERMISSION, NO_LOCATION, NO_ANSWER }

data class SkyState(
    val address: String = "",
    val reading: StationReading? = null,
    /** Wall-clock time of the last reading that arrived, in milliseconds. */
    val readAt: Long = 0L,
    val stationTrouble: StationTrouble = StationTrouble.NONE,
    val asking: Boolean = false,
    val days: List<Day> = emptyList(),
    val forecastTrouble: ForecastTrouble = ForecastTrouble.NONE,
)

/**
 * The station and the forecast: what the first screen shows.
 *
 * Both are asked again every time the app comes to the front, the station always and the
 * forecast at most every half hour. The station is a few hundred bytes on the local
 * network and is the reason anyone opens this, so it is never held back; the forecast
 * changes a few times a day and costs a trip to a server someone else runs for free.
 */
class SkyViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("sky", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(SkyState(address = prefs.getString(KEY_ADDRESS, "").orEmpty()))
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

    fun setAddress(address: String) {
        val trimmed = address.trim()
        prefs.edit().putString(KEY_ADDRESS, trimmed).apply()
        // A different gateway's last reading is not this one's.
        _state.update { SkyState(address = trimmed, days = it.days, forecastTrouble = it.forecastTrouble) }
        readStation()
    }

    fun onPermissionResult() = readForecast(force = true)

    private fun readStation() {
        val address = _state.value.address
        if (address.isEmpty()) {
            _state.update { it.copy(stationTrouble = StationTrouble.NOT_SET) }
            // The forecast's units follow the station, and with no station there is nothing
            // to follow; it falls back to the phone's country, which readForecast handles.
            return
        }
        stationJob?.cancel()
        stationJob = viewModelScope.launch {
            _state.update { it.copy(asking = true) }
            try {
                val reading = StationClient.read(address)
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
        if (!LocationProvider.hasPermission(context)) {
            _state.update { it.copy(forecastTrouble = ForecastTrouble.NO_PERMISSION) }
            return
        }
        val metric = _state.value.reading?.metric ?: localeIsMetric()
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
            val here = LocationProvider.here(context)
            if (here == null) {
                if (_state.value.days.isEmpty()) {
                    _state.update { it.copy(forecastTrouble = ForecastTrouble.NO_LOCATION) }
                }
                return@launch
            }
            try {
                val days = Forecast.fetch(here.lat, here.lon, metric)
                forecastAtMs = SystemClock.elapsedRealtime()
                forecastFor = here to metric
                _state.update { it.copy(days = days, forecastTrouble = ForecastTrouble.NONE) }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _state.update { it.copy(forecastTrouble = ForecastTrouble.NO_ANSWER) }
            }
        }
    }

    companion object {
        private const val KEY_ADDRESS = "station_address"
        private const val FORECAST_EVERY_MS = 30 * 60 * 1000L

        /** The three countries that still count in Fahrenheit. */
        private fun localeIsMetric(): Boolean =
            Locale.getDefault().country !in setOf("US", "LR", "MM")
    }
}
