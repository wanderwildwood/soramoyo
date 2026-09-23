package com.wanderwildwood.soramoyo.ui

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
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/**
 * The two things there are to set: where the station is, and where the forecast and radar
 * are for.
 *
 * Units are not here on purpose. The gateway already knows how its owner counts, and a
 * second place to say it is a second place for the two to disagree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    address: String,
    place: Place?,
    onClose: () -> Unit,
    onAddress: (String) -> Unit,
    onPlace: (Place?) -> Unit,
) {
    var aboutOpen by remember { mutableStateOf(false) }
    var addressOpen by remember { mutableStateOf(false) }
    var placeOpen by remember { mutableStateOf(false) }

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
                    value = address.ifEmpty { stringResource(R.string.settings_not_set) },
                    onClick = { addressOpen = true },
                )
            }
            item {
                Setting(
                    title = stringResource(R.string.settings_place),
                    value = place?.label ?: stringResource(R.string.settings_place_phone),
                    onClick = { placeOpen = true },
                )
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

    if (addressOpen) {
        AddressDialog(
            address = address,
            onSave = {
                onAddress(it)
                addressOpen = false
            },
            onDismiss = { addressOpen = false },
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
private fun Choice(label: String, chosen: Boolean = false, onClick: () -> Unit) {
    TextMMD(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
    )
}

/**
 * The gateway's address on the local network.
 *
 * This is the one row whose meaning its label cannot carry — a stranger does not know which
 * address is meant or where to find it — so the dialog says, once, in the place it is asked.
 */
@Composable
private fun AddressDialog(address: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf(address) }

    EInkDialog(onDismiss = onDismiss) {
        TextMMD(text = stringResource(R.string.settings_station), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(14.dp))
        TextMMD(text = stringResource(R.string.settings_station_where), style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(14.dp))
        TextFieldMMD(
            value = typed,
            onValueChange = { typed = it },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButtonMMD(
                onClick = onDismiss,
                modifier = Modifier.weight(1f).height(48.dp),
            ) { TextMMD(text = stringResource(R.string.settings_cancel), style = MaterialTheme.typography.bodySmall) }
            OutlinedButtonMMD(
                onClick = { onSave(typed) },
                modifier = Modifier.weight(1f).height(48.dp),
            ) { TextMMD(text = stringResource(R.string.settings_save), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
