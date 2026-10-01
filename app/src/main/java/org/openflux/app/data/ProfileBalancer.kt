package org.openflux.app.data

/**
 * Ranks profiles so connecting lands on one that works.
 *
 * Order of preference:
 *  1. profiles that are not in a failure cooldown, best proven first;
 *  2. untried profiles, which beat a profile that just failed - an untried config is the whole
 *     point of having more than one;
 *  3. profiles in cooldown, least-recently-failed first, so a balancer never returns an empty list.
 *
 * A profile the user pinned explicitly is not overridden here; that choice is made by the caller.
 */
object ProfileBalancer {

    fun pick(
        profiles: List<Profile>,
        health: Map<String, ProfileHealth>,
        isInCooldown: (String) -> Boolean,
    ): Profile? {
        if (profiles.isEmpty()) return null
        val now = System.currentTimeMillis()
        val fresh = profiles.filterNot { isInCooldown(it.id) }
        val untried = fresh.filter { health[it.id] == null }
        if (untried.isNotEmpty()) return untried.first()
        if (fresh.isEmpty()) {
            return profiles.maxByOrNull { now - (health[it.id]?.lastFailureAt ?: 0L) }
        }
        return fresh.minWithOrNull(
            compareBy<Profile> { health[it.id]?.lastSuccessAt ?: 0L }
                .thenBy { -(health[it.id]?.successes ?: 0) }
                .thenBy { health[it.id]?.failures ?: 0 },
        )
    }

    /** The same choice expressed for a picker, so the list can hint at what the balancer would pick. */
    fun bestId(
        profiles: List<Profile>,
        health: Map<String, ProfileHealth>,
        isInCooldown: (String) -> Boolean,
    ): String? = pick(profiles, health, isInCooldown)?.id
}