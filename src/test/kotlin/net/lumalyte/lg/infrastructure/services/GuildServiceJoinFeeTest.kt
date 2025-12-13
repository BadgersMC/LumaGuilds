package net.lumalyte.lg.infrastructure.services

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.lumalyte.lg.application.persistence.GuildRepository
import net.lumalyte.lg.application.persistence.MemberRepository
import net.lumalyte.lg.application.persistence.RankRepository
import net.lumalyte.lg.application.services.MemberService
import net.lumalyte.lg.application.services.RankService
import net.lumalyte.lg.application.services.GuildVaultService
import net.lumalyte.lg.domain.entities.Guild
import net.lumalyte.lg.domain.entities.Member
import net.lumalyte.lg.domain.entities.Rank
import net.lumalyte.lg.domain.entities.RankPermission
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant
import java.util.UUID

/**
 * Tests for GuildService join fee management methods.
 */
class GuildServiceJoinFeeTest {

    private lateinit var guildRepository: GuildRepository
    private lateinit var rankRepository: RankRepository
    private lateinit var memberRepository: MemberRepository
    private lateinit var rankService: RankService
    private lateinit var memberService: MemberService
    private lateinit var nexoEmojiService: NexoEmojiService
    private lateinit var vaultService: GuildVaultService
    private lateinit var hologramService: VaultHologramService

    private lateinit var guildService: GuildServiceBukkit

    private lateinit var testGuild: Guild
    private lateinit var testGuildId: UUID
    private lateinit var ownerId: UUID
    private lateinit var memberId: UUID
    private lateinit var ownerRank: Rank
    private lateinit var memberRank: Rank

    @BeforeEach
    fun setUp() {
        // Initialize mocks
        guildRepository = mockk(relaxed = true)
        rankRepository = mockk(relaxed = true)
        memberRepository = mockk(relaxed = true)
        rankService = mockk(relaxed = true)
        memberService = mockk(relaxed = true)
        nexoEmojiService = mockk(relaxed = true)
        vaultService = mockk(relaxed = true)
        hologramService = mockk(relaxed = true)

        // Create service
        guildService = GuildServiceBukkit(
            guildRepository = guildRepository,
            rankRepository = rankRepository,
            memberRepository = memberRepository,
            rankService = rankService,
            memberService = memberService,
            nexoEmojiService = nexoEmojiService,
            vaultService = vaultService,
            hologramService = hologramService
        )

        // Set up test data
        testGuildId = UUID.randomUUID()
        ownerId = UUID.randomUUID()
        memberId = UUID.randomUUID()

        testGuild = Guild(
            id = testGuildId,
            name = "Test Guild",
            createdAt = Instant.now(),
            joinFeeEnabled = false,
            joinFeeAmount = 0
        )

        // Owner rank with MANAGE_GUILD_SETTINGS permission
        ownerRank = Rank(
            id = UUID.randomUUID(),
            guildId = testGuildId,
            name = "Owner",
            priority = 100,
            permissions = setOf(RankPermission.MANAGE_GUILD_SETTINGS, RankPermission.MANAGE_RANKS)
        )

        // Member rank without MANAGE_GUILD_SETTINGS permission
        memberRank = Rank(
            id = UUID.randomUUID(),
            guildId = testGuildId,
            name = "Member",
            priority = 0,
            permissions = emptySet()
        )
    }

    // ===== setJoinFeeEnabled Tests =====

    @Test
    fun `setJoinFeeEnabled should enable join requirements when actor has permission`() {
        // Given: Owner with MANAGE_GUILD_SETTINGS permission
        val ownerMember = Member(ownerId, testGuildId, ownerRank.id, Instant.now())
        every { guildRepository.getById(testGuildId) } returns testGuild
        every { memberRepository.getByPlayerAndGuild(ownerId, testGuildId) } returns ownerMember
        every { rankRepository.getById(ownerRank.id) } returns ownerRank
        every { guildRepository.update(any()) } returns true

        // When: Enable join requirements
        val result = guildService.setJoinFeeEnabled(testGuildId, true, ownerId)

        // Then: Should succeed
        assertTrue(result, "setJoinFeeEnabled should return true")
        verify { guildRepository.update(match { it.joinFeeEnabled }) }
    }

