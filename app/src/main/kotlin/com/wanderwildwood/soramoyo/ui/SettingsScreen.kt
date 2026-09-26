package com.wanderwildwood.soramoyo.ui

import androidx.compose.ui.Alignment
import com.wanderwildwood.soramoyo.glance.LockScreen
import com.mudita.mmd.components.switcher.SwitchMMD
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.soramoyo.R
import com.wanderwildwood.soramoyo.location.Place
import com.wanderwildwood.soramoyo.location.Places
import com.wanderwildwood.soramoyo.station.Source
import com.wanderwildwood.soramoyo.station.Units
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The three things there are to set: which station, where the forecast and radar are for,
 * and the units.
 *
 * Units follow the station, else the phone's country, until told otherwise. They were left
 * out at first, on the grounds that the gateway already knows how its owner counts; but a
 * phone set to one country and a person from another is common, and most have no gateway.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    source: Source,
    place: Place?,
    units: Units.Choice,
    onClose: () -> Unit,
    onSource: (Source) -> Unit,
    onPlace: (Place?) -> Unit,
    onUnits: (Units.Choice) -> Unit,
) {
    var aboutOpen by remember { mutableStateOf(false) }
    var stationOpen by remember { mutableStateOf(false) }
    var placeOpen by remember { mutableStateOf(false) }
    var unitsOpen by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBarMMD(
                title = { TextMMD(text = stringResource(R.string.settings_title)) },
                navigationIcon = { BarButton(Icons.Close, stringResource(R.string.settings_cd_close), onClose) },
                actions = { BarButton(Icons.Info, stringResource(R.string.settings_cd_about), { aboutOpen = true }) },
            )
        },
    ) { contentPadding ->
        LazyColumnMMD(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Setting(
                    title = stringResource(R.string.settings_station),
                    value = source.describe(),
                    onClick = { stationOpen = true },
                )
            }
            item {
                Setting(
                    title = stringResource(R.string.settings_place),
                    value = place?.label ?: stringResource(R.string.settings_place_phone),
                    onClick = { placeOpen = true },
                )
            }
            item {
                Setting(
                    title = stringResource(R.string.settings_units),
                    value = stringResource(unitsName(units)),
                    onClick = { unitsOpen = true },
                )
            }
            item {
                // Read and written here: nothing else on this screen depends on it.
                val context = LocalContext.current
                var onLock by remember { mutableStateOf(LockScreen.on(context)) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onLock = !onLock
                            LockScreen.set(context, onLock)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextMMD(
                        text = stringResource(R.string.settings_lock_screen),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    SwitchMMD(checked = onLock, onCheckedChange = null)
                }
            }
        }
    }

    if (aboutOpen) AboutDialog(onDismiss = { aboutOpen = false })

    if (placeOpen) {
        PlaceDialog(
            chosen = place,
            onChoose = {
                onPlace(it)
                placeOpen = false
            },
            onDismiss = { placeOpen = false },
        )
    }

    if (unitsOpen) {
        EInkDialog(onDismiss = { unitsOpen = false }) {
            TextMMD(text = stringResource(R.string.settings_units), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(10.dp))
            Units.Choice.entries.forEach { choice ->
                Choice(stringResource(unitsName(choice)), chosen = choice == units) {
                    onUnits(choice)
                    unitsOpen = false
                }
            }
        }
    }

    if (stationOpen) {
        StationDialog(
            source = source,
            onSave = {
                onSource(it)
                stationOpen = false
            },
            onDismiss = { stationOpen = false },
        )
    }
}

@Composable
private fun Setting(title: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    ) {
        TextMMD(text = title, style = MaterialTheme.typography.bodyMedium)
        TextMMD(text = value, style = MaterialTheme.typography.labelSmall)
    }
}

private fun unitsName(choice: Units.Choice): Int = when (choice) {
    Units.Choice.AUTOMATIC -> R.string.settings_units_automatic
    Units.Choice.METRIC -> R.string.settings_units_metric
    Units.Choice.IMPERIAL -> R.string.settings_units_imperial
}

