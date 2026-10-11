package org.openflux.app.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps managed key documents current. A cupsonline key gets its room list from the node after
 * creation, so a profile imported from an already-shared deep link can sit with a blank document
 * until a pass picks it up. The only document that ever changes is the node's room list, and that
 * changes when the node loses its rooms - so an hour is generous, not a heartbeat.
 */
class ProfileDocRefresher(
    private val scope: CoroutineScope,
    private val profileRepository: ProfileRepository,
    private val socksPort: () -> Int = { 0 },
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            while (isActive) {
                delay(REFRESH_INTERVAL_MS)
                refreshOnce(viaTunnel = false)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun refreshOnce(viaTunnel: Boolean): Int {
        val resolver = KeyResolver(socksPort)
        val profiles = profileRepository.observeAll().first()
        var updated = 0
        for (profile in profiles) {
            if (profile.docUrl.isNotBlank() || profile.docUrls.isNotEmpty()) continue
            val resolved = resolver.resolve(profile.controlUrl, profile.keyToken, viaTunnel) ?: continue
            val docUrl = resolved.docUrl
            val docUrls = resolved.docUrls
            if (docUrl.isBlank() && docUrls.isEmpty()) continue
            val transport = if (resolved.transportName.isNotBlank()) {
                parseTransportName(resolved.transportName)
            } else {
                profile.manualTransport
            }
            profileRepository.save(
                profile.copy(
                    docUrl = if (docUrl.isNotBlank()) docUrl else profile.docUrl,
                    docUrls = if (docUrls.isNotEmpty()) docUrls else profile.docUrls,
                    manualTransport = transport,
                    e2eEncryption = resolved.e2eEncryption || profile.e2eEncryption,
                ),
            )
            updated++
            Log.i("ProfileDocRefresher", "document published for ${profile.name}: $docUrl")
        }
        return updated
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 60 * 60 * 1000L
    }
}