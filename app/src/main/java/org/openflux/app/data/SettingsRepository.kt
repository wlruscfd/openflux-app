package org.openflux.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore(name = "openflux_settings")

// EXCLUDE: listed apps bypass the VPN. INCLUDE: only listed apps are tunneled. OFF: full tunnel (default).
enum class SplitTunnelMode { OFF, EXCLUDE, INCLUDE }

/** App-level (not per-profile) flexible settings. */
class SettingsRepository(private val context: Context) {

    val activeProfileId: Flow<String?> =
        context.dataStore.data.map { it[Keys.ACTIVE_PROFILE_ID] }

    // Distinct from activeProfileId (a UI-picker selection); this tracks what the tunnel is actually running.
    val lastConnectedProfileId: Flow<String?> =
        context.dataStore.data.map { it[Keys.LAST_CONNECTED_PROFILE_ID] }

    val startOnBoot: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.START_ON_BOOT] ?: false }

    val defaultMtu: Flow<Int> =
        context.dataStore.data.map { it[Keys.DEFAULT_MTU] ?: 1400 }

    val defaultDns: Flow<String> =
        context.dataStore.data.map { it[Keys.DEFAULT_DNS] ?: "77.88.8.8" }

    val verboseLogging: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.VERBOSE_LOGGING] ?: false }

    val splitTunnelMode: Flow<SplitTunnelMode> =
        context.dataStore.data.map {
            runCatching { SplitTunnelMode.valueOf(it[Keys.SPLIT_TUNNEL_MODE] ?: "") }.getOrDefault(SplitTunnelMode.OFF)
        }

    val splitTunnelApps: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.SPLIT_TUNNEL_APPS] ?: emptySet() }

    val splitTunnelSitesMode: Flow<SplitTunnelMode> =
        context.dataStore.data.map {
            runCatching { SplitTunnelMode.valueOf(it[Keys.SPLIT_TUNNEL_SITES_MODE] ?: "") }.getOrDefault(SplitTunnelMode.OFF)
        }

    val splitTunnelSites: Flow<Set<String>> =
        context.dataStore.data.map { it[Keys.SPLIT_TUNNEL_SITES] ?: emptySet() }

    // Configurable since 1080 can already be taken by something else on the device.
    val socks5Port: Flow<Int> =
        context.dataStore.data.map { it[Keys.SOCKS5_PORT] ?: 1080 }

    // NonCancellable: a fast tab switch tears down the ViewModel and would otherwise cancel the write mid-flight.
    suspend fun setActiveProfileId(id: String?) = withContext(NonCancellable) {
        context.dataStore.edit {
            if (id == null) it.remove(Keys.ACTIVE_PROFILE_ID) else it[Keys.ACTIVE_PROFILE_ID] = id
        }
    }

    suspend fun setLastConnectedProfileId(id: String?) = withContext(NonCancellable) {
        context.dataStore.edit {
            if (id == null) it.remove(Keys.LAST_CONNECTED_PROFILE_ID) else it[Keys.LAST_CONNECTED_PROFILE_ID] = id
        }
    }

    suspend fun setStartOnBoot(enabled: Boolean) = withContext(NonCancellable) {
        context.dataStore.edit { it[Keys.START_ON_BOOT] = enabled }
    }

    suspend fun setDefaultMtu(mtu: Int) = withContext(NonCancellable) {
        context.dataStore.edit { it[Keys.DEFAULT_MTU] = mtu }
    }

    suspend fun setDefaultDns(dns: String) = withContext(NonCancellable) {
        context.dataStore.edit { it[Keys.DEFAULT_DNS] = dns }
    }

    suspend fun setVerboseLogging(enabled: Boolean) = withContext(NonCancellable) {
        context.dataStore.edit { it[Keys.VERBOSE_LOGGING] = enabled }
    }

    suspend fun setSplitTunnelMode(mode: SplitTunnelMode) = withContext(NonCancellable) {
        context.dataStore.edit { it[Keys.SPLIT_TUNNEL_MODE] = mode.name }
    }

    suspend fun setSplitTunnelApps(packages: Set<String>) = withContext(NonCancellable) {
        context.dataStore.edit { it[Keys.SPLIT_TUNNEL_APPS] = packages }
    }

    suspend fun setSplitTunnelSitesMode(mode: SplitTunnelMode) = withContext(NonCancellable) {
        context.dataStore.edit { it[Keys.SPLIT_TUNNEL_SITES_MODE] = mode.name }
    }

    suspend fun setSplitTunnelSites(sites: Set<String>) = withContext(NonCancellable) {
        context.dataStore.edit { it[Keys.SPLIT_TUNNEL_SITES] = sites }
    }

    suspend fun setSocks5Port(port: Int) = withContext(NonCancellable) {
        context.dataStore.edit { it[Keys.SOCKS5_PORT] = port }
    }

    private object Keys {
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val LAST_CONNECTED_PROFILE_ID = stringPreferencesKey("last_connected_profile_id")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val DEFAULT_MTU = intPreferencesKey("default_mtu")
        val DEFAULT_DNS = stringPreferencesKey("default_dns")
        val VERBOSE_LOGGING = booleanPreferencesKey("verbose_logging")
        val SPLIT_TUNNEL_MODE = stringPreferencesKey("split_tunnel_mode")
        val SPLIT_TUNNEL_APPS = stringSetPreferencesKey("split_tunnel_apps")
        val SPLIT_TUNNEL_SITES_MODE = stringPreferencesKey("split_tunnel_sites_mode")
        val SPLIT_TUNNEL_SITES = stringSetPreferencesKey("split_tunnel_sites")
        val SOCKS5_PORT = intPreferencesKey("socks5_port")
    }
}
