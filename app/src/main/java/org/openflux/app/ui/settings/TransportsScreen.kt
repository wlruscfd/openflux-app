package org.openflux.app.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openflux.app.LocalOpenFluxApp
import org.openflux.app.R
import org.openflux.app.ui.design.staggeredEntrance
import org.openflux.app.data.SettingsRepository
import org.openflux.app.data.TransportException
import org.openflux.app.data.TransportInfo
import org.openflux.app.data.TransportReport
import org.openflux.app.data.TransportUpdater

data class TransportsMessage(@StringRes val res: Int, val arg: String = "", val error: Boolean = false)

class TransportsViewModel(
    private val updater: TransportUpdater,
    private val settings: SettingsRepository,
) : ViewModel() {
    val useDownloaded = settings.dynamicTransports
    val autoUpdate = settings.transportsAutoUpdate
    val channel = settings.transportsChannel
    val catalogUrl = settings.transportsCatalogUrl

    var installed by mutableStateOf<List<TransportInfo>>(emptyList())
        private set
    var reports by mutableStateOf<Map<String, TransportReport>>(emptyMap())
        private set
    var busy by mutableStateOf<Set<String>>(emptySet())
        private set
    var message by mutableStateOf<TransportsMessage?>(null)
        private set
    var addError by mutableStateOf<String?>(null)
        private set

    init {
        viewModelScope.launch {
            refresh()
            check(silent = true)
        }
    }

    private suspend fun refresh() {
        installed = updater.list()
    }

    private fun <T> track(key: String, block: suspend () -> T) {
        if (key in busy) return
        busy = busy + key
        viewModelScope.launch {
            try {
                block()
            } finally {
                busy = busy - key
            }
        }
    }

    fun check(silent: Boolean = false) = track("*") {
        updater.check().fold(
            onSuccess = { list ->
                reports = list.associateBy { it.id }
                if (!silent) {
                    val n = list.count { it.status == "available" }
                    message = if (n > 0) TransportsMessage(R.string.transports_found, n.toString()) else TransportsMessage(R.string.transports_none_found)
                }
            },
            onFailure = { if (!silent) message = failure(it) },
        )
    }

    fun updateAll() = track("*") {
        updater.updateAll().fold(
            onSuccess = { list ->
                val n = list.count { it.status == "installed" }
                message = if (n > 0) TransportsMessage(R.string.transports_updated, n.toString()) else TransportsMessage(R.string.transports_none_found)
                reports = list.associateBy { it.id }
            },
            onFailure = { message = failure(it) },
        )
        refresh()
    }

    fun updateOne(id: String, allowWireBreak: Boolean) = track(id) {
        updater.updateOne(id, allowWireBreak).fold(
            onSuccess = { r ->
                reports = reports + (id to r)
                message = when (r.status) {
                    "installed" -> TransportsMessage(R.string.transports_updated_one, "$id ${r.to}")
                    "up_to_date" -> TransportsMessage(R.string.transports_up_to_date)
                    else -> TransportsMessage(codeRes(r.code), error = true)
                }
            },
            onFailure = { message = failure(it) },
        )
        refresh()
    }

    fun rollback(id: String) = track(id) {
        updater.rollback(id)
        reports = reports - id
        refresh()
        check(silent = true)
    }

    fun setEnabled(id: String, enabled: Boolean) = track(id) {
        updater.setEnabled(id, enabled)
        refresh()
    }

    fun remove(id: String) = track(id) {
        updater.remove(id)
        reports = reports - id
        refresh()
    }

    fun addCustom(source: String, pubkey: String, onDone: () -> Unit) = track("add") {
        addError = null
        updater.addCustom(source, pubkey).fold(
            onSuccess = { id ->
                message = TransportsMessage(R.string.transports_added, id)
                refresh()
                onDone()
            },
            onFailure = { addError = ((it as? TransportException)?.code ?: "error") },
        )
    }

    fun clearAddError() {
        addError = null
    }

    fun dismissMessage() {
        message = null
    }

    fun setUseDownloaded(enabled: Boolean) = viewModelScope.launch { settings.setDynamicTransports(enabled) }

    fun setAutoUpdate(enabled: Boolean) = viewModelScope.launch { settings.setTransportsAutoUpdate(enabled) }

    fun setChannel(value: String) = viewModelScope.launch {
        settings.setTransportsChannel(value)
        check(silent = true)
    }

    fun setCatalogUrl(value: String) = viewModelScope.launch {
        settings.setTransportsCatalogUrl(value)
        check(silent = true)
    }

    private fun failure(t: Throwable): TransportsMessage =
        TransportsMessage(codeRes((t as? TransportException)?.code ?: "error"), error = true)
}

