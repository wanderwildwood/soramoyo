package org.ok1cdj.kradar.ui

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.ok1cdj.kradar.location.LatLon
import org.ok1cdj.kradar.location.LocationProvider
import org.ok1cdj.kradar.motion.CloudMotion
import org.ok1cdj.kradar.motion.IntensityField
import org.ok1cdj.kradar.net.RadarFrame
import org.ok1cdj.kradar.net.RainViewerClient
import org.ok1cdj.kradar.render.EinkConverter

/**
 * Owns the radar state machine: location, zoom, prefetch of all frames, and
 * animation playback. All tiles are downloaded + quantized up front in
 * [refresh]; playback only swaps in-memory bitmaps (no network at play time).
 */
class RadarViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {

    // Zoom (and last location) persist across config change AND process death, so
    // a restart returns to the same view instead of resetting to the default.
    private val _state = MutableStateFlow(
        RadarUiState(
            zoom = saved.get<Int>(KEY_ZOOM) ?: RadarUiState.DEFAULT_ZOOM,
            location = savedLocation(),
        )
    )
    val state = _state.asStateFlow()

    private var refreshJob: Job? = null
    private var playJob: Job? = null
    private var panJob: Job? = null

    /** Monotonic timestamp of the last radar fetch; 0 until the first load. */
    private var lastRefreshAtMs = 0L

    /** Playback tick interval — tune on device. */
    private val playbackIntervalMs = 550L

    /** Read last-known GPS/network location and (re)load the radar. */
    fun locate() {
        if (_state.value.manualOverride) return // keep the manually explored view
        val ctx = getApplication<Application>()
        if (!LocationProvider.hasPermission(ctx)) {
            _state.update { it.copy(permissionDenied = true) }
            return
        }
        val loc = LocationProvider.lastKnown(ctx) ?: savedLocation()
        if (loc == null) {
            // Permission granted but no cached fix yet.
            _state.update {
                if (it.location == null) it.copy(permissionDenied = false, error = NO_FIX) else it
            }
            return
        }
        saved[KEY_LAT] = loc.lat
        saved[KEY_LON] = loc.lon
        _state.update { it.copy(location = loc, permissionDenied = false, error = null) }
        refresh()
    }

    /**
     * Called each time the app returns to the foreground (ON_RESUME). Refreshes
     * at most once per [REFRESH_MIN_INTERVAL_MS]; when it does, it re-reads the
     * GPS fix so the map re-centers if the user has moved. Location and frames are
     * updated together so the overlay never drifts out of alignment.
     */
    fun onForeground() {
        if (_state.value.manualOverride) return // don't yank the view back while exploring
        val ctx = getApplication<Application>()
        if (!LocationProvider.hasPermission(ctx)) {
            _state.update { it.copy(permissionDenied = true) }
            return
        }
        val now = SystemClock.elapsedRealtime()
        val stale = lastRefreshAtMs == 0L || now - lastRefreshAtMs >= REFRESH_MIN_INTERVAL_MS
        if (!stale) return // keep the current view; overlay stays aligned
        val loc = LocationProvider.lastKnown(ctx) ?: savedLocation()
        if (loc == null) {
            _state.update {
                if (it.location == null) it.copy(permissionDenied = false, error = NO_FIX) else it
            }
            return
        }
        saved[KEY_LAT] = loc.lat
        saved[KEY_LON] = loc.lon
        _state.update { it.copy(location = loc, permissionDenied = false, error = null) }
        refresh(force = true)
    }

    private fun savedLocation(): LatLon? {
        val lat = saved.get<Double>(KEY_LAT) ?: return null
        val lon = saved.get<Double>(KEY_LON) ?: return null
        return LatLon(lat, lon)
    }

    /** Called after the runtime permission dialog resolves. */
    fun onPermissionResult(granted: Boolean) {
        if (granted) locate() else _state.update { it.copy(permissionDenied = true) }
    }

    // --- dev mode (hidden long-press; session-only, never persisted) -------

    /** Toggle the hidden dev overlay (manual lat/lon panel + drag-to-pan). */
    fun toggleDevMode() = _state.update { it.copy(devMode = !it.devMode) }

    /** Jump to a manually entered location. Bypasses GPS; not persisted. */
    fun setManualLocation(lat: Double, lon: Double) {
        panJob?.cancel()
        _state.update {
            it.copy(location = LatLon(lat, lon), manualOverride = true, permissionDenied = false, error = null)
        }
        refresh(force = true)
    }

    /** Discard any manual override and return to real GPS centering. */
    fun useGps() {
        panJob?.cancel()
        _state.update { it.copy(manualOverride = false) }
        locate()
    }

    /**
     * Re-center after a drag-to-pan. The center updates immediately so the vector
     * layer reprojects, but the network tile reload is debounced so a flurry of
     * drags doesn't hammer RainViewer or thrash the e-ink redraw.
     */
    fun setCenter(lat: Double, lon: Double) {
        _state.update { it.copy(location = LatLon(lat, lon), manualOverride = true, error = null) }
        panJob?.cancel()
        panJob = viewModelScope.launch {
            delay(PAN_DEBOUNCE_MS)
            refresh(force = true)
        }
    }

    fun zoomIn() = changeZoom(+1)
    fun zoomOut() = changeZoom(-1)

    private fun changeZoom(delta: Int) {
        val cur = _state.value
        val z = (cur.zoom + delta).coerceIn(RadarUiState.MIN_ZOOM, RadarUiState.MAX_ZOOM)
        if (z == cur.zoom || cur.location == null) return
        saved[KEY_ZOOM] = z
        _state.update { it.copy(zoom = z) }
        refresh(force = true)
    }

