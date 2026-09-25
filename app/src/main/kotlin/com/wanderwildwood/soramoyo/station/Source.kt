package com.wanderwildwood.soramoyo.station

import android.content.Context

/**
 * Where the station reading comes from. One at a time; none is a fine answer, and most
 * people will give it.
 *
 * Three are read on the home network and ask for no account: an Ecowitt gateway and a Davis
 * WeatherLink Live by their address, and a WeatherFlow Tempest by listening for what its hub
 * broadcasts. The fourth, Weather Underground, reaches nearly every other make, because most
 * stations upload there, at the cost of going through its servers with the owner's own key.
 */
sealed interface Source {
    data object None : Source
    data class Ecowitt(val address: String) : Source
    data class Davis(val address: String) : Source
    data object Tempest : Source
    data class Underground(val stationId: String, val apiKey: String) : Source

    /** Whether the reading can only be had on the station's own network. */
    val local: Boolean get() = this is Ecowitt || this is Davis || this is Tempest

    companion object {
        private const val PREFS = "sky"
        private const val KIND = "station_kind"
        private const val ADDRESS = "station_address"
        private const val WU_ID = "station_wu_id"
        private const val WU_KEY = "station_wu_key"

        fun load(context: Context): Source {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val address = p.getString(ADDRESS, "").orEmpty()
            return when (p.getString(KIND, null)) {
                "ecowitt" -> Ecowitt(address)
                "davis" -> Davis(address)
                "tempest" -> Tempest
                "wu" -> Underground(p.getString(WU_ID, "").orEmpty(), apiKey(context, p.getString(WU_KEY, "").orEmpty()))
                "none" -> None
                // Before there was a choice, an address was always an Ecowitt gateway's.
                else -> if (address.isNotEmpty()) Ecowitt(address) else None
            }
        }

        /**
         * The Weather Underground key is kept sealed (see [Secrets]). One written in the clear
         * by 0.7.1 or earlier is sealed where it lies the first time it is read, so upgrading
         * keeps it; if the keystore will not seal it, it is left as it was and tried again next
         * time. One that cannot be opened reads as no key at all.
         */
        private fun apiKey(context: Context, stored: String): String {
            if (stored.isNotEmpty() && !Secrets.isSealed(stored)) {
                runCatching { Secrets.seal(stored) }.getOrNull()?.let { sealed ->
                    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(WU_KEY, sealed).apply()
                }
            }
            return Secrets.open(stored)
        }

        fun save(context: Context, source: Source) {
            val e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .remove(ADDRESS).remove(WU_ID).remove(WU_KEY)
            when (source) {
                None -> e.putString(KIND, "none")
                is Ecowitt -> e.putString(KIND, "ecowitt").putString(ADDRESS, source.address)
                is Davis -> e.putString(KIND, "davis").putString(ADDRESS, source.address)
                Tempest -> e.putString(KIND, "tempest")
                is Underground -> e.putString(KIND, "wu")
                    .putString(WU_ID, source.stationId)
                    // Not written at all, rather than in the clear, if the keystore will not seal it.
                    .putString(WU_KEY, runCatching { Secrets.seal(source.apiKey) }.getOrDefault(""))
            }
            e.apply()
        }
    }
}
