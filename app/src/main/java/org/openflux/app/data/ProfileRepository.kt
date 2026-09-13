package org.openflux.app.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

class ProfileRepository(
    private val dao: ProfileDao,
    private val secrets: SecretsStore,
) {
    fun observeAll(): Flow<List<Profile>> =
        dao.observeAll().map { entities -> entities.map { it.toProfile(secrets.load(it.id)) } }

    suspend fun getById(id: String): Profile? =
        dao.getById(id)?.let { it.toProfile(secrets.load(it.id)) }

    /** Inserts a new profile (empty id) or updates an existing one. */
    suspend fun save(profile: Profile): Profile {
        val id = profile.id.ifBlank { UUID.randomUUID().toString() }
        val existing = if (profile.id.isBlank()) null else dao.getById(id)

        dao.upsert(
            ProfileEntity(
                id = id,
                name = profile.name,
                mode = profile.mode.name,
                manualTransport = profile.manualTransport.name,
                mtu = profile.mtu,
                dnsUpstream = profile.dnsUpstream,
                autoReconnect = profile.autoReconnect,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            ),
        )
        secrets.save(
            id,
            ProfileSecrets(
                controlUrl = profile.controlUrl,
                keyToken = profile.keyToken,
                docUrl = profile.docUrl,
                maxToken = profile.maxToken,
                maxUid = profile.maxUid,
            ),
        )
        return profile.copy(id = id)
    }

    suspend fun delete(id: String) {
        dao.deleteById(id)
        secrets.delete(id)
    }

    private fun ProfileEntity.toProfile(s: ProfileSecrets): Profile = Profile(
        id = id,
        name = name,
        mode = runCatching { ProfileMode.valueOf(mode) }.getOrDefault(ProfileMode.MANUAL),
        controlUrl = s.controlUrl,
        keyToken = s.keyToken,
        manualTransport = runCatching { ManualTransport.valueOf(manualTransport) }.getOrDefault(ManualTransport.YANDEX),
        docUrl = s.docUrl,
        maxToken = s.maxToken,
        maxUid = s.maxUid,
        mtu = mtu,
        dnsUpstream = dnsUpstream,
        autoReconnect = autoReconnect,
    )
}

/**
 * Builds the JSON contract the `mobile` Go package's StartTunnel expects
 * (see mobile/mobile.go's Config struct) from this profile.
 *
 * KEY and MANUAL profiles connect identically: control_url/key_token are
 * only ever used for the explicit, user-initiated "check key" action (see
 * ProfileEditScreen) - connecting always uses the already-known doc_url,
 * either typed in directly (MANUAL) or cached from a deep link import or a
 * past "check key" (KEY), so it never depends on a live, unshielded request
 * to the controlplane that a hostile network could block. Callers must not
 * call this for a KEY profile with a blank docUrl - see
 * Profile.isReadyToConnect.
 */
fun Profile.toStartTunnelConfigJson(
    siteSplitMode: SplitTunnelMode = SplitTunnelMode.OFF,
    siteSplitSites: Set<String> = emptySet(),
): String = JSONObject().apply {
    put("mode", "manual")
    put("transport", transportName(manualTransport))
    when (manualTransport) {
        ManualTransport.YANDEX, ManualTransport.VOLGA -> put("doc_url", docUrl)
        ManualTransport.MAX -> {
            put("max_token", maxToken)
            put("max_uid", maxUid)
        }
    }
    put("mtu", mtu)
    put("dns_upstream", dnsUpstream)
    if (siteSplitMode != SplitTunnelMode.OFF && siteSplitSites.isNotEmpty()) {
        // lowercasing the enum's name gives exactly the wire strings the Go
        // package's ParseSiteSplitMode expects ("exclude"/"include").
        put("site_split_mode", siteSplitMode.name.lowercase())
        put("site_split_sites", org.json.JSONArray(siteSplitSites.toList().sorted()))
    }
}.toString()

/**
 * False only for a KEY profile that has never been resolved (no deep-link
 * import, no successful "check key" yet) - connecting it would need a live
 * controlplane request this app no longer makes automatically.
 */
val Profile.isReadyToConnect: Boolean
    get() = when (mode) {
        ProfileMode.KEY -> docUrl.isNotBlank()
        ProfileMode.MANUAL -> true
    }
