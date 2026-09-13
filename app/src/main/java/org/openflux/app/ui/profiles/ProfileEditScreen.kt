package org.openflux.app.ui.profiles

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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.openflux.app.data.ManualTransport
import org.openflux.app.data.Profile
import org.openflux.app.data.ProfileMode
import org.openflux.app.data.ProfileRepository
import org.openflux.app.ui.IntTextField
import org.openflux.app.ui.LongTextField

class ProfileEditViewModel(private val repository: ProfileRepository) : ViewModel() {
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
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditScreen(profileId: String?, importedProfile: Profile? = null, onDone: () -> Unit) {
    val app = LocalOpenFluxApp.current
    val viewModel: ProfileEditViewModel = viewModel(
        factory = viewModelFactory { initializer { ProfileEditViewModel(app.profileRepository) } },
    )
    val defaultMtu by app.settingsRepository.defaultMtu.collectAsState(initial = 1400)
    val defaultDns by app.settingsRepository.defaultDns.collectAsState(initial = "77.88.8.8")

    var profile by remember { mutableStateOf<Profile?>(null) }

    // Keyed on profileId + importedProfile so this only (re)loads when the
    // screen is actually navigated to for a different target, not on every
    // recomposition - each visit starts from a clean, correctly-sourced
    // draft rather than whatever the previous visit left behind.
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
                    // No live request to a controlplane belongs anywhere in
                    // this mode, one-off or otherwise - doc_url/transport
                    // come entirely from data already embedded in an
                    // imported deep link (see ProfileDeepLink.kt) or typed
                    // in by hand here, exactly like Fork mode's own
                    // covert-channel philosophy: everything needed travels
                    // through the document, never a separate plaintext-
                    // detectable API call.
                    OutlinedTextField(
                        value = current.controlUrl,
                        onValueChange = { profile = current.copy(controlUrl = it) },
                        label = { Text(stringResource(R.string.profile_edit_control_url)) },
                        placeholder = { Text(stringResource(R.string.profile_edit_control_url_placeholder)) },
                        supportingText = { Text(stringResource(R.string.profile_edit_control_url_hint)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    OutlinedTextField(
                        value = current.keyToken,
                        onValueChange = { profile = current.copy(keyToken = it) },
                        label = { Text(stringResource(R.string.profile_edit_key_token)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
                    OutlinedTextField(
                        value = current.docUrl,
                        onValueChange = { profile = current.copy(docUrl = it) },
                        label = { Text(stringResource(R.string.profile_edit_doc_url)) },
                        supportingText = { Text(stringResource(R.string.profile_edit_doc_url_key_hint)) },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    )
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
                    }
                    when (current.manualTransport) {
                        ManualTransport.YANDEX, ManualTransport.VOLGA -> {
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

            Row(modifier = Modifier.padding(top = 24.dp)) {
                Button(onClick = { viewModel.save(current, onDone) }) {
                    Text(stringResource(R.string.profile_edit_save))
                }
                OutlinedButton(onClick = onDone, modifier = Modifier.padding(start = 12.dp)) {
                    Text(stringResource(R.string.profile_edit_cancel))
                }
            }
        }
    }
}
