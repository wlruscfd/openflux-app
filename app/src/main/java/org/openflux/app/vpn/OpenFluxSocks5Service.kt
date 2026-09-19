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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import mobile.Mobile
import org.openflux.app.MainActivity
import org.openflux.app.OpenFluxApplication
import org.openflux.app.R
import org.openflux.app.data.ManualTransport
import org.openflux.app.data.isReadyToConnect
import org.openflux.app.data.toStartTunnelConfigJson

// Never touches VpnService; mutually exclusive with OpenFluxVpnService, enforced at the mobile.go layer.
class OpenFluxSocks5Service : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var connectJob: Job? = null

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
        connectJob?.cancel()
        callback.reset()
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.socks5_notification_connecting)))

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

            val port = app.settingsRepository.socks5Port.first()
            val listenAddr = "127.0.0.1:$port"

            try {
                Mobile.startSocks5Proxy(profile.toStartTunnelConfigJson(), listenAddr, callback)
            } catch (t: Throwable) {
                callback.onStatus("error:${t.message}")
                stopSelf()
                return@launch
            }

            updateNotification(getString(R.string.socks5_notification_running, listenAddr))
        }
    }

    private fun stop() {
        connectJob?.cancel()
        serviceScope.launch {
            runCatching { Mobile.stopSocks5Proxy() }
            callback.onStatus("stopped")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        connectJob?.cancel()
        runCatching { Mobile.stopSocks5Proxy() }
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

    companion object {
        const val ACTION_START = "org.openflux.app.action.SOCKS5_START"
        const val ACTION_STOP = "org.openflux.app.action.SOCKS5_STOP"
        const val EXTRA_PROFILE_ID = "profile_id"

        private const val NOTIFICATION_ID = 2

        // Kept separate from [OpenFluxVpnService.callback] so one starting doesn't clobber the other's status history.
        val callback = MobileCallback()
    }
}
