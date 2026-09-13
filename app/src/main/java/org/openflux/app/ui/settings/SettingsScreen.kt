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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import org.openflux.app.data.SettingsRepository

private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService<PowerManager>() ?: return true
    return powerManager.isIgnoringBatteryOptimizations(context.packageName)
}

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {
    val startOnBoot = repository.startOnBoot
    val defaultMtu = repository.defaultMtu
    val defaultDns = repository.defaultDns

    fun setStartOnBoot(enabled: Boolean) = viewModelScope.launch { repository.setStartOnBoot(enabled) }
    fun setDefaultMtu(mtu: Int) = viewModelScope.launch { repository.setDefaultMtu(mtu) }
    fun setDefaultDns(dns: String) = viewModelScope.launch { repository.setDefaultDns(dns) }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenSplitTunnel: () -> Unit, onOpenSiteSplitTunnel: () -> Unit) {
    val app = LocalOpenFluxApp.current
    val viewModel: SettingsViewModel = viewModel(
        factory = viewModelFactory { initializer { SettingsViewModel(app.settingsRepository) } },
    )

    val startOnBoot by viewModel.startOnBoot.collectAsState(initial = false)

    // Not collectAsState: binding these fields straight to a DataStore-backed
    // Flow meant every keystroke wrote to disk and then waited for that same
    // write to echo back through the Flow before the field showed anything
    // new - on a slower device (or just typing fast, which a 12-13 character
    // IP address invites) that round trip lagging behind a keystroke or two
    // is what made typing here feel like it was eating or reordering
    // characters. Loaded once instead (nulls until then, so the fields below
    // don't render with a placeholder default and then jump once the real
    // value arrives); edits update local state immediately and persist as a
    // side effect, never waiting on themselves to render.
    var mtuText by remember { mutableStateOf<String?>(null) }
    var dnsText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        mtuText = viewModel.defaultMtu.first().toString()
        dnsText = viewModel.defaultDns.first()
    }

    val context = LocalContext.current
    var batteryUnrestricted by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    // The system settings screen this button opens runs outside our own
    // activity - there's no callback for "the user came back and changed
    // it", only re-checking once they do. ON_RESUME (not a one-shot
    // LaunchedEffect) is what actually fires for that, since this screen
    // never leaves composition while the user is away in system settings.
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
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.settings_start_on_boot),
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Switch(checked = startOnBoot, onCheckedChange = viewModel::setStartOnBoot)
            }

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

            // Entirely optional, user-initiated - never requested
            // automatically. Battery optimization can throttle or kill the
            // VPN's background network activity (and any in-progress SSH
            // deploy) once the app isn't in the foreground; this just gives
            // whoever wants that fixed a one-tap way to ask the system for
            // it, without forcing the decision on anyone who doesn't.
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
