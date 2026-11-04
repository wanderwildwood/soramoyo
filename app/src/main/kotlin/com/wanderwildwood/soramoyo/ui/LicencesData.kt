package com.wanderwildwood.soramoyo.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.wanderwildwood.soramoyo.R

/** The address of the app's own source code. */
const val SOURCE_URL = "https://github.com/wanderwildwood/soramoyo"

/** What the APK carries that is not this app's own code, and the licence each project declares. */
val LICENCE_COMPONENTS: List<LicenceComponent> = listOf(
    LicenceComponent("kRadar (the work Sky started from)", "Ond\u0159ej Koloni\u010dn\u00fd (OK1CDJ)", "GPL-3.0", "https://github.com/ok1cdj/kRadar"),
    LicenceComponent("Open-Meteo forecasts and place search (fetched)", "Open-Meteo.com", "CC BY 4.0", "https://open-meteo.com"),
    LicenceComponent("RainViewer radar tiles (fetched)", "RainViewer", "Free tier terms", "https://www.rainviewer.com"),
    LicenceComponent("National Weather Service alerts (fetched)", "US National Weather Service", "Public domain", "https://www.weather.gov"),
    LicenceComponent("Alerts outside the US, from KDE's public alert server (fetched)", "KDE and the national warning services it gathers", "Each service's own terms", "https://alerts.kde.org/sources/status"),
    LicenceComponent("Natural Earth borders, states and cities", "Natural Earth", "Public domain", "https://www.naturalearthdata.com"),
    LicenceComponent("OkHttp and Okio", "Square", "Apache-2.0", "https://square.github.io/okhttp/"),
    LicenceComponent("WorkManager", "Google", "Apache-2.0", "https://developer.android.com/jetpack/androidx/releases/work"),
    LicenceComponent("Mudita Mindful Design (MMD)", "Mudita", "Apache-2.0", "https://github.com/mudita/MMD"),
    LicenceComponent("Lato typeface, supplied with MMD", "\u0141ukasz Dziedzic", "OFL-1.1", "https://www.latofonts.com"),
    LicenceComponent("AndroidX and Jetpack Compose", "Google", "Apache-2.0", "https://developer.android.com/jetpack"),
    LicenceComponent("Kotlin and kotlinx.coroutines", "JetBrains", "Apache-2.0", "https://kotlinlang.org"),
    LicenceComponent("Material Symbols icons", "Google", "Apache-2.0", "https://fonts.google.com/icons"),
)

/** Each distinct licence text once: family id and the raw resource. */
val LICENCE_RAW: List<Pair<String, Int>> = listOf(
    "gpl" to R.raw.gpl_3_0,
    "apache" to R.raw.apache_2_0,
    "ofl" to R.raw.ofl_1_1,
)

@Composable
fun rememberLicencesData(): LicencesData = LicencesData(
    appName = "Sky",
    sourceUrl = SOURCE_URL,
    licenceLine = stringResource(R.string.licences_licence),
    components = LICENCE_COMPONENTS,
    texts = LICENCE_RAW.map { (id, raw) ->
        LicenceText(id, stringResource(when (id) {
            "gpl" -> R.string.licences_full_gpl
            "apache" -> R.string.licences_full_apache
            "ofl" -> R.string.licences_full_ofl
            else -> R.string.licences_full_head
        }), raw)
    },
)

@Composable
fun rememberLicencesLabels(): LicencesLabels {
    val none = stringResource(R.string.about_no_browser_address, "@@")
    val noText = stringResource(R.string.licences_no_text, "@@")
    return LicencesLabels(
        title = stringResource(R.string.licences_title),
        back = stringResource(R.string.licences_back),
        sourceHeading = stringResource(R.string.licences_source_head),
        noText = { licence -> noText.replace("@@", licence) },
        noBrowser = { address -> none.replace("@@", address) },
    )
}