@StringRes
internal fun codeRes(code: String): Int = when (code) {
    "no_source" -> R.string.transports_err_no_source
    "fetch_failed", "catalog: fetch" -> R.string.transports_err_fetch_failed
    "insecure_url" -> R.string.transports_err_insecure_url
    "bad_index" -> R.string.transports_err_bad_index
    "id_mismatch" -> R.string.transports_err_id_mismatch
    "no_channel" -> R.string.transports_err_no_channel
    "needs_newer_app" -> R.string.transports_err_needs_newer_app
    "key_changed" -> R.string.transports_err_key_changed
    "wire_break" -> R.string.transports_err_wire_break
    "bad_hash" -> R.string.transports_err_bad_hash
    "bad_package" -> R.string.transports_err_bad_package
    "bad_signature" -> R.string.transports_err_bad_signature
    "version_mismatch" -> R.string.transports_err_version_mismatch
    "not_newer" -> R.string.transports_err_not_newer
    "install_failed" -> R.string.transports_err_install_failed
    "bad_pubkey" -> R.string.transports_err_bad_pubkey
    "rolled_back_version" -> R.string.transports_err_rolled_back
    else -> R.string.transports_err_generic
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransportsScreen(onDone: () -> Unit) {
    val app = LocalOpenFluxApp.current
    val viewModel: TransportsViewModel = viewModel(
        factory = viewModelFactory { initializer { TransportsViewModel(app.transportUpdater, app.settingsRepository) } },
    )

    val useDownloaded by viewModel.useDownloaded.collectAsState(initial = true)
    val autoUpdate by viewModel.autoUpdate.collectAsState(initial = true)
    val channel by viewModel.channel.collectAsState(initial = "stable")
    var catalogUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { catalogUrl = viewModel.catalogUrl.first() }

    var showAdd by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }
    var confirmWire by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf<String?>(null) }

    val globalBusy = "*" in viewModel.busy || "add" in viewModel.busy
    val installedIds = viewModel.installed.map { it.id }.toSet()
    val pending = viewModel.reports.values.filter { it.id !in installedIds && it.id != "catalog" && it.status != "error" && it.status != "up_to_date" }
    val catalogError = viewModel.reports["catalog"]

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.transports_title)) },
                actions = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.transports_use_downloaded),
                                modifier = Modifier.weight(1f).padding(end = 12.dp),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Switch(checked = useDownloaded, onCheckedChange = viewModel::setUseDownloaded)
                        }
                        Text(
                            stringResource(R.string.transports_use_downloaded_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(onClick = { viewModel.check() }, enabled = !globalBusy) {
                                Text(stringResource(R.string.transports_check))
                            }
                            OutlinedButton(onClick = viewModel::updateAll, enabled = !globalBusy) {
                                Text(stringResource(R.string.transports_update_all))
                            }
                            OutlinedButton(onClick = { viewModel.clearAddError(); showAdd = true }, enabled = !globalBusy) {
                                Text(stringResource(R.string.transports_add_custom))
                            }
                        }
                        if (globalBusy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                        }
                        viewModel.message?.let { m ->
                            Text(
                                stringResource(m.res, m.arg),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (m.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                        if (catalogError != null) {
                            Text(
                                stringResource(R.string.transports_catalog_unreachable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }

            if (viewModel.installed.isEmpty() && pending.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.transports_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            itemsIndexed(viewModel.installed, key = { _, t -> t.id }) { index, t ->
                val report = viewModel.reports[t.id]
                val rowBusy = t.id in viewModel.busy
                Card(
                    modifier = Modifier.fillMaxWidth().staggeredEntrance(index),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("v${t.version}", style = MaterialTheme.typography.labelLarge)
                        }
                        Text(
                            t.id + (if (t.custom) " · " + stringResource(R.string.transports_custom) else "") +
                                (if (t.disabled) " · " + stringResource(R.string.transports_builtin_in_use) else ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (t.description.isNotBlank()) {
                            Text(t.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (!t.wired && !t.custom) {
                            Text(
                                stringResource(R.string.transports_not_wired),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        if (t.custom) {
                            Text(
                                stringResource(R.string.transports_source, t.update.firstOrNull() ?: stringResource(R.string.transports_source_direct)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                            Text(
                                stringResource(R.string.transports_key, t.pubkey.take(16) + "…"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (t.badVersion.isNotBlank()) {
                            Text(
                                stringResource(R.string.transports_bad_version, t.badVersion),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        ReportLine(report, Modifier.padding(top = 4.dp))
                        if (rowBusy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (report != null && (report.status == "available" || report.status == "blocked" && report.code == "wire_break")) {
                                TextButton(
                                    enabled = !rowBusy,
                                    onClick = { if (report.wireBreak) confirmWire = t.id else viewModel.updateOne(t.id, false) },
                                ) { Text(stringResource(R.string.transports_update_to, report.to)) }
                            } else {
                                TextButton(enabled = !rowBusy, onClick = { viewModel.updateOne(t.id, false) }) {
                                    Text(stringResource(R.string.transports_update))
                                }
                            }
                            if (t.hasPrevious) {
                                TextButton(enabled = !rowBusy, onClick = { viewModel.rollback(t.id) }) {
                                    Text(stringResource(R.string.transports_rollback))
                                }
                            }
                            TextButton(enabled = !rowBusy, onClick = { viewModel.setEnabled(t.id, t.disabled) }) {
                                Text(stringResource(if (t.disabled) R.string.transports_enable else R.string.transports_disable))
                            }
                            if (t.custom) {
                                TextButton(enabled = !rowBusy, onClick = { confirmRemove = t.id }) {
                                    Text(stringResource(R.string.transports_remove), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            items(pending, key = { "new-" + it.id }) { r ->
                val rowBusy = r.id in viewModel.busy
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(r.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("v${r.to}", style = MaterialTheme.typography.labelLarge)
                        }
                        Text(
                            r.id + " · " + stringResource(R.string.transports_not_installed),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (rowBusy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                        }
                        TextButton(enabled = !rowBusy, onClick = { viewModel.updateOne(r.id, true) }) {
                            Text(stringResource(R.string.transports_install))
                        }
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.transports_advanced),
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { advancedOpen = !advancedOpen }) {
                                Text(stringResource(if (advancedOpen) R.string.transports_hide else R.string.transports_show))
                            }
                        }
                        if (advancedOpen) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.transports_auto_update),
                                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                                )
                                Switch(checked = autoUpdate, onCheckedChange = viewModel::setAutoUpdate)
                            }
                            Text(
                                stringResource(R.string.transports_auto_update_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(R.string.transports_channel),
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = channel == "stable",
                                    onClick = { viewModel.setChannel("stable") },
                                    label = { Text("stable") },
                                )
                                FilterChip(
                                    selected = channel == "nightly",
                                    onClick = { viewModel.setChannel("nightly") },
                                    label = { Text("nightly") },
                                )
                            }
                            val current = catalogUrl
                            if (current != null) {
                                var draft by remember(current) { mutableStateOf(current) }
                                OutlinedTextField(
                                    value = draft,
                                    onValueChange = { draft = it },
                                    label = { Text(stringResource(R.string.transports_catalog_url)) },
                                    placeholder = { Text(stringResource(R.string.transports_catalog_default)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                )
                                Text(
                                    stringResource(R.string.transports_catalog_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    TextButton(
                                        enabled = draft.trim() != current,
                                        onClick = { viewModel.setCatalogUrl(draft.trim()); catalogUrl = draft.trim() },
                                    ) { Text(stringResource(R.string.transports_apply)) }
                                    TextButton(
                                        enabled = current.isNotEmpty(),
                                        onClick = { viewModel.setCatalogUrl(""); catalogUrl = "" },
                                    ) { Text(stringResource(R.string.transports_reset_default)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddCustomDialog(
            busy = "add" in viewModel.busy,
            errorCode = viewModel.addError,
            onDismiss = { showAdd = false },
            onAdd = { source, key -> viewModel.addCustom(source, key) { showAdd = false } },
        )
    }

    confirmWire?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmWire = null },
            title = { Text(stringResource(R.string.transports_wire_title)) },
            text = { Text(stringResource(R.string.transports_wire_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.updateOne(id, true); confirmWire = null }) {
                    Text(stringResource(R.string.transports_update_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWire = null }) { Text(stringResource(R.string.transports_cancel)) }
            },
        )
    }

    confirmRemove?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text(stringResource(R.string.transports_remove_title, id)) },
            text = { Text(stringResource(R.string.transports_remove_text)) },
            confirmButton = {
                TextButton(onClick = { viewModel.remove(id); confirmRemove = null }) {
                    Text(stringResource(R.string.transports_remove), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = null }) { Text(stringResource(R.string.transports_cancel)) }
            },
        )
    }
}

@Composable
private fun ReportLine(report: TransportReport?, modifier: Modifier = Modifier) {
    if (report == null) return
    val (text, isError) = when (report.status) {
        "available" -> stringResource(R.string.transports_status_available, report.to) to false
        "up_to_date" -> stringResource(R.string.transports_status_up_to_date) to false
        "installed" -> stringResource(R.string.transports_status_installed, report.to) to false
        "skipped" -> stringResource(codeRes(report.code)) to false
        else -> stringResource(codeRes(report.code)) to true
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
    if (report.status == "available" && report.notes.isNotBlank()) {
        Text(report.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AddCustomDialog(
    busy: Boolean,
    errorCode: String?,
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit,
) {
    var source by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.transports_add_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.transports_add_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text(stringResource(R.string.transports_add_source)) },
                    placeholder = { Text("https://…/update.json") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text(stringResource(R.string.transports_add_key)) },
                    placeholder = { Text("64 hex") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                if (errorCode != null) {
                    Text(
                        stringResource(codeRes(errorCode)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && source.isNotBlank() && key.isNotBlank(), onClick = { onAdd(source, key) }) {
                Text(stringResource(R.string.transports_add))
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) { Text(stringResource(R.string.transports_cancel)) }
        },
    )
}
