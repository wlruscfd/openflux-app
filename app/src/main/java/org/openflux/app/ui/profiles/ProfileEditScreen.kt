package org.openflux.app.ui.profiles

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.openflux.app.data.CookiePushClient
import org.openflux.app.data.CookiePushOutcome
import org.openflux.app.data.CookiePushResult
import org.openflux.app.data.ManualTransport
import org.openflux.app.data.Profile
import org.openflux.app.data.ProfileMode
import org.openflux.app.data.ProfileRepository
import org.openflux.app.data.PushFailure
import org.openflux.app.data.PushRoute
import org.openflux.app.LocalOpenFluxApp
import org.openflux.app.OpenFluxApplication
import org.openflux.app.R
import org.openflux.app.ui.home.CaptchaWebViewDialog
import org.openflux.app.ui.IntTextField
import org.openflux.app.ui.LongTextField
import org.openflux.app.vpn.OpenFluxSocks5Service
import org.openflux.app.vpn.OpenFluxVpnService
import org.openflux.app.vpn.TunnelStatus



class ProfileEditViewModel(private val repository: ProfileRepository) : ViewModel() {
    private val _cookiePushMessage = MutableStateFlow<String?>(null)
    val cookiePushMessage: StateFlow<String?> = _cookiePushMessage

    private val _cookiePushBusy = MutableStateFlow(false)
    val cookiePushBusy: StateFlow<Boolean> = _cookiePushBusy

    fun clearCookiePushMessage() {
        _cookiePushMessage.value = null
    }

    fun loadOrNew(id: String?, onLoaded: (Profile) -> Unit) {
        viewModelScope.launch {
            val profile = id?.let { repository.getById(it) } ?: Profile(
                id = "",
                name = "",
                mode = ProfileMode.KEY,
            )
            onLoaded(profile)
        }
    }

    fun save(profile: Profile, onSaved: () -> Unit) {
        viewModelScope.launch {
            repository.save(profile)
            onSaved()
        }
    }

    fun pushCookies(
        profile: Profile,
        cookies: String,
        app: OpenFluxApplication,
        route: PushRoute = PushRoute.DIRECT,
        socksPort: Int = 0,
        resolvedUrl: String = "",
    ) {
        if (_cookiePushBusy.value) return
        _cookiePushBusy.value = true
        _cookiePushMessage.value = null
        viewModelScope.launch {
            val result = CookiePushClient(profile.controlUrl, profile.keyToken, route, socksPort).push(cookies, resolvedUrl)
            when (result) {
                is CookiePushResult.Sent ->
                    app.cookiePushStore.record(profile.id, CookiePushOutcome.SENT, null, route)
                is CookiePushResult.Failed ->
                    app.cookiePushStore.record(profile.id, CookiePushOutcome.FAILED, result.reason, route)
            }
            _cookiePushMessage.value = when (result) {
                is CookiePushResult.Sent -> app.getString(R.string.profile_edit_send_cookies_ok)
                is CookiePushResult.Failed -> app.getString(result.reason.messageRes())
            }
            _cookiePushBusy.value = false
        }
    }
}

// The jar is pushed for the key the profile authenticates as, so both a control URL and a key
// token have to be present, and the provider has to be one that can be checked in a browser.
private val cookieTimeFormatter = java.text.SimpleDateFormat("dd.MM HH:mm", java.util.Locale.getDefault())

private fun formatCookieTime(millis: Long): String = cookieTimeFormatter.format(java.util.Date(millis))

internal fun PushFailure.messageRes(): Int = when (this) {
    PushFailure.NO_CONTROL_URL, PushFailure.BAD_CONTROL_URL -> R.string.profile_edit_send_cookies_no_url
    PushFailure.NO_KEY_TOKEN -> R.string.profile_edit_send_cookies_no_token
    PushFailure.REJECTED -> R.string.profile_edit_send_cookies_rejected
    PushFailure.NETWORK -> R.string.profile_edit_send_cookies_network
    PushFailure.NO_TUNNEL -> R.string.profile_edit_send_cookies_no_tunnel
    PushFailure.RATE_LIMITED -> R.string.profile_edit_send_cookies_rate_limited
    PushFailure.UNKNOWN_KEY -> R.string.profile_edit_send_cookies_unknown_key
}