    @Test
    fun `setJoinFeeEnabled should disable join requirements when actor has permission`() {
        // Given: Guild with join fee enabled
        val guildWithFee = testGuild.copy(joinFeeEnabled = true, joinFeeAmount = 500)
        val ownerMember = Member(ownerId, testGuildId, ownerRank.id, Instant.now())
        every { guildRepository.getById(testGuildId) } returns guildWithFee
        every { memberRepository.getByPlayerAndGuild(ownerId, testGuildId) } returns ownerMember
        every { rankRepository.getById(ownerRank.id) } returns ownerRank
        every { guildRepository.update(any()) } returns true

        // When: Disable join requirements
        val result = guildService.setJoinFeeEnabled(testGuildId, false, ownerId)

        // Then: Should succeed
        assertTrue(result, "setJoinFeeEnabled should return true")
        verify { guildRepository.update(match { !it.joinFeeEnabled }) }
    }

    @Test
    fun `setJoinFeeEnabled should fail when actor lacks MANAGE_GUILD_SETTINGS permission`() {
        // Given: Member without MANAGE_GUILD_SETTINGS permission
        val memberMember = Member(memberId, testGuildId, memberRank.id, Instant.now())
        every { guildRepository.getById(testGuildId) } returns testGuild
        every { memberRepository.getByPlayerAndGuild(memberId, testGuildId) } returns memberMember
        every { rankRepository.getById(memberRank.id) } returns memberRank

        // When: Try to enable join requirements
        val result = guildService.setJoinFeeEnabled(testGuildId, true, memberId)

        // Then: Should fail
        assertFalse(result, "setJoinFeeEnabled should return false without permission")
        verify(exactly = 0) { guildRepository.update(any()) }
    }

    @Test
    fun `setJoinFeeEnabled should fail when guild does not exist`() {
        // Given: Non-existent guild
        every { guildRepository.getById(testGuildId) } returns null

        // When: Try to enable join requirements
        val result = guildService.setJoinFeeEnabled(testGuildId, true, ownerId)

        // Then: Should fail
        assertFalse(result, "setJoinFeeEnabled should return false for non-existent guild")
        verify(exactly = 0) { guildRepository.update(any()) }
    }

    // ===== setJoinFeeAmount Tests =====

    @Test
    fun `setJoinFeeAmount should set valid amount when actor has permission`() {
        // Given: Owner with permission
        val ownerMember = Member(ownerId, testGuildId, ownerRank.id, Instant.now())
        every { guildRepository.getById(testGuildId) } returns testGuild
        every { memberRepository.getByPlayerAndGuild(ownerId, testGuildId) } returns ownerMember
        every { rankRepository.getById(ownerRank.id) } returns ownerRank
        every { guildRepository.update(any()) } returns true

        // When: Set join fee amount
        val result = guildService.setJoinFeeAmount(testGuildId, 500, ownerId)

        // Then: Should succeed
        assertTrue(result, "setJoinFeeAmount should return true")
        verify { guildRepository.update(match { it.joinFeeAmount == 500 }) }
    }

    @Test
    fun `setJoinFeeAmount should reject negative amount`() {
        // Given: Owner with permission
        val ownerMember = Member(ownerId, testGuildId, ownerRank.id, Instant.now())
        every { guildRepository.getById(testGuildId) } returns testGuild
        every { memberRepository.getByPlayerAndGuild(ownerId, testGuildId) } returns ownerMember
        every { rankRepository.getById(ownerRank.id) } returns ownerRank

        // When: Try to set negative amount
        val result = guildService.setJoinFeeAmount(testGuildId, -100, ownerId)

        // Then: Should fail
        assertFalse(result, "setJoinFeeAmount should reject negative amount")
        verify(exactly = 0) { guildRepository.update(any()) }
    }

