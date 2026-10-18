package org.openflux.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import mobile.Mobile
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.graphicsLayer
import org.openflux.app.ui.design.AuroraBackground
import org.openflux.app.ui.design.Motion
import org.openflux.app.ui.design.StatPill
import org.openflux.app.ui.design.staggeredEntrance
import org.openflux.app.ui.design.pressScale
import org.openflux.app.LocalOpenFluxApp
import org.openflux.app.R
import org.openflux.app.data.BALANCER_PROFILE_ID
import org.openflux.app.data.ConnectionMode
import org.openflux.app.data.Profile
import org.openflux.app.data.ProfileBalancer
import org.openflux.app.data.ProfileRepository
import org.openflux.app.data.SettingsRepository
import org.openflux.app.data.transportLabel
import org.openflux.app.vpn.OpenFluxSocks5Service
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
    onSocks5Requested: (String) -> Unit,
    onSocks5StopRequested: () -> Unit,
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
    val isBalancerSelected = homeState.activeProfileId == BALANCER_PROFILE_ID
    val connectionMode by app.settingsRepository.connectionMode.collectAsState(initial = ConnectionMode.VPN)
    val health by app.profileHealthStore.health.collectAsState()
    val lastConnectedProfileId by app.settingsRepository.lastConnectedProfileId.collectAsState(initial = null)
    val status by OpenFluxVpnService.callback.status.collectAsState()
    val channelReady by OpenFluxVpnService.callback.channelReady.collectAsState()
    val stats by OpenFluxVpnService.callback.stats.collectAsState()
    val lastRetryDetail by OpenFluxVpnService.callback.lastRetryDetail.collectAsState()

    // With the balancer selected, connecting goes to whichever profile has been behaving, not a
    // fixed pick. A manual pick still wins - the balancer only chooses when the tunnel is down, so
    // it never overrides an active connection.
    val connectTarget = remember(isBalancerSelected, health, homeState.profiles, homeState.activeProfileId, status) {
        if (isBalancerSelected && status !is TunnelStatus.Connected && status !is TunnelStatus.Connecting) {
            ProfileBalancer.pick(homeState.profiles, health, app.profileHealthStore::isInCooldown)
        } else {
            activeProfile
        }
    }
    // What the balancer is actually running right now, as opposed to what it would pick if asked
    // again this instant - health can shift mid-connection without that meaning a different
    // profile is suddenly the one carrying traffic.
    val balancerRunningProfile = remember(isBalancerSelected, lastConnectedProfileId, homeState.profiles) {
        if (isBalancerSelected) homeState.profiles.find { it.id == lastConnectedProfileId } else null
    }
    val connectivityOk by OpenFluxVpnService.callback.connectivityOk.collectAsState()
    val socks5Status by OpenFluxSocks5Service.callback.status.collectAsState()
    val socks5ChannelReady by OpenFluxSocks5Service.callback.channelReady.collectAsState()
    val socks5Stats by OpenFluxSocks5Service.callback.stats.collectAsState()
    val socks5RetryDetail by OpenFluxSocks5Service.callback.lastRetryDetail.collectAsState()
    val socks5ConnectivityOk by OpenFluxSocks5Service.callback.connectivityOk.collectAsState()

    // The two modes are separate services with separate lifetimes, so the button has to answer
    // for whichever one is actually running. Reading only the VPN service left a SOCKS5 tunnel
    // un-stoppable from the button: the tap asked a service that was not running to disconnect.
    val vpnActive = status is TunnelStatus.Connected || status is TunnelStatus.Connecting
    val socks5Active = socks5Status is TunnelStatus.Connected || socks5Status is TunnelStatus.Connecting
    val anyActive = vpnActive || socks5Active
    val channelUp = (status is TunnelStatus.Connected && channelReady) ||
        (socks5Status is TunnelStatus.Connected && socks5ChannelReady)
    val activeStatus = if (socks5Active) socks5Status else status
    val activeChannelReady = if (socks5Active) socks5ChannelReady else channelReady
    val buttonState = when {
        channelUp -> ConnectionButtonState.Connected
        // The interface/proxy is up but the covert channel hasn't finished its own handshake yet -
        // a visually distinct state from "just started dialing", not just a slower Connecting.
        activeStatus is TunnelStatus.Connected && !activeChannelReady -> ConnectionButtonState.Establishing
        anyActive -> ConnectionButtonState.Connecting
        else -> ConnectionButtonState.Idle
    }

    val socks5Port by app.settingsRepository.socks5Port.collectAsState(initial = 1080)

    // Only one of these is ever non-null at a time in practice - VPN and SOCKS5 modes are mutually exclusive - but reading both keeps this screen agnostic to which one is active.
    val vpnCaptchaUrl by OpenFluxVpnService.callback.captchaDocUrl.collectAsState()
    val socks5CaptchaUrl by OpenFluxSocks5Service.callback.captchaDocUrl.collectAsState()
    val captchaUrl = vpnCaptchaUrl ?: socks5CaptchaUrl
    val vpnCaptchaPending by OpenFluxVpnService.callback.captchaPending.collectAsState()
    val socks5CaptchaPending by OpenFluxSocks5Service.callback.captchaPending.collectAsState()
    val captchaPending = vpnCaptchaPending || socks5CaptchaPending
    val captchaDialogShowing = captchaUrl != null

    val auroraIntensity = when (buttonState) {
        ConnectionButtonState.Idle -> 0f
        ConnectionButtonState.Connecting -> 0.45f
        ConnectionButtonState.Establishing -> 0.7f
        ConnectionButtonState.Connected -> 1f
    }

    AuroraBackground(modifier = Modifier.fillMaxSize(), intensity = auroraIntensity) {
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
                    label = statusLabel(
                        if (socks5Active) socks5Status else status,
                        if (socks5Active) socks5ChannelReady else channelReady,
                        if (socks5Active) socks5RetryDetail else lastRetryDetail,
                        captchaPending,
                        captchaDialogShowing,
                        if (socks5Active) socks5ConnectivityOk else connectivityOk,
                    ),
                    // Starting one mode now stops the other, so neither button has to be disabled
                    // for the other - that coupling is what used to leave both unusable.
                    enabled = buttonState != ConnectionButtonState.Idle || connectTarget != null,
                    onClick = {
                        when {
                            socks5Active -> onSocks5StopRequested()
                            vpnActive -> onDisconnectRequested()
                            connectionMode == ConnectionMode.SOCKS5 -> connectTarget?.let { onSocks5Requested(it.id) }
                            else -> connectTarget?.let { onConnectRequested(it.id) }
                        }
                    },
                )

                if (captchaPending && !captchaDialogShowing) {
                    TextButton(
                        onClick = {
                            OpenFluxVpnService.callback.reopenCaptchaPrompt()
                            OpenFluxSocks5Service.callback.reopenCaptchaPrompt()
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(stringResource(R.string.home_solve_check))
                    }
                }

                Box(
                    modifier = Modifier.heightIn(min = 52.dp).padding(top = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (anyActive) {
                        val live = if (socks5Active) socks5Stats else stats
                        TrafficLine(sent = live.bytesSent, received = live.bytesReceived)
                    }
                }

                if (connectionMode == ConnectionMode.SOCKS5 && socks5Active) {
                    Socks5Hint(port = socks5Port)
                }
            }
        }

        var sheetOpen by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier.fillMaxWidth(),
        ) {
            ProfileSelector(
                balancer = isBalancerSelected,
                title = when {
                        isBalancerSelected && anyActive && balancerRunningProfile != null ->
                            stringResource(R.string.balancer_profile_name) + " · " +
                                stringResource(R.string.balancer_connecting_via, balancerRunningProfile.name)
                        isBalancerSelected && anyActive ->
                            stringResource(R.string.balancer_profile_name) + " · " +
                                stringResource(R.string.balancer_no_target)
                        isBalancerSelected -> stringResource(R.string.balancer_profile_name)
                        activeProfile != null -> activeProfile.name
                        homeState.profiles.isEmpty() -> stringResource(R.string.profiles_add)
                        else -> stringResource(R.string.home_profile_selector_select)
                    },
                expanded = sheetOpen,
                onClick = {
                    if (homeState.profiles.isEmpty()) {
                        onManageProfiles()
                    } else {
                        sheetOpen = true
                    }
                },
            )

            if (sheetOpen) {
                ProfilePickerSheet(
                    profiles = homeState.profiles,
                    activeProfileId = homeState.activeProfileId,
                    showBalancer = homeState.profiles.size > 1,
                    // Commits the selection only; the sheet dismisses itself once its checkmark animation finishes.
                    onSelect = { viewModel.setActive(it) },
                    onDismiss = { sheetOpen = false },
                )
            }
        }

        if (captchaUrl != null) {
CaptchaWebViewDialog(
            docUrl = captchaUrl,
            proxyHostPort = if (socks5Active) "127.0.0.1:$socks5Port" else null,
                onDismiss = {
                    OpenFluxVpnService.callback.dismissCaptchaPrompt()
                    OpenFluxSocks5Service.callback.dismissCaptchaPrompt()
                },
                onSolved = { cookies, _ ->
                    if (runCatching { Mobile.provideCaptchaCookies(cookies) }.isSuccess) {
                        OpenFluxVpnService.callback.captchaSolved()
                        OpenFluxSocks5Service.callback.captchaSolved()
                    }
                },
            )
        }
    }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilePickerSheet(
    profiles: List<Profile>,
    activeProfileId: String?,
    showBalancer: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // pendingId reflects the tap immediately so the checkmark can animate before onSelect's dismiss tears the sheet down.
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

            if (showBalancer) {
                val balancerActive = (pendingId ?: activeProfileId) == BALANCER_PROFILE_ID
                val balancerBackground by animateColorAsState(
                    targetValue = if (balancerActive) {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.18f)
                    },
                    label = "balancerRowBackground",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(balancerBackground)
                        .clickable {
                            if (activeProfileId == BALANCER_PROFILE_ID) {
                                onDismiss()
                                return@clickable
                            }
                            pendingId = BALANCER_PROFILE_ID
                            onSelect(BALANCER_PROFILE_ID)
                            scope.launch {
                                delay(260)
                                onDismiss()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            text = stringResource(R.string.balancer_profile_name),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = stringResource(R.string.balancer_profile_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    AnimatedVisibility(
                        visible = balancerActive,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut(),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }

            profiles.forEachIndexed { index, profile ->
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
                        .staggeredEntrance(index)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(background)
                        .clickable {
                            if (profile.id == activeProfileId) {
                                onDismiss() // already active - nothing to animate, just close
                                return@clickable
                            }
                            pendingId = profile.id
                            // Commit immediately: onSelect runs on viewModelScope, which outlives this sheet.
                            onSelect(profile.id)
                            scope.launch {
                                delay(260)
                                onDismiss()
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = stringResource(R.string.profile_transport_label) + ": " + transportLabel(profile.manualTransport),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val badgeAlpha by animateFloatAsState(if (active) 1f else 0f, label = "profileBadgeAlpha")
                        Text(
                            text = stringResource(R.string.profiles_active_badge),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.alpha(badgeAlpha),
                        )
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

// TunnelStatus.Connected only means the VPN interface is up, not that the covert channel has
// finished connecting, so the label has to distinguish the two or it claims a tunnel that
// carries nothing.
@Composable
private fun statusLabel(
    status: TunnelStatus,
    channelReady: Boolean,
    lastRetryDetail: String?,
    captchaPending: Boolean,
    captchaDialogShowing: Boolean,
    connectivityOk: Boolean? = null,
): String = when {
    status is TunnelStatus.Stopped -> stringResource(R.string.home_status_stopped)
    // A doc_url created in Yandex's newer editor fails every retry with the same "balancer_url missing".
    (status is TunnelStatus.Connecting || (status is TunnelStatus.Connected && !channelReady)) &&
        lastRetryDetail?.contains("balancer_url", ignoreCase = true) == true ->
        stringResource(R.string.home_status_wrong_editor_type)
    status is TunnelStatus.Error -> stringResource(R.string.home_status_error, (status as TunnelStatus.Error).message)
    captchaPending && !captchaDialogShowing -> stringResource(R.string.home_status_captcha_pending)
    captchaPending -> stringResource(R.string.home_status_captcha_open)
    status is TunnelStatus.Connecting -> stringResource(R.string.home_status_connecting)
    status is TunnelStatus.Connected && !channelReady -> stringResource(R.string.home_status_connecting_channel)
    status is TunnelStatus.Connected && connectivityOk == false -> stringResource(R.string.home_status_one_way)
    status is TunnelStatus.Connected -> stringResource(R.string.home_status_connected)
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

// Deliberately not styled like the primary ConnectionButton: this is the alternative path, not the default one.
@Composable
private fun Socks5Hint(port: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.home_socks5_running, "127.0.0.1:$port"),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.home_socks5_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun TrafficLine(sent: Long, received: Long, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        StatPill(icon = painterResource(R.drawable.ic_arrow_up), value = formatBytes(sent))
        StatPill(icon = painterResource(R.drawable.ic_arrow_down), value = formatBytes(received))
    }
}

@Composable
private fun ProfileSelector(
    title: String,
    balancer: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, Motion.bouncy(), label = "selector-chevron")
    Surface(
        onClick = onClick,
        interactionSource = interaction,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp)
            .pressScale(interaction, 0.97f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (balancer) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                } else {
                    Text(
                        text = title.trim().take(1).uppercase().ifEmpty { "+" },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                modifier = Modifier.size(28.dp).graphicsLayer { rotationZ = chevron },
            )
        }
    }
}
