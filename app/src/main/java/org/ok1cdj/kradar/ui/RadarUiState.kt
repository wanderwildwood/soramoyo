package org.ok1cdj.kradar.ui

import android.graphics.Bitmap
import org.ok1cdj.kradar.location.LatLon
import org.ok1cdj.kradar.motion.CloudMotion

/**
 * Single immutable snapshot of the radar screen. [frames] and [frameTimes] are
 * parallel lists; [currentIndex] selects the frame shown. [tileSize] and the
 * center ([location]) + [zoom] together define the [org.ok1cdj.kradar.map.MapProjection]
 * the vector layer must use so map and radar stay aligned.
 */
data class RadarUiState(
    val location: LatLon? = null,
    val permissionDenied: Boolean = false,
    val zoom: Int = DEFAULT_ZOOM,
    val tileSize: Int = TILE_SIZE,
    val frames: List<Bitmap> = emptyList(),
    val frameTimes: List<Long> = emptyList(),
    val frameNowcast: List<Boolean> = emptyList(),
    // Marks frames we synthesized locally by advection (see [CloudMotion]), as
    // opposed to real API nowcast frames. Parallel to [frames]. [motion] is the
    // estimated cloud-motion vector behind them (null when none could be trusted).
    val frameEstimated: List<Boolean> = emptyList(),
    val motion: CloudMotion.Vector? = null,
    // The projection params the current [frames] were captured at. The overlay is
    // only drawn when these match the live zoom/location, so map and radar never
    // drift apart during a reload or after a failed fetch.
    val framesZoom: Int = -1,
    val framesCenter: LatLon? = null,
    val currentIndex: Int = 0,
    val isPlaying: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    // Transient dev/test flags — never persisted. [devMode] is toggled by a hidden
    // long-press and reveals the manual lat/lon panel + drag-to-pan. [manualOverride]
    // is set once the center comes from the panel or a drag, so GPS refreshes stop
    // clobbering the explored location.
    val devMode: Boolean = false,
    val manualOverride: Boolean = false,
) {
    val hasFrames: Boolean get() = frames.isNotEmpty()
    val currentTime: Long? get() = frameTimes.getOrNull(currentIndex)
    val currentIsNowcast: Boolean get() = frameNowcast.getOrNull(currentIndex) ?: false
    val currentIsEstimated: Boolean get() = frameEstimated.getOrNull(currentIndex) ?: false

    /** True when the loaded frames match the current projection (safe to overlay). */
    val overlayAligned: Boolean
        get() = framesZoom == zoom && framesCenter == location

    /**
     * Newest PAST frame's unix time (seconds) — the "last update" of real radar
     * data. Estimated/nowcast frames carry future timestamps, so they're excluded.
     */
    val lastUpdate: Long?
        get() = frameTimes.filterIndexed { i, _ -> !(frameNowcast.getOrNull(i) ?: false) }.maxOrNull()

    companion object {
        const val DEFAULT_ZOOM = 6
        const val MIN_ZOOM = 4
        const val MAX_ZOOM = 7
        const val TILE_SIZE = 512
    }
}
