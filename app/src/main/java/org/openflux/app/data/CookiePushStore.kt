package org.openflux.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class CookiePushOutcome { NONE, SENT, FAILED }

data class CookiePushRecord(
    val outcome: CookiePushOutcome,
    val reason: PushFailure?,
    val route: PushRoute,
    val atMillis: Long,
)

private const val PREFS = "cookie_push_status"
private const val KEY_OUTCOME = "outcome"
private const val KEY_REASON = "reason"
private const val KEY_ROUTE = "route"
private const val KEY_AT = "at"

/**
 * Keeps the last cookie-push result per profile.
 *
 * A transient message is not enough here: the interesting question is whether the exit node ever
 * received the jar, and that stays true long after the toast is gone. Losing it on rotation or on
 * navigating away left the profile looking exactly like one that was never checked.
 */
class CookiePushStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _records = MutableStateFlow(loadAll())

    val records: StateFlow<Map<String, CookiePushRecord>> = _records.asStateFlow()

    fun of(profileId: String): CookiePushRecord? = _records.value[profileId]

    fun record(profileId: String, outcome: CookiePushOutcome, reason: PushFailure?, route: PushRoute) {
        val stored = CookiePushRecord(outcome, reason, route, System.currentTimeMillis())
        prefs.edit()
            .putString("$profileId.$KEY_OUTCOME", outcome.name)
            .putString("$profileId.$KEY_REASON", reason?.name ?: "")
            .putString("$profileId.$KEY_ROUTE", route.name)
            .putLong("$profileId.$KEY_AT", stored.atMillis)
            .apply()
        _records.value = _records.value + (profileId to stored)
    }

    fun clear(profileId: String) {
        prefs.edit()
            .remove("$profileId.$KEY_OUTCOME")
            .remove("$profileId.$KEY_REASON")
            .remove("$profileId.$KEY_ROUTE")
            .remove("$profileId.$KEY_AT")
            .apply()
        _records.value = _records.value - profileId
    }

    private fun loadAll(): Map<String, CookiePushRecord> {
        val all = prefs.all
        val out = HashMap<String, CookiePushRecord>()
        all.keys.filter { it.endsWith(".$KEY_OUTCOME") }.forEach { marker ->
            val profileId = marker.removeSuffix(".$KEY_OUTCOME")
            val outcome = runCatching { CookiePushOutcome.valueOf(all[marker] as String) }.getOrNull()
                ?: return@forEach
            val reason = (all["$profileId.$KEY_REASON"] as? String)?.takeIf { it.isNotBlank() }
                ?.let { name -> runCatching { PushFailure.valueOf(name) }.getOrNull() }
            val route = (all["$profileId.$KEY_ROUTE"] as? String)
                ?.let { name -> runCatching { PushRoute.valueOf(name) }.getOrNull() }
                ?: PushRoute.DIRECT
            out[profileId] = CookiePushRecord(outcome, reason, route, all["$profileId.$KEY_AT"] as? Long ?: 0L)
        }
        return out
    }
}