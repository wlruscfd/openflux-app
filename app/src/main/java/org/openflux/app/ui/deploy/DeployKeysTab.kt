package org.openflux.app.ui.deploy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.openflux.app.R
import org.openflux.app.data.AdminKey

// One LazyColumn for the whole tab: two stacked fillMaxSize() containers both claim full height and hide content.
@Composable
fun DeployKeysTab(viewModel: DeployServerDetailViewModel) {
    val keys by viewModel.keys.collectAsState()
    var label by remember { mutableStateOf("") }
    var transport by remember { mutableStateOf("yandex") }
    var docUrl by remember { mutableStateOf("") }
    var trafficLimitGb by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<AdminKey?>(null) }
    var editLabel by remember { mutableStateOf("") }
    var editTransport by remember { mutableStateOf("yandex") }
    var editDocUrl by remember { mutableStateOf("") }
    var editLimitGb by remember { mutableStateOf("") }
    var editEnabled by remember { mutableStateOf(true) }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.deploy_keys_new_header), style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text(stringResource(R.string.deploy_keys_label)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                    ) {
                        FilterChip(
                            selected = transport == "yandex",
                            onClick = { transport = "yandex" },
                            label = { Text(stringResource(R.string.profile_edit_transport_yandex)) },
                        )
                        FilterChip(
                            selected = transport == "volga",
                            onClick = { transport = "volga" },
                            label = { Text(stringResource(R.string.profile_edit_transport_volga)) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        FilterChip(
                            selected = transport == "mailru",
                            onClick = { transport = "mailru" },
                            label = { Text(stringResource(R.string.profile_edit_transport_mailru)) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        FilterChip(
                            selected = transport == "boards",
                            onClick = { transport = "boards" },
                            label = { Text(stringResource(R.string.profile_edit_transport_boards)) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        FilterChip(
                            selected = transport == "mts",
                            onClick = { transport = "mts" },
                            label = { Text(stringResource(R.string.profile_edit_transport_mts)) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    OutlinedTextField(
                        value = docUrl,
                        onValueChange = { docUrl = it },
                        label = { Text(stringResource(docUrlLabelRes(transport))) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = trafficLimitGb,
                        onValueChange = { trafficLimitGb = it },
                        label = { Text(stringResource(R.string.deploy_keys_traffic_limit_gb)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Button(
                        onClick = {
                            viewModel.createKey(label, docUrl, trafficLimitGb.toDoubleOrNull(), "", transport)
                            label = ""
                            transport = "yandex"
                            docUrl = ""
                            trafficLimitGb = ""
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    ) { Text(stringResource(R.string.deploy_keys_create)) }
                }
            }

            Text(
                stringResource(R.string.deploy_keys_section),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            if (keys.isEmpty()) {
                Text(stringResource(R.string.deploy_keys_empty), modifier = Modifier.padding(16.dp))
            }
        }

        items(keys, key = { it.id }) { key ->
            Column(Modifier.animateItem()) {
                KeyRow(
                    key = key,
                    onToggleEnabled = { viewModel.setKeyEnabled(key.id, !key.enabled) },
                    onRotateToken = { viewModel.rotateKeyToken(key.id) },
                    onEdit = {
                        if (editing?.id == key.id) {
                            editing = null
                        } else {
                            editing = key
                            editLabel = key.label
                            editTransport = key.transport
                            editDocUrl = key.docUrl
                            editLimitGb = if (key.trafficLimitBytes != null) {
                                "%.1f".format(key.trafficLimitBytes.toDouble() / 1024 / 1024 / 1024)
                            } else {
                                ""
                            }
                            editEnabled = key.enabled
                        }
                    },
                    onDelete = { viewModel.deleteKey(key.id) },
                )
                AnimatedVisibility(
                    visible = editing?.id == key.id,
                    enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
                ) {
                    val target = editing
                    if (target != null) {
                        Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(R.string.deploy_keys_edit_title),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                                OutlinedTextField(
                                    value = editLabel,
                                    onValueChange = { editLabel = it },
                                    label = { Text(stringResource(R.string.deploy_keys_label)) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                                ) {
                                    listOf("yandex", "volga", "mailru", "boards", "mts").forEach { option ->
                                        FilterChip(
                                            selected = editTransport == option,
                                            onClick = { editTransport = option },
                                            label = { Text(option) },
                                            modifier = Modifier.padding(end = 8.dp),
                                        )
                                    }
                                }
                                OutlinedTextField(
                                    value = editDocUrl,
                                    onValueChange = { editDocUrl = it },
                                    label = { Text(stringResource(docUrlLabelRes(editTransport))) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                                OutlinedTextField(
                                    value = editLimitGb,
                                    onValueChange = { editLimitGb = it },
                                    label = { Text(stringResource(R.string.deploy_keys_traffic_limit_gb)) },
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(stringResource(R.string.deploy_keys_edit_enabled))
                                    androidx.compose.material3.Switch(checked = editEnabled, onCheckedChange = { editEnabled = it })
                                }
                                Text(
                                    stringResource(R.string.deploy_keys_edit_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(onClick = { editing = null }, modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.deploy_keys_edit_cancel))
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.updateKey(
                                                target.id,
                                                editLabel,
                                                editTransport,
                                                editDocUrl,
                                                editLimitGb.toDoubleOrNull(),
                                                editEnabled,
                                            )
                                            editing = null
                                        },
                                        modifier = Modifier.weight(1f),
                                    ) { Text(stringResource(R.string.deploy_keys_saved)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun docUrlLabelRes(transport: String): Int = when (transport) {
    "mts" -> R.string.deploy_keys_doc_url_mts
    "boards" -> R.string.deploy_keys_doc_url_boards
    "mailru" -> R.string.deploy_keys_doc_url_mailru
    else -> R.string.deploy_keys_doc_url_yandex
}

@Composable
private fun KeyRow(
    key: AdminKey,
    onToggleEnabled: () -> Unit,
    onRotateToken: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(key.label.ifBlank { "(no label)" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    key.transport + " · " + formatBytes(key.bytesSentTotal + key.bytesReceivedTotal) + " used",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            OutlinedButton(onClick = onToggleEnabled) {
                Text(stringResource(if (key.enabled) R.string.deploy_keys_disable else R.string.deploy_keys_enable))
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.deploy_keys_edit))
            }
            IconButton(onClick = onRotateToken) {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.deploy_keys_rotate_token))
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.deploy_keys_delete))
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}
