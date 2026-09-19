package org.openflux.app.ui.deploy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import org.openflux.app.R
import org.openflux.app.data.AdminIngestToken

// A deployed server always has exactly one exit node, so this shows that single node, not a general nodes list.
@Composable
fun DeploySettingsTab(viewModel: DeployServerDetailViewModel) {
    val server by viewModel.server.collectAsState()
    val nodes by viewModel.nodes.collectAsState()
    val ingestTokens by viewModel.ingestTokens.collectAsState()
    var ingestLabel by remember { mutableStateOf("") }

    val node = server?.let { s -> nodes.firstOrNull { it.name == s.nodeName } }

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Card(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.deploy_settings_node_section), style = MaterialTheme.typography.titleMedium)
                    if (node != null) {
                        Text(
                            stringResource(R.string.deploy_settings_node_status, nodeStatusLabel(node.status)),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Text(stringResource(R.string.deploy_settings_node_max_keys, node.maxKeys))
                        Text(
                            stringResource(
                                R.string.deploy_settings_node_heartbeat,
                                formatHeartbeat(node.lastHeartbeatAt)
                                    ?: stringResource(R.string.deploy_settings_node_never),
                            ),
                        )
                        OutlinedButton(
                            onClick = { viewModel.rotateNodeToken(node.id) },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        ) { Text(stringResource(R.string.deploy_settings_rotate_token)) }
                    } else {
                        Text(
                            stringResource(R.string.deploy_settings_node_missing),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        Button(
                            onClick = { server?.let { viewModel.createNode(it.nodeName, it.nodeMaxKeys) } },
                            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        ) { Text(stringResource(R.string.deploy_settings_register_node)) }
                    }
                }
            }

            Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.deploy_settings_ingest_section), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.deploy_settings_ingest_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedTextField(
                        value = ingestLabel,
                        onValueChange = { ingestLabel = it },
                        label = { Text(stringResource(R.string.deploy_settings_ingest_label)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Button(
                        onClick = {
                            viewModel.createIngestToken(ingestLabel)
                            ingestLabel = ""
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    ) { Text(stringResource(R.string.deploy_settings_ingest_create)) }
                }
            }

            if (ingestTokens.isNotEmpty()) {
                Text(
                    stringResource(R.string.deploy_settings_ingest_list_header),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                )
            } else {
                Text(
                    stringResource(R.string.deploy_settings_ingest_empty),
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        items(ingestTokens, key = { it.id }) { token ->
            IngestTokenRow(
                token = token,
                onToggleEnabled = { viewModel.setIngestTokenEnabled(token.id, !token.enabled) },
            )
        }
    }
}

@Composable
private fun IngestTokenRow(token: AdminIngestToken, onToggleEnabled: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                token.label.ifBlank { "—" },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onToggleEnabled) {
                Text(
                    stringResource(
                        if (token.enabled) {
                            R.string.deploy_settings_ingest_disable
                        } else {
                            R.string.deploy_settings_ingest_enable
                        },
                    ),
                )
            }
        }
    }
}

private val heartbeatFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

private fun formatHeartbeat(raw: String?): String? {
    if (raw == null) return null
    return runCatching { OffsetDateTime.parse(raw).format(heartbeatFormatter) }.getOrDefault(raw)
}

@Composable
private fun nodeStatusLabel(status: String): String = when (status) {
    "active" -> stringResource(R.string.deploy_settings_node_status_active)
    "disabled" -> stringResource(R.string.deploy_settings_node_status_disabled)
    else -> status
}
