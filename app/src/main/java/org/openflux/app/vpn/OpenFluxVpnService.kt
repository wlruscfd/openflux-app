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

/**
 * Owns the single running tunnel. Only one profile can be connected at a
 * time (matches how a device has one active VPN connection), so tunnel
 * state - and the [MobileCallback] the UI observes - lives on the
 * companion object rather than needing a bound-service/Messenger dance.
 *
 * Also implements Go's mobile.Protector: once the tunnel is up, Android
 * routes ALL outbound traffic - including this app's own - through it by
 * default, so without exempting the transport's own sockets (to the doc,
 * to DNS, ...) via VpnService.protect(fd), the transport ends up trying to
 * dial itself and deadlocks. VpnService already declares a same-signature
 * protect(fd: Int): Boolean, which satisfies Protector with no extra code.
 */
class OpenFluxVpnService : VpnService(), Protector {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var establishedFd: Int? = null

    // Set right after a successful Mobile.startTunnel, cleared by
    // disconnect()/onDestroy() - what onRevoke and a network-change callback
    // need to know whether (and what) to reconnect, since neither carries a
    // profile id of its own the way an ACTION_CONNECT Intent does.
    private var connectedProfile: Profile? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var pendingReconnectJob: Job? = null

    // The job actually performing connect()'s work (establish() through
    // Mobile.startTunnel), tracked so a later disconnect() - or a new
    // connect() call arriving while one is already in flight - can actually
    // cancel it, instead of racing an untracked anonymous coroutine.
    private var connectJob: Job? = null

    // gomobile binds Go's `int` to a Java/Kotlin `long` (Go's int width is
    // platform-dependent), so mobile.Protector.protect takes a Long here
    // even though VpnService.protect itself takes an Int.
    override fun protect(fd: Long): Boolean = super.protect(fd.toInt())

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // The system restarted this service after the process died while
            // it was still running (Android only redelivers a null intent for
            // START_STICKY, never the original one) - reconnect the profile
            // that was active, the same way BootReceiver does after a device
            // reboot, instead of silently doing nothing despite promising
            // STICKY. Gated on autoReconnect like every other resilience path
            // here (see connectedProfile's other users below).
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
            // lastConnectedProfileId (set by connect() on success, cleared by
            // disconnect()) tracks what was actually tunneling - unlike
            // activeProfileId, which is just the profile-picker's current
            // selection and can point somewhere else entirely if the user
            // browsed profiles without connecting after this one came up.
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
        // A new connect() (a fresh ACTION_CONNECT, or onRevoke's delayed
        // retry finally firing) always supersedes whatever this service was
        // previously trying to do - cancel it first so the two can't run
        // Mobile.startTunnel()/establish() concurrently against shared state.
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
                // No IPv6 data path exists yet (see gateway package) - routing
                // it here too means it's captured and dropped, not leaked
                // outside the tunnel.
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

            // detachFd() hands raw fd ownership to Go; Mobile.stopTunnel()
            // closes it on disconnect. Do not use `pfd` after this point.
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

