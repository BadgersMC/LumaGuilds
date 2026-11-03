package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.*
import net.lumalyte.lg.application.actions.claim.GetClaimAtPosition
import net.lumalyte.lg.application.results.claim.GetClaimAtPositionResult
import net.lumalyte.lg.application.services.*
import net.lumalyte.lg.config.MainConfig
import net.lumalyte.lg.domain.entities.*
import net.lumalyte.lg.domain.values.Position3D
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertFalse
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.time.Instant
import java.util.UUID

class GuildHomeWritePermissionTest {
    private val guildId = UUID.randomUUID()
    private val actorId = UUID.randomUUID()
    private val rankId = UUID.randomUUID()
    private val ownerRankId = UUID.randomUUID()
    private val home = GuildHome(UUID.randomUUID(), Position3D(0, 64, 0), setOf(ownerRankId))
    private val guildRepository = mockk<GuildRepository>()
    private val memberRepository = mockk<MemberRepository>()
    private val rankRepository = mockk<RankRepository>()
    private val configService = mockk<ConfigService>()
    private val adminOverride = mockk<AdminOverrideService>()
    private lateinit var service: GuildServiceBukkit
    private var actorPermissions: Set<RankPermission> = emptySet()

    @BeforeEach
    fun setup() {
        stopKoin()
        every { configService.loadConfig() } returns MainConfig(chapterTwoRewardsEnabled = false, claimsEnabled = false)
        startKoin { modules(module { single { configService } }) }
        val guild = Guild(
            id = guildId, name = "Homes", createdAt = Instant.now(),
            homes = GuildHomes(mapOf("existing" to home))
        )
        every { guildRepository.getById(guildId) } returns guild
        every { guildRepository.update(any()) } returns false
        every { memberRepository.getByPlayerAndGuild(actorId, guildId) } returns Member(actorId, guildId, rankId, Instant.now())
        every { rankRepository.getById(rankId) } answers {
            Rank(rankId, guildId, "Home Manager", 10, actorPermissions)
        }
        every { rankRepository.getHighestRank(guildId) } returns Rank(ownerRankId, guildId, "Owner", 0, RankPermission.entries.toSet())
        every { adminOverride.hasOverride(actorId) } returns false
        service = GuildServiceBukkit(
            guildRepository, rankRepository, memberRepository,
            mockk<RankService>(relaxed = true),
            mockk<MemberService>(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), adminOverride,
            mockk(relaxed = true),
        )
    }

    @AfterEach
    fun cleanup() = stopKoin()

    @Test
    fun `MANAGE_HOME permits writing an existing guild home without MANAGE_MEMBERS`() {
        actorPermissions = setOf(RankPermission.MANAGE_MEMBERS)
        assertFalse(service.setHome(guildId, "existing", home, actorId))
        verify(exactly = 0) { guildRepository.update(any()) }

        actorPermissions = setOf(RankPermission.MANAGE_HOME)
        // Repository intentionally rejects persistence: verify the service reached
        // the write stage, rather than denying the home-only manager.
        assertFalse(service.setHome(guildId, "existing", home, actorId))
        verify(exactly = 1) { guildRepository.update(any()) }
    }

    @Test
    fun `direct service write refuses unclaimed personal enemy and claim lookup failures`() {
        actorPermissions = setOf(RankPermission.MANAGE_HOME)
        every { configService.loadConfig() } returns MainConfig(chapterTwoRewardsEnabled = false, claimsEnabled = true)
        val claimLookup = mockk<GetClaimAtPosition>()
        org.koin.core.context.loadKoinModules(module { single { claimLookup } })
        val movedHome = home.copy(position = Position3D(1, 64, 0))
        val world = movedHome.worldId
        val position = movedHome.position
        val foreign = net.lumalyte.lg.domain.entities.Claim(world, actorId, UUID.randomUUID(), position, "Foreign")
        val personal = net.lumalyte.lg.domain.entities.Claim(world, actorId, null, position, "Personal")
        val results = listOf(
            GetClaimAtPositionResult.NoClaimFound,
            GetClaimAtPositionResult.StorageError,
            GetClaimAtPositionResult.Success(foreign),
            GetClaimAtPositionResult.Success(personal),
        )
        results.forEach { result ->
            every { claimLookup.execute(world, position) } returns result
            assertFalse(service.setHome(guildId, "existing", movedHome, actorId))
        }
        every { claimLookup.execute(world, position) } throws IllegalStateException("claims unavailable")
        assertFalse(service.setHome(guildId, "existing", movedHome, actorId))
        verify(exactly = 0) { guildRepository.update(any()) }
    }

    @Test
    fun `direct service write accepts guild owned claim and reaches persistence`() {
        actorPermissions = setOf(RankPermission.MANAGE_HOME)
        every { configService.loadConfig() } returns MainConfig(chapterTwoRewardsEnabled = false, claimsEnabled = true)
        val claimLookup = mockk<GetClaimAtPosition>()
        org.koin.core.context.loadKoinModules(module { single { claimLookup } })
        val movedHome = home.copy(position = Position3D(1, 64, 0))
        every { claimLookup.execute(movedHome.worldId, movedHome.position) } returns GetClaimAtPositionResult.Success(
            net.lumalyte.lg.domain.entities.Claim(movedHome.worldId, actorId, guildId, movedHome.position, "Ours")
        )
        assertFalse(service.setHome(guildId, "existing", movedHome, actorId))
        verify(exactly = 1) { guildRepository.update(any()) }
    }

    @Test
    fun `direct service preserves exact unchanged home even if legacy claims are absent`() {
        actorPermissions = setOf(RankPermission.MANAGE_HOME)
        every { configService.loadConfig() } returns MainConfig(chapterTwoRewardsEnabled = false, claimsEnabled = true)
        // A missing claim service would fail closed for every new or moved location.
        // This unchanged Chapter 1 location is deliberately exempt.
        assertFalse(service.setHome(guildId, "existing", home, actorId))
        verify(exactly = 1) { guildRepository.update(any()) }
    }

    @Test
    fun `MANAGE_HOME permits removing a guild home without MANAGE_MEMBERS`() {
        actorPermissions = setOf(RankPermission.MANAGE_MEMBERS)
        assertFalse(service.removeHome(guildId, "existing", actorId))
        verify(exactly = 0) { guildRepository.update(any()) }

        actorPermissions = setOf(RankPermission.MANAGE_HOME)
        assertFalse(service.removeHome(guildId, "existing", actorId))
        verify(exactly = 1) { guildRepository.update(any()) }
    }

    @Test
    fun `MANAGE_HOME permits clearing all homes without MANAGE_MEMBERS`() {
        actorPermissions = setOf(RankPermission.MANAGE_MEMBERS)
        assertFalse(service.removeHome(guildId, actorId))
        verify(exactly = 0) { guildRepository.update(any()) }

        actorPermissions = setOf(RankPermission.MANAGE_HOME)
        assertFalse(service.removeHome(guildId, actorId))
        verify(exactly = 1) { guildRepository.update(any()) }
    }
}
