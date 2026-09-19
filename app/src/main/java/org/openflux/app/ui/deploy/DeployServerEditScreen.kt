package org.openflux.app.ui.deploy

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.launch
import org.openflux.app.LocalOpenFluxApp
import org.openflux.app.R
import org.openflux.app.data.DeployServer
import org.openflux.app.data.DeployServerRepository
import org.openflux.app.data.SshAuthMethod
import org.openflux.app.data.TlsMode
import org.openflux.app.ui.IntTextField

class DeployServerEditViewModel(private val repository: DeployServerRepository) : ViewModel() {
    fun loadOrNew(id: String?, onLoaded: (DeployServer) -> Unit) {
        viewModelScope.launch {
            onLoaded(id?.let { repository.getById(it) } ?: DeployServer())
        }
    }

    fun save(server: DeployServer, onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.save(server)
            onSaved()
        }
    }

    fun delete(id: String, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.delete(id)
            onDone()
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DeployServerEditScreen(serverId: String?, onDone: () -> Unit) {
    val app = LocalOpenFluxApp.current
    val viewModel: DeployServerEditViewModel = viewModel(
        factory = viewModelFactory { initializer { DeployServerEditViewModel(app.deployServerRepository) } },
    )

    var server by remember { mutableStateOf<DeployServer?>(null) }
    LaunchedEffect(serverId) { viewModel.loadOrNew(serverId) { server = it } }
    val current = server ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (serverId == null) R.string.deploy_edit_new_title else R.string.deploy_edit_title,
                        ),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            OutlinedTextField(
                value = current.name,
                onValueChange = { server = current.copy(name = it) },
                label = { Text(stringResource(R.string.deploy_edit_name)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.deploy_edit_ssh_section), modifier = Modifier.padding(top = 20.dp))
            OutlinedTextField(
                value = current.host,
                onValueChange = { server = current.copy(host = it) },
                label = { Text(stringResource(R.string.deploy_edit_host)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            IntTextField(
                value = current.port,
                onValueChange = { server = current.copy(port = it) },
                label = { Text(stringResource(R.string.deploy_edit_port)) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            OutlinedTextField(
                value = current.username,
                onValueChange = { server = current.copy(username = it) },
                label = { Text(stringResource(R.string.deploy_edit_username)) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            Text(stringResource(R.string.deploy_edit_auth_method), modifier = Modifier.padding(top = 12.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                FilterChip(
                    selected = current.authMethod == SshAuthMethod.PASSWORD,
                    onClick = { server = current.copy(authMethod = SshAuthMethod.PASSWORD) },
                    label = { Text(stringResource(R.string.deploy_edit_auth_password)) },
                )
                FilterChip(
                    selected = current.authMethod == SshAuthMethod.KEY,
                    onClick = { server = current.copy(authMethod = SshAuthMethod.KEY) },
                    label = { Text(stringResource(R.string.deploy_edit_auth_key)) },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (current.authMethod == SshAuthMethod.PASSWORD) {
                OutlinedTextField(
                    value = current.sshPassword,
                    onValueChange = { server = current.copy(sshPassword = it) },
                    label = { Text(stringResource(R.string.deploy_edit_password)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            } else {
                OutlinedTextField(
                    value = current.sshPrivateKeyPem,
                    onValueChange = { server = current.copy(sshPrivateKeyPem = it) },
                    label = { Text(stringResource(R.string.deploy_edit_private_key)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = current.sshPassphrase,
                    onValueChange = { server = current.copy(sshPassphrase = it) },
                    label = { Text(stringResource(R.string.deploy_edit_passphrase)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }

            Text(stringResource(R.string.deploy_edit_install_section), modifier = Modifier.padding(top = 20.dp))
            OutlinedTextField(
                value = current.repoUrl,
                onValueChange = { server = current.copy(repoUrl = it) },
                label = { Text(stringResource(R.string.deploy_edit_repo_url)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = current.gitRef,
                onValueChange = { server = current.copy(gitRef = it) },
                label = { Text(stringResource(R.string.deploy_edit_git_ref)) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            Text(stringResource(R.string.deploy_edit_tls_mode), modifier = Modifier.padding(top = 12.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                FilterChip(
                    selected = current.tlsMode == TlsMode.DOMAIN,
                    onClick = { server = current.copy(tlsMode = TlsMode.DOMAIN) },
                    label = { Text(stringResource(R.string.deploy_edit_tls_domain)) },
                )
                FilterChip(
                    selected = current.tlsMode == TlsMode.IP,
                    onClick = { server = current.copy(tlsMode = TlsMode.IP) },
                    label = { Text(stringResource(R.string.deploy_edit_tls_ip)) },
                    modifier = Modifier.padding(start = 8.dp),
                )
                FilterChip(
                    selected = current.tlsMode == TlsMode.HTTP,
                    onClick = { server = current.copy(tlsMode = TlsMode.HTTP) },
                    label = { Text(stringResource(R.string.deploy_edit_tls_http)) },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            // The matching port must be open in the VPS's firewall/cloud security group, or nothing is reachable.
            Text(
                stringResource(
                    if (current.tlsMode == TlsMode.HTTP) {
                        R.string.deploy_edit_port_hint_http
                    } else {
                        R.string.deploy_edit_port_hint_tls
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (current.tlsMode == TlsMode.DOMAIN) {
                OutlinedTextField(
                    value = current.domain,
                    onValueChange = { server = current.copy(domain = it) },
                    label = { Text(stringResource(R.string.deploy_edit_domain)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = current.email,
                    onValueChange = { server = current.copy(email = it) },
                    label = { Text(stringResource(R.string.deploy_edit_email)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
            if (current.tlsMode == TlsMode.HTTP) {
                Text(
                    stringResource(R.string.deploy_edit_tls_http_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.deploy_edit_register_node),
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Switch(
                    checked = current.registerNode,
                    onCheckedChange = { server = current.copy(registerNode = it) },
                )
            }
            if (current.registerNode) {
                OutlinedTextField(
                    value = current.nodeName,
                    onValueChange = { server = current.copy(nodeName = it) },
                    label = { Text(stringResource(R.string.deploy_edit_node_name)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                IntTextField(
                    value = current.nodeMaxKeys,
                    onValueChange = { server = current.copy(nodeMaxKeys = it) },
                    label = { Text(stringResource(R.string.deploy_edit_node_max_keys)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.deploy_edit_run_node_here),
                        modifier = Modifier.weight(1f).padding(end = 12.dp),
                    )
                    Switch(
                        checked = current.runNodeHere,
                        onCheckedChange = { server = current.copy(runNodeHere = it) },
                    )
                }
            }

            Row(modifier = Modifier.padding(top = 24.dp)) {
                Button(onClick = { viewModel.save(current, onDone) }) {
                    Text(stringResource(R.string.deploy_edit_save))
                }
                OutlinedButton(onClick = onDone, modifier = Modifier.padding(start = 12.dp)) {
                    Text(stringResource(R.string.deploy_edit_cancel))
                }
            }
            if (serverId != null) {
                OutlinedButton(
                    onClick = { viewModel.delete(serverId, onDone) },
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Text(stringResource(R.string.deploy_edit_delete))
                }
            }
        }
    }
}
