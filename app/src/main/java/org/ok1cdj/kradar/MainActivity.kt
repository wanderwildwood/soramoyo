package org.ok1cdj.kradar

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.ok1cdj.kradar.ui.KRadarTheme
import org.ok1cdj.kradar.ui.MeteoRadarScreen
import org.ok1cdj.kradar.ui.RadarViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KRadarTheme {
                App()
            }
        }
    }
}

@Composable
private fun App() {
    val vm: RadarViewModel = viewModel()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> vm.onPermissionResult(granted) }

    // Refresh on every return to the foreground (and the first show). onForeground()
    // re-reads location and reloads the radar, throttled to ~10 min. If the
    // permission is missing it flips to the permission-prompt state; the button
    // there launches the request, and onPermissionResult retries locate().
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onForeground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MeteoRadarScreen(
        vm = vm,
        onRequestLocationPermission = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
    )
}
