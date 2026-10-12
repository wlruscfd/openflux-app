package org.openflux.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openflux.app.LocalOpenFluxApp
import org.openflux.app.R
import org.openflux.app.data.ConnectionMode
import org.openflux.app.data.InstalledTransport
import org.openflux.app.data.SettingsRepository
import org.openflux.app.data.TransportUpdater

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService<PowerManager>() ?: return true
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val transports: TransportUpdater,
) : ViewModel() {
    val connectionMode = repository.connectionMode
    val defaultMtu = repository.defaultMtu
    val defaultDns = repository.defaultDns
    val verboseLogging = repository.verboseLogging
    val socks5Port = repository.socks5Port

    fun setConnectionMode(mode: ConnectionMode) = viewModelScope.launch { repository.setConnectionMode(mode) }
    fun setDefaultMtu(mtu: Int) = viewModelScope.launch { repository.setDefaultMtu(mtu) }
    fun setDefaultDns(dns: String) = viewModelScope.launch { repository.setDefaultDns(dns) }
    fun setVerboseLogging(enabled: Boolean) = viewModelScope.launch { repository.setVerboseLogging(enabled) }
    fun setSocks5Port(port: Int) = viewModelScope.launch { repository.setSocks5Port(port) }

    val dynamicTransports = repository.dynamicTransports
    fun setDynamicTransports(enabled: Boolean) = viewModelScope.launch { repository.setDynamicTransports(enabled) }

    var installedTransports by mutableStateOf<List<InstalledTransport>>(emptyList())
        private set
    var transportsStatus by mutableStateOf<TransportsStatus>(TransportsStatus.Idle)
        private set

    init {
        refreshInstalled()
    }

    private fun refreshInstalled() {
        viewModelScope.launch { installedTransports = transports.installed() }
    }

    fun checkTransports() {
        if (transportsStatus == TransportsStatus.Checking) return
        transportsStatus = TransportsStatus.Checking
        viewModelScope.launch {
            val result = transports.checkNow()
            transportsStatus = result.fold(
                onSuccess = { list -> TransportsStatus.Done(list.count { it.status == "installed" }) },
                onFailure = { TransportsStatus.Failed },
            )
            refreshInstalled()
        }
    }

    fun rollbackTransport(id: String) {
        viewModelScope.launch {
            transports.rollback(id)
            refreshInstalled()
        }
    }
}

sealed interface TransportsStatus {
    data object Idle : TransportsStatus
    data object Checking : TransportsStatus
    data class Done(val installed: Int) : TransportsStatus
    data object Failed : TransportsStatus
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenSplitTunnel: () -> Unit, onOpenSiteSplitTunnel: () -> Unit) {
    val app = LocalOpenFluxApp.current
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(app.settingsRepository, app.transportUpdater) } },
    )

    val verboseLogging by viewModel.verboseLogging.collectAsState(initial = false)
    val dynamicTransports by viewModel.dynamicTransports.collectAsState(initial = true)
    val connectionMode by viewModel.connectionMode.collectAsState(initial = ConnectionMode.VPN)

    // Not collectAsState: round-tripping every keystroke through the DataStore Flow made typing feel laggy.
    var mtuText by remember { mutableStateOf<String?>(null) }
    var dnsText by remember { mutableStateOf<String?>(null) }
    var socks5PortText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        mtuText = viewModel.defaultMtu.first().toString()
        dnsText = viewModel.defaultDns.first()
        socks5PortText = viewModel.socks5Port.first().toString()
    }

    val context = LocalContext.current
    var batteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    // No callback exists for "returned from system settings", so re-check on ON_RESUME instead.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryUnrestricted = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.settings_connection_mode),
                style = MaterialTheme.typography.titleSmall,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                SegmentedButton(
                    selected = connectionMode == ConnectionMode.VPN,
                    onClick = { viewModel.setConnectionMode(ConnectionMode.VPN) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.settings_connection_mode_vpn)) }
                SegmentedButton(
                    selected = connectionMode == ConnectionMode.SOCKS5,
                    onClick = { viewModel.setConnectionMode(ConnectionMode.SOCKS5) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.settings_connection_mode_socks5)) }
            }
            Text(
                stringResource(
                    if (connectionMode == ConnectionMode.VPN) {
                        R.string.settings_connection_mode_vpn_hint
                    } else {
                        R.string.settings_connection_mode_socks5_hint
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            if (mtuText != null && dnsText != null) {
                OutlinedTextField(
                    value = mtuText!!,
                    onValueChange = {
                        mtuText = it
                        it.toIntOrNull()?.let(viewModel::setDefaultMtu)
                    },
                    label = { Text(stringResource(R.string.settings_default_mtu)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = dnsText!!,
                    onValueChange = {
                        dnsText = it
                        viewModel.setDefaultDns(it)
                    },
                    label = { Text(stringResource(R.string.settings_default_dns)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }

            if (socks5PortText != null) {
                OutlinedTextField(
                    value = socks5PortText!!,
                    onValueChange = {
                        socks5PortText = it
                        it.toIntOrNull()?.let(viewModel::setSocks5Port)
                    },
                    label = { Text(stringResource(R.string.settings_socks5_port)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_verbose_logging),
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Switch(checked = verboseLogging, onCheckedChange = viewModel::setVerboseLogging)
            }
            Text(
                stringResource(R.string.settings_verbose_logging_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_dynamic_transports),
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Switch(checked = dynamicTransports, onCheckedChange = viewModel::setDynamicTransports)
            }
            Text(
                stringResource(R.string.settings_dynamic_transports_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (dynamicTransports) {
                viewModel.installedTransports.forEach { t ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${t.id} ${t.version}" + if (t.disabled) " (${stringResource(R.string.settings_transport_builtin)})" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                        )
                        if (t.hasPrevious || !t.disabled) {
                            TextButton(onClick = { viewModel.rollbackTransport(t.id) }) {
                                Text(stringResource(R.string.settings_transport_rollback))
                            }
                        }
                    }
                }
                OutlinedButton(
                    onClick = viewModel::checkTransports,
                    enabled = viewModel.transportsStatus != TransportsStatus.Checking,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.settings_transports_check))
                }
                when (val st = viewModel.transportsStatus) {
                    TransportsStatus.Idle, TransportsStatus.Checking -> Unit
                    is TransportsStatus.Done -> Text(
                        if (st.installed > 0) stringResource(R.string.settings_transports_updated, st.installed)
                        else stringResource(R.string.settings_transports_up_to_date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TransportsStatus.Failed -> Text(
                        stringResource(R.string.settings_transports_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            OutlinedButton(onClick = onOpenSplitTunnel, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_split_tunnel))
            }

            OutlinedButton(
                onClick = onOpenSiteSplitTunnel,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.settings_site_split_tunnel))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 24.dp))

            // Entirely optional, user-initiated: never requested automatically.
            if (!batteryUnrestricted) {
                Text(
                    stringResource(R.string.settings_battery_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}"),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_battery_optimization))
                }
            } else {
                Text(
                    stringResource(R.string.settings_battery_already_unrestricted),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
