package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.MembershipHistoryRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.persistence.RelationRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.GuildVaultService
import net.lumalyte.lg.application.services.AdminOverrideService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for GuildService bannerman management methods.
 */
@Suppress("LateinitUsage", "TooManyFunctions")
internal class GuildServiceBannermanTest {
    private var guildRepository: GuildRepository? = null
    private var rankRepository: RankRepository? = null
    private lateinit var memberRepository: MemberRepository
    private lateinit var rankService: RankService
    private lateinit var memberService: MemberService
    private lateinit var nexoEmojiService: NexoEmojiService
    private lateinit var vaultService: GuildVaultService
    private lateinit var hologramService: VaultHologramService
    private lateinit var relationRepository: RelationRepository
    private lateinit var historyRepository: MembershipHistoryRepository
    private lateinit var adminOverrideService: AdminOverrideService

    private lateinit var guildService: GuildServiceBukkit

    private lateinit var testGuild: Guild
    private lateinit var testGuildId: UUID
    private lateinit var ownerId: UUID
    private var memberId: UUID? = null
    private lateinit var ownerRank: Rank
    private lateinit var memberRank: Rank

    @BeforeEach
    fun setUp() {
        initMocks()
        initService()
        initTestData()
    }

    private fun initMocks() {
        guildRepository = mockk(relaxed = true)
        rankRepository = mockk(relaxed = true)
        memberRepository = mockk(relaxed = true)
        rankService = mockk(relaxed = true)
        memberService = mockk(relaxed = true)
        nexoEmojiService = mockk(relaxed = true)
        vaultService = mockk(relaxed = true)
        hologramService = mockk(relaxed = true)
        relationRepository = mockk(relaxed = true)
        historyRepository = mockk(relaxed = true)
        adminOverrideService = mockk(relaxed = true)
    }

    private fun initService() {
        guildService =
            GuildServiceBukkit(
                guildRepository = guildRepository!!,
                rankRepository = rankRepository!!,
                memberRepository = memberRepository,
                rankService = rankService,
                memberService = memberService,
                nexoEmojiService = nexoEmojiService,
                vaultService = vaultService,
                hologramService = hologramService,
                relationRepository = relationRepository,
                historyRepository = historyRepository,
                adminOverrideService = adminOverrideService,
            )
    }

    private fun initTestData() {
        testGuildId = UUID.randomUUID()
        ownerId = UUID.randomUUID()
        memberId = UUID.randomUUID()

        testGuild =
            Guild(
                id = testGuildId,
                name = "Test Guild",
                createdAt = Instant.now(),
                bannermanEnabled = false,
            )

        ownerRank =
            Rank(
                id = UUID.randomUUID(),
                guildId = testGuildId,
                name = "Owner",
                priority = 100,
                permissions = setOf(RankPermission.MANAGE_BANNER),
            )

        memberRank =
            Rank(
                id = UUID.randomUUID(),
                guildId = testGuildId,
                name = "Member",
                priority = 1,
                permissions = emptySet(),
            )
    }

    // ===== setBannermanEnabled Tests =====

    @Test
    fun enableBannermanWithPermission() {
        // Given: Owner with MANAGE_BANNER permission
        val ownerMember = Member(ownerId, testGuildId, ownerRank.id, Instant.now())
        every { guildRepository!!.getById(testGuildId) } returns testGuild
        every { memberRepository.getByPlayerAndGuild(ownerId, testGuildId) } returns ownerMember
        every { rankRepository!!.getById(ownerRank.id) } returns ownerRank
        every { guildRepository!!.update(any()) } returns true

        // When: Enable bannerman
        val result = guildService.setBannermanEnabled(testGuildId, true, ownerId)

        // Then: Should succeed
        assertTrue(result, "setBannermanEnabled should return true")
        verify { guildRepository!!.update(match { it.bannermanEnabled }) }
    }

    @Test
    fun disableBannermanWithPermission() {
        // Given: Guild with bannerman enabled
        val guildWithBannerman = testGuild.copy(bannermanEnabled = true)
        val ownerMember = Member(ownerId, testGuildId, ownerRank.id, Instant.now())
        every { guildRepository!!.getById(testGuildId) } returns guildWithBannerman
        every { memberRepository.getByPlayerAndGuild(ownerId, testGuildId) } returns ownerMember
        every { rankRepository!!.getById(ownerRank.id) } returns ownerRank
        every { guildRepository!!.update(any()) } returns true

        // When: Disable bannerman
        val result = guildService.setBannermanEnabled(testGuildId, false, ownerId)

        // Then: Should succeed
        assertTrue(result, "setBannermanEnabled should return true")
        verify { guildRepository!!.update(match { !it.bannermanEnabled }) }
    }

    @Test
    fun bannermanFailsWithoutPermission() {
        // Given: Member without MANAGE_BANNER permission
        val memberMember = Member(memberId!!, testGuildId, memberRank.id, Instant.now())
        every { guildRepository!!.getById(testGuildId) } returns testGuild
        every { memberRepository.getByPlayerAndGuild(memberId!!, testGuildId) } returns memberMember
        every { rankRepository!!.getById(memberRank.id) } returns memberRank

        // When: Try to enable bannerman
        val result = guildService.setBannermanEnabled(testGuildId, true, memberId!!)

        // Then: Should fail
        assertFalse(result, "setBannermanEnabled should return false without permission")
        verify(exactly = 0) { guildRepository!!.update(any()) }
    }

    @Test
    fun bannermanFailsForMissingGuild() {
        // Given: Non-existent guild
        every { guildRepository!!.getById(testGuildId) } returns null

        // When: Try to enable bannerman
        val result = guildService.setBannermanEnabled(testGuildId, true, ownerId)

        // Then: Should fail
        assertFalse(result, "setBannermanEnabled should return false for non-existent guild")
        verify(exactly = 0) { guildRepository!!.update(any()) }
    }

    // ===== getBannermanEnabled Tests =====

    @Test
    fun getBannermanWhenEnabled() {
        // Given: Guild with bannerman enabled
        val guildWithBannerman = testGuild.copy(bannermanEnabled = true)
        every { guildRepository!!.getById(testGuildId) } returns guildWithBannerman

        // When: Get bannerman enabled status
        val result = guildService.getBannermanEnabled(testGuildId)

        // Then: Should return true
        assertTrue(result, "getBannermanEnabled should return true")
    }

    @Test
    fun getBannermanWhenDisabled() {
        // Given: Guild with bannerman disabled
        every { guildRepository!!.getById(testGuildId) } returns testGuild

        // When: Get bannerman enabled status
        val result = guildService.getBannermanEnabled(testGuildId)

        // Then: Should return false
        assertFalse(result, "getBannermanEnabled should return false")
    }

    @Test
    fun getBannermanForMissingGuild() {
        // Given: Non-existent guild
        every { guildRepository!!.getById(testGuildId) } returns null

        // When: Get bannerman enabled status
        val result = guildService.getBannermanEnabled(testGuildId)

        // Then: Should return false
        assertFalse(result, "getBannermanEnabled should return false for non-existent guild")
    }
}
