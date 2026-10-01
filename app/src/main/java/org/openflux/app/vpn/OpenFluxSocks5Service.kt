package org.openflux.app.vpn

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mobile.Mobile
import org.openflux.app.MainActivity
import org.openflux.app.OpenFluxApplication
import org.openflux.app.R
import org.openflux.app.data.BALANCER_PROFILE_ID
import org.openflux.app.data.ManualTransport
import org.openflux.app.data.Profile
import org.openflux.app.data.ProfileBalancer
import org.openflux.app.data.isReadyToConnect
import org.openflux.app.data.toStartTunnelConfigJson

// Never touches VpnService; mutually exclusive with OpenFluxVpnService, enforced at the mobile.go layer.
class OpenFluxSocks5Service : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var connectJob: Job? = null
    private var connectedProfile: Profile? = null

    private val healthChecker = TunnelHealthChecker(serviceScope, callback.channelReady, callback.stats) { ok ->
        callback.setConnectivityOk(ok)
        if (ok == false) handleOneWayTraffic() else clearOneWayTrafficAlert()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stop()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                if (profileId != null) start(profileId)
                return START_NOT_STICKY
            }
        }
        return START_NOT_STICKY
    }

    private fun start(profileId: String) {
        val previousConnectJob = connectJob
        healthChecker.stop()
        clearOneWayTrafficAlert()
        callback.reset()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.socks5_notification_connecting)))

        connectJob = serviceScope.launch {
            previousConnectJob?.cancelAndJoin()
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
                app.profileHealthStore.recordFailure(profile.id, reason)
                stopSelf()
                return@launch
            }

            val port = app.settingsRepository.socks5Port.first()
            val listenAddr = "127.0.0.1:$port"

            runCatching { Mobile.stopSocks5Proxy() }

            try {
                Mobile.startSocks5Proxy(profile.toStartTunnelConfigJson(), listenAddr, callback)
            } catch (t: Throwable) {
                callback.onStatus("error:${t.message}")
                app.profileHealthStore.recordFailure(profile.id, t.message ?: "startSocks5Proxy failed")
                stopSelf()
                return@launch
            }

            connectedProfile = profile
            app.profileHealthStore.recordSuccess(profile.id)
            updateNotification(getString(R.string.socks5_notification_running, listenAddr))
            healthChecker.start()
        }
    }

    private fun stop() {
        connectJob?.cancel()
        connectedProfile = null
        healthChecker.stop()
        clearOneWayTrafficAlert()
        serviceScope.launch {
            runCatching { Mobile.stopSocks5Proxy() }
            callback.onStatus("stopped")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        connectJob?.cancel()
        healthChecker.stop()
        clearOneWayTrafficAlert()
        runCatching { Mobile.stopSocks5Proxy() }
        // The callback object outlives this Service instance, so a teardown that does not
        // publish a terminal status leaves the Home screen believing the proxy is still up -
        // which disables the VPN button with nothing left to stop.
        callback.onStatus("stopped")
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, OpenFluxSocks5Service::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, OpenFluxApplication.SOCKS5_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.socks5_notification_title))
            .setContentText(text)
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.vpn_notification_disconnect_action), stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun handleOneWayTraffic() {
        postOneWayTrafficAlert()
        val profile = connectedProfile ?: return
        val app = application as OpenFluxApplication
        app.profileHealthStore.recordFailure(profile.id, "one-way traffic")
        serviceScope.launch {
            if (app.settingsRepository.activeProfileId.first() != BALANCER_PROFILE_ID) return@launch
            val profiles = app.profileRepository.observeAll().first()
            val next = ProfileBalancer.pick(profiles, app.profileHealthStore.health.value, app.profileHealthStore::isInCooldown)
            if (next != null && next.id != profile.id) start(next.id)
        }
    }

    private fun postOneWayTrafficAlert() {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, OpenFluxApplication.ALERTS_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.alerts_one_way_traffic_title))
            .setContentText(getString(R.string.alerts_one_way_traffic_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(getString(R.string.alerts_one_way_traffic_text)))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        getSystemService(android.app.NotificationManager::class.java).notify(ONE_WAY_ALERT_NOTIFICATION_ID, notification)
    }

    private fun clearOneWayTrafficAlert() {
        getSystemService(android.app.NotificationManager::class.java).cancel(ONE_WAY_ALERT_NOTIFICATION_ID)
    }

    companion object {
        const val ACTION_START = "org.openflux.app.action.SOCKS5_START"
        const val ACTION_STOP = "org.openflux.app.action.SOCKS5_STOP"
        const val EXTRA_PROFILE_ID = "profile_id"

        private const val NOTIFICATION_ID = 2
        private const val ONE_WAY_ALERT_NOTIFICATION_ID = 4

        // Kept separate from [OpenFluxVpnService.callback] so one starting doesn't clobber the other's status history.
        val callback = MobileCallback()
    }
}
