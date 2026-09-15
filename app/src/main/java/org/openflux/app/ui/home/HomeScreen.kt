package org.openflux.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.openflux.app.LocalOpenFluxApp
import org.openflux.app.R
import org.openflux.app.data.Profile
import org.openflux.app.data.ProfileRepository
import org.openflux.app.data.SettingsRepository
import org.openflux.app.vpn.OpenFluxVpnService
import org.openflux.app.vpn.TunnelStatus

internal data class HomeState(
    val profiles: List<Profile> = emptyList(),
    val activeProfileId: String? = null,
) {
    val activeProfile: Profile? get() = profiles.find { it.id == activeProfileId }
}

class HomeViewModel(
    private val profileRepository: ProfileRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    internal val state: StateFlow<HomeState> = combine(
        profileRepository.observeAll(),
        settingsRepository.activeProfileId,
    ) { profiles, activeId -> HomeState(profiles, activeId) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = HomeState(),
        )

    fun setActive(id: String) {
        viewModelScope.launch { settingsRepository.setActiveProfileId(id) }
    }
}

@Composable
fun HomeScreen(
    onConnectRequested: (String) -> Unit,
    onDisconnectRequested: () -> Unit,
    onManageProfiles: () -> Unit,
) {
    val app = LocalOpenFluxApp.current
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(app.profileRepository, app.settingsRepository) }
        },
    )

    val homeState by viewModel.state.collectAsState()
    val activeProfile = homeState.activeProfile
    val status by OpenFluxVpnService.callback.status.collectAsState()
    val channelReady by OpenFluxVpnService.callback.channelReady.collectAsState()
    val stats by OpenFluxVpnService.callback.stats.collectAsState()
    val lastRetryDetail by OpenFluxVpnService.callback.lastRetryDetail.collectAsState()
    val connected = status is TunnelStatus.Connected || status is TunnelStatus.Connecting
    val buttonState = when {
        status is TunnelStatus.Connected && channelReady -> ConnectionButtonState.Connected
        status is TunnelStatus.Connected -> ConnectionButtonState.Establishing
        status is TunnelStatus.Connecting -> ConnectionButtonState.Connecting
        else -> ConnectionButtonState.Idle
    }

    // Column keeps the profile selector pinned to the very bottom edge
    // (flush against the nav bar) while the VPN control group sits at the
    // vertical centre of the remaining space above it.
    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ConnectionButton(
                    state = buttonState,
                    label = statusLabel(status, channelReady, lastRetryDetail),
                    // Disabled only when there's genuinely nothing this tap
                    // could do: idle with no profile picked yet. Every other
                    // state - Connecting, Establishing, Connected - has a
                    // real action (cancel or disconnect), and onClick below
                    // already handles all of them via `connected`; disabling
                    // the button while stuck retrying (a real, reachable
                    // state - a doc_url that fails every attempt the same
                    // way) left the notification's own disconnect action as
                    // the only way to back out.
                    enabled = buttonState != ConnectionButtonState.Idle || activeProfile != null,
                    onClick = {
                        if (connected) {
                            onDisconnectRequested()
                        } else {
                            activeProfile?.let { onConnectRequested(it.id) }
                        }
                    },
                )

                Box(
                    modifier = Modifier.height(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (connected) {
                        TrafficLine(sent = stats.bytesSent, received = stats.bytesReceived)
                    }
                }
            }
        }

        // Compact profile picker: a single selector that opens a bottom
        // sheet listing every profile. Picking one makes it the active
        // profile - the previous always-visible list pushed the button down
        // and read as a second screen; this stays a one-tap footer. With no
        // profiles the button doubles as "Add profile".
        var sheetOpen by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(
                onClick = {
                    if (homeState.profiles.isEmpty()) {
                        onManageProfiles()
                    } else {
                        sheetOpen = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RectangleShape,
            ) {
                Text(
                    text = when {
                        activeProfile != null -> activeProfile.name
                        homeState.profiles.isEmpty() -> stringResource(R.string.profiles_add)
                        else -> stringResource(R.string.home_profile_selector_select)
                    },
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurface,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (sheetOpen) {
                ProfilePickerSheet(
                    profiles = homeState.profiles,
                    activeProfileId = homeState.activeProfileId,
                    onSelect = {
                        sheetOpen = false
                        viewModel.setActive(it)
                    },
                    onDismiss = { sheetOpen = false },
                )
            }
        }
    }
}

// Bottom sheet (slides up from the bottom edge) that lists every profile and
// lets the user pick the active one. Opened from the compact selector under
// the connect button.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilePickerSheet(
    profiles: List<Profile>,
    activeProfileId: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Tapping a different profile used to call onSelect (which sets
    // activeProfileId AND dismisses the sheet) in the same frame as the
    // tap, so the checkmark never had a chance to actually animate before
    // the sheet it was in disappeared. pendingId reflects the tap
    // immediately (so the row highlights and the checkmark animates in
    // right away) while the real onSelect - and the dismiss that comes
    // with it - waits out that animation first.
    var pendingId by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.home_profile_selector_select),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )

            profiles.forEach { profile ->
                val active = profile.id == (pendingId ?: activeProfileId)
                val background by animateColorAsState(
                    targetValue = if (active) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                    },
                    label = "profileRowBackground",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(background)
                        .clickable(enabled = pendingId == null) {
                            if (profile.id == activeProfileId) {
                                onSelect(profile.id) // already active - nothing to animate, just close
                                return@clickable
                            }
                            pendingId = profile.id
                            scope.launch {
                                delay(260)
                                onSelect(profile.id)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        AnimatedVisibility(visible = active, enter = fadeIn(), exit = fadeOut()) {
                            Text(
                                text = stringResource(R.string.profiles_active_badge),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = active,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

// TunnelStatus.Connected only means the local VPN interface came up and the
// transport was told to start - not that the covert channel it depends on
// has actually finished connecting yet (see MobileCallback.channelReady's
// doc comment). Without checking channelReady too, this said "Подключено"
// the instant the VPN interface existed, which read as "done" long before
// traffic could actually flow - including on every silent background
// reconnect after a drop, not just the first connect.
@Composable
private fun statusLabel(status: TunnelStatus, channelReady: Boolean, lastRetryDetail: String?): String = when {
    status is TunnelStatus.Stopped -> stringResource(R.string.home_status_stopped)
    // A doc_url whose document was created in Yandex's newer editor makes
    // every single retry fail with the exact same "balancer_url missing"
    // error forever - no number of retries fixes it, so surfacing that
    // specific, actionable reason here beats leaving the user staring at a
    // generic "connecting" spinner that will never resolve on its own (see
    // server/transport/yandex/yandex.go's fetchDocInfo, which already
    // rewrote this error message once to point at Volga - this is that same
    // signal, one layer up). Scoped to the two "still trying" states only,
    // so a genuine, unrelated TunnelStatus.Error never gets misread as this
    // just because an earlier retry in the same session happened to be one.
    (status is TunnelStatus.Connecting || (status is TunnelStatus.Connected && !channelReady)) &&
        lastRetryDetail?.contains("balancer_url", ignoreCase = true) == true ->
        stringResource(R.string.home_status_wrong_editor_type)
    status is TunnelStatus.Connecting -> stringResource(R.string.home_status_connecting)
    status is TunnelStatus.Connected && !channelReady -> stringResource(R.string.home_status_connecting_channel)
    status is TunnelStatus.Connected -> stringResource(R.string.home_status_connected)
    status is TunnelStatus.Error -> stringResource(R.string.home_status_error, (status as TunnelStatus.Error).message)
    else -> ""
}

private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return "%.1f %s".format(value, units[unitIndex])
}

// One quiet status line under the button's "Connected" label - sent and
// received as a single row (up/down arrows), deliberately not a Card so it
// reads as part of the status text rather than its own surface.
@Composable
private fun TrafficLine(sent: Long, received: Long, modifier: Modifier = Modifier) {
    val arrowTint = MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_up),
            contentDescription = "sent",
            tint = arrowTint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = formatBytes(sent),
            style = MaterialTheme.typography.bodyMedium,
            color = arrowTint,
        )
        Spacer(Modifier.width(16.dp))
        Icon(
            painter = painterResource(R.drawable.ic_arrow_down),
            contentDescription = "received",
            tint = arrowTint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = formatBytes(received),
            style = MaterialTheme.typography.bodyMedium,
            color = arrowTint,
        )
    }
}
