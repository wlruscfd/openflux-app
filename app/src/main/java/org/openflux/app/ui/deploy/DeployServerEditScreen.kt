package org.openflux.app.ui.deploy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

private val SECTION_SPACING = 16.dp

@Composable
private fun FormSection(title: String? = null, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(top = SECTION_SPACING)) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            content()
        }
    }
}

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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onDone, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.deploy_edit_cancel))
                    }
                    Button(onClick = { viewModel.save(current, onDone) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.deploy_edit_save))
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        ) {
            OutlinedTextField(
                value = current.name,
                onValueChange = { server = current.copy(name = it) },
                label = { Text(stringResource(R.string.deploy_edit_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )

            FormSection(title = stringResource(R.string.deploy_edit_ssh_section)) {
                OutlinedTextField(
                    value = current.host,
                    onValueChange = { server = current.copy(host = it) },
                    label = { Text(stringResource(R.string.deploy_edit_host)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
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
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )

                if (current.knownHostKeyFingerprint.isNotBlank()) {
                    Text(
                        stringResource(R.string.deploy_edit_host_key_saved, current.knownHostKeyFingerprint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = { server = current.copy(knownHostKeyFingerprint = "") }) {
                        Text(stringResource(R.string.deploy_edit_forget_host_key))
                    }
                }

                Text(stringResource(R.string.deploy_edit_auth_method), modifier = Modifier.padding(top = 16.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = current.authMethod == SshAuthMethod.PASSWORD,
                        onClick = { server = current.copy(authMethod = SshAuthMethod.PASSWORD) },
                        label = { Text(stringResource(R.string.deploy_edit_auth_password)) },
                    )
                    FilterChip(
                        selected = current.authMethod == SshAuthMethod.KEY,
                        onClick = { server = current.copy(authMethod = SshAuthMethod.KEY) },
                        label = { Text(stringResource(R.string.deploy_edit_auth_key)) },
                    )
                }
                AnimatedVisibility(
                    visible = current.authMethod == SshAuthMethod.PASSWORD,
                    enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
                ) {
                    OutlinedTextField(
                        value = current.sshPassword,
                        onValueChange = { server = current.copy(sshPassword = it) },
                        label = { Text(stringResource(R.string.deploy_edit_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                }
                AnimatedVisibility(
                    visible = current.authMethod == SshAuthMethod.KEY,
                    enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
                ) {
                    Column {
                        OutlinedTextField(
                            value = current.sshPrivateKeyPem,
                            onValueChange = { server = current.copy(sshPrivateKeyPem = it) },
                            label = { Text(stringResource(R.string.deploy_edit_private_key)) },
                            minLines = 3,
                            maxLines = 6,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                        OutlinedTextField(
                            value = current.sshPassphrase,
                            onValueChange = { server = current.copy(sshPassphrase = it) },
                            label = { Text(stringResource(R.string.deploy_edit_passphrase)) },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                }
            }

            FormSection(title = stringResource(R.string.deploy_edit_install_section)) {
                OutlinedTextField(
                    value = current.repoUrl,
                    onValueChange = { server = current.copy(repoUrl = it) },
                    label = { Text(stringResource(R.string.deploy_edit_repo_url)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = current.gitRef,
                    onValueChange = { server = current.copy(gitRef = it) },
                    label = { Text(stringResource(R.string.deploy_edit_git_ref)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }

            FormSection(title = stringResource(R.string.deploy_edit_tls_mode)) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = current.tlsMode == TlsMode.DOMAIN,
                        onClick = { server = current.copy(tlsMode = TlsMode.DOMAIN) },
                        label = { Text(stringResource(R.string.deploy_edit_tls_domain)) },
                    )
                    FilterChip(
                        selected = current.tlsMode == TlsMode.IP,
                        onClick = { server = current.copy(tlsMode = TlsMode.IP) },
                        label = { Text(stringResource(R.string.deploy_edit_tls_ip)) },
                    )
                    FilterChip(
                        selected = current.tlsMode == TlsMode.HTTP,
                        onClick = { server = current.copy(tlsMode = TlsMode.HTTP) },
                        label = { Text(stringResource(R.string.deploy_edit_tls_http)) },
                    )
                }
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
                    modifier = Modifier.padding(top = 8.dp),
                )
                AnimatedVisibility(
                    visible = current.tlsMode == TlsMode.DOMAIN,
                    enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
                ) {
                    Column {
                        OutlinedTextField(
                            value = current.domain,
                            onValueChange = { server = current.copy(domain = it) },
                            label = { Text(stringResource(R.string.deploy_edit_domain)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                        OutlinedTextField(
                            value = current.email,
                            onValueChange = { server = current.copy(email = it) },
                            label = { Text(stringResource(R.string.deploy_edit_email)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                }
                AnimatedVisibility(
                    visible = current.tlsMode == TlsMode.HTTP,
                    enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
                ) {
                    Text(
                        stringResource(R.string.deploy_edit_tls_http_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }

            FormSection {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                AnimatedVisibility(
                    visible = current.registerNode,
                    enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
                ) {
                    Column {
                        OutlinedTextField(
                            value = current.nodeName,
                            onValueChange = { server = current.copy(nodeName = it) },
                            label = { Text(stringResource(R.string.deploy_edit_node_name)) },
                            singleLine = true,
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
                }
            }

            if (serverId != null) {
                OutlinedButton(
                    onClick = { viewModel.delete(serverId, onDone) },
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                ) {
                    Text(stringResource(R.string.deploy_edit_delete))
                }
            }
        }
    }
}