    /**
     * Tells the running transport to redial immediately whenever the
     * system's default network changes (Wi-Fi <-> mobile data, or the same
     * network dropping and coming back) instead of waiting for a read or
     * write on the old socket to eventually time out - see
     * mobile.NetworkChanged's doc comment for why that can take far longer
     * than reconnecting proactively. Only registered for a profile that
     * asked for autoReconnect; unregistered in disconnect()/onDestroy().
     */
    private fun registerNetworkCallback() {
        // Unregister any callback already registered first - connect() can
        // run again (a manual reconnect, or the onRevoke/process-restart
        // retry paths) while a previous one is still live, and Android never
        // releases a registration on its own; without this every such cycle
        // permanently leaked one NetworkCallback (see the class doc on why
        // that matters beyond the leak itself).
        unregisterNetworkCallback()

        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        // NOT_VPN excludes the tunnel's own virtual network this service just
        // created - without it, the "default network" this app resolves to
        // could just be its own always-up VPN interface, which never changes
        // and defeats the point of watching for the underlying one dropping
        // out from under it.
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()
        var lastChangeAt = 0L
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // registerNetworkCallback (unlike registerDefaultNetworkCallback,
                // which can't see past this app's own VPN once it's up - see
                // the NOT_VPN comment above) reports EVERY matching network,
                // not just the one actually carrying this device's traffic: a
                // phone with Wi-Fi and mobile data both active fires this for
                // each independently, including on cellular radio power-
                // cycling that never touched the Wi-Fi path this tunnel was
                // actually using. Debounced so a burst of these forces at
                // most one reconnect, not one per network - an unthrottled
                // reconnect storm here was tearing down and rebuilding
                // multistream's already-open streams far more often than the
                // real network changes it was meant to recover from,
                // visible as choppy throughput and connections dying mid-load.
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

    /**
     * Applies the app-wide (not per-profile - see SettingsRepository) split
     * tunnel selection to a not-yet-established VpnService.Builder. Silently
     * skips a package that no longer exists (e.g. uninstalled after being
     * selected) rather than failing the whole connection over it.
     */
    private suspend fun applySplitTunneling(app: OpenFluxApplication, builder: Builder) {
        val mode = app.settingsRepository.splitTunnelMode.first()
        if (mode == SplitTunnelMode.OFF) return

        val packages = app.settingsRepository.splitTunnelApps.first()
        for (packageName in packages) {
            try {
                when (mode) {
                    SplitTunnelMode.EXCLUDE -> builder.addDisallowedApplication(packageName)
                    SplitTunnelMode.INCLUDE -> builder.addAllowedApplication(packageName)
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

    /**
     * Android revoked this app's VPN session - another VPN app took over,
     * the user (or an MDM policy) turned it off in system Settings, etc.
     * With autoReconnect on, treat this the same as any other connection
     * drop this service tries to recover from rather than giving up: the
     * old TUN fd is gone regardless, so reconnecting means establishing a
     * fresh one, same as connect() already does. The short delay avoids
     * hammering `establish()` in a tight loop if something keeps revoking
     * it right back (another VPN app actively holding the slot).
     */
    override fun onRevoke() {
        val profile = connectedProfile
        if (profile != null && profile.autoReconnect) {
            // Cancel any reconnect already scheduled/running before
            // scheduling this one - onRevoke can fire again (another VPN
            // app repeatedly grabbing and releasing the slot) before a
            // previous pending reconnect has finished, and without this the
            // two would independently call connect() around the same time.
            pendingReconnectJob?.cancel()
            connectJob?.cancel()
            // The old "stopped" transition disconnect() used to always send
            // gave the UI a clear signal the tunnel dropped; without any
            // update here it kept showing "Connected" for the whole gap
            // below even though traffic isn't flowing. "connecting" is an
            // existing, already-understood status (see MobileCallback) that
            // fits this window better than inventing a new one.
            callback.onStatus("connecting")
            pendingReconnectJob = serviceScope.launch {
                // Android already tore down the VPN interface by the time
                // onRevoke runs, but the Go side doesn't know that yet -
                // StartTunnel refuses a second call while it still thinks
                // one is running, so the now-defunct session has to be torn
                // down here first, same as a normal disconnect would.
                runCatching { Mobile.stopTunnel() }
                establishedFd = null
                delay(2000)
                connect(profile.id)
            }
            // Deliberately not calling super.onRevoke(): VpnService's
            // default implementation calls stopSelf(), which would destroy
            // this service - and the reconnect attempt just scheduled with
            // it - before the delay above ever elapses.
        } else {
            disconnect()
            super.onRevoke()
        }
    }

    override fun onDestroy() {
        unregisterNetworkCallback()
        runCatching { Mobile.stopTunnel() }
        // Without this, a coroutine already launched on serviceScope (e.g.
        // onRevoke's delayed reconnect, still sleeping out its delay(2000))
        // keeps running after the service is torn down and can still call
        // connect() -> startForeground()/registerNetworkCallback() against a
        // destroyed Service instance.
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

        /**
         * Shared across the app's lifetime: the UI observes this to render
         * connection status/traffic regardless of whether an Activity is
         * currently bound to the service.
         */
        val callback = MobileCallback()
    }
}
