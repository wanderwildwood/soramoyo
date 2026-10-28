package com.wanderwildwood.soramoyo

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * Whether Sky turns sideways when the phone does: the switch in Settings, on unless turned off.
 * The Kompakt's own settings have no auto-rotate to follow, so the app watches the phone itself.
 * Held as Compose state as well as kept, so the screen follows the switch at once.
 */
object Turning {
    private const val PREFS = "sky"
    private const val KEY = "turn_with_phone"
    private var state: MutableState<Boolean>? = null

    private fun state(context: Context): MutableState<Boolean> = state
        ?: mutableStateOf(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)).also { state = it }

    fun on(context: Context): Boolean = state(context).value

    fun set(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
        state(context).value = on
    }
}
