package org.openflux.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileBalancerTest {
    private fun profile(id: String) = Profile(id = id, name = id, mode = ProfileMode.MANUAL)

    private fun health(ok: Int = 0, fail: Int = 0, lastOk: Long = 0, lastFail: Long = 0) =
        ProfileHealth(ok, fail, lastOk, lastFail, "")

    @Test
    fun noProfilesPicksNothing() {
        assertNull(ProfileBalancer.pick(emptyList(), emptyMap()) { false })
    }

    @Test
    fun anUntriedProfileBeatsOneThatWorkedBefore() {
        val profiles = listOf(profile("old"), profile("new"))
        val picked = ProfileBalancer.pick(profiles, mapOf("old" to health(ok = 5, lastOk = 100))) { false }
        assertEquals("new", picked?.id)
    }

    @Test
    fun aProfileInCooldownIsSkippedWhileAnotherIsAvailable() {
        val profiles = listOf(profile("down"), profile("up"))
        val h = mapOf("down" to health(fail = 3, lastFail = 900), "up" to health(ok = 1, lastOk = 500))
        assertEquals("up", ProfileBalancer.pick(profiles, h) { it == "down" }?.id)
    }

    @Test
    fun theLeastRecentlyFailedWinsWhenEverythingIsInCooldown() {
        val profiles = listOf(profile("recent"), profile("stale"))
        val h = mapOf("recent" to health(fail = 1, lastFail = 10_000), "stale" to health(fail = 1, lastFail = 1_000))
        assertEquals("stale", ProfileBalancer.pick(profiles, h) { true }?.id)
    }

    @Test
    fun theMostRecentlyProvenProfileIsNotTheOneChosenFirst() {
        val profiles = listOf(profile("a"), profile("b"))
        val h = mapOf("a" to health(ok = 2, lastOk = 5_000), "b" to health(ok = 2, lastOk = 1_000))
        assertEquals("b", ProfileBalancer.pick(profiles, h) { false }?.id)
    }
}
