package org.openflux.app.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps managed key documents current. A cupsonline key gets its room list from the node after
 * creation, so a profile imported from an already-shared deep link can sit with a blank document
 * until a pass picks it up. A pass runs shortly after the app starts and keeps retrying every few
 * minutes while any profile is still blank; once every document is in, it only re-checks hourly,
 * because the one document that changes is the node's room list, and that changes when the node
 * loses its rooms. Connecting resolves a blank document on the spot as well ([ensureResolved]).
 */
class ProfileDocRefresher(
    private val scope: CoroutineScope,
    private val profileRepository: ProfileRepository,
    private val socksPort: () -> Int = { 0 },
) {
    private var job: Job? = null
    private val saveLock = Mutex()

    private class Pass(val updated: Int, val stillBlank: Int)

    fun start() {
        if (job != null) return
        job = scope.launch {
            delay(FIRST_PASS_DELAY_MS)
            while (isActive) {
                val pass = runCatching { pass(viaTunnel = false) }.getOrNull()
                delay(if (pass == null || pass.stillBlank > 0) BLANK_RETRY_INTERVAL_MS else REFRESH_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    suspend fun refreshOnce(viaTunnel: Boolean): Int = pass(viaTunnel).updated

    /** Returns the profile with its document filled in when the key can be resolved now, otherwise it unchanged. */
    suspend fun ensureResolved(profile: Profile): Profile {
        if (profile.isReadyToConnect || profile.mode != ProfileMode.KEY) return profile
        return resolve(profile, viaTunnel = false) ?: profile
    }

    private suspend fun pass(viaTunnel: Boolean): Pass {
        val profiles = profileRepository.observeAll().first()
        var updated = 0
        var stillBlank = 0
        for (profile in profiles) {
            if (!isBlank(profile)) continue
            if (resolve(profile, viaTunnel) != null) updated++ else stillBlank++
        }
        return Pass(updated, stillBlank)
    }

    private fun isBlank(profile: Profile) = profile.docUrl.isBlank() && profile.docUrls.isEmpty() &&
        profile.controlUrl.isNotBlank() && profile.keyToken.isNotBlank()

    private suspend fun resolve(profile: Profile, viaTunnel: Boolean): Profile? {
        val resolved = KeyResolver(socksPort).resolve(profile.controlUrl, profile.keyToken, viaTunnel) ?: return null
        val docUrl = resolved.docUrl
        val docUrls = resolved.docUrls
        if (docUrl.isBlank() && docUrls.isEmpty()) return null
        val transport = if (resolved.transportName.isNotBlank()) {
            parseTransportName(resolved.transportName)
        } else {
            profile.manualTransport
        }
        val saved = saveLock.withLock {
            val current = profileRepository.getById(profile.id) ?: return null
            if (current.docUrl.isNotBlank() || current.docUrls.isNotEmpty()) return current
            profileRepository.save(
                current.copy(
                    docUrl = if (docUrl.isNotBlank()) docUrl else current.docUrl,
                    docUrls = if (docUrls.isNotEmpty()) docUrls else current.docUrls,
                    manualTransport = transport,
                    e2eEncryption = resolved.e2eEncryption || current.e2eEncryption,
                ),
            )
        }
        Log.i("ProfileDocRefresher", "document published for ${profile.name}: $docUrl")
        return saved
    }

    companion object {
        private const val FIRST_PASS_DELAY_MS = 5_000L
        private const val BLANK_RETRY_INTERVAL_MS = 5 * 60 * 1000L
        private const val REFRESH_INTERVAL_MS = 60 * 60 * 1000L
    }
}
