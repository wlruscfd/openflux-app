package org.openflux.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ProfileHealth(
    val successes: Int,
    val failures: Int,
    val lastSuccessAt: Long,
    val lastFailureAt: Long,
    val lastFailureReason: String,
)

private const val PREFS = "profile_health"
private const val K_OK = "ok"
private const val K_FAIL = "fail"
private const val K_LAST_OK = "lastOk"
private const val K_LAST_FAIL = "lastFail"
private const val K_REASON = "reason"

/** A profile that failed inside this window is skipped rather than retried first. */
private const val COOLDOWN_MS = 5 * 60 * 1000L

/**
 * Remembers how each profile has been behaving, so connecting can prefer the one that actually
 * works instead of always the one the user happened to tap last.
 *
 * Failures are scored harder than successes are rewarded, because a profile that just died is a
 * much worse first guess than one that has been quiet for a week.
 */
class ProfileHealthStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _health = MutableStateFlow(loadAll())

    val health: StateFlow<Map<String, ProfileHealth>> = _health.asStateFlow()

    fun of(profileId: String): ProfileHealth? = _health.value[profileId]

    fun recordSuccess(profileId: String) {
        val current = of(profileId) ?: ProfileHealth(0, 0, 0, 0, "")
        val now = System.currentTimeMillis()
        val next = current.copy(successes = current.successes + 1, lastSuccessAt = now)
        persist(profileId, next)
    }

    fun recordFailure(profileId: String, reason: String) {
        val current = of(profileId) ?: ProfileHealth(0, 0, 0, 0, "")
        val now = System.currentTimeMillis()
        val next = current.copy(
            failures = current.failures + 1,
            lastFailureAt = now,
            lastFailureReason = reason,
        )
        persist(profileId, next)
    }

    fun clear(profileId: String) {
        prefs.edit()
            .remove("$profileId.$K_OK")
            .remove("$profileId.$K_FAIL")
            .remove("$profileId.$K_LAST_OK")
            .remove("$profileId.$K_LAST_FAIL")
            .remove("$profileId.$K_REASON")
            .apply()
        _health.value = _health.value - profileId
    }

    fun isInCooldown(profileId: String): Boolean {
        val h = of(profileId) ?: return false
        return h.lastFailureAt > h.lastSuccessAt &&
            System.currentTimeMillis() - h.lastFailureAt < COOLDOWN_MS
    }

    private fun persist(profileId: String, h: ProfileHealth) {
        prefs.edit()
            .putInt("$profileId.$K_OK", h.successes)
            .putInt("$profileId.$K_FAIL", h.failures)
            .putLong("$profileId.$K_LAST_OK", h.lastSuccessAt)
            .putLong("$profileId.$K_LAST_FAIL", h.lastFailureAt)
            .putString("$profileId.$K_REASON", h.lastFailureReason)
            .apply()
        _health.value = _health.value + (profileId to h)
    }

    private fun loadAll(): Map<String, ProfileHealth> {
        val all = prefs.all
        val out = HashMap<String, ProfileHealth>()
        all.keys.filter { it.endsWith(".$K_OK") }.forEach { marker ->
            val id = marker.removeSuffix(".$K_OK")
            out[id] = ProfileHealth(
                successes = all[marker] as? Int ?: 0,
                failures = all["$id.$K_FAIL"] as? Int ?: 0,
                lastSuccessAt = all["$id.$K_LAST_OK"] as? Long ?: 0L,
                lastFailureAt = all["$id.$K_LAST_FAIL"] as? Long ?: 0L,
                lastFailureReason = all["$id.$K_REASON"] as? String ?: "",
            )
        }
        return out
    }
}