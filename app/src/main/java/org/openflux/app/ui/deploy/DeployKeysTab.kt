package org.openflux.app.ui.deploy

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
    var ownerRef by remember { mutableStateOf("") }

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
                        label = { Text(stringResource(R.string.deploy_keys_doc_url)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = trafficLimitGb,
                        onValueChange = { trafficLimitGb = it },
                        label = { Text(stringResource(R.string.deploy_keys_traffic_limit_gb)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    OutlinedTextField(
                        value = ownerRef,
                        onValueChange = { ownerRef = it },
                        label = { Text(stringResource(R.string.deploy_keys_owner_ref)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                    Button(
                        onClick = {
                            viewModel.createKey(label, docUrl, trafficLimitGb.toDoubleOrNull(), ownerRef, transport)
                            label = ""
                            transport = "yandex"
                            docUrl = ""
                            trafficLimitGb = ""
                            ownerRef = ""
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
            KeyRow(
                key = key,
                onToggleEnabled = { viewModel.setKeyEnabled(key.id, !key.enabled) },
                onRotateToken = { viewModel.rotateKeyToken(key.id) },
                onDelete = { viewModel.deleteKey(key.id) },
            )
        }
    }
}

@Composable
private fun KeyRow(key: AdminKey, onToggleEnabled: () -> Unit, onRotateToken: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(key.label.ifBlank { "(no label)" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatBytes(key.bytesSentTotal + key.bytesReceivedTotal) + " used")
            }
            OutlinedButton(onClick = onToggleEnabled) {
                Text(stringResource(if (key.enabled) R.string.deploy_keys_disable else R.string.deploy_keys_enable))
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
