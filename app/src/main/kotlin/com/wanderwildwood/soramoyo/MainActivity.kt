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
import com.wanderwildwood.soramoyo.ui.RadarScreen
import com.wanderwildwood.soramoyo.ui.RadarViewModel
import com.wanderwildwood.soramoyo.ui.SettingsScreen
import com.wanderwildwood.soramoyo.ui.SkyScreen
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

/** Which of the three screens is up. */
private enum class Screen { SKY, RADAR, SETTINGS }

@Composable
private fun Sky(sky: SkyViewModel = viewModel()) {
    val state by sky.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.SKY) }

    // Held here rather than inside the radar screen so that its frames outlive a trip back
    // to the first screen: going to look at the temperature and coming back should not
    // download two hours of radar again.
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
    val showing by rememberUpdatedState(screen)
    DisposableEffect(lifecycleOwner) {
        val watcher = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                sky.onForeground()
                if (showing == Screen.RADAR) radar.onForeground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(watcher)
        onDispose { lifecycleOwner.lifecycle.removeObserver(watcher) }
    }

    BackHandler(enabled = screen != Screen.SKY) { screen = Screen.SKY }

    when (screen) {
        Screen.SKY -> SkyScreen(
            state = state,
            onRadar = { screen = Screen.RADAR },
            onSettings = { screen = Screen.SETTINGS },
            onAllowLocation = allowLocation,
        )

        Screen.RADAR -> RadarScreen(
            vm = radar,
            onBack = { screen = Screen.SKY },
            onAllowLocation = allowLocation,
        )

        Screen.SETTINGS -> SettingsScreen(
            address = state.address,
            onClose = { screen = Screen.SKY },
            onAddress = sky::setAddress,
        )
    }
}
