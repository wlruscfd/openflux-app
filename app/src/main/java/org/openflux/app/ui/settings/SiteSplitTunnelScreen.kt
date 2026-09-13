package org.openflux.app.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openflux.app.LocalOpenFluxApp
import org.openflux.app.R
import org.openflux.app.data.SettingsRepository
import org.openflux.app.data.SplitTunnelMode

class SiteSplitTunnelViewModel(private val repository: SettingsRepository) : ViewModel() {
    val mode: StateFlow<SplitTunnelMode> =
        repository.splitTunnelSitesMode.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SplitTunnelMode.OFF)

    val sites: StateFlow<Set<String>> =
        repository.splitTunnelSites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun setMode(mode: SplitTunnelMode) = viewModelScope.launch { repository.setSplitTunnelSitesMode(mode) }

    fun addSite(site: String) = viewModelScope.launch {
        val normalized = normalizeSite(site) ?: return@launch
        if (normalized in sites.value) return@launch
        repository.setSplitTunnelSites(sites.value + normalized)
    }

    fun removeSite(site: String) = viewModelScope.launch {
        if (site !in sites.value) return@launch
        repository.setSplitTunnelSites(sites.value - site)
    }
}

// domainLabelRe allows only plain hostnames/TLDs (letters, digits, dots,
// hyphens, underscores), rejecting scheme/path/port junk up front.
private val domainLabelRe = Regex("^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$")

private val ipv4LabelRe = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")
private val ipv6LabelRe = Regex("^[0-9a-f:]+$")

private fun isIpAddress(s: String): Boolean {
    if (ipv4LabelRe.matches(s)) return true
    return s.contains(':') && ipv6LabelRe.matches(s)
}

/** Normalizes a raw user-typed site into a bare domain or IP, or null if invalid. */
internal fun normalizeSite(raw: String): String? {
    var s = raw.trim().lowercase()
    // Strip an optional scheme and anything after a slash - lets people paste
    // "https://www.example.com/path" and still get "www.example.com".
    val scheme = s.indexOf("://")
    if (scheme >= 0) s = s.substring(scheme + 3)
    val slash = s.indexOf('/')
    if (slash >= 0) s = s.substring(0, slash)
    if (isIpAddress(s)) return s
    // ".ru" and "*.ru" are the same "every .ru" rule - both leave a bare TLD
    // behind after stripping, so the dot check below must be skipped for them.
    val wildcard = s.startsWith("*.") || s.startsWith(".")
    s = s.removePrefix("www.")
    s = s.removePrefix("*.")
    s = s.removePrefix(".")
    s = s.removeSuffix(".")
    if (s.isBlank() || !domainLabelRe.matches(s)) return null
    if (!wildcard && !s.contains('.')) return null
    return s
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteSplitTunnelScreen(onDone: () -> Unit) {
    val app = LocalOpenFluxApp.current
    val viewModel: SiteSplitTunnelViewModel = viewModel(
        factory = viewModelFactory { initializer { SiteSplitTunnelViewModel(app.settingsRepository) } },
    )

    val mode by viewModel.mode.collectAsState()
    val sites by viewModel.sites.collectAsState()
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.site_split_tunnel_title)) },
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
                Text(
                    stringResource(R.string.site_split_tunnel_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

            if (mode != SplitTunnelMode.OFF) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(stringResource(R.string.site_split_tunnel_input_label)) },
                        placeholder = { Text(stringResource(R.string.site_split_tunnel_input_placeholder)) },
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            viewModel.addSite(input)
                            input = ""
                        },
                        enabled = normalizeSite(input) != null,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.site_split_tunnel_add))
                    }
                }

                if (sites.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            stringResource(R.string.site_split_tunnel_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(32.dp),
                        )
                    }
                } else {
                    val sortedSites = sites.sorted()
                    LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                        items(sortedSites, key = { it }) { site ->
                            SiteRow(site = site, onRemove = { viewModel.removeSite(site) })
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.site_split_tunnel_off_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(32.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SiteRow(site: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            site,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = R.string.site_split_tunnel_remove.toString(),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}