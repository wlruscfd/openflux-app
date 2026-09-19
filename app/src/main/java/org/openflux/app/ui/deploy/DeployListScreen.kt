package org.openflux.app.ui.deploy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.openflux.app.LocalOpenFluxApp
import org.openflux.app.R
import org.openflux.app.data.DeployServer
import org.openflux.app.data.DeployServerRepository
import org.openflux.app.data.DeployStatus
import org.openflux.app.deploy.DeployManager

internal data class DeployListState(
    val servers: List<DeployServer> = emptyList(),
    val liveStatus: Map<String, DeployStatus> = emptyMap(),
    val currentStep: Map<String, String> = emptyMap(),
    val selectedIds: Set<String> = emptySet(),
    val batchRunning: Boolean = false,
) {
    fun statusFor(server: DeployServer): DeployStatus = liveStatus[server.id] ?: server.lastDeployStatus
}

class DeployListViewModel(private val repository: DeployServerRepository) : ViewModel() {
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    internal val state: StateFlow<DeployListState> = combine(
        repository.observeAll(),
        DeployManager.status,
        DeployManager.currentStep,
        selectedIds,
        DeployManager.batchRunning,
    ) { servers, liveStatus, currentStep, selected, batchRunning ->
        DeployListState(servers, liveStatus, currentStep, selected, batchRunning)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeployListState())

    fun toggleSelected(id: String) {
        selectedIds.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun deploySelected() {
        val servers = state.value.servers.filter { it.id in state.value.selectedIds }
        DeployManager.deployAll(repository, servers)
        clearSelection()
    }

    fun deploy(server: DeployServer) {
        DeployManager.deploy(repository, server)
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }
}

@Composable
fun DeployListScreen(onAddServer: () -> Unit, onOpenServer: (String) -> Unit) {
    val app = LocalOpenFluxApp.current
    val viewModel: DeployListViewModel = viewModel(
        factory = viewModelFactory { initializer { DeployListViewModel(app.deployServerRepository) } },
    )
    val state by viewModel.state.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddServer) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.deploy_add_server))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.selectedIds.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.deploy_selected_count, state.selectedIds.size),
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = viewModel::deploySelected, enabled = !state.batchRunning) {
                        Text(stringResource(R.string.deploy_deploy_selected))
                    }
                }
            }
            if (state.batchRunning) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.deploy_batch_running, state.selectedIds.size))
                }
            }

            if (state.servers.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.deploy_empty))
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(state.servers, key = { it.id }) { server ->
                        DeployServerRow(
                            server = server,
                            status = state.statusFor(server),
                            currentStep = state.currentStep[server.id],
                            selected = server.id in state.selectedIds,
                            onClick = { onOpenServer(server.id) },
                            onToggleSelected = { viewModel.toggleSelected(server.id) },
                            onDeployNow = { viewModel.deploy(server) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeployServerRow(
    server: DeployServer,
    status: DeployStatus,
    currentStep: String?,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleSelected: () -> Unit,
    onDeployNow: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
            Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                Text(server.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(server.host, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (status == DeployStatus.RUNNING && currentStep != null) currentStep else statusLabel(status),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // A plain icon button, not a full text button, so the name/status Column keeps most of the row's width.
            if (status == DeployStatus.RUNNING) {
                CircularProgressIndicator(modifier = Modifier.padding(horizontal = 8.dp))
            } else {
                IconButton(onClick = onDeployNow) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.deploy_detail_deploy_now))
                }
            }
        }
    }
}

@Composable
private fun statusLabel(status: DeployStatus): String = when (status) {
    DeployStatus.NONE -> stringResource(R.string.deploy_status_none)
    DeployStatus.RUNNING -> stringResource(R.string.deploy_status_running)
    DeployStatus.SUCCESS -> stringResource(R.string.deploy_status_success)
    DeployStatus.FAILED -> stringResource(R.string.deploy_status_failed)
}