private fun cookiePushAvailable(profile: Profile): Boolean =
    profile.controlUrl.isNotBlank() &&
        profile.keyToken.isNotBlank() &&
        profile.manualTransport in setOf(
            ManualTransport.YANDEX,
            ManualTransport.VOLGA,
            ManualTransport.BOARDS,
            ManualTransport.YANDEX_MULTISTREAM,
        )

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(
    profileId: String?,
    importedProfile: Profile? = null,
    onStartTunnelForCookies: (String) -> Unit = {},
    onStopTunnelAfterCookies: () -> Unit = {},
    onDone: () -> Unit,
) {
    val app = LocalOpenFluxApp.current
    val viewModel: ProfileEditViewModel = viewModel(
        factory = viewModelFactory { initializer { ProfileEditViewModel(app.profileRepository) } },
    )
    val defaultMtu by app.settingsRepository.defaultMtu.collectAsState(initial = 1400)
    val defaultDns by app.settingsRepository.defaultDns.collectAsState(initial = "77.88.8.8")

    var profile by remember { mutableStateOf<Profile?>(null) }
    var cookieSheetFor by remember { mutableStateOf<Profile?>(null) }
    var tunnelPickerOpen by remember { mutableStateOf(false) }
    var cookieTargetFor by remember { mutableStateOf<Profile?>(null) }
    var refreshingDoc by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var cookieTunnelPending by remember { mutableStateOf(false) }
    var cookieTunnelError by remember { mutableStateOf<String?>(null) }
    val profiles by app.profileRepository.observeAll().collectAsState(initial = emptyList())
    val vpnStatus by OpenFluxVpnService.callback.status.collectAsState()
    val vpnUp = vpnStatus is TunnelStatus.Connected
    val socks5Ready by OpenFluxSocks5Service.callback.channelReady.collectAsState()
    val socks5Status by OpenFluxSocks5Service.callback.status.collectAsState()
    val socks5Port by app.settingsRepository.socks5Port.collectAsState(initial = 0)
    val socks5Address = if (socks5Port > 0) "127.0.0.1:$socks5Port" else null
    val cookieRecords by app.cookiePushStore.records.collectAsState()
    val tunnelFailedFormat = stringResource(R.string.profile_edit_cookies_tunnel_failed)
    val tunnelTimeoutMessage = stringResource(R.string.profile_edit_cookies_tunnel_timeout)
    val cookiePushMessage by viewModel.cookiePushMessage.collectAsState()
    val cookiePushBusy by viewModel.cookiePushBusy.collectAsState()

    val cookieStatusLine = remember(cookieRecords, profile) {
        { id: String ->
            val record = cookieRecords[id] ?: return@remember null
            when (record.outcome) {
                CookiePushOutcome.SENT -> app.getString(R.string.profile_edit_cookies_sent_at, formatCookieTime(record.atMillis))
                CookiePushOutcome.FAILED -> app.getString(
                    R.string.profile_edit_cookies_failed_at,
                    formatCookieTime(record.atMillis),
                    record.reason?.let { app.getString(it.messageRes()) } ?: "",
                )
                CookiePushOutcome.NONE -> null
            }
        }
    }

    // Keyed on profileId + importedProfile so this only reloads on navigation, not every recomposition.
    LaunchedEffect(profileId, importedProfile) {
        when {
            importedProfile != null -> profile = importedProfile
            profileId != null -> viewModel.loadOrNew(profileId) { profile = it }
            else -> viewModel.loadOrNew(null) { profile = it.copy(mtu = defaultMtu, dnsUpstream = defaultDns) }
        }
    }

    val current = profile ?: return

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (profileId == null) R.string.profile_edit_new_title else R.string.profile_edit_title,
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
                onValueChange = { profile = current.copy(name = it) },
                label = { Text(stringResource(R.string.profile_edit_name)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.profile_edit_mode), modifier = Modifier.padding(top = 16.dp))
            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                FilterChip(
                    selected = current.mode == ProfileMode.KEY,
                    onClick = { profile = current.copy(mode = ProfileMode.KEY) },
                    label = { Text(stringResource(R.string.profile_edit_mode_key)) },
                )
                FilterChip(
                    selected = current.mode == ProfileMode.MANUAL,
                    onClick = { profile = current.copy(mode = ProfileMode.MANUAL) },
                    label = { Text(stringResource(R.string.profile_edit_mode_manual)) },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            when (current.mode) {
                ProfileMode.KEY -> {
                    OutlinedTextField(
                        value = current.keyToken,
                        onValueChange = { profile = current.copy(keyToken = it) },
                        label = { Text(stringResource(R.string.profile_edit_key_token)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp)) {
                        FilterChip(
                            selected = current.manualTransport == ManualTransport.YANDEX,
                            onClick = { profile = current.copy(manualTransport = ManualTransport.YANDEX) },
                            label = { Text(stringResource(R.string.profile_edit_transport_yandex)) },
                        )
                        FilterChip(
                            selected = current.manualTransport == ManualTransport.YANDEX_MULTISTREAM,
                            onClick = { profile = current.copy(manualTransport = ManualTransport.YANDEX_MULTISTREAM) },
                            label = { Text(stringResource(R.string.profile_edit_transport_multistream)) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        FilterChip(
                            selected = current.manualTransport == ManualTransport.MAILRU,
                            onClick = { profile = current.copy(manualTransport = ManualTransport.MAILRU) },
                            label = { Text(stringResource(R.string.profile_edit_transport_mailru)) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        FilterChip(
                            selected = current.manualTransport == ManualTransport.BOARDS,
                            onClick = { profile = current.copy(manualTransport = ManualTransport.BOARDS) },
                            label = { Text(stringResource(R.string.profile_edit_transport_boards)) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        FilterChip(
                            selected = current.manualTransport == ManualTransport.MTS,
                            onClick = { profile = current.copy(manualTransport = ManualTransport.MTS) },
                            label = { Text(stringResource(R.string.profile_edit_transport_mts)) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        FilterChip(
                            selected = current.manualTransport == ManualTransport.TELEMOST,
                            onClick = { profile = current.copy(manualTransport = ManualTransport.TELEMOST) },
                            label = { Text(stringResource(R.string.profile_edit_transport_telemost)) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        FilterChip(
                            selected = current.manualTransport == ManualTransport.CUPS,
                            onClick = { profile = current.copy(manualTransport = ManualTransport.CUPS) },
                            label = { Text(stringResource(R.string.profile_edit_transport_cups)) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        FilterChip(
                            selected = current.manualTransport == ManualTransport.AVITO,
                            onClick = { profile = current.copy(manualTransport = ManualTransport.AVITO) },
                            label = { Text(stringResource(R.string.profile_edit_transport_avito)) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    if (current.manualTransport == ManualTransport.YANDEX_MULTISTREAM) {
                        OutlinedTextField(
                            value = current.docUrls.joinToString("\n"),
                            onValueChange = { profile = current.copy(docUrls = it.split("\n")) },
                            label = { Text(stringResource(R.string.profile_edit_doc_urls)) },
                            supportingText = { Text(stringResource(R.string.profile_edit_doc_urls_hint)) },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    } else {
                        OutlinedTextField(
                            value = current.docUrl,
                            onValueChange = { profile = current.copy(docUrl = it) },
                            label = { Text(stringResource(R.string.profile_edit_doc_url)) },
                            supportingText = { Text(stringResource(R.string.profile_edit_doc_url_key_hint)) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                    }
                    if (current.keyToken.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(R.string.profile_edit_e2e_encryption),
                                modifier = Modifier.weight(1f).padding(end = 12.dp),
                            )
                            Switch(
                                checked = current.e2eEncryption,
                                onCheckedChange = { profile = current.copy(e2eEncryption = it) },
                            )
                        }
                        Text(
                            stringResource(R.string.profile_edit_e2e_encryption_hint),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                ProfileMode.MANUAL -> {
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 12.dp)) {
                        FilterChip(
                            selected = current.manualTransport == ManualTransport.YANDEX,
                            onClick = { profile = current.copy(manualTransport = ManualTransport.YANDEX) },
                            label = { Text(stringResource(R.string.profile_edit_transport_yandex)) },
                        )
                        FilterChip(
                            selected = current.manualTransport == ManualTransport.VOLGA,
                            onClick = { profile = current.copy(manualTransport = ManualTransport.VOLGA) },
                            label = { Text(stringResource(R.string.profile_edit_transport_volga)) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        FilterChip(
                            selected = current.manualTransport == ManualTransport.MAX,
                            onClick = { profile = current.copy(manualTransport = ManualTransport.MAX) },
                            label = { Text(stringResource(R.string.profile_edit_transport_max)) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        FilterChip(
                            selected = current.manualTransport == ManualTransport.MAILRU,
                            onClick = { profile = current.copy(manualTransport = ManualTransport.MAILRU) },
                            label = { Text(stringResource(R.string.profile_edit_transport_mailru)) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                        FilterChip(
                            selected = current.manualTransport == ManualTransport.BOARDS,
                            onClick = { profile = current.copy(manualTransport = ManualTransport.BOARDS) },
                            label = { Text(stringResource(R.string.profile_edit_transport_boards)) },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    when (current.manualTransport) {
                        ManualTransport.YANDEX, ManualTransport.VOLGA, ManualTransport.MAILRU, ManualTransport.BOARDS, ManualTransport.MTS, ManualTransport.TELEMOST, ManualTransport.CUPS, ManualTransport.AVITO -> {
                            OutlinedTextField(
                                value = current.docUrl,
                                onValueChange = { profile = current.copy(docUrl = it) },
                                label = { Text(stringResource(R.string.profile_edit_doc_url)) },
                                supportingText = if (current.manualTransport == ManualTransport.YANDEX) {
                                    { Text(stringResource(R.string.profile_edit_doc_url_yandex_hint)) }
                                } else {
                                    null
                                },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                        }
                        ManualTransport.MAX -> {
                            OutlinedTextField(
                                value = current.maxToken,
                                onValueChange = { profile = current.copy(maxToken = it) },
                                label = { Text(stringResource(R.string.profile_edit_max_token)) },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                            LongTextField(
                                value = current.maxUid,
                                onValueChange = { profile = current.copy(maxUid = it) },
                                label = { Text(stringResource(R.string.profile_edit_max_uid)) },
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                        }
                        ManualTransport.YANDEX_MULTISTREAM -> {
                            // Unreachable from this screen's chips now, but an older/imported profile can still carry it.
                            OutlinedTextField(
                                value = current.docUrls.joinToString("\n"),
                                onValueChange = { profile = current.copy(docUrls = it.split("\n")) },
                                label = { Text(stringResource(R.string.profile_edit_doc_urls)) },
                                supportingText = { Text(stringResource(R.string.profile_edit_doc_urls_hint)) },
                                minLines = 3,
                                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                            )
                        }
                    }
                }
            }

            Text(stringResource(R.string.profile_edit_advanced), modifier = Modifier.padding(top = 24.dp))
            IntTextField(
                value = current.mtu,
                onValueChange = { profile = current.copy(mtu = it) },
                label = { Text(stringResource(R.string.profile_edit_mtu)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = current.dnsUpstream,
                onValueChange = { profile = current.copy(dnsUpstream = it) },
                label = { Text(stringResource(R.string.profile_edit_dns)) },
                supportingText = { Text(stringResource(R.string.profile_edit_dns_hint)) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            OutlinedTextField(
                value = current.forceBootstrapDns,
                onValueChange = { profile = current.copy(forceBootstrapDns = it) },
                label = { Text(stringResource(R.string.profile_edit_force_bootstrap_dns)) },
                placeholder = { Text(stringResource(R.string.profile_edit_force_bootstrap_dns_placeholder)) },
                supportingText = { Text(stringResource(R.string.profile_edit_force_bootstrap_dns_hint)) },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.profile_edit_auto_reconnect),
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                )
                Switch(
                    checked = current.autoReconnect,
                    onCheckedChange = { profile = current.copy(autoReconnect = it) },
                )
            }

            OutlinedButton(
                onClick = {
                    scope.launch {
                        refreshingDoc = true
                        val port = socks5Port.takeIf { it > 0 } ?: 0
                        val currentId = profile?.id ?: return@launch
                        app.docRefresher.refreshOnce(viaTunnel = port > 0)
                        profile = app.profileRepository.getById(currentId) ?: profile
                        refreshingDoc = false
                    }
                },
                enabled = !refreshingDoc,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(
                    if (refreshingDoc) stringResource(R.string.profile_edit_refresh_doc_running)
                    else stringResource(R.string.profile_edit_refresh_doc),
                )
            }

            if (cookiePushAvailable(current)) {
                OutlinedButton(
                    onClick = { tunnelPickerOpen = true },
                    enabled = !cookiePushBusy,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                ) {
                    Text(
                        if (cookiePushBusy) stringResource(R.string.profile_edit_send_cookies_sending)
                        else stringResource(R.string.profile_edit_send_cookies),
                    )
                }

                cookieStatusLine(current.id)?.let {
                    Text(
                        it,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }

            Row(modifier = Modifier.padding(top = 24.dp)) {
                Button(onClick = { viewModel.save(current, onDone) }) {
                    Text(stringResource(R.string.profile_edit_save))
                }
                OutlinedButton(onClick = onDone, modifier = Modifier.padding(start = 12.dp)) {
                    Text(stringResource(R.string.profile_edit_cancel))
                }
            }

            cookiePushMessage?.let {
                Text(
                    it,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }

    if (tunnelPickerOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { tunnelPickerOpen = false },
            title = { Text(stringResource(R.string.profile_edit_send_cookies_pick_tunnel)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.profile_edit_send_cookies_pick_tunnel_hint),
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                    val usable = profiles.filter { it.id != profile?.id }
                    if (usable.isEmpty()) {
                        Text(
                            stringResource(R.string.profile_edit_send_cookies_no_other_profile),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    usable.forEach { candidate ->
                        TextButton(
                            onClick = {
                                tunnelPickerOpen = false
                                cookieSheetFor = current
                                cookieTargetFor = current
                                onStartTunnelForCookies(candidate.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(candidate.name, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { tunnelPickerOpen = false }) {
                    Text(stringResource(R.string.profile_edit_cancel))
                }
            },
        )
    }

    cookieSheetFor?.let { target ->
        if (socks5Ready && socks5Address != null) {
            CaptchaWebViewDialog(
                docUrl = target.docUrl.ifBlank { "https://disk.yandex.ru/" },
                proxyHostPort = socks5Address,
                onDismiss = {
                    cookieSheetFor = null
                    viewModel.clearCookiePushMessage()
                    onStopTunnelAfterCookies()
                },
                onSolved = { cookies, resolvedUrl ->
                    viewModel.pushCookies(
                        profile = target,
                        cookies = cookies,
                        app = app,
                        resolvedUrl = resolvedUrl,
                    )
                    cookieSheetFor = null
                    onStopTunnelAfterCookies()
                },
            )
        }
    }

LaunchedEffect(cookieSheetFor) {
        if (cookieSheetFor == null) {
            cookieTunnelPending = false
            return@LaunchedEffect
        }
        cookieTunnelPending = true
        cookieTunnelError = null
        val settled = withTimeoutOrNull(TUNNEL_WAIT_MS) {
            snapshotFlow {
                OpenFluxSocks5Service.callback.channelReady.value to
                    OpenFluxSocks5Service.callback.status.value
            }.first { it.first || it.second is TunnelStatus.Error }
        }
        cookieTunnelPending = false
        val socksError = settled?.second as? TunnelStatus.Error
        when {
            settled?.first == true -> Unit
            socksError != null -> {
                cookieTunnelError = tunnelFailedFormat.format(app, socksError.message)
                cookieSheetFor = null
                onStopTunnelAfterCookies()
            }
            else -> {
                cookieTunnelError = tunnelTimeoutMessage
                cookieSheetFor = null
                onStopTunnelAfterCookies()
            }
        }
    }

    val cookieBusy = cookiePushBusy || cookieTunnelPending
    if (cookieBusy || cookieTunnelError != null) {
        androidx.compose.material3.Card(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        ) {
            Column(Modifier.padding(14.dp)) {
                if (cookieBusy) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.profile_edit_cookies_tunnel_waiting),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
                cookieTunnelError?.let { message ->
                    Text(
                        text = message,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    )
                    TextButton(
                        onClick = { tunnelPickerOpen = true },
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.profile_edit_cookies_pick_another))
                    }
                }
            }
        }
    }
}

private const val TUNNEL_WAIT_MS = 60_000L