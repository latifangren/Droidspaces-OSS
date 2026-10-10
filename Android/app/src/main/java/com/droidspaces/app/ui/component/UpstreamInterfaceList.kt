package com.droidspaces.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.droidspaces.app.ui.component.DsDialog
import com.droidspaces.app.R
import com.droidspaces.app.util.ContainerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun UpstreamInterfaceList(
    upstreamInterfaces: List<String>,
    onInterfacesChange: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showUpstreamDialog by remember { mutableStateOf(false) }
    var availableUpstreams by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        availableUpstreams = ContainerManager.listUpstreamInterfaces()
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Selected interfaces (drag the handle to reorder; height is bounded since
        // this LazyColumn sits inside the form's own scrolling Column)
        val lazyListState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
            onInterfacesChange(upstreamInterfaces.toMutableList().apply { add(to.index, removeAt(from.index)) })
        }
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(upstreamInterfaces, key = { it }) { iface ->
                ReorderableItem(reorderableState, key = iface) {
                    ListEntryCard(text = iface, onDelete = { onInterfacesChange(upstreamInterfaces - iface) }) {
                        Icon(
                            Icons.Default.DragHandle,
                            contentDescription = null,
                            modifier = Modifier.draggableHandle().padding(end = 12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Add Button
        if (upstreamInterfaces.size < 8) {
            AddEntryButton(context.getString(R.string.add_upstream_interface), onClick = { showUpstreamDialog = true })
        }
    }

    if (showUpstreamDialog) {
        InterfacePickerDialog(
            title = context.getString(R.string.add_upstream_interface),
            confirmLabel = context.getString(R.string.add),
            manualLabel = context.getString(R.string.enter_manually),
            fieldLabel = context.getString(R.string.interface_name_hint),
            available = availableUpstreams,
            selectedInterfaces = upstreamInterfaces,
            onDismiss = { showUpstreamDialog = false },
            onRefresh = {
                val newOnes = ContainerManager.listUpstreamInterfaces()
                availableUpstreams = newOnes
            },
            onAdd = { iface ->
                if (!upstreamInterfaces.contains(iface)) {
                    onInterfacesChange(upstreamInterfaces + iface)
                }
                showUpstreamDialog = false
            }
        )
    }
}

/**
 * Pick a host interface from the ones present now, or type one. Shared by the
 * upstream list and macvlan mode, which word [manualLabel] and [fieldLabel] for
 * what each accepts (upstream takes wildcards, macvlan one exact name).
 * [inputFilter] runs on every keystroke of the typed name; [selectedInterfaces]
 * are shown but cannot be picked again.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InterfacePickerDialog(
    title: String,
    confirmLabel: String,
    manualLabel: String,
    fieldLabel: String,
    available: List<String>,
    selectedInterfaces: List<String>,
    onDismiss: () -> Unit,
    onRefresh: suspend () -> Unit,
    onAdd: (String) -> Unit,
    inputFilter: (String) -> String = { it }
) {
    val context = LocalContext.current
    var customIface by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    val rotation by animateFloatAsState(
        targetValue = if (isRefreshing) 360f else 0f,
        animationSpec = if (isRefreshing) {
            tween(durationMillis = 600, easing = LinearEasing)
        } else {
            tween(durationMillis = 0, easing = LinearEasing)
        },
        label = "refresh_rotation"
    )

    DsDialog(
        onDismiss = onDismiss,
        modifier = Modifier.heightIn(max = 420.dp),
        footer = {
            DialogFooterRow(
                dismissLabel = context.getString(R.string.cancel),
                confirmLabel = confirmLabel,
                onDismiss = onDismiss,
                onConfirm = { onAdd(customIface.trim()) },
                confirmEnabled = customIface.isNotBlank() && selectedInterfaces.size < 8,
            )
        }
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = {
                        if (!isRefreshing) {
                            isRefreshing = true
                            scope.launch {
                                val startTime = System.currentTimeMillis()
                                onRefresh()
                                val elapsed = System.currentTimeMillis() - startTime
                                if (elapsed < 600L) delay(600L - elapsed)
                                isRefreshing = false
                            }
                        }
                    },
                    enabled = !isRefreshing
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation }
                    )
                }
            }

            if (available.isNotEmpty()) {
                Text(context.getString(R.string.available_system_interfaces), style = MaterialTheme.typography.labelMedium)
                Box(modifier = Modifier.fillMaxWidth()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        available.forEach { iface ->
                            OutlinedButton(
                                onClick = { onAdd(iface) },
                                enabled = !selectedInterfaces.contains(iface),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(iface)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(manualLabel, style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = customIface,
                onValueChange = { customIface = inputFilter(it) },
                label = { Text(fieldLabel) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = DsTextFieldDefaults.colors()
            )
    }
}
