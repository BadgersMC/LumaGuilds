package net.lumalyte.lg.application.services

import net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.GuildHome
import net.lumalyte.lg.domain.values.Position3D
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuildHomeClaimPolicyTest {
    private val guildId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val worldId = UUID.randomUUID()
    private val position = Position3D(15, 67, -23)
    private val home = GuildHome(worldId, position)

    private fun claim(owner: UUID?) = GetClaimAtPositionResult.Success(
        Claim(worldId, actorId, owner, position, "Claim")
    )

    @Test
    fun `enabled claim policy only accepts owned claim at the actual world and position`() {
        assertTrue(GuildHomeClaimPolicy.allows(guildId, home, true) { world, at ->
            assertTrue(world == worldId && at == position)
            claim(guildId)
        })
        assertFalse(GuildHomeClaimPolicy.allows(guildId, home, true) { _, _ -> claim(null) })
        assertFalse(GuildHomeClaimPolicy.allows(guildId, home, true) { _, _ -> claim(UUID.randomUUID()) })
    }

    @Test
    fun `enabled claim policy fails closed on unclaimed storage errors missing resolver and exceptions`() {
        assertFalse(GuildHomeClaimPolicy.allows(guildId, home, true) { _, _ -> GetClaimAtPositionResult.NoClaimFound })
        assertFalse(GuildHomeClaimPolicy.allows(guildId, home, true) { _, _ -> GetClaimAtPositionResult.StorageError })
        assertFalse(GuildHomeClaimPolicy.allows(guildId, home, true) { _, _ -> null })
        assertFalse(GuildHomeClaimPolicy.allows(guildId, home, true) { _, _ -> error("claims storage down") })
    }

    @Test
    fun `grandfathered homes may activate unchanged but not move without owned claim`() {
        assertTrue(GuildHomeClaimPolicy.allowsChange(guildId, home, home, true) { _, _ ->
            error("grandfathered location must not need claim lookup")
        })
        val moved = home.copy(position = Position3D(position.x + 1, position.y, position.z))
        assertFalse(GuildHomeClaimPolicy.allowsChange(guildId, moved, home, true) { _, _ ->
            GetClaimAtPositionResult.NoClaimFound
        })
        assertFalse(GuildHomeClaimPolicy.allowsChange(
            guildId, home.copy(worldId = UUID.randomUUID()), home, true,
        ) { _, _ -> GetClaimAtPositionResult.NoClaimFound })
        // A new name with no persisted home is never grandfathered.
        assertFalse(GuildHomeClaimPolicy.allowsChange(guildId, home, null, true) { _, _ ->
            GetClaimAtPositionResult.NoClaimFound
        })
    }

    @Test
    fun `disabled claims leave all home locations valid without lookup`() {
        assertTrue(GuildHomeClaimPolicy.allows(guildId, home, false) { _, _ ->
            error("claim service must not be called with claims disabled")
        })
    }
}
