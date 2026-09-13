package org.openflux.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "openflux_settings")

/**
 * OFF: full tunnel, no exclusions (default).
 * EXCLUDE: every app in splitTunnelApps bypasses the VPN; everything else is tunneled.
 * INCLUDE: only the apps in splitTunnelApps are tunneled; everything else bypasses it.
 */
enum class SplitTunnelMode { OFF, EXCLUDE, INCLUDE }

/** App-level (not per-profile) flexible settings. */
class SettingsRepository(private val context: Context) {

    val activeProfileId: Flow<String?> =
        context.dataStore.data.map { it[Keys.ACTIVE_PROFILE_ID] }

    val startOnBoot: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.START_ON_BOOT] ?: false }

    val defaultMtu: Flow<Int> =
        context.dataStore.data.map { it[Keys.DEFAULT_MTU] ?: 1400 }

    val defaultDns: Flow<String> =
        context.dataStore.data.map { it[Keys.DEFAULT_DNS] ?: "77.88.8.8" }

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

    suspend fun setActiveProfileId(id: String?) {
        context.dataStore.edit {
            if (id == null) it.remove(Keys.ACTIVE_PROFILE_ID) else it[Keys.ACTIVE_PROFILE_ID] = id
        }
    }

    suspend fun setStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[Keys.START_ON_BOOT] = enabled }
    }

    suspend fun setDefaultMtu(mtu: Int) {
        context.dataStore.edit { it[Keys.DEFAULT_MTU] = mtu }
    }

    suspend fun setDefaultDns(dns: String) {
        context.dataStore.edit { it[Keys.DEFAULT_DNS] = dns }
    }

    suspend fun setSplitTunnelMode(mode: SplitTunnelMode) {
        context.dataStore.edit { it[Keys.SPLIT_TUNNEL_MODE] = mode.name }
    }

    suspend fun setSplitTunnelApps(packages: Set<String>) {
        context.dataStore.edit { it[Keys.SPLIT_TUNNEL_APPS] = packages }
    }

    suspend fun setSplitTunnelSitesMode(mode: SplitTunnelMode) {
        context.dataStore.edit { it[Keys.SPLIT_TUNNEL_SITES_MODE] = mode.name }
    }

    suspend fun setSplitTunnelSites(sites: Set<String>) {
        context.dataStore.edit { it[Keys.SPLIT_TUNNEL_SITES] = sites }
    }

    private object Keys {
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val DEFAULT_MTU = intPreferencesKey("default_mtu")
        val DEFAULT_DNS = stringPreferencesKey("default_dns")
        val SPLIT_TUNNEL_MODE = stringPreferencesKey("split_tunnel_mode")
        val SPLIT_TUNNEL_APPS = stringSetPreferencesKey("split_tunnel_apps")
        val SPLIT_TUNNEL_SITES_MODE = stringPreferencesKey("split_tunnel_sites_mode")
        val SPLIT_TUNNEL_SITES = stringSetPreferencesKey("split_tunnel_sites")
    }
}
