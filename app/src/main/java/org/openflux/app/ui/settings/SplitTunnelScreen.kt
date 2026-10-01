package org.openflux.app.ui.settings

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openflux.app.LocalOpenFluxApp
import org.openflux.app.R
import org.openflux.app.data.SettingsRepository
import org.openflux.app.data.SplitTunnelMode

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.ImageBitmap?,
)

class SplitTunnelViewModel(private val repository: SettingsRepository, private val context: Context) : ViewModel() {
    val mode: StateFlow<SplitTunnelMode> =
        repository.splitTunnelMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SplitTunnelMode.OFF)

    val selectedApps: StateFlow<Set<String>> =
        repository.splitTunnelApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps

    fun loadApps() {
        if (_apps.value.isNotEmpty()) return
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) {
                val pm = context.packageManager
                pm.getInstalledApplications(0)
                    .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
                    .map { installed ->
                        InstalledApp(
                            packageName = installed.packageName,
                            label = installed.loadLabel(pm).toString(),
                            icon = runCatching {
                                pm.getApplicationIcon(installed.packageName).toBitmap(ICON_PX).asImageBitmap()
                            }.getOrNull(),
                        )
                    }
                    .sortedBy { it.label.lowercase() }
            }
            _apps.value = list
        }
    }

    fun setMode(mode: SplitTunnelMode) = viewModelScope.launch { repository.setSplitTunnelMode(mode) }

    fun toggleApp(packageName: String) = viewModelScope.launch {
        val current = selectedApps.value
        repository.setSplitTunnelApps(if (packageName in current) current - packageName else current + packageName)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitTunnelScreen(onDone: () -> Unit) {
    val app = LocalOpenFluxApp.current
    val context = LocalContext.current
    val viewModel: SplitTunnelViewModel = viewModel(
        factory = viewModelFactory { initializer { SplitTunnelViewModel(app.settingsRepository, context.applicationContext) } },
    )
    LaunchedEffect(Unit) { viewModel.loadApps() }

    val mode by viewModel.mode.collectAsState()
    val selected by viewModel.selectedApps.collectAsState()
    val apps by viewModel.apps.collectAsState()
    var filter by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.split_tunnel_title)) },
                actions = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.split_tunnel_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp)) {
                    FilterChip(
                        selected = mode == SplitTunnelMode.OFF,
                        onClick = { viewModel.setMode(SplitTunnelMode.OFF) },
                        label = { Text(stringResource(R.string.split_tunnel_mode_off)) },
                    )
                    FilterChip(
                        selected = mode == SplitTunnelMode.EXCLUDE,
                        onClick = { viewModel.setMode(SplitTunnelMode.EXCLUDE) },
                        label = { Text(stringResource(R.string.split_tunnel_mode_exclude)) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    FilterChip(
                        selected = mode == SplitTunnelMode.INCLUDE,
                        onClick = { viewModel.setMode(SplitTunnelMode.INCLUDE) },
                        label = { Text(stringResource(R.string.split_tunnel_mode_include)) },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            AnimatedContent(
                targetState = mode != SplitTunnelMode.OFF,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
                label = "split-tunnel-mode",
                modifier = Modifier.weight(1f),
            ) { enabled ->
                if (enabled) {
                    Column(Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            value = filter,
                            onValueChange = { filter = it },
                            label = { Text(stringResource(R.string.split_tunnel_filter)) },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )

                        val filtered = if (filter.isBlank()) apps else apps.filter { it.label.contains(filter, ignoreCase = true) }
                        val visibleApps = filtered.sortedByDescending { it.packageName in selected }

                        if (apps.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.split_tunnel_loading))
                            }
                        } else {
                            LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                                item(key = "selected-header") {
                                    Text(
                                        text = stringResource(R.string.split_tunnel_selected_count, selected.size),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                                items(visibleApps, key = { it.packageName }) { installedApp ->
                                    AppRow(
                                        app = installedApp,
                                        checked = installedApp.packageName in selected,
                                        onToggle = { viewModel.toggleApp(installedApp.packageName) },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.split_tunnel_off_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(app: InstalledApp, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val background by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0f)
        },
        label = "app-row-background",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .clickable { onToggle() }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        app.icon?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)),
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                app.packageName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

private const val ICON_PX = 96
