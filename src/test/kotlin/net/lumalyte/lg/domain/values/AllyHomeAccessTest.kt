package net.lumalyte.lg.domain.values

import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** REQ-182: the host's own rules for who may use its allied homes. */
class AllyHomeAccessTest {
    private val ally = UUID.randomUUID()
    private val officer = UUID.randomUUID()
    private val recruit = UUID.randomUUID()
    private val player = UUID.randomUUID()

    @Test
    fun `with no rules every rank of an allowed ally is admitted`() {
        assertTrue(AllyHomeAccess().admits(ally, recruit, player))
    }

    @Test
    fun `picked ranks admit only those ranks`() {
        val access = AllyHomeAccess(visitorRanks = mapOf(ally to setOf(officer)))
        assertTrue(access.admits(ally, officer, player))
        assertFalse(access.admits(ally, recruit, player))
    }

    @Test
    fun `an empty pick admits no one from that ally`() {
        assertFalse(AllyHomeAccess(visitorRanks = mapOf(ally to emptySet())).admits(ally, officer, player))
    }

    @Test
    fun `a blocked player is refused whatever their rank`() {
        assertFalse(AllyHomeAccess(blockedPlayers = setOf(player)).admits(ally, officer, player))
    }

    @Test
    fun `rules for one ally do not touch another`() {
        val other = UUID.randomUUID()
        assertTrue(AllyHomeAccess(visitorRanks = mapOf(ally to setOf(officer))).admits(other, recruit, player))
    }

    // REQ-183: the visitor's own guild picks its ranks per ally.
    @Test
    fun `with no outbound pick the rank permission decides`() {
        assertTrue(AllyHomeOutbound.allows(null, recruit, isOwner = false, hasUsePermission = true))
        assertFalse(AllyHomeOutbound.allows(null, recruit, isOwner = false, hasUsePermission = false))
    }

    @Test
    fun `an outbound pick overrides the rank permission both ways`() {
        assertTrue(AllyHomeOutbound.allows(setOf(recruit), recruit, isOwner = false, hasUsePermission = false))
        assertFalse(AllyHomeOutbound.allows(setOf(officer), recruit, isOwner = false, hasUsePermission = true))
    }

    @Test
    fun `the owner always keeps access on their own side`() {
        assertTrue(AllyHomeOutbound.allows(emptySet(), officer, isOwner = true, hasUsePermission = false))
    }
}