    @Test
    fun `setJoinFeeAmount should allow zero amount`() {
        // Given: Owner with permission
        val ownerMember = Member(ownerId, testGuildId, ownerRank.id, Instant.now())
        every { guildRepository.getById(testGuildId) } returns testGuild
        every { memberRepository.getByPlayerAndGuild(ownerId, testGuildId) } returns ownerMember
        every { rankRepository.getById(ownerRank.id) } returns ownerRank
        every { guildRepository.update(any()) } returns true

        // When: Set join fee amount to zero
        val result = guildService.setJoinFeeAmount(testGuildId, 0, ownerId)

        // Then: Should succeed
        assertTrue(result, "setJoinFeeAmount should allow zero amount")
        verify { guildRepository.update(match { it.joinFeeAmount == 0 }) }
    }

    @Test
    fun `setJoinFeeAmount should fail when actor lacks MANAGE_GUILD_SETTINGS permission`() {
        // Given: Member without permission
        val memberMember = Member(memberId, testGuildId, memberRank.id, Instant.now())
        every { guildRepository.getById(testGuildId) } returns testGuild
        every { memberRepository.getByPlayerAndGuild(memberId, testGuildId) } returns memberMember
        every { rankRepository.getById(memberRank.id) } returns memberRank

        // When: Try to set join fee amount
        val result = guildService.setJoinFeeAmount(testGuildId, 500, memberId)

        // Then: Should fail
        assertFalse(result, "setJoinFeeAmount should fail without permission")
        verify(exactly = 0) { guildRepository.update(any()) }
    }

    @Test
    fun `setJoinFeeAmount should fail when guild does not exist`() {
        // Given: Non-existent guild
        every { guildRepository.getById(testGuildId) } returns null

        // When: Try to set join fee amount
        val result = guildService.setJoinFeeAmount(testGuildId, 500, ownerId)

        // Then: Should fail
        assertFalse(result, "setJoinFeeAmount should fail for non-existent guild")
        verify(exactly = 0) { guildRepository.update(any()) }
    }

    // ===== getJoinFeeSettings Tests =====

    @Test
    fun `getJoinFeeSettings should return current settings`() {
        // Given: Guild with join fee settings
        val guildWithFee = testGuild.copy(joinFeeEnabled = true, joinFeeAmount = 750)
        every { guildRepository.getById(testGuildId) } returns guildWithFee

        // When: Get join fee settings
        val result = guildService.getJoinFeeSettings(testGuildId)

        // Then: Should return correct values
        assertNotNull(result)
        assertEquals(true, result!!.first, "joinFeeEnabled should be true")
        assertEquals(750, result.second, "joinFeeAmount should be 750")
    }

    @Test
    fun `getJoinFeeSettings should return null for non-existent guild`() {
        // Given: Non-existent guild
        every { guildRepository.getById(testGuildId) } returns null

        // When: Get join fee settings
        val result = guildService.getJoinFeeSettings(testGuildId)

        // Then: Should return null
        assertNull(result, "getJoinFeeSettings should return null for non-existent guild")
    }

    @Test
    fun `getJoinFeeSettings should return disabled settings for new guild`() {
        // Given: New guild with default settings
        every { guildRepository.getById(testGuildId) } returns testGuild

        // When: Get join fee settings
        val result = guildService.getJoinFeeSettings(testGuildId)

        // Then: Should return default disabled values
        assertNotNull(result)
        assertEquals(false, result!!.first, "joinFeeEnabled should be false by default")
        assertEquals(0, result.second, "joinFeeAmount should be 0 by default")
    }

    // ===== Combined Operations Tests =====

    @Test
    fun `setJoinFeeAmount should allow large values`() {
        // Given: Owner with permission
        val ownerMember = Member(ownerId, testGuildId, ownerRank.id, Instant.now())
        every { guildRepository.getById(testGuildId) } returns testGuild
        every { memberRepository.getByPlayerAndGuild(ownerId, testGuildId) } returns ownerMember
        every { rankRepository.getById(ownerRank.id) } returns ownerRank
        every { guildRepository.update(any()) } returns true

        // When: Set large amount
        val result = guildService.setJoinFeeAmount(testGuildId, 999999, ownerId)

        // Then: Should succeed
        assertTrue(result, "setJoinFeeAmount should allow large values")
        verify { guildRepository.update(match { it.joinFeeAmount == 999999 }) }
    }
}
