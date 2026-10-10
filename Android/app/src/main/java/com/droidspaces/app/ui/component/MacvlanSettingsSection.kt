package com.droidspaces.app.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.droidspaces.app.R
import com.droidspaces.app.util.ContainerManager
import com.droidspaces.app.util.MacvlanErrors

/** The macvlan modes the backend accepts, default first, with what each does. */
private val MACVLAN_MODES = listOf("bridge", "private", "vepa", "passthru")

private fun modeName(mode: String) = when (mode) {
    "private" -> R.string.macvlan_mode_private
    "vepa" -> R.string.macvlan_mode_vepa
    "passthru" -> R.string.macvlan_mode_passthru
    else -> R.string.macvlan_mode_bridge
}

private fun modeDescription(mode: String) = when (mode) {
    "private" -> R.string.macvlan_mode_private_desc
    "vepa" -> R.string.macvlan_mode_vepa_desc
    "passthru" -> R.string.macvlan_mode_passthru_desc
    else -> R.string.macvlan_mode_bridge_desc
}

/**
 * Macvlan-mode settings, shared by the installer and edit-container screens: the
 * host interface the container's eth0 sits on, and the macvlan mode.
 *
 * The interface is picked the way an upstream interface is, with the same
 * dialog and the same entry card, from the ones present now or typed, since the
 * USB adapter may be unplugged while the container is being set up. A typed name
 * keeps only interface-name characters, because it goes into the config file as
 * a key=value line.
 */
@Composable
fun MacvlanSettingsSection(
    parent: String,
    mode: String,
    errors: MacvlanErrors,
    onChange: (parent: String, mode: String) -> Unit
) {
    val context = LocalContext.current
    var showPicker by remember { mutableStateOf(false) }
    var available by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { available = ContainerManager.listEthernetInterfaces() }
    val helper = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    // Laid out like the NAT section: header, description, the single-value
    // control, then the list-style setting as a bold title, its hint and its
    // card at the bottom.
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionHeader(text = context.getString(R.string.macvlan_settings))
        Text(
            text = context.getString(R.string.macvlan_settings_description),
            style = MaterialTheme.typography.bodySmall,
            color = helper
        )

        val selected = mode.ifBlank { "bridge" }
        DsDropdown(
            label = context.getString(R.string.macvlan_mode),
            selected = selected,
            options = MACVLAN_MODES,
            displayName = { context.getString(modeName(it)) },
            onSelect = { onChange(parent, it) },
            leadingIcon = Icons.Default.Tune,
            supportingText = context.getString(modeDescription(selected))
        )

        Text(
            text = context.getString(R.string.macvlan_parent),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = context.getString(R.string.macvlan_parent_hint),
            style = MaterialTheme.typography.bodySmall,
            color = helper
        )
        if (parent.isBlank()) {
            AddEntryButton(context.getString(R.string.macvlan_parent_choose), onClick = { showPicker = true })
        } else {
            ListEntryCard(text = parent, onDelete = { onChange("", mode) })
        }
        (errors.parent ?: errors.warning)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (errors.parent != null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 4.dp)
            )
        }

    }

    if (showPicker) {
        InterfacePickerDialog(
            title = context.getString(R.string.macvlan_parent),
            confirmLabel = context.getString(R.string.ok),
            manualLabel = context.getString(R.string.macvlan_enter_manually),
            fieldLabel = context.getString(R.string.macvlan_interface_name_hint),
            available = available,
            selectedInterfaces = emptyList(),
            onDismiss = { showPicker = false },
            onRefresh = { available = ContainerManager.listEthernetInterfaces() },
            onAdd = { iface ->
                onChange(iface, mode)
                showPicker = false
            },
            inputFilter = { input -> input.filter { it.isLetterOrDigit() || it in "_-." }.take(15) }
        )
    }
}
