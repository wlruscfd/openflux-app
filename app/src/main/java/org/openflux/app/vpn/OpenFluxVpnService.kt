package org.openflux.app.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import androidx.core.app.NotificationCompat
import android.content.pm.PackageManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mobile.Mobile
import mobile.Protector
import org.openflux.app.MainActivity
import org.openflux.app.OpenFluxApplication
import org.openflux.app.R
import org.openflux.app.data.ManualTransport
import org.openflux.app.data.Profile
import org.openflux.app.data.SplitTunnelMode
import org.openflux.app.data.isReadyToConnect
import org.openflux.app.data.toStartTunnelConfigJson

// Implements Go's mobile.Protector: without exempting the transport's own sockets via protect(fd), it dials itself and deadlocks.
class OpenFluxVpnService : VpnService(), Protector {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var establishedFd: Int? = null

    // What onRevoke and the network-change callback use to know whether/what to reconnect.
    private var connectedProfile: Profile? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var pendingReconnectJob: Job? = null

    // Tracked so a later disconnect() or overlapping connect() can actually cancel it.
    private var connectJob: Job? = null

    // gomobile binds Go's `int` to a Kotlin `long`, so protect takes a Long here even though VpnService.protect takes an Int.
    override fun protect(fd: Long): Boolean = super.protect(fd.toInt())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Android redelivers a null intent for START_STICKY after the process died while still running.
            reconnectAfterProcessRestart()
            return START_STICKY
        }
        when (intent.action) {
            ACTION_DISCONNECT -> {
                disconnect()
                return START_NOT_STICKY
            }
            ACTION_CONNECT -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                if (profileId != null) connect(profileId)
                return START_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun reconnectAfterProcessRestart() {
        serviceScope.launch {
            val app = application as OpenFluxApplication
            // lastConnectedProfileId tracks what was actually tunneling, unlike activeProfileId (the picker's selection).
            val profileId = app.settingsRepository.lastConnectedProfileId.first()
            val profile = profileId?.let { app.profileRepository.getById(it) }
            if (profile != null && profile.autoReconnect) {
                connect(profile.id)
            } else {
                stopSelf()
            }
        }
    }

    private fun connect(profileId: String) {
        // A new connect() always supersedes whatever this service was previously trying to do.
        pendingReconnectJob?.cancel()
        pendingReconnectJob = null
        connectJob?.cancel()

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.vpn_notification_connecting)))
        callback.reset()

        connectJob = serviceScope.launch {
            val app = application as OpenFluxApplication
            val profile = app.profileRepository.getById(profileId)
            if (profile == null) {
                callback.onStatus("error:profile not found")
                stopSelf()
                return@launch
            }
            if (!profile.isReadyToConnect) {
                val reason = if (profile.manualTransport == ManualTransport.YANDEX_MULTISTREAM) {
                    "multistream needs 2+ doc URLs - open the profile and add another"
                } else {
                    "key not resolved yet - open the profile and tap \"Check key\""
                }
                callback.onStatus("error:$reason")
                stopSelf()
                return@launch
            }

            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(profile.mtu)
                .addAddress(VPN_ADDRESS_V4, 24)
                .addAddress(VPN_ADDRESS_V6, 128)
                .addRoute("0.0.0.0", 0)
                // No IPv6 data path exists yet; routing it here too means it's captured and dropped, not leaked.
                .addRoute("::", 0)
                .addDnsServer(profile.dnsUpstream)

            applySplitTunneling(app, builder)

            val siteSplitMode = app.settingsRepository.splitTunnelSitesMode.first()
            val siteSplitSites = app.settingsRepository.splitTunnelSites.first()
            val verboseLogging = app.settingsRepository.verboseLogging.first()

            val pfd = try {
                builder.establish()
            } catch (t: Throwable) {
                callback.onStatus("error:${t.message}")
                stopSelf()
                return@launch
            }
            if (pfd == null) {
                callback.onStatus("error:VPN permission not granted")
                stopSelf()
                return@launch
            }

            // detachFd() hands raw fd ownership to Go; do not use `pfd` after this point.
            val fd = pfd.detachFd()
            establishedFd = fd

            try {
                Mobile.startTunnel(
                    fd.toLong(),
                    profile.toStartTunnelConfigJson(siteSplitMode, siteSplitSites, verboseLogging),
                    this@OpenFluxVpnService,
                    callback,
                )
            } catch (t: Throwable) {
                callback.onStatus("error:${t.message}")
                stopSelf()
                return@launch
            }

            connectedProfile = profile
            app.settingsRepository.setLastConnectedProfileId(profile.id)
            if (profile.autoReconnect) registerNetworkCallback()
            updateNotification(getString(R.string.vpn_notification_connected, profile.name))
        }
    }

    // Redials immediately on a network change instead of waiting for a read/write timeout on the old socket.
    private fun registerNetworkCallback() {
        // Unregister first: Android never releases a stale registration on its own, and connect() can run again.
        unregisterNetworkCallback()

        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        // NOT_VPN excludes this service's own tunnel interface, or "default network" would just resolve to itself.
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        var lastChangeAt = 0L
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // Debounced: this fires per matching network, and an unthrottled reconnect storm caused choppy throughput.
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastChangeAt < NETWORK_CHANGE_DEBOUNCE_MS) return
                lastChangeAt = now
                runCatching { Mobile.networkChanged() }
            }
        }
        runCatching { cm.registerNetworkCallback(request, cb) }
            .onSuccess { networkCallback = cb }
    }

    private fun unregisterNetworkCallback() {
        val cb = networkCallback ?: return
        networkCallback = null
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        runCatching { cm.unregisterNetworkCallback(cb) }
    }

    // Silently skips a package that no longer exists rather than failing the whole connection over it.
    private suspend fun applySplitTunneling(app: OpenFluxApplication, builder: Builder) {
        val mode = app.settingsRepository.splitTunnelMode.first()

        // Without this, VpnService captures this app's own non-protected sockets too - including
        // CaptchaWebViewDialog's WebView, which needs real internet access to show a CAPTCHA that
        // is, by definition, blocking the tunnel it would otherwise be routed through. INCLUDE
        // mode already excludes everything not explicitly allow-listed (and can't be combined with
        // addDisallowedApplication on the same Builder), so this only applies to OFF/EXCLUDE.
        if (mode != SplitTunnelMode.INCLUDE) {
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
            }
        }

        if (mode == SplitTunnelMode.OFF) return

        val packages = app.settingsRepository.splitTunnelApps.first()
        for (pkg in packages) {
            try {
                when (mode) {
                    SplitTunnelMode.EXCLUDE -> builder.addDisallowedApplication(pkg)
                    SplitTunnelMode.INCLUDE -> builder.addAllowedApplication(pkg)
                    SplitTunnelMode.OFF -> Unit
                }
            } catch (e: PackageManager.NameNotFoundException) {
                // Selected earlier, uninstalled since - nothing to exclude/include anymore.
            }
        }
    }

    private fun disconnect() {
        pendingReconnectJob?.cancel()
        pendingReconnectJob = null
        connectJob?.cancel()
        connectJob = null
        unregisterNetworkCallback()
        connectedProfile = null
        val app = application as OpenFluxApplication
        serviceScope.launch {
            app.settingsRepository.setLastConnectedProfileId(null)
            runCatching { Mobile.stopTunnel() }
            establishedFd = null
            callback.onStatus("stopped")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // With autoReconnect on, the short delay avoids hammering establish() if another VPN app keeps grabbing the slot back.
    override fun onRevoke() {
        val profile = connectedProfile
        if (profile != null && profile.autoReconnect) {
            // Cancel any reconnect already scheduled/running, since onRevoke can fire again before one finishes.
            pendingReconnectJob?.cancel()
            connectJob?.cancel()
            // "connecting" is an existing status that fits this gap, rather than leaving the UI showing "Connected".
            callback.onStatus("connecting")
            pendingReconnectJob = serviceScope.launch {
                // StartTunnel refuses a second call while it still thinks one is running, so tear the old one down first.
                runCatching { Mobile.stopTunnel() }
                establishedFd = null
                delay(2000)
                connect(profile.id)
            }
            // Not calling super.onRevoke(): its default stopSelf() would destroy the reconnect attempt just scheduled.
        } else {
            disconnect()
            super.onRevoke()
        }
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        runCatching { Mobile.stopTunnel() }
        // Without this, onRevoke's delayed reconnect coroutine could still call connect() against a destroyed Service.
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnectIntent = PendingIntent.getService(
            this, 0, Intent(this, OpenFluxVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, OpenFluxApplication.VPN_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.vpn_notification_disconnect_action), disconnectIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_CONNECT = "org.openflux.app.action.CONNECT"
        const val ACTION_DISCONNECT = "org.openflux.app.action.DISCONNECT"
        const val EXTRA_PROFILE_ID = "profile_id"

        private const val NOTIFICATION_ID = 1
        private const val VPN_ADDRESS_V4 = "10.111.0.2"
        private const val VPN_ADDRESS_V6 = "fd00:6f70:666c::2"
        private const val NETWORK_CHANGE_DEBOUNCE_MS = 5000L

        // Shared across the app's lifetime, regardless of whether an Activity is currently bound to the service.
        val callback = MobileCallback()
    }
}
