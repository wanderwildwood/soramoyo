package com.wanderwildwood.soramoyo

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.soramoyo.ui.RadarViewModel
import com.wanderwildwood.soramoyo.ui.SettingsScreen
import com.wanderwildwood.soramoyo.ui.SkyTabs
import com.wanderwildwood.soramoyo.ui.Tab
import com.wanderwildwood.soramoyo.ui.SkyViewModel
import com.wanderwildwood.soramoyo.ui.monochrome

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                Sky()
            }
        }
    }
}

/** The tabs, or settings over them. */
private enum class Screen { TABS, SETTINGS }

@Composable
private fun Sky(sky: SkyViewModel = viewModel()) {
    val state by sky.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.TABS) }
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }

    // Held here rather than inside the radar tab so that its frames outlive a trip to another
    // tab: going to look at the temperature and coming back should not download two hours of
    // radar again.
    val radar: RadarViewModel = viewModel()

    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        sky.onPermissionResult()
        radar.onPermissionResult(granted)
    }
    val allowLocation = { ask.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

    // Every return to the front asks the station again. The radar has its own throttle and
    // is only asked while it is the screen showing.
    // Keyed on the owner alone: an observer added to a lifecycle that is already resumed is
    // sent ON_RESUME at once, so re-adding it on every change of screen would ask the
    // station again each time the reader moved between screens.
    val lifecycleOwner = LocalLifecycleOwner.current
    val showing by rememberUpdatedState(screen to tab)
    DisposableEffect(lifecycleOwner) {
        val watcher = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sky.onForeground()
                if (showing == (Screen.TABS to Tab.RADAR)) radar.onForeground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(watcher)
        onDispose { lifecycleOwner.lifecycle.removeObserver(watcher) }
    }

    // Back leaves settings, then goes to Today, and only from Today leaves the app.
    BackHandler(enabled = screen != Screen.TABS || tab != Tab.TODAY) {
        if (screen != Screen.TABS) screen = Screen.TABS else tab = Tab.TODAY
    }

    when (screen) {
        Screen.TABS -> SkyTabs(
            tab = tab,
            onTab = { tab = it },
            state = state,
            radar = radar,
            onSettings = { screen = Screen.SETTINGS },
            onAllowLocation = allowLocation,
        )

        Screen.SETTINGS -> SettingsScreen(
            address = state.address,
            place = state.place,
            onClose = { screen = Screen.TABS },
            onAddress = sky::setAddress,
            onPlace = {
                sky.setPlace(it)
                radar.placeChanged()
            },
        )
    }
}
