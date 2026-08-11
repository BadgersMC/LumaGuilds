package net.lumalyte.lg.interaction.listeners

import net.lumalyte.lg.application.actions.claim.GetClaimAtPosition
import net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult
import net.lumalyte.lg.application.services.CombatService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.domain.entities.Claim
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID

/**
 * PR-5 — REQ-007: ClaimPvpProtectionListener event-path tests.
 *
 * Verifies the full Bukkit pipeline: claim resolution at the victim's location,
 * projectile-shooter resolution, `CombatService.canAttack` delegation, and
 * cancellation of rejected damage (allowed and rejected attacks in a
 * guild-owned claim).
 */
class ClaimPvpProtectionListenerTest {

    private val combatService = mockk<CombatService>()
    private val getClaimAtPosition = mockk<GetClaimAtPosition>()
    private val guildService = mockk<GuildService>()

    private val worldId = UUID.randomUUID()
    private val territoryGuildId = UUID.randomUUID()

    private lateinit var listener: ClaimPvpProtectionListener

    @BeforeEach
    fun setUp() {
        stopKoin()
        startKoin {
            modules(module {
                single { combatService }
                single { getClaimAtPosition }
                single { guildService }
                single { ClaimPvpProtectionListener() }
            })
        }
        listener = ClaimPvpProtectionListener()
    }

    // ---------- pipeline pieces ----------

    @Test
    fun `resolveAttacker returns direct player attacker`() {
        val attacker = mockk<Player>(relaxed = true)
        assertTrue(listener.resolveAttacker(attacker) === attacker)
    }

    @Test
    fun `resolveAttacker returns projectile shooter`() {
        val shooter = mockk<Player>(relaxed = true)
        val projectile = mockk<Projectile>()
        every { projectile.shooter } returns shooter

        assertTrue(listener.resolveAttacker(projectile) === shooter)
    }

    @Test
    fun `resolveAttacker returns null for non-player damager`() {
        assertTrue(listener.resolveAttacker(mockk(relaxed = true)) == null)
    }

    @Test
    fun `resolveTerritoryGuild returns guild for a guild-owned claim at position`() {
        val claim = guildClaim(territoryGuildId)
        every { getClaimAtPosition.execute(worldId, any()) } returns GetClaimAtPositionResult.Success(claim)
        every { guildService.getGuild(territoryGuildId) } returns mockk(relaxed = true)

        val result = listener.resolveTerritoryGuild(worldId, 10, 20)
        assertTrue(result == territoryGuildId)
        verify { getClaimAtPosition.execute(worldId, net.lumalyte.lg.domain.values.Position2D(10, 20)) }
    }

    @Test
    fun `resolveTerritoryGuild returns null when no claim exists`() {
        every { getClaimAtPosition.execute(worldId, any()) } returns GetClaimAtPositionResult.NoClaimFound

        assertTrue(listener.resolveTerritoryGuild(worldId, 10, 20) == null)
    }

    @Test
    fun `resolveTerritoryGuild returns null for a personal claim without guild`() {
        val claim = Claim(
            id = UUID.randomUUID(),
            worldId = worldId,
            playerId = UUID.randomUUID(),
            teamId = null,
            creationTime = Instant.now(),
            name = "Personal Claim",
            description = "",
            position = net.lumalyte.lg.domain.values.Position3D(0, 0, 0),
            icon = "GRASS_BLOCK"
        )
        every { getClaimAtPosition.execute(worldId, any()) } returns GetClaimAtPositionResult.Success(claim)

        assertTrue(listener.resolveTerritoryGuild(worldId, 10, 20) == null)
    }

    // ---------- full event path ----------

    @Test
    fun `onPlayerDamage cancels rejected attack in guild-owned claim`() {
        val attacker = player(UUID.randomUUID())
        val victim = player(UUID.randomUUID())
        every { getClaimAtPosition.execute(worldId, any()) } returns
            GetClaimAtPositionResult.Success(guildClaim(territoryGuildId))
        every { guildService.getGuild(territoryGuildId) } returns mockk(relaxed = true)
        every { combatService.canAttack(attacker.uniqueId, victim.uniqueId, territoryGuildId) } returns false

        val event = damageEvent(attacker, victim)

        listener.onPlayerDamage(event)

        assertTrue(event.isCancelled)
        verify { combatService.canAttack(attacker.uniqueId, victim.uniqueId, territoryGuildId) }
    }

    @Test
    fun `onPlayerDamage allows legal attack in guild-owned claim`() {
        val attacker = player(UUID.randomUUID())
        val victim = player(UUID.randomUUID())
        every { getClaimAtPosition.execute(worldId, any()) } returns
            GetClaimAtPositionResult.Success(guildClaim(territoryGuildId))
        every { guildService.getGuild(territoryGuildId) } returns mockk(relaxed = true)
        every { combatService.canAttack(attacker.uniqueId, victim.uniqueId, territoryGuildId) } returns true

        val event = damageEvent(attacker, victim)

        listener.onPlayerDamage(event)

        assertFalse(event.isCancelled)
    }

    @Test
    fun `onPlayerDamage ignores damage outside any claim`() {
        val attacker = player(UUID.randomUUID())
        val victim = player(UUID.randomUUID())
        every { getClaimAtPosition.execute(worldId, any()) } returns GetClaimAtPositionResult.NoClaimFound

        val event = damageEvent(attacker, victim)

        listener.onPlayerDamage(event)

        assertFalse(event.isCancelled)
        verify(exactly = 0) { combatService.canAttack(any(), any(), any()) }
    }

    @Test
    fun `onPlayerDamage ignores non-player victims`() {
        val attacker = player(UUID.randomUUID())
        val victim = mockk<org.bukkit.entity.Zombie>(relaxed = true)
        val world = mockk<World>()
        every { world.uid } returns worldId
        every { victim.location } returns location(world)

        val event = mockk<EntityDamageByEntityEvent>(relaxed = true)
        every { event.entity } returns victim
        every { event.damager } returns attacker

        listener.onPlayerDamage(event)

        assertFalse(event.isCancelled)
        verify(exactly = 0) { combatService.canAttack(any(), any(), any()) }
    }

    // ---------- helpers ----------

    private fun player(id: UUID): Player {
        val p = mockk<Player>(relaxed = true)
        every { p.uniqueId } returns id
        val world = mockk<World>()
        every { world.uid } returns worldId
        every { p.location } returns location(world)
        every { p.world } returns world
        return p
    }

    private fun location(world: World): Location {
        val loc = mockk<Location>()
        every { loc.world } returns world
        every { loc.blockX } returns 10
        every { loc.blockZ } returns 20
        return loc
    }

    private fun damageEvent(attacker: Player, victim: Player): EntityDamageByEntityEvent {
        val event = mockk<EntityDamageByEntityEvent>(relaxed = true)
        var cancelled = false
        every { event.entity } returns victim
        every { event.damager } returns attacker
        every { event.isCancelled } answers { cancelled }
        every { event.isCancelled = any() } answers { cancelled = arg(0) }
        return event
    }

    private fun guildClaim(teamId: UUID): Claim =
        Claim(
            id = UUID.randomUUID(),
            worldId = worldId,
            playerId = UUID.randomUUID(),
            teamId = teamId,
            creationTime = Instant.now(),
            name = "Guild Claim",
            description = "",
            position = net.lumalyte.lg.domain.values.Position3D(0, 0, 0),
            icon = "GRASS_BLOCK"
        )
}
