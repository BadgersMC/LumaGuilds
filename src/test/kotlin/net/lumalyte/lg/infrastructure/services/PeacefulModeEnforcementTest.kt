package net.lumalyte.lg.infrastructure.services

import net.lumalyte.lg.application.actions.claim.GetClaimAtPosition
import net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult
import net.lumalyte.lg.application.services.ConfigService
import net.lumalyte.lg.application.services.GuildService
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RelationService
import net.lumalyte.lg.application.services.VaultResult
import net.lumalyte.lg.config.GuildConfig
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.Claim
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.GuildMode
import net.lumalyte.lg.domain.entities.RelationType
import org.bukkit.Location
import org.bukkit.World
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import java.util.UUID

/**
 * PR-5 — REQ-007 (peaceful-mode claim PvP), REQ-015 (vault placement vs claims),
 * REQ-027 (peacefulGuildPvpOptIn load + consume).
 */
class PeacefulModeEnforcementTest {

    // ---------- REQ-027: peaceful guild PvP opt-in ----------

    @Test
    fun `peaceful guild PvP is blocked when opt-in is disabled (default)`() {
        val service = modeService(peacefulGuildPvpOptIn = false)

        val peaceful = UUID.randomUUID()
        val hostile = UUID.randomUUID()
        every { memberService.getPlayerGuilds(any()) } returnsMany listOf(setOf(peaceful), setOf(hostile))
        every { guildService.getMode(peaceful) } returns GuildMode.PEACEFUL
        every { guildService.getMode(hostile) } returns GuildMode.HOSTILE

        assertFalse(service.isPvpAllowed(attackerId = peaceful, victimId = hostile))
        assertFalse(service.isPvpAllowed(attackerId = hostile, victimId = peaceful))
    }

    @Test
    fun `peaceful guild PvP is allowed when opt-in is enabled`() {
        val service = modeService(peacefulGuildPvpOptIn = true)

        val peaceful = UUID.randomUUID()
        val hostile = UUID.randomUUID()
        every { memberService.getPlayerGuilds(any()) } returnsMany listOf(setOf(peaceful), setOf(hostile))
        every { guildService.getMode(peaceful) } returns GuildMode.PEACEFUL
        every { guildService.getMode(hostile) } returns GuildMode.HOSTILE
        every { relationService.getRelationType(peaceful, hostile) } returns RelationType.NEUTRAL

        assertTrue(service.isPvpAllowed(attackerId = peaceful, victimId = hostile))
    }

    // ---------- REQ-007: peaceful-mode claim PvP disabled ----------

    @Test
    fun `peaceful territory blocks PvP when claim pvp disabled flag is on`() {
        val service = modeService(peacefulModeClaimPvpDisabled = true)

        val territoryGuild = UUID.randomUUID()
        every { guildService.getMode(territoryGuild) } returns GuildMode.PEACEFUL

        assertFalse(service.isPvpAllowedInTerritory(UUID.randomUUID(), territoryGuild))
    }

    @Test
    fun `peaceful territory allows PvP when claim pvp disabled flag is off`() {
        val service = modeService(peacefulModeClaimPvpDisabled = false)

        val territoryGuild = UUID.randomUUID()
        every { guildService.getMode(territoryGuild) } returns GuildMode.PEACEFUL
        every { memberService.getPlayerGuilds(any()) } returns emptySet()

        assertTrue(service.isPvpAllowedInTerritory(UUID.randomUUID(), territoryGuild))
    }

    // ---------- REQ-015: vault placement validates against claims ----------

    @Test
    fun `vault placement allowed anywhere when claims are disabled`() {
        val service = vaultService(claimsEnabled = false, claimResult = GetClaimAtPositionResult.NoClaimFound)

        val world = mockk<World>()
        every { world.uid } returns UUID.randomUUID()
        val location = mockk<Location>()
        every { location.world } returns world
        every { location.blockX } returns 10
        every { location.blockZ } returns 20
        every { world.isChunkLoaded(any(), any()) } returns true

        val result = service.isValidVaultLocation(location, guild())
        assertTrue((result as VaultResult.Success).data)
    }

