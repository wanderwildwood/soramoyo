package com.wanderwildwood.soramoyo

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import com.wanderwildwood.soramoyo.ui.openAppSettings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.soramoyo.alerts.AlertNotices
import com.wanderwildwood.soramoyo.ui.AlertsScreen
import com.wanderwildwood.soramoyo.ui.DayScreen
import com.wanderwildwood.soramoyo.ui.RadarViewModel
import com.wanderwildwood.soramoyo.ui.SettingsScreen
import com.wanderwildwood.soramoyo.ui.SkyTabs
import com.wanderwildwood.soramoyo.ui.Tab
import com.wanderwildwood.soramoyo.ui.SkyViewModel
import com.wanderwildwood.soramoyo.ui.monochrome
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    /** Set by a press on an alert notification: open the alerts. */
    private var openAlerts by mutableStateOf(false)
    private val turner = TurnWithPhone(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openAlerts = intent.getBooleanExtra(AlertNotices.OPEN_EXTRA, false)
        // Brought back after an update or a cleared app, while the setting is on.
        AlertNotices.schedule(applicationContext)
        turner.set(Turning.on(this))
        setContent {
            // The switch in Settings takes effect at once.
            val turning = Turning.on(this)
            LaunchedEffect(turning) { turner.set(turning) }
            // A steady cursor: a blinking one repaints the panel twice a second for as long as a
            // field has focus.
            CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                ThemeMMD(colorScheme = monochrome) {
                    Sky(openAlerts = openAlerts, onAlertsOpened = { openAlerts = false })
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        turner.resume()
    }

    override fun onPause() {
        turner.pause()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(AlertNotices.OPEN_EXTRA, false)) openAlerts = true
    }
}

/** The tabs, or settings, one day's forecast or the alerts over them. */
private enum class Screen { TABS, SETTINGS, DAY, ALERTS }

@Composable
private fun Sky(openAlerts: Boolean, onAlertsOpened: () -> Unit, sky: SkyViewModel = viewModel()) {
    val state by sky.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.TABS) }
    var tab by rememberSaveable { mutableStateOf(Tab.TODAY) }
    // The day open on its own page, as an ISO date so it survives being saved.
    var day by rememberSaveable { mutableStateOf("") }

    // Held here rather than inside the radar tab so that its frames outlive a trip to another
    // tab: going to look at the temperature and coming back should not download two hours of
    // radar again.
    val radar: RadarViewModel = viewModel()

    // Refused with "don't ask again": asking again answers at once with nothing, so the button
    // then says why and takes the reader to the app's page in Android's settings.
    var refusedForGood by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? android.app.Activity
    val ask = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        refusedForGood = !granted && activity != null &&
            !androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)
        sky.onPermissionResult()
        radar.onPermissionResult(granted)
    }
    val here = LocalContext.current
    val allowLocation = {
        if (refusedForGood) {
            android.widget.Toast.makeText(here, R.string.location_refused, android.widget.Toast.LENGTH_LONG).show()
            openAppSettings(here)
        } else {
            ask.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

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
            if (event == Lifecycle.Event.ON_PAUSE) sky.onBackground()
        }
        lifecycleOwner.lifecycle.addObserver(watcher)
        onDispose { lifecycleOwner.lifecycle.removeObserver(watcher) }
    }

    // A notification's alert is for the first place: go back to it if the second is shown.
    LaunchedEffect(openAlerts) {
        if (!openAlerts) return@LaunchedEffect
        if (sky.state.value.showingSecond) {
            sky.switchPlace()
            radar.placeChanged()
        }
        screen = Screen.ALERTS
        onAlertsOpened()
    }

    // Back leaves settings or a day, then goes to Today, and only from Today leaves the app.
    BackHandler(enabled = screen != Screen.TABS || tab != Tab.TODAY) {
        if (screen != Screen.TABS) screen = Screen.TABS else tab = Tab.TODAY
    }

    when (screen) {
        Screen.TABS -> SkyTabs(
            tab = tab,
            onTab = { tab = it },
            state = state,
            radar = radar,
            onSwitchPlace = {
                sky.switchPlace()
                radar.placeChanged()
                // The radar tab asks only when it opens; already open, it is asked here.
                if (tab == Tab.RADAR) radar.onForeground()
            },
            onSettings = { screen = Screen.SETTINGS },
            onAllowLocation = allowLocation,
            onDay = {
                day = it.toString()
                screen = Screen.DAY
            },
            onAlerts = { screen = Screen.ALERTS },
            onPull = sky::refresh,
        )

        Screen.ALERTS -> AlertsScreen(
            alerts = state.alerts,
            pull = state.pull,
            onClose = { screen = Screen.TABS },
            onPull = sky::refresh,
        )

        Screen.DAY -> DayScreen(
            date = LocalDate.parse(day),
            state = state.forScreen(),
            onClose = { screen = Screen.TABS },
            onPull = sky::refresh,
        )

        Screen.SETTINGS -> SettingsScreen(
            source = state.source,
            place = state.place,
            second = state.second,
            units = state.units,
            onClose = { screen = Screen.TABS },
            onSource = sky::setSource,
            onPlace = {
                sky.setPlace(it)
                radar.placeChanged()
            },
            onSecond = {
                sky.setSecond(it)
                radar.placeChanged()
            },
            onUnits = sky::setUnits,
        )
    }
}
