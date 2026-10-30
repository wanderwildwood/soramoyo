package com.wanderwildwood.soramoyo.ui

import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import com.wanderwildwood.soramoyo.glance.LockScreen
import com.wanderwildwood.soramoyo.alerts.AlertNotices
import com.mudita.mmd.components.switcher.SwitchMMD
import com.wanderwildwood.soramoyo.Turning
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
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
 * The things there are to set: which station, where the forecast and radar are for (and a
 * second place to switch to), the units, the lock screen and alert notifications.
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
    second: Place?,
    units: Units.Choice,
    onClose: () -> Unit,
    onSource: (Source) -> Unit,
    onPlace: (Place?) -> Unit,
    onSecond: (Place?) -> Unit,
    onUnits: (Units.Choice) -> Unit,
) {
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    var stationOpen by rememberSaveable { mutableStateOf(false) }
    var placeOpen by rememberSaveable { mutableStateOf(false) }
    var secondOpen by rememberSaveable { mutableStateOf(false) }
    var unitsOpen by rememberSaveable { mutableStateOf(false) }

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
        val glanceHere = LockScreen.glanceInstalled(LocalContext.current)
        PagedColumn(
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
                    title = stringResource(R.string.settings_second),
                    value = second?.label ?: stringResource(R.string.settings_not_set),
                    onClick = { secondOpen = true },
                )
            }
            item {
                Setting(
                    title = stringResource(R.string.settings_units),
                    value = stringResource(unitsName(units)),
                    onClick = { unitsOpen = true },
                )
            }
            // Only with Glance on the phone: without it the switch would change nothing anyone can see.
            if (glanceHere) item {
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
                        .heightIn(min = 48.dp)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextMMD(
                        text = stringResource(R.string.settings_lock_screen),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(16.dp))
                    SwitchMMD(checked = onLock, onCheckedChange = null)
                }
            }
            item {
                // Read and written here as well; the screen turns, or stops turning, at once.
                val context = LocalContext.current
                val turning = Turning.on(context)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { Turning.set(context, !turning) }
                        .heightIn(min = 48.dp)
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextMMD(
                        text = stringResource(R.string.settings_turn),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(16.dp))
                    SwitchMMD(checked = turning, onCheckedChange = null)
                }
            }
            item {
                // Read and written here too. Switching it on is what asks the phone for leave to
                // notify, where the phone asks at all.
                val context = LocalContext.current
                var notify by remember { mutableStateOf(AlertNotices.on(context)) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            notify = !notify
                            AlertNotices.set(context, notify)
                        }
                        .padding(vertical = 12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextMMD(
                            text = stringResource(R.string.settings_alerts),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(16.dp))
                        SwitchMMD(checked = notify, onCheckedChange = null)
                    }
                    TextMMD(text = stringResource(R.string.settings_alerts_note), style = MaterialTheme.typography.labelSmall)
                    if (notify && !AlertNotices.allowed(context)) {
                        TextMMD(text = stringResource(R.string.settings_alerts_blocked), style = MaterialTheme.typography.labelSmall)
                        TextMMD(
                            text = stringResource(R.string.settings_open_app_settings),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { openNotificationSettings(context) }
                                .fillMaxWidth().heightIn(min = 48.dp).wrapContentHeight(Alignment.CenterVertically),
                        )
                    }
                }
            }
        }
    }

    if (aboutOpen) AboutDialog(onDismiss = { aboutOpen = false })

    if (placeOpen) {
        PlaceDialog(
            title = R.string.settings_place,
            why = R.string.settings_place_why,
            nothing = R.string.settings_place_phone,
            chosen = place,
            onChoose = {
                onPlace(it)
                placeOpen = false
            },
            onDismiss = { placeOpen = false },
        )
    }

    if (secondOpen) {
        PlaceDialog(
            title = R.string.settings_second,
            why = R.string.settings_second_why,
            nothing = R.string.settings_second_none,
            chosen = second,
            onChoose = {
                onSecond(it)
                secondOpen = false
            },
            onDismiss = { secondOpen = false },
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
            OutlinedButtonMMD(
                onClick = { unitsOpen = false },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { TextMMD(text = stringResource(R.string.settings_cancel), style = MaterialTheme.typography.bodySmall) }
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
            .heightIn(min = 48.dp)
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
 * why a phone needs telling where it is. The second place is chosen the same way, with
 * "none" where the first has the phone.
 */
@Composable
private fun PlaceDialog(
    title: Int,
    why: Int,
    nothing: Int,
    chosen: Place?,
    onChoose: (Place?) -> Unit,
    onDismiss: () -> Unit,
) {
    var typed by rememberSaveable { mutableStateOf("") }
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
        TextMMD(text = stringResource(title), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(14.dp))
        TextMMD(text = stringResource(why), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(10.dp))
        Choice(stringResource(nothing), chosen = chosen == null) { onChoose(null) }
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
                // Never greyed out: with nothing typed it simply waits for something to look up.
                onClick = { look() },
                modifier = Modifier.weight(1f).height(48.dp),
            ) { TextMMD(text = stringResource(R.string.settings_place_search), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** One place to choose. The one in use is bold, the house's only emphasis. */
@Composable
private fun Choice(label: String, chosen: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        TextMMD(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
        )
    }
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
    // Kept through a turn of the phone, which builds the screen again; the key is not, being a secret.
    var kind by rememberSaveable { mutableStateOf(source.kind()) }
    var address by rememberSaveable {
        mutableStateOf(
            when (source) {
                is Source.Ecowitt -> source.address
                is Source.Davis -> source.address
                else -> ""
            },
        )
    }
    var stationId by rememberSaveable { mutableStateOf((source as? Source.Underground)?.stationId.orEmpty()) }
    var apiKey by remember { mutableStateOf((source as? Source.Underground)?.apiKey.orEmpty()) }

    // An address that is plainly an internet one is refused here, not at the first fetch, where
    // all anyone sees is "the station is not answering".
    val publicAddress = (kind == Kind.ECOWITT || kind == Kind.DAVIS) && isPublicIpAddress(address)
    val chosen: Source? = when {
        publicAddress -> null
        else -> when (kind) {
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
    }
    val save = { chosen?.let(onSave); Unit }

    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = stringResource(R.string.settings_station), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        // Five kinds in 48 dp rows, a note and two fields are more than a 4.3" panel holds over the
        // buttons: the body pages on whole rows and the buttons stay where they are.
        val room = (LocalConfiguration.current.screenHeightDp - 190).coerceIn(140, 520).dp
        PagedColumn(Modifier.fillMaxWidth().height(room)) {
            Kind.entries.forEach { k ->
                item { Choice(stringResource(k.label), chosen = k == kind) { kind = k } }
            }
            item { Spacer(Modifier.height(8.dp)) }
            when (kind) {
                Kind.NONE -> Unit
                Kind.TEMPEST -> item { Note(R.string.station_tempest_note) }
                Kind.ECOWITT, Kind.DAVIS -> {
                    item { Note(if (kind == Kind.ECOWITT) R.string.station_ecowitt_note else R.string.station_davis_note) }
                    item { Field(address, { address = it }, KeyboardType.Uri, last = true, onDone = save) }
                    if (publicAddress) item { Note(R.string.station_address_public) }
                }
                Kind.UNDERGROUND -> {
                    item { Note(R.string.station_wu_note) }
                    item {
                        Column {
                            TextMMD(text = stringResource(R.string.station_wu_id), style = MaterialTheme.typography.labelSmall)
                            Field(stationId, { stationId = it }, KeyboardType.Ascii, last = false, onDone = save)
                        }
                    }
                    item {
                        Column(Modifier.padding(top = 4.dp)) {
                            TextMMD(text = stringResource(R.string.station_wu_key), style = MaterialTheme.typography.labelSmall)
                            Field(apiKey, { apiKey = it }, KeyboardType.Password, last = true, onDone = save, secret = true)
                        }
                    }
                }
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
private fun Field(value: String, onChange: (String) -> Unit, type: KeyboardType, last: Boolean, onDone: () -> Unit, secret: Boolean = false) {
    TextFieldMMD(
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
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

/**
 * Whether a typed station address is an IPv4 number that is not on the home network. A name is
 * not judged here (it takes a lookup); only the plain case, so it can be said when saving.
 */
internal fun isPublicIpAddress(typed: String): Boolean {
    val host = typed.trim().substringAfter("://").substringBefore('/').substringBefore(':')
    val parts = host.split('.')
    if (parts.size != 4 || parts.any { it.isEmpty() || it.length > 3 || !it.all(Char::isDigit) }) return false
    val bytes = parts.map { it.toInt() }
    if (bytes.any { it > 255 }) return false
    return !com.wanderwildwood.soramoyo.net.LocalHttp.isLocal(ByteArray(4) { bytes[it].toByte() })
}
