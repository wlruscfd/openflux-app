package org.openflux.app.ui.deploy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.openflux.app.LocalOpenFluxApp
import org.openflux.app.OpenFluxApplication
import org.openflux.app.R
import org.openflux.app.data.AdminIngestToken
import org.openflux.app.data.AdminKey
import org.openflux.app.data.AdminNode
import org.openflux.app.data.ControlPlaneAdminClient
import org.openflux.app.data.DeployServer
import org.openflux.app.data.DeployServerRepository
import org.openflux.app.data.DeployStatus
import org.openflux.app.data.KeyToken
import org.openflux.app.deploy.DeployManager

/** text + whether it represents an error, shown as a dismissible banner. */
data class DetailMessage(val text: String, val isError: Boolean)

class DeployServerDetailViewModel(
    private val repository: DeployServerRepository,
    private val serverId: String,
    private val app: OpenFluxApplication,
) : ViewModel() {
    private val _server = MutableStateFlow<DeployServer?>(null)
    val server: StateFlow<DeployServer?> = _server

    private val _nodes = MutableStateFlow<List<AdminNode>>(emptyList())
    val nodes: StateFlow<List<AdminNode>> = _nodes

    private val _keys = MutableStateFlow<List<AdminKey>>(emptyList())
    val keys: StateFlow<List<AdminKey>> = _keys

    private val _ingestTokens = MutableStateFlow<List<AdminIngestToken>>(emptyList())
    val ingestTokens: StateFlow<List<AdminIngestToken>> = _ingestTokens

    private val _message = MutableStateFlow<DetailMessage?>(null)
    val message: StateFlow<DetailMessage?> = _message

    private var adminClient: ControlPlaneAdminClient? = null

    fun load() {
        viewModelScope.launch {
            val s = repository.getById(serverId) ?: return@launch
            _server.value = s
            adminClient = ControlPlaneAdminClient(s.baseUrl, s.adminToken)
            refreshAll()
        }
    }

    fun deployNow() {
        val s = _server.value ?: return
        DeployManager.deploy(repository, s)
    }

    fun clearMessage() {
        _message.value = null
    }

    fun refreshAll() {
        viewModelScope.launch(Dispatchers.IO) {
            val client = adminClient ?: return@launch
            runCatching { _nodes.value = client.listNodes() }
            runCatching { _keys.value = client.listKeys() }
            runCatching { _ingestTokens.value = client.listIngestTokens() }
        }
    }

    fun createNode(name: String, maxKeys: Int) = runAction { client ->
        val created = client.createNode(name, maxKeys)
        _message.value = DetailMessage(app.getString(R.string.deploy_settings_node_token_issued, created.token), isError = false)
        _nodes.value = client.listNodes()
    }

    fun rotateNodeToken(id: String) = runAction { client ->
        val token = client.rotateNodeToken(id)
        _message.value = DetailMessage(app.getString(R.string.deploy_settings_node_token_issued, token), isError = false)
    }

    fun createKey(label: String, docUrl: String, trafficLimitGb: Double?, ownerRef: String) = runAction { client ->
        val bytes = trafficLimitGb?.let { (it * 1024 * 1024 * 1024).toLong() }
        val created = client.createKey(label, docUrl, bytes, ownerRef)
        _message.value = DetailMessage(keyTokenMessage(R.string.deploy_keys_new_token, created), isError = false)
        _keys.value = client.listKeys()
    }

    fun setKeyEnabled(id: String, enabled: Boolean) = runAction { client ->
        client.setKeyEnabled(id, enabled)
        _keys.value = client.listKeys()
    }

    fun rotateKeyToken(id: String) = runAction { client ->
        val rotated = client.rotateKeyToken(id)
        _message.value = DetailMessage(keyTokenMessage(R.string.deploy_keys_new_token, rotated), isError = false)
        _keys.value = client.listKeys()
    }

    private fun keyTokenMessage(tokenLabelRes: Int, key: KeyToken): String {
        val base = app.getString(tokenLabelRes, key.token)
        return if (key.deepLink.isNullOrBlank()) base else base + "\n" + app.getString(R.string.deploy_keys_deep_link, key.deepLink)
    }

    fun deleteKey(id: String) = runAction { client ->
        client.deleteKey(id)
        _keys.value = client.listKeys()
    }

    fun createIngestToken(label: String) = runAction { client ->
        val created = client.createIngestToken(label)
        _message.value = DetailMessage(app.getString(R.string.deploy_settings_ingest_token_issued, created.token), isError = false)
        _ingestTokens.value = client.listIngestTokens()
    }

    fun setIngestTokenEnabled(id: String, enabled: Boolean) = runAction { client ->
        client.setIngestTokenEnabled(id, enabled)
        _ingestTokens.value = client.listIngestTokens()
    }

    private fun runAction(block: suspend (ControlPlaneAdminClient) -> Unit) {
        val client = adminClient ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { block(client) }
                .onFailure { _message.value = DetailMessage(it.message ?: app.getString(R.string.deploy_generic_error), isError = true) }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DeployServerDetailScreen(serverId: String, onEditServer: (String) -> Unit) {
    val app = LocalOpenFluxApp.current
    val viewModel: DeployServerDetailViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DeployServerDetailViewModel(app.deployServerRepository, serverId, app) }
        },
    )
    LaunchedEffect(serverId) { viewModel.load() }

    val server by viewModel.server.collectAsState()
    val liveStatus by DeployManager.status.collectAsState()
    val status = liveStatus[serverId] ?: server?.lastDeployStatus ?: DeployStatus.NONE
    val message by viewModel.message.collectAsState()
    val currentStepMap by DeployManager.currentStep.collectAsState()
    val currentStep = currentStepMap[serverId]

    var tabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        R.string.deploy_detail_tab_log,
        R.string.deploy_detail_tab_keys,
        R.string.deploy_detail_tab_settings,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(server?.name.orEmpty()) },
                actions = {
                    IconButton(onClick = { onEditServer(serverId) }) {
                        Icon(Icons.Filled.Edit, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (status == DeployStatus.RUNNING) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                    Text(currentStep ?: stringResource(R.string.deploy_detail_deploying))
                } else {
                    Button(onClick = viewModel::deployNow) {
                        Text(stringResource(R.string.deploy_detail_deploy_now))
                    }
                }
            }
            server?.let { s ->
                if (status == DeployStatus.SUCCESS) {
                    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        SelectionContainer {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(stringResource(R.string.deploy_detail_panel_url, s.baseUrl + "/admin/"))
                                Text(
                                    stringResource(R.string.deploy_detail_admin_token, s.adminToken),
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                if (s.nodeToken.isNotBlank()) {
                                    Text(
                                        stringResource(R.string.deploy_detail_node_token, s.nodeToken),
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            message?.let { msg ->
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(msg.text, modifier = Modifier.weight(1f))
                        IconButton(onClick = viewModel::clearMessage) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    }
                }
            }

            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, labelRes ->
                    Tab(
                        selected = tabIndex == index,
                        onClick = { tabIndex = index },
                        text = { Text(stringResource(labelRes)) },
                    )
                }
            }

            // weight(1f) is required here to avoid the same fillMaxSize overflow bug as inside each tab's content.
            Box(modifier = Modifier.weight(1f)) {
                when (tabIndex) {
                    0 -> DeployLogTab(serverId)
                    1 -> DeployKeysTab(viewModel)
                    2 -> DeploySettingsTab(viewModel)
                }
            }
        }
    }
}

@Composable
private fun DeployLogTab(serverId: String) {
    val logsMap by DeployManager.logs.collectAsState()
    val lines = logsMap[serverId].orEmpty()
    LazyColumn(Modifier.fillMaxSize().padding(12.dp)) {
        items(lines) { line ->
            Text(line, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth())
        }
    }
}
