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
                forceBootstrapDns = profile.forceBootstrapDns,
                autoReconnect = profile.autoReconnect,
                e2eEncryption = profile.e2eEncryption,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            ),
        )
        secrets.save(
            id,
            ProfileSecrets(
                controlUrl = profile.controlUrl,
                keyToken = profile.keyToken,
                docUrl = profile.docUrl,
                docUrls = profile.docUrls,
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

    private fun ProfileEntity.toProfile(s: ProfileSecrets): Profile {
        val transport = runCatching { ManualTransport.valueOf(manualTransport) }.getOrDefault(ManualTransport.YANDEX)
        return Profile(
            id = id,
            name = name,
            mode = runCatching { ProfileMode.valueOf(mode) }.getOrDefault(ProfileMode.MANUAL),
            controlUrl = s.controlUrl,
            keyToken = s.keyToken,
            manualTransport = transport,
            docUrl = s.docUrl,
            docUrls = s.docUrls,
            maxToken = s.maxToken,
            maxUid = s.maxUid,
            mtu = mtu,
            dnsUpstream = dnsUpstream,
            forceBootstrapDns = forceBootstrapDns,
            autoReconnect = autoReconnect,
            e2eEncryption = e2eEncryption && transport != ManualTransport.MAILRU && transport != ManualTransport.BOARDS,
        )
    }
}

// Connecting always uses the already-known doc_url, never a live controlplane request a hostile network could block.
fun Profile.toStartTunnelConfigJson(
    siteSplitMode: SplitTunnelMode = SplitTunnelMode.OFF,
    siteSplitSites: Set<String> = emptySet(),
    verboseLogging: Boolean = false,
): String = JSONObject().apply {
    put("mode", "manual")
    put("transport", transportName(manualTransport))
    when (manualTransport) {
        ManualTransport.YANDEX, ManualTransport.VOLGA, ManualTransport.MAILRU, ManualTransport.BOARDS, ManualTransport.MTS -> put("doc_url", docUrl)
        ManualTransport.MAX -> {
            put("max_token", maxToken)
            put("max_uid", maxUid)
        }
        ManualTransport.YANDEX_MULTISTREAM ->
            put("doc_urls", org.json.JSONArray(docUrls.map { it.trim() }.filter { it.isNotEmpty() }))
    }
    put("mtu", mtu)
    put("dns_upstream", dnsUpstream)
    if (keyToken.isNotBlank()) {
        put("key_token", keyToken)
        if (e2eEncryption && manualTransport != ManualTransport.MAILRU && manualTransport != ManualTransport.BOARDS) {
            put("e2e_encryption", true)
        }
    }
    if (forceBootstrapDns.isNotBlank()) {
        put("force_bootstrap_dns", forceBootstrapDns)
    }
    if (verboseLogging) {
        put("verbose_logging", true)
    }
    if (siteSplitMode != SplitTunnelMode.OFF && siteSplitSites.isNotEmpty()) {
        // Lowercasing the enum name gives the wire strings ParseSiteSplitMode expects.
        put("site_split_mode", siteSplitMode.name.lowercase())
        put("site_split_sites", org.json.JSONArray(siteSplitSites.toList().sorted()))
    }
}.toString()

// False for an unresolved KEY profile, or YANDEX_MULTISTREAM with fewer than 2 usable doc_urls.
val Profile.isReadyToConnect: Boolean
    get() {
        if (manualTransport == ManualTransport.YANDEX_MULTISTREAM) {
            return docUrls.count { it.isNotBlank() } >= 2
        }
        return when (mode) {
            ProfileMode.KEY -> docUrl.isNotBlank()
            ProfileMode.MANUAL -> true
        }
    }
