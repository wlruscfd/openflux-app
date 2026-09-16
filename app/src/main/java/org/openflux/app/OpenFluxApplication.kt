package org.openflux.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import org.openflux.app.data.AppDatabase
import org.openflux.app.data.DeployServerRepository
import org.openflux.app.data.DeployServerSecretsStore
import org.openflux.app.data.ProfileRepository
import org.openflux.app.data.SecretsStore
import org.openflux.app.data.SettingsRepository

class OpenFluxApplication : Application() {

    lateinit var profileRepository: ProfileRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var deployServerRepository: DeployServerRepository
        private set

    override fun onCreate() {
        super.onCreate()

        val db = AppDatabase.build(this)
        val secrets = SecretsStore(this)
        profileRepository = ProfileRepository(db.profileDao(), secrets)
        settingsRepository = SettingsRepository(this)
        deployServerRepository = DeployServerRepository(db.deployServerDao(), DeployServerSecretsStore(this))

        createVpnNotificationChannel()
        createSocks5NotificationChannel()
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

    // Separate from the VPN channel - OpenFluxSocks5Service's notification
    // ("SOCKS5 proxy running on 127.0.0.1:1080") would read oddly under a
    // channel literally named "OpenFlux VPN" when it isn't a VPN connection
    // at all.
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

    companion object {
        const val VPN_NOTIFICATION_CHANNEL_ID = "openflux_vpn"
        const val SOCKS5_NOTIFICATION_CHANNEL_ID = "openflux_socks5"
    }
}
