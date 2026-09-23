package com.wanderwildwood.soramoyo.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** A geographic position (degrees). */
data class LatLon(val lat: Double, val lon: Double)

/**
 * Location via AOSP [LocationManager] — deliberately NOT FusedLocationProviderClient,
 * which needs Google Play Services (absent on the Mudita Kompakt). Returns null when
 * permission is missing or no fix can be had.
 */
object LocationProvider {
    private fun round2(degrees: Double): Double = Math.round(degrees * 100.0) / 100.0

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Where the phone is now, as near as can be had in [waitMs].
     *
     * A cached fix is only as fresh as the last app that asked for one, and on a phone with
     * few apps that use the GPS it can be days old and a state away — which a radar centred
     * on it and a forecast fetched for it both pass off as here, without a word. So a cached
     * fix younger than [FRESH_MS] is taken as it is; anything older is asked for again, and
     * the old one is used only if no new one arrives in time.
     */
    suspend fun here(context: Context): LatLon? {
        if (!hasPermission(context)) return null
        val cached = newestCached(context)
        if (cached != null && ageMs(cached) < FRESH_MS) return rounded(cached)
        // The network first, because it answers in seconds where it answers at all; but a
        // phone without Google's location service may list a network provider that never
        // answers, so it gets a short turn before the GPS gets a long one.
        val fresh = withTimeoutOrNull(NETWORK_WAIT_MS) { current(context, LocationManager.NETWORK_PROVIDER) }
            ?: withTimeoutOrNull(GPS_WAIT_MS) { current(context, LocationManager.GPS_PROVIDER) }
        return (fresh ?: cached)?.let(::rounded)
    }

    /** The newer of the two cached fixes. GPS first would prefer a day-old GPS fix to a minute-old network one. */
    private fun newestCached(context: Context): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .mapNotNull { provider ->
                try {
                    lm.getLastKnownLocation(provider)
                } catch (_: SecurityException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null // a provider this phone does not have
                }
            }
            .maxByOrNull { it.elapsedRealtimeNanos }
    }

    /** One new fix from [provider], or null at once if the phone has it switched off or lacks it. */
    private suspend fun current(context: Context, provider: String): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        if (!runCatching { lm.isProviderEnabled(provider) }.getOrDefault(false)) return null
        return suspendCancellableCoroutine { continuation ->
            val cancel = CancellationSignal()
            continuation.invokeOnCancellation { cancel.cancel() }
            try {
                lm.getCurrentLocation(provider, cancel, ContextCompat.getMainExecutor(context)) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } catch (_: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private fun ageMs(location: Location): Long =
        (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L

    // Rounded to two places, about a kilometre, before anything else sees it. The radar and
    // the forecast both send the position to someone else's server, and neither is any
    // better for knowing which house.
    private fun rounded(location: Location): LatLon =
        LatLon(round2(location.latitude), round2(location.longitude))

    private const val FRESH_MS = 15 * 60 * 1000L
    private const val NETWORK_WAIT_MS = 8_000L
    private const val GPS_WAIT_MS = 30_000L
}