    @Test
    fun `vault placement rejected when not inside any claim`() {
        val service = vaultService(claimsEnabled = true, claimResult = GetClaimAtPositionResult.NoClaimFound)

        val world = mockk<World>()
        every { world.uid } returns UUID.randomUUID()
        val location = mockk<Location>()
        every { location.world } returns world
        every { location.blockX } returns 10
        every { location.blockZ } returns 20
        every { world.isChunkLoaded(any(), any()) } returns true

        val result = service.isValidVaultLocation(location, guild())
        assertTrue(result is VaultResult.Failure)
    }

    @Test
    fun `vault placement rejected when claim belongs to another guild`() {
        val otherGuildId = UUID.randomUUID()
        val service = vaultService(
            claimsEnabled = true,
            claimResult = GetClaimAtPositionResult.Success(claim(otherGuildId))
        )

        val world = mockk<World>()
        every { world.uid } returns UUID.randomUUID()
        val location = mockk<Location>()
        every { location.world } returns world
        every { location.blockX } returns 10
        every { location.blockZ } returns 20
        every { world.isChunkLoaded(any(), any()) } returns true

        val result = service.isValidVaultLocation(location, guild())
        assertTrue(result is VaultResult.Failure)
    }

    @Test
    fun `vault placement allowed inside own guild claim`() {
        val guildId = UUID.randomUUID()
        val service = vaultService(
            claimsEnabled = true,
            claimResult = GetClaimAtPositionResult.Success(claim(guildId))
        )

        val world = mockk<World>()
        every { world.uid } returns UUID.randomUUID()
        val location = mockk<Location>()
        every { location.world } returns world
        every { location.blockX } returns 10
        every { location.blockZ } returns 20
        every { world.isChunkLoaded(any(), any()) } returns true

        val result = service.isValidVaultLocation(location, guild(guildId))
        assertTrue((result as VaultResult.Success).data)
    }

    // ---------- helpers ----------

    private val configService = mockk<ConfigService>()
    private val memberService = mockk<MemberService>(relaxed = true)
    private val guildService = mockk<GuildService>(relaxed = true)
    private val relationService = mockk<RelationService>(relaxed = true)

    private fun modeService(
        peacefulGuildPvpOptIn: Boolean = false,
        peacefulModeClaimPvpDisabled: Boolean = true,
    ): ModeServiceBukkit {
        val config = mockk<MainConfig>()
        val guildConfig = mockk<GuildConfig>()
        every { guildConfig.peacefulGuildPvpOptIn } returns peacefulGuildPvpOptIn
        every { guildConfig.peacefulModeClaimPvpDisabled } returns peacefulModeClaimPvpDisabled
        every { config.guild } returns guildConfig
        every { configService.loadConfig() } returns config

        return ModeServiceBukkit(guildService, memberService, relationService, configService)
    }

    private fun vaultService(
        claimsEnabled: Boolean,
        claimResult: GetClaimAtPositionResult,
    ): GuildVaultServiceBukkit {
        val config = mockk<MainConfig>()
        every { config.claimsEnabled } returns claimsEnabled
        every { configService.loadConfig() } returns config

        val getClaimAtPosition = mockk<GetClaimAtPosition>()
        every { getClaimAtPosition.execute(any(), any()) } returns claimResult

        return GuildVaultServiceBukkit(
            plugin = mockk(relaxed = true),
            guildRepository = mockk(relaxed = true),
            vaultRepository = mockk(relaxed = true),
            memberRepository = mockk(relaxed = true),
            configService = configService,
            vaultInventoryManager = mockk(relaxed = true),
            hologramService = mockk(relaxed = true),
            rankService = mockk(relaxed = true),
            getClaimAtPosition = getClaimAtPosition,
            lang = mockk(relaxed = true),
        )
    }

    private fun guild(id: UUID = UUID.randomUUID()): Guild =
        mockk<Guild>(relaxed = true).apply {
            every { this@apply.id } returns id
        }

    private fun claim(teamId: UUID?): Claim =
        Claim(
            id = UUID.randomUUID(),
            worldId = UUID.randomUUID(),
            playerId = UUID.randomUUID(),
            teamId = teamId,
            creationTime = Instant.now(),
            name = "Test Claim",
            description = "",
            position = net.lumalyte.lg.domain.values.Position3D(0, 0, 0),
            icon = "GRASS_BLOCK"
        )
}