/** What the place search has to say for itself. */
private sealed interface Search {
    data object Idle : Search
    data object Asking : Search
    data class Found(val places: List<Place>) : Search
    data object Failed : Search
}

/**
 * Choosing where the forecast and radar are for: the phone's own position, or a place found
 * by name. The note says why it is here at all, because a stranger would otherwise wonder
 * why a phone needs telling where it is.
 */
@Composable
private fun PlaceDialog(chosen: Place?, onChoose: (Place?) -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    var search by remember { mutableStateOf<Search>(Search.Idle) }
    val scope = rememberCoroutineScope()
    fun look() {
        if (typed.isBlank()) return
        search = Search.Asking
        scope.launch {
            search = try {
                Search.Found(Places.search(typed))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Search.Failed
            }
        }
    }

    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = stringResource(R.string.settings_place), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(14.dp))
        TextMMD(text = stringResource(R.string.settings_place_why), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(10.dp))
        Choice(stringResource(R.string.settings_place_phone), chosen = chosen == null) { onChoose(null) }
        chosen?.let { Choice(it.label, chosen = true) { onChoose(it) } }
        // Above the field rather than under it, because the keyboard stays up. On the
        // Kompakt it will not close when asked from inside a dialog — Compose's controller,
        // clearing focus and the input method's own hide were each tried on the phone — and
        // whatever lies under the field lies under the keyboard.
        when (val s = search) {
            Search.Idle -> Unit
            Search.Asking -> TextMMD(text = stringResource(R.string.settings_place_asking), style = MaterialTheme.typography.labelSmall)
            Search.Failed -> TextMMD(text = stringResource(R.string.settings_place_failed), style = MaterialTheme.typography.labelSmall)
            is Search.Found ->
                if (s.places.isEmpty()) {
                    TextMMD(text = stringResource(R.string.settings_place_none, typed.trim()), style = MaterialTheme.typography.labelSmall)
                } else {
                    // Three at most. Five pushed Cancel and Search off the bottom of the
                    // panel, and a fourth Portland is a sign to type the state as well.
                    s.places.take(3).forEach { place -> Choice(place.label, chosen = place == chosen) { onChoose(place) } }
                }
        }
        Spacer(Modifier.height(10.dp))
        TextMMD(text = stringResource(R.string.settings_place_by_name), style = MaterialTheme.typography.labelSmall)
        TextFieldMMD(
            value = typed,
            onValueChange = { typed = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { look() }),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButtonMMD(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(48.dp),
            ) { TextMMD(text = stringResource(R.string.settings_cancel), style = MaterialTheme.typography.bodySmall) }
            OutlinedButtonMMD(
                onClick = { look() },
                enabled = typed.isNotBlank(),
                modifier = Modifier.weight(1f).height(48.dp),
            ) { TextMMD(text = stringResource(R.string.settings_place_search), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** One place to choose. The one in use is bold, the house's only emphasis. */
@Composable
private fun Choice(label: String, chosen: Boolean = false, padding: Int = 9, onClick: () -> Unit) {
    TextMMD(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = padding.dp),
    )
}

/**
 * Which station, if any, and what it needs to be found.
 *
 * Each kind asks only for what it needs and says, once, where to find it — an address or a
 * key is the one setting a stranger cannot guess the meaning of. Saving is also the keyboard's
 * own Done key: on the Kompakt the keyboard stays up over a dialog's buttons.
 */
@Composable
private fun StationDialog(source: Source, onSave: (Source) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(source.kind()) }
    var address by remember {
        mutableStateOf(
            when (source) {
                is Source.Ecowitt -> source.address
                is Source.Davis -> source.address
                else -> ""
            },
        )
    }
    var stationId by remember { mutableStateOf((source as? Source.Underground)?.stationId.orEmpty()) }
    var apiKey by remember { mutableStateOf((source as? Source.Underground)?.apiKey.orEmpty()) }

    val chosen: Source? = when (kind) {
        Kind.NONE -> Source.None
        Kind.ECOWITT -> address.trim().takeIf { it.isNotEmpty() }?.let { Source.Ecowitt(it) }
        Kind.DAVIS -> address.trim().takeIf { it.isNotEmpty() }?.let { Source.Davis(it) }
        Kind.TEMPEST -> Source.Tempest
        Kind.UNDERGROUND -> if (stationId.isNotBlank() && apiKey.isNotBlank()) {
            Source.Underground(stationId.trim().uppercase(), apiKey.trim())
        } else {
            null
        }
    }
    val save = { chosen?.let(onSave); Unit }

    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = stringResource(R.string.settings_station), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        // Closer together than the place list's rows: five kinds, a note and two fields
        // have to fit over the buttons on a 4.3" panel.
        Kind.entries.forEach { k ->
            Choice(stringResource(k.label), chosen = k == kind, padding = 6) { kind = k }
        }
        Spacer(Modifier.height(8.dp))
        when (kind) {
            Kind.NONE -> Unit
            Kind.TEMPEST -> Note(R.string.station_tempest_note)
            Kind.ECOWITT, Kind.DAVIS -> {
                Note(if (kind == Kind.ECOWITT) R.string.station_ecowitt_note else R.string.station_davis_note)
                Field(address, { address = it }, KeyboardType.Uri, last = true, onDone = save)
            }
            Kind.UNDERGROUND -> {
                Note(R.string.station_wu_note)
                TextMMD(text = stringResource(R.string.station_wu_id), style = MaterialTheme.typography.labelSmall)
                Field(stationId, { stationId = it }, KeyboardType.Ascii, last = false, onDone = save)
                Spacer(Modifier.height(4.dp))
                TextMMD(text = stringResource(R.string.station_wu_key), style = MaterialTheme.typography.labelSmall)
                Field(apiKey, { apiKey = it }, KeyboardType.Password, last = true, onDone = save)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButtonMMD(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(48.dp),
            ) { TextMMD(text = stringResource(R.string.settings_cancel), style = MaterialTheme.typography.bodySmall) }
            OutlinedButtonMMD(
                onClick = save,
                enabled = chosen != null,
                modifier = Modifier.weight(1f).height(48.dp),
            ) { TextMMD(text = stringResource(R.string.settings_save), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** The kinds of station, in the order they are offered. */
private enum class Kind(val label: Int) {
    NONE(R.string.station_none),
    ECOWITT(R.string.station_ecowitt),
    UNDERGROUND(R.string.station_wu),
    TEMPEST(R.string.station_tempest),
    DAVIS(R.string.station_davis),
}

private fun Source.kind(): Kind = when (this) {
    Source.None -> Kind.NONE
    is Source.Ecowitt -> Kind.ECOWITT
    is Source.Davis -> Kind.DAVIS
    Source.Tempest -> Kind.TEMPEST
    is Source.Underground -> Kind.UNDERGROUND
}

/** What the settings row says about the station. */
@Composable
private fun Source.describe(): String = when (this) {
    Source.None -> stringResource(R.string.settings_not_set)
    is Source.Ecowitt -> stringResource(R.string.station_ecowitt_at, address)
    is Source.Davis -> stringResource(R.string.station_davis_at, address)
    Source.Tempest -> stringResource(R.string.station_tempest)
    is Source.Underground -> stringResource(R.string.station_wu_at, stationId)
}

@Composable
private fun Note(text: Int) {
    TextMMD(text = stringResource(text), style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, type: KeyboardType, last: Boolean, onDone: () -> Unit) {
    TextFieldMMD(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = type,
            autoCorrectEnabled = false,
            imeAction = if (last) ImeAction.Done else ImeAction.Next,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier.fillMaxWidth(),
    )
}