    /**
     * Fetch metadata, download + quantize every frame at the current
     * location/zoom, then swap them in. Cancels any in-flight refresh.
     */
    fun refresh(force: Boolean = false) {
        val loc = _state.value.location ?: return
        val now = SystemClock.elapsedRealtime()
        if (!force && lastRefreshAtMs != 0L && now - lastRefreshAtMs < REFRESH_MIN_INTERVAL_MS) return
        lastRefreshAtMs = now
        pause()
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val zoom = _state.value.zoom
                val size = _state.value.tileSize
                val result = downloadFrames(loc, zoom, size)
                if (result.bitmaps.isEmpty()) {
                    _state.update { it.copy(loading = false, error = NO_DATA) }
                    return@launch
                }
                val old = _state.value.frames
                // "now" = the latest PAST frame; frames run past-then-forecast, so
                // bitmaps.lastIndex is the furthest forecast, not the current state.
                val nowIndex = result.nowcast.indexOfLast { !it }
                    .let { if (it >= 0) it else result.bitmaps.lastIndex }
                _state.update {
                    it.copy(
                        frames = result.bitmaps,
                        frameTimes = result.times,
                        frameNowcast = result.nowcast,
                        frameEstimated = result.estimated,
                        motion = result.motion,
                        framesZoom = zoom,       // tag so the overlay only shows when aligned
                        framesCenter = loc,
                        currentIndex = nowIndex, // start on the latest past frame ("now")
                        loading = false,
                        error = null,
                    )
                }
                old.forEach { if (!it.isRecycled) it.recycle() }
            } catch (_: kotlinx.coroutines.CancellationException) {
                throw kotlinx.coroutines.CancellationException()
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "network error") }
            }
        }
    }

    /** Result of a full radar load: parallel frame lists plus the motion estimate. */
    private data class FrameSet(
        val bitmaps: List<Bitmap>,
        val times: List<Long>,
        val nowcast: List<Boolean>,
        val estimated: List<Boolean>,
        val motion: CloudMotion.Vector?,
    )

    private suspend fun downloadFrames(
        loc: LatLon,
        zoom: Int,
        size: Int,
    ): FrameSet {
        // Network + decode: fan out on the IO pool.
        val ok = withContext(Dispatchers.IO) {
            val frames = RainViewerClient.fetchFrames()
            val deferred: List<Deferred<Triple<Bitmap, IntensityField, RadarFrame>?>> =
                frames.map { f ->
                    async {
                        try {
                            val png = RainViewerClient.fetchTile(f, loc.lat, loc.lon, zoom, size)
                            EinkConverter.decode(png)?.let { (bmp, field) -> Triple(bmp, field, f) }
                        } catch (_: Exception) {
                            null // skip a bad frame rather than failing the whole set
                        }
                    }
                }
            deferred.awaitAll().filterNotNull()
        }

        val bitmaps = ok.mapTo(ArrayList()) { it.first }
        val times = ok.mapTo(ArrayList()) { it.third.timeSec }
        val nowcast = ok.mapTo(ArrayList()) { it.third.nowcast }
        val estimated = MutableList(ok.size) { false }
        var motion: CloudMotion.Vector? = null

        // RainViewer's free API serves no forecast frames. When there are none,
        // synthesize our own by advecting the latest past frame (see CloudMotion).
        // This is CPU-bound (correlation + bitmap synthesis), so run it on Default.
        val past = ok.filterNot { it.third.nowcast }
        if (nowcast.none { it } && past.isNotEmpty()) {
            val synth = withContext(Dispatchers.Default) {
                CloudMotion.estimate(past.map { it.second }, past.map { it.third.timeSec })
                    ?.let { m ->
                        val lastTime = past.last().third.timeSec
                        m to CloudMotion.extrapolate(past.last().second, m, FORECAST_STEPS)
                            .mapIndexed { k, field ->
                                EinkConverter.renderField(field) to
                                    lastTime + (k + 1) * CloudMotion.STEP_SECONDS
                            }
                    }
            }
            if (synth != null) {
                motion = synth.first
                synth.second.forEach { (bmp, t) ->
                    bitmaps.add(bmp)
                    times.add(t)
                    nowcast.add(true)
                    estimated.add(true)
                }
            }
        }
        return FrameSet(bitmaps, times, nowcast, estimated, motion)
    }

    // --- playback ---------------------------------------------------------

    fun togglePlay() = if (_state.value.isPlaying) pause() else play()

    fun play() {
        if (!_state.value.hasFrames || _state.value.isPlaying) return
        _state.update { it.copy(isPlaying = true) }
        playJob = viewModelScope.launch {
            while (true) {
                delay(playbackIntervalMs)
                _state.update {
                    if (!it.hasFrames) it
                    else it.copy(currentIndex = (it.currentIndex + 1) % it.frames.size)
                }
            }
        }
    }

    fun pause() {
        playJob?.cancel()
        playJob = null
        if (_state.value.isPlaying) _state.update { it.copy(isPlaying = false) }
    }

    fun stepForward() = step(+1)
    fun stepBack() = step(-1)

    private fun step(delta: Int) {
        pause()
        _state.update {
            if (!it.hasFrames) it
            else {
                val n = it.frames.size
                it.copy(currentIndex = ((it.currentIndex + delta) % n + n) % n)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        _state.value.frames.forEach { if (!it.isRecycled) it.recycle() }
    }

    companion object {
        private const val NO_DATA = "No radar data available."
        private const val NO_FIX = "No location fix yet — try again in a moment."
        private const val KEY_ZOOM = "zoom"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"
        private const val REFRESH_MIN_INTERVAL_MS = 10 * 60 * 1000L
        private const val PAN_DEBOUNCE_MS = 350L

        /** Locally-synthesized forecast frames (10-min steps) → 30 min ahead. */
        private const val FORECAST_STEPS = 3
    }
}
