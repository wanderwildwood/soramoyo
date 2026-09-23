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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.lazy.LazyColumnMMD
import com.mudita.mmd.components.text.TextMMD
import com.mudita.mmd.components.text_field.TextFieldMMD
import com.mudita.mmd.components.top_app_bar.TopAppBarMMD
import com.wanderwildwood.soramoyo.R

/**
 * The one thing there is to set: where the station is.
 *
 * Units are not here on purpose. The gateway already knows how its owner counts, and a
 * second place to say it is a second place for the two to disagree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    address: String,
    onClose: () -> Unit,
    onAddress: (String) -> Unit,
) {
    var aboutOpen by remember { mutableStateOf(false) }
    var addressOpen by remember { mutableStateOf(false) }

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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { addressOpen = true }
                        .padding(vertical = 14.dp),
                ) {
                    TextMMD(text = stringResource(R.string.settings_station), style = MaterialTheme.typography.bodyMedium)
                    TextMMD(
                        text = address.ifEmpty { stringResource(R.string.settings_not_set) },
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }

    if (aboutOpen) AboutDialog(onDismiss = { aboutOpen = false })

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
