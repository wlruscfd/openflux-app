package org.openflux.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.openflux.app.data.AppDatabase
import org.openflux.app.data.CookiePushStore
import org.openflux.app.data.DeployServerRepository
import org.openflux.app.deploy.DeployManager
import org.openflux.app.data.DeployServerSecretsStore
import org.openflux.app.data.ProfileDocRefresher
import org.openflux.app.data.ProfileHealthStore
import org.openflux.app.data.ProfileRepository
import org.openflux.app.data.SecretsStore
import org.openflux.app.data.SettingsRepository
import org.openflux.app.data.TransportUpdater

class OpenFluxApplication : Application() {

    lateinit var profileRepository: ProfileRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var deployServerRepository: DeployServerRepository
        private set

    lateinit var cookiePushStore: CookiePushStore
        private set

    lateinit var profileHealthStore: ProfileHealthStore
        private set

    lateinit var docRefresher: ProfileDocRefresher
        private set

    lateinit var transportUpdater: TransportUpdater
        private set

    override fun onCreate() {
        super.onCreate()

        org.openflux.app.data.ControlTrust.init(this)
        val db = AppDatabase.build(this)
        val secrets = SecretsStore(this)
        profileRepository = ProfileRepository(db.profileDao(), secrets)
        settingsRepository = SettingsRepository(this)
        deployServerRepository = DeployServerRepository(db.deployServerDao(), DeployServerSecretsStore(this))
        cookiePushStore = CookiePushStore(this)
        DeployManager.init(this, deployServerRepository)
        profileHealthStore = ProfileHealthStore(this)
        profileRepository = profileRepository
        docRefresher = ProfileDocRefresher(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            profileRepository = profileRepository,
            socksPort = { runBlocking { settingsRepository.socks5Port.first() } },
        ).also { it.start() }

        transportUpdater = TransportUpdater(this, settingsRepository)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            delay(TRANSPORT_UPDATE_FIRST_DELAY_MS)
            while (true) {
                if (transportUpdater.scriptsDir() != null && settingsRepository.transportsAutoUpdate.first()) transportUpdater.updateAll()
                delay(TRANSPORT_UPDATE_INTERVAL_MS)
            }
        }

        createVpnNotificationChannel()
        createSocks5NotificationChannel()
        createAlertsNotificationChannel()
    }

    private fun createVpnNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            VPN_NOTIFICATION_CHANNEL_ID,
            getString(R.string.vpn_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    // Separate from the VPN channel since a SOCKS5 proxy isn't a VPN connection.
    private fun createSocks5NotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            SOCKS5_NOTIFICATION_CHANNEL_ID,
            getString(R.string.socks5_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    // Separate from the ongoing VPN/SOCKS5 channels (both LOW importance, silent) since a one-way
    // tunnel is worth actually alerting on, not just quietly updating a status line.
    private fun createAlertsNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            ALERTS_NOTIFICATION_CHANNEL_ID,
            getString(R.string.alerts_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val TRANSPORT_UPDATE_FIRST_DELAY_MS = 20_000L
        private const val TRANSPORT_UPDATE_INTERVAL_MS = 6 * 60 * 60 * 1000L
        const val VPN_NOTIFICATION_CHANNEL_ID = "openflux_vpn"
        const val SOCKS5_NOTIFICATION_CHANNEL_ID = "openflux_socks5"
        const val ALERTS_NOTIFICATION_CHANNEL_ID = "openflux_alerts"
    }
}
